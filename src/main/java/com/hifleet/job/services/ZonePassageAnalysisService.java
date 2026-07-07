package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.beans.ShipForm;
import com.hifleet.domain.ZoneCrossingEvent;
import com.hifleet.domain.ZoneDefinition;
import com.hifleet.hbase.GetTrajectoryFromHbase;
import com.hifleet.hbase.NewOffersForm;
import com.hifleet.job.ZonePassagePeriod;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ZonePassageStatusRepository;
import com.hifleet.repository.ZonePassageStatusRepository.CompletedRecord;
import com.hifleet.route.beans.NewShipForm;
import com.hifleet.service.MultiZoneCrossingDetector;
import com.hifleet.service.StormRegionIndex;
import com.hifleet.service.StormWavePassageService;
import com.hifleet.service.ZoneCrossingDetector;
import com.hifleet.service.ZoneCrossingStoreService;
import com.hifleet.service.ZoneLoaderService;
import com.hifleet.service.ZoneSpatialMatcher;
import com.xxl.job.core.log.XxlJobLogger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 区域通行分析主服务。
 * <p>
 * 支持断点续跑：每周期全船任务执行过的船舶记录在 ship_zone_passage_status 表，
 * 任务中断后重启会自动跳过已完成的船，只计算未完成的。
 */
@Service
public class ZonePassageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ZonePassageAnalysisService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int WEEK_DAYS = 7;
    private static final String WIND_FARM_NAME_PREFIX = "风电";
    private static final String ALERT_AREA_ZONE_SOURCE = "ais41.alertzones";

    private final GetTrajectoryFromHbase getTrajectoryFromHbase = new GetTrajectoryFromHbase();
    private final MultiZoneCrossingDetector multiZoneCrossingDetector = new MultiZoneCrossingDetector();
    private final ZoneCrossingDetector zoneCrossingDetector = new ZoneCrossingDetector();

    @Value("${zone.passage.parallelism:4}")
    private int shipParallelism;

    @Value("${zone.passage.progress-interval:100}")
    private int progressInterval;

    @Value("${zone.passage.status.batch-size:50}")
    private int statusBatchSize;

    /** HBase 轨迹 DP 抽稀 zoom，与 AisAnalysisService getVoffersFromHbase 一致；≤0 表示不抽稀。推荐 8~9 */
    @Value("${zone.passage.trajectory.simplify-zoom:9}")
    private int trajectorySimplifyZoom;

    @Value("${zone.passage.backfill.from-date:2025-01-01}")
    private String backfillFromDate;

    /** 补算结束日期（不含当天），默认 2026-07-01 表示覆盖至 2026-06 最后一周 */
    @Value("${zone.passage.backfill.to-date:2026-07-01}")
    private String backfillToDate;

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    @Autowired
    private ZoneLoaderService zoneLoaderService;

    @Autowired
    private StormWavePassageService stormWavePassageService;

    @Autowired
    private ZoneCrossingStoreService zoneCrossingStoreService;

    @Autowired
    private ZonePassageStatusRepository zonePassageStatusRepository;

    /**
     * 历史补算入口：单次 Job 从断点周起循环处理至范围结束，按周查轨迹，支持周级 + 船级断点续跑。
     * <p>
     * 默认范围 2025-01-01～2026-06（按自然周切分，每段 7 天）。任务中断后下次从断点周继续。
     * <p>
     * 参数示例：
     * <ul>
     *   <li>空：循环补算范围内所有未完成周</li>
     *   <li>{@code week=2025-01-01}：只补该周（周一起算）</li>
     *   <li>{@code mmsi=413123456}：只算指定船</li>
     * </ul>
     */
    public BackfillResult analysisZonePassageWeeklyBackfill(String params) {
        JobParams baseParams = parseParams(params);
        applyDefaultBackfillRange(baseParams);

        List<String> allMmsiList = resolveMmsiList(baseParams);
        if (allMmsiList.isEmpty()) {
            XxlJobLogger.log("没有可处理的船舶，任务结束");
            return BackfillResult.failed("没有可处理的船舶");
        }

        LocalDateTime rangeStart = resolveBackfillStart(baseParams);
        LocalDateTime rangeEnd = resolveBackfillEnd(baseParams);
        List<WeekSlice> pendingWeeks = resolvePendingWeeks(baseParams, allMmsiList, rangeStart, rangeEnd);
        if (pendingWeeks.isEmpty()) {
            String msg = "补算范围 " + formatDate(rangeStart) + "~" + formatDate(rangeEnd) + " 内所有周均已完成";
            log.info(msg);
            XxlJobLogger.log(msg);
            return BackfillResult.allComplete(msg);
        }

        WeekSlice firstWeek = pendingWeeks.get(0);
        WeekSlice lastWeekInList = pendingWeeks.get(pendingWeeks.size() - 1);
        XxlJobLogger.log("补算范围 " + formatDate(rangeStart) + "~" + formatDate(rangeEnd)
                + " | 本次循环周数=" + pendingWeeks.size()
                + " | 首周=" + firstWeek.getBatchKey()
                + " | 末周=" + lastWeekInList.getBatchKey()
                + " | 全船=" + allMmsiList.size()
                + " | 并行度=" + effectiveParallelism());

        ZoneContext zoneContext = loadZoneContext();
        int totalProcessed = 0;
        int totalFailed = 0;
        int totalEvents = 0;
        String lastBatchKey = null;

        for (WeekSlice week : pendingWeeks) {
            lastBatchKey = week.getBatchKey();
            JobParams weekParams = copyTimeFields(baseParams);
            applyWeekRange(weekParams, week);

            int doneCount = zonePassageStatusRepository.countByStatus(
                    weekParams.getBatchMonth(), weekParams.getStartTime(), weekParams.getEndTime(), "DONE");
            int failedCount = zonePassageStatusRepository.countByStatus(
                    weekParams.getBatchMonth(), weekParams.getStartTime(), weekParams.getEndTime(), "FAILED");
            List<String> pendingMmsiList = filterPendingShips(allMmsiList, weekParams);

            XxlJobLogger.log(">>> 开始周 " + week.getBatchKey()
                    + " (" + weekParams.getStartTime() + " ~ " + weekParams.getEndTime() + ")"
                    + " | 已完成=" + doneCount
                    + " | 失败待重试=" + failedCount
                    + " | 待处理=" + pendingMmsiList.size());

            AnalysisResult weekResult = executeAnalysis(weekParams, zoneContext, allMmsiList);
            totalProcessed += weekResult.getProcessed();
            totalFailed += weekResult.getFailed();
            totalEvents += weekResult.getTotalEvents();

            XxlJobLogger.log("<<< 周 " + week.getBatchKey() + " 结束：" + weekResult.getSummary());

            if (!weekResult.isSuccess()) {
                String message = "周 " + week.getBatchKey() + " 存在失败船舶，已停止后续周，请修复后重跑";
                XxlJobLogger.log(message);
                return BackfillResult.of(lastBatchKey, totalProcessed, totalFailed, totalEvents, message);
            }
        }

        String message = "补算范围 " + formatDate(rangeStart) + "~" + formatDate(rangeEnd)
                + " 全部周已完成，共循环 " + pendingWeeks.size() + " 周";
        XxlJobLogger.log(message);
        return BackfillResult.of(lastBatchKey, totalProcessed, totalFailed, totalEvents, message);
    }

    public boolean analysisZonePassageByPeriod(ZonePassagePeriod period, String params) {
        JobParams jobParams = parseParams(params);
        applyPeriodTimeRange(jobParams, period);
        log.info("区域通行分析任务开始, period={}, params={}", period, jobParams);
        XxlJobLogger.log("区域通行分析任务开始, period=" + period + ", " + jobParams);
        return executeAnalysis(jobParams).isSuccess();
    }

    public boolean analysisZonePassageByTimeRange(String params,
                                                  String defaultStartTime,
                                                  String defaultEndTime,
                                                  String defaultBatchMonth) {
        JobParams jobParams = parseParams(params);
        applyFixedTimeRange(jobParams, defaultStartTime, defaultEndTime, defaultBatchMonth);
        log.info("区域通行分析任务开始, timeRange={}, params={}", defaultBatchMonth, jobParams);
        XxlJobLogger.log("区域通行分析任务开始, batch=" + jobParams.getBatchMonth() + ", " + jobParams);
        return executeAnalysis(jobParams).isSuccess();
    }

    private void applyDefaultBackfillRange(JobParams baseParams) {
        if (StringUtils.isBlank(baseParams.getFromDate()) && StringUtils.isBlank(baseParams.getFromMonth())) {
            baseParams.setFromDate(backfillFromDate);
        }
        if (StringUtils.isBlank(baseParams.getToDate()) && StringUtils.isBlank(baseParams.getToMonth())) {
            baseParams.setToDate(backfillToDate);
        }
        if (StringUtils.isNotBlank(baseParams.getFromMonth()) && StringUtils.isBlank(baseParams.getFromDate())) {
            baseParams.setFromDate(baseParams.getFromMonth() + "-01");
        }
        if (StringUtils.isNotBlank(baseParams.getToMonth()) && StringUtils.isBlank(baseParams.getToDate())) {
            YearMonth endMonth = YearMonth.parse(baseParams.getToMonth().trim(), MONTH_FORMATTER);
            baseParams.setToDate(endMonth.plusMonths(1).atDay(1).format(DATE_FORMATTER));
        }
    }

    private LocalDateTime resolveBackfillStart(JobParams baseParams) {
        if (StringUtils.isNotBlank(baseParams.getWeek())) {
            return parseDate(baseParams.getWeek()).atStartOfDay();
        }
        String date = StringUtils.isNotBlank(baseParams.getFromDate())
                ? baseParams.getFromDate() : backfillFromDate;
        return parseDate(date).atStartOfDay();
    }

    private LocalDateTime resolveBackfillEnd(JobParams baseParams) {
        if (StringUtils.isNotBlank(baseParams.getWeek())) {
            return parseDate(baseParams.getWeek()).plusDays(WEEK_DAYS).atStartOfDay();
        }
        String date = StringUtils.isNotBlank(baseParams.getToDate())
                ? baseParams.getToDate() : backfillToDate;
        return parseDate(date).atStartOfDay();
    }

    private List<WeekSlice> buildWeekSlices(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        List<WeekSlice> weeks = new ArrayList<WeekSlice>();
        LocalDateTime cursor = rangeStart;
        while (cursor.isBefore(rangeEnd)) {
            LocalDateTime weekEnd = cursor.plusDays(WEEK_DAYS);
            if (weekEnd.isAfter(rangeEnd)) {
                weekEnd = rangeEnd;
            }
            weeks.add(new WeekSlice(cursor, weekEnd));
            cursor = weekEnd;
        }
        return weeks;
    }

    private List<WeekSlice> resolvePendingWeeks(JobParams baseParams, List<String> allMmsiList,
                                                LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        List<WeekSlice> candidates;
        if (StringUtils.isNotBlank(baseParams.getWeek())) {
            LocalDateTime weekStart = parseDate(baseParams.getWeek()).atStartOfDay();
            LocalDateTime weekEnd = weekStart.plusDays(WEEK_DAYS);
            if (weekEnd.isAfter(rangeEnd)) {
                weekEnd = rangeEnd;
            }
            candidates = Collections.singletonList(new WeekSlice(weekStart, weekEnd));
        } else {
            candidates = buildWeekSlices(rangeStart, rangeEnd);
        }

        List<WeekSlice> pendingWeeks = new ArrayList<WeekSlice>();
        for (WeekSlice week : candidates) {
            JobParams weekParams = copyTimeFields(baseParams);
            applyWeekRange(weekParams, week);
            if (!filterPendingShips(allMmsiList, weekParams).isEmpty()) {
                pendingWeeks.add(week);
            }
        }
        return pendingWeeks;
    }

    private void applyWeekRange(JobParams jobParams, WeekSlice week) {
        jobParams.setStartTime(week.getStart().format(TIME_FORMATTER));
        jobParams.setEndTime(week.getEnd().format(TIME_FORMATTER));
        jobParams.setBatchMonth(week.getBatchKey());
    }

    private LocalDate parseDate(String value) {
        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_FORMATTER);
    }

    private JobParams copyTimeFields(JobParams source) {
        JobParams copy = new JobParams();
        copy.getMmsiList().addAll(source.getMmsiList());
        copy.setFromMonth(source.getFromMonth());
        copy.setToMonth(source.getToMonth());
        copy.setMonth(source.getMonth());
        copy.setWeek(source.getWeek());
        copy.setFromDate(source.getFromDate());
        copy.setToDate(source.getToDate());
        return copy;
    }

    private void applyPeriodTimeRange(JobParams jobParams, ZonePassagePeriod period) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start;
        String batchKey;
        switch (period) {
            case YEAR:
                start = end.minusYears(1);
                batchKey = end.format(DateTimeFormatter.ofPattern("yyyy")) + "-YEAR";
                break;
            case MONTH:
                start = end.minusMonths(1);
                batchKey = end.format(MONTH_FORMATTER);
                break;
            case DAY:
                start = end.minusDays(1);
                batchKey = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                break;
            case WEEK:
                start = end.minusWeeks(1);
                batchKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "-W";
                break;
            default:
                start = end.minusMonths(1);
                batchKey = end.format(MONTH_FORMATTER);
        }
        jobParams.setStartTime(start.format(TIME_FORMATTER));
        jobParams.setEndTime(end.format(TIME_FORMATTER));
        jobParams.setBatchMonth(batchKey);
    }

    private void applyFixedTimeRange(JobParams jobParams,
                                     String defaultStartTime,
                                     String defaultEndTime,
                                     String defaultBatchMonth) {
        if (StringUtils.isBlank(jobParams.getStartTime())) {
            jobParams.setStartTime(defaultStartTime);
        }
        if (StringUtils.isBlank(jobParams.getEndTime())) {
            jobParams.setEndTime(defaultEndTime);
        }
        if (StringUtils.isBlank(jobParams.getBatchMonth())) {
            jobParams.setBatchMonth(defaultBatchMonth);
        }
    }

    private AnalysisResult executeAnalysis(JobParams jobParams) {
        ZoneContext zoneContext = loadZoneContext();
        List<String> allMmsiList = resolveMmsiList(jobParams);
        return executeAnalysis(jobParams, zoneContext, allMmsiList);
    }

    private AnalysisResult executeAnalysis(JobParams jobParams, ZoneContext zoneContext, List<String> allMmsiList) {
        if (allMmsiList.isEmpty()) {
            XxlJobLogger.log("没有可处理的船舶，任务结束");
            return AnalysisResult.failed("没有可处理的船舶");
        }

        List<String> pendingMmsiList = filterPendingShips(allMmsiList, jobParams);
        XxlJobLogger.log("本批次=" + jobParams.getBatchMonth()
                + "，总船舶=" + allMmsiList.size()
                + "，待处理=" + pendingMmsiList.size()
                + "，已跳过=" + (allMmsiList.size() - pendingMmsiList.size()));

        if (pendingMmsiList.isEmpty()) {
            XxlJobLogger.log("本批次所有船舶已计算完成，无需重复执行");
            return AnalysisResult.success(0, 0, 0);
        }

        if (!stormWavePassageService.initPgAvailability()) {
            XxlJobLogger.log("PostgreSQL 不可达，本次跳过风浪/大风，仅计算海峡/海区/桥区/风电区/alertarea");
        }

        StormRegionIndex stormRegionIndex = stormWavePassageService.loadStormRegions(
                jobParams.getStartTime(), jobParams.getEndTime());
        XxlJobLogger.log("预加载风浪区域 wave=" + stormRegionIndex.getWaveCount()
                + "，wind=" + stormRegionIndex.getWindCount());

        XxlJobLogger.log("alertarea 可计算区域数量=" + zoneContext.getAlertAreaZoneCount()
                + "，船级并行度=" + effectiveParallelism()
                + "，轨迹抽稀 zoom=" + trajectorySimplifyZoom + "（getVoffersFromHbase）");

        int parallelism = effectiveParallelism();
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger totalEvents = new AtomicInteger(0);
        int logInterval = Math.max(1, progressInterval);
        StatusBatchBuffer statusBatchBuffer = new StatusBatchBuffer(zonePassageStatusRepository, statusBatchSize);

        if (parallelism <= 1) {
            for (String mmsi : pendingMmsiList) {
                processShip(mmsi, jobParams, zoneContext, stormRegionIndex, statusBatchBuffer,
                        processed, failed, totalEvents, pendingMmsiList.size(), logInterval);
            }
        } else {
            ExecutorService executor = Executors.newFixedThreadPool(parallelism);
            try {
                for (final String mmsi : pendingMmsiList) {
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            processShip(mmsi, jobParams, zoneContext, stormRegionIndex, statusBatchBuffer,
                                    processed, failed, totalEvents, pendingMmsiList.size(), logInterval);
                        }
                    });
                }
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(7, TimeUnit.DAYS)) {
                        executor.shutdownNow();
                        XxlJobLogger.log("任务等待超时，已强制停止线程池");
                    }
                } catch (InterruptedException ex) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        statusBatchBuffer.flush();

        AnalysisResult result = new AnalysisResult(processed.get(), failed.get(), totalEvents.get());
        log.info("区域通行分析任务结束, {}", result.getSummary());
        XxlJobLogger.log("任务结束, " + result.getSummary());
        return result;
    }

    private int effectiveParallelism() {
        return Math.max(1, shipParallelism);
    }

    private void processShip(String mmsi,
                             JobParams jobParams,
                             ZoneContext zoneContext,
                             StormRegionIndex stormRegionIndex,
                             StatusBatchBuffer statusBatchBuffer,
                             AtomicInteger processed,
                             AtomicInteger failed,
                             AtomicInteger totalEvents,
                             int totalShips,
                             int logInterval) {
        try {
            Vector<NewShipForm> trajectoryPoints = loadTrajectoryPoints(
                    mmsi, jobParams.getStartTime(), jobParams.getEndTime());

            if (trajectoryPoints.isEmpty()) {
                statusBatchBuffer.markCompleted(jobParams.getBatchMonth(), mmsi,
                        jobParams.getStartTime(), jobParams.getEndTime(), 0);
                int done = processed.incrementAndGet();
                logProgress(done, totalShips, totalEvents.get(), logInterval);
                return;
            }

            List<ZoneCrossingEvent> allEvents = new ArrayList<ZoneCrossingEvent>();
            allEvents.addAll(stormWavePassageService.calculateStormPassage(mmsi, trajectoryPoints, stormRegionIndex));
            allEvents.addAll(multiZoneCrossingDetector.detectEvents(
                    mmsi,
                    trajectoryPoints,
                    zoneContext.getStraitMatcher(),
                    zoneContext.getSeaAreaMatcher(),
                    zoneContext.getBridgeWindFarmMatcher(),
                    zoneContext.getAlertAreaMatcher()));

            zoneCrossingStoreService.saveEventsForShip(
                    mmsi, jobParams.getStartTime(), jobParams.getEndTime(), allEvents);

            statusBatchBuffer.markCompleted(jobParams.getBatchMonth(), mmsi,
                    jobParams.getStartTime(), jobParams.getEndTime(), allEvents.size());

            totalEvents.addAndGet(allEvents.size());
            int done = processed.incrementAndGet();
            logProgress(done, totalShips, totalEvents.get(), logInterval);
        } catch (Exception ex) {
            failed.incrementAndGet();
            log.error("处理船舶失败, mmsi={}", mmsi, ex);
            zonePassageStatusRepository.markFailed(jobParams.getBatchMonth(), mmsi,
                    jobParams.getStartTime(), jobParams.getEndTime());
            XxlJobLogger.log("处理船舶失败 mmsi=" + mmsi + "，原因：" + ex.getMessage());
        }
    }

    private void logProgress(int processed, int totalShips, int totalEvents, int logInterval) {
        if (processed % logInterval == 0 || processed == totalShips) {
            XxlJobLogger.log("已处理 " + processed + "/" + totalShips + "，累计事件 " + totalEvents);
        }
    }

    private ZoneContext loadZoneContext() {
        List<ZoneDefinition> straits = zoneLoaderService.loadStraits();
        List<ZoneDefinition> seaAreas = zoneLoaderService.loadSeaAreas();
        List<ZoneDefinition> bridgeAndWindFarmZones = zoneLoaderService.loadBridgeAndWindFarmZones();
        List<ZoneDefinition> alertAreaZones = filterCalculableAlertAreaZones(zoneLoaderService.loadAlertAreaZones());

        ZoneSpatialMatcher straitMatcher = straits.isEmpty() ? null : new ZoneSpatialMatcher(straits);
        ZoneSpatialMatcher seaAreaMatcher = seaAreas.isEmpty() ? null : new ZoneSpatialMatcher(seaAreas);
        ZoneSpatialMatcher bridgeWindFarmMatcher = bridgeAndWindFarmZones.isEmpty()
                ? null : new ZoneSpatialMatcher(bridgeAndWindFarmZones);
        ZoneSpatialMatcher alertAreaMatcher = alertAreaZones.isEmpty()
                ? null : new ZoneSpatialMatcher(alertAreaZones);

        return new ZoneContext(straitMatcher, seaAreaMatcher, bridgeWindFarmMatcher, alertAreaMatcher, alertAreaZones.size());
    }

    private List<String> filterPendingShips(List<String> allMmsiList, JobParams jobParams) {
        Set<String> completed = zonePassageStatusRepository.findCompletedMmsi(
                jobParams.getBatchMonth(), jobParams.getStartTime(), jobParams.getEndTime());
        List<String> pending = new ArrayList<String>();
        for (String mmsi : allMmsiList) {
            if (!completed.contains(mmsi)) {
                pending.add(mmsi);
            }
        }
        return pending;
    }

    public List<ZoneCrossingEvent> calculateStormWavePassage(String mmsi,
                                                             Vector<NewShipForm> trajectoryPoints,
                                                             StormRegionIndex stormRegionIndex) {
        return stormWavePassageService.calculateStormPassage(mmsi, trajectoryPoints, stormRegionIndex);
    }

    /**
     * 质量评估抽样回放：按与 Job 相同逻辑重算事件，不落库。
     */
    public List<ZoneCrossingEvent> recalculateEventsForShip(String mmsi, String startTime, String endTime) {
        Vector<NewShipForm> trajectoryPoints = loadTrajectoryPoints(mmsi, startTime, endTime);
        if (trajectoryPoints.isEmpty()) {
            return Collections.emptyList();
        }
        ZoneContext zoneContext = loadZoneContext();
        stormWavePassageService.initPgAvailability();
        StormRegionIndex stormRegionIndex = stormWavePassageService.loadStormRegions(startTime, endTime);
        List<ZoneCrossingEvent> allEvents = new ArrayList<ZoneCrossingEvent>();
        allEvents.addAll(stormWavePassageService.calculateStormPassage(mmsi, trajectoryPoints, stormRegionIndex));
        allEvents.addAll(multiZoneCrossingDetector.detectEvents(
                mmsi, trajectoryPoints,
                zoneContext.getStraitMatcher(), zoneContext.getSeaAreaMatcher(),
                zoneContext.getBridgeWindFarmMatcher(), zoneContext.getAlertAreaMatcher()));
        return allEvents;
    }

    private static final class StatusBatchBuffer {
        private final ZonePassageStatusRepository repository;
        private final int batchSize;
        private final List<CompletedRecord> buffer = new ArrayList<CompletedRecord>();

        private StatusBatchBuffer(ZonePassageStatusRepository repository, int batchSize) {
            this.repository = repository;
            this.batchSize = Math.max(1, batchSize);
        }

        private synchronized void markCompleted(String batchMonth, String mmsi,
                                                String startTime, String endTime, int eventCount) {
            buffer.add(new CompletedRecord(batchMonth, mmsi, startTime, endTime, eventCount));
            if (buffer.size() >= batchSize) {
                flushLocked();
            }
        }

        private synchronized void flush() {
            flushLocked();
        }

        private void flushLocked() {
            if (buffer.isEmpty()) {
                return;
            }
            repository.markCompletedBatch(new ArrayList<CompletedRecord>(buffer));
            buffer.clear();
        }
    }

    public List<ZoneCrossingEvent> calculateStraitPassage(String mmsi, Vector<NewShipForm> trajectoryPoints,
                                                          ZoneSpatialMatcher straitMatcher) {
        if (straitMatcher == null || trajectoryPoints == null || trajectoryPoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<ZoneCrossingEvent> events = zoneCrossingDetector.detectEvents(mmsi, trajectoryPoints, straitMatcher);
        tagEvents(events, ZoneCrossingEvent.CATEGORY_STRAIT, "ais41.alertzones");
        return events;
    }

    public List<ZoneCrossingEvent> calculateSeaAreaPassage(String mmsi, Vector<NewShipForm> trajectoryPoints,
                                                           ZoneSpatialMatcher seaAreaMatcher) {
        if (seaAreaMatcher == null || trajectoryPoints == null || trajectoryPoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<ZoneCrossingEvent> events = zoneCrossingDetector.detectEvents(mmsi, trajectoryPoints, seaAreaMatcher);
        tagEvents(events, ZoneCrossingEvent.CATEGORY_SEA_AREA, "ais41.world_sea");
        return events;
    }

    public List<ZoneCrossingEvent> calculateBridgeAndWindFarmPassage(String mmsi, Vector<NewShipForm> trajectoryPoints,
                                                                     ZoneSpatialMatcher bridgeWindFarmMatcher) {
        if (bridgeWindFarmMatcher == null || trajectoryPoints == null || trajectoryPoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<ZoneCrossingEvent> events = zoneCrossingDetector.detectEvents(mmsi, trajectoryPoints, bridgeWindFarmMatcher);
        tagBridgeAndWindFarmEvents(events);
        return events;
    }

    private void tagBridgeAndWindFarmEvents(List<ZoneCrossingEvent> events) {
        for (ZoneCrossingEvent event : events) {
            String zoneName = event.getZoneName();
            if (zoneName != null && zoneName.startsWith(WIND_FARM_NAME_PREFIX)) {
                event.setEventCategory(ZoneCrossingEvent.CATEGORY_WIND_FARM);
            } else {
                event.setEventCategory(ZoneCrossingEvent.CATEGORY_BRIDGE);
            }
            event.setZoneSource("ais41.alertzones");
        }
    }

    public List<ZoneCrossingEvent> calculateAlertAreaPassage(String mmsi, Vector<NewShipForm> trajectoryPoints,
                                                             ZoneSpatialMatcher alertAreaMatcher) {
        if (alertAreaMatcher == null || trajectoryPoints == null || trajectoryPoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<ZoneCrossingEvent> events = zoneCrossingDetector.detectEvents(mmsi, trajectoryPoints, alertAreaMatcher);
        return tagAlertAreaEvents(events);
    }

    private List<ZoneCrossingEvent> tagAlertAreaEvents(List<ZoneCrossingEvent> events) {
        List<ZoneCrossingEvent> tagged = new ArrayList<ZoneCrossingEvent>();
        for (ZoneCrossingEvent event : events) {
            String category = resolveAlertAreaCategory(event.getZoneName());
            if (category == null) {
                continue;
            }
            event.setEventCategory(category);
            event.setZoneSource(ALERT_AREA_ZONE_SOURCE);
            tagged.add(event);
        }
        return tagged;
    }

    private String resolveAlertAreaCategory(String zoneName) {
        if (StringUtils.isBlank(zoneName)) {
            return null;
        }
        String name = zoneName.trim();
        if (name.contains("桥") || name.contains("海盗")) {
            return null;
        }
        if (name.contains("(JWC)")) {
            return ZoneCrossingEvent.CATEGORY_JWC;
        }
        if (StringUtils.containsIgnoreCase(name, "marpol")) {
            return ZoneCrossingEvent.CATEGORY_MARPOL;
        }
        if (name.contains("排放控制区")) {
            return ZoneCrossingEvent.CATEGORY_EMISSION_CONTROL;
        }
        if (name.contains("养殖区")) {
            return ZoneCrossingEvent.CATEGORY_AQUACULTURE;
        }
        if (name.contains("海洋保护区")) {
            return ZoneCrossingEvent.CATEGORY_MARINE_PROTECTED;
        }
        if (name.contains("冰区")) {
            return ZoneCrossingEvent.CATEGORY_ICE;
        }
        if (name.contains("高风险警示区") || name.contains("警示区")) {
            return ZoneCrossingEvent.CATEGORY_HIGH_RISK;
        }
        return null;
    }

    private List<ZoneDefinition> filterCalculableAlertAreaZones(List<ZoneDefinition> zones) {
        List<ZoneDefinition> result = new ArrayList<ZoneDefinition>();
        if (zones == null) {
            return result;
        }
        for (ZoneDefinition zone : zones) {
            if (zone != null && resolveAlertAreaCategory(zone.getName()) != null) {
                result.add(zone);
            }
        }
        log.info("alertarea zones loaded={}, calculable={}", zones.size(), result.size());
        return result;
    }

    private Vector<NewShipForm> loadTrajectoryPoints(String mmsi, String startTime, String endTime) {
        NewOffersForm offers;
        if (trajectorySimplifyZoom > 0) {
            offers = getTrajectoryFromHbase.getVoffersFromHbase(mmsi, startTime, endTime, trajectorySimplifyZoom);
        } else {
            offers = getTrajectoryFromHbase.getOriginalVoffersFromHbase(mmsi, startTime, endTime);
        }
        if (offers == null || offers.getOffers() == null) {
            return new Vector<NewShipForm>();
        }
        return offers.getOffers();
    }

    private void tagEvents(List<ZoneCrossingEvent> events, String category, String source) {
        for (ZoneCrossingEvent event : events) {
            event.setEventCategory(category);
            event.setZoneSource(source);
        }
    }

    private List<String> resolveMmsiList(JobParams jobParams) {
        if (!jobParams.getMmsiList().isEmpty()) {
            return jobParams.getMmsiList();
        }
        List<ShipForm> ships = ais41GeneralQueryRepository.selectRecentShips();
        List<String> mmsiList = new ArrayList<String>();
        if (ships != null) {
            for (ShipForm ship : ships) {
                if (ship != null && StringUtils.isNotBlank(ship.getMmsi())) {
                    mmsiList.add(ship.getMmsi().trim());
                }
            }
        }
        log.info("从 ais41.recentships 加载船舶数量={}", mmsiList.size());
        return mmsiList;
    }

    private JobParams parseParams(String params) {
        JobParams jobParams = new JobParams();
        if (StringUtils.isBlank(params)) {
            return jobParams;
        }

        String trimmed = params.trim();
        try {
            if (trimmed.startsWith("{")) {
                JSONObject json = JSON.parseObject(trimmed);
                if (json.containsKey("startTime")) {
                    jobParams.setStartTime(json.getString("startTime"));
                }
                if (json.containsKey("endTime")) {
                    jobParams.setEndTime(json.getString("endTime"));
                }
                if (json.containsKey("batchMonth")) {
                    jobParams.setBatchMonth(json.getString("batchMonth"));
                }
                if (json.containsKey("month")) {
                    jobParams.setMonth(json.getString("month"));
                }
                if (json.containsKey("week")) {
                    jobParams.setWeek(json.getString("week"));
                }
                if (json.containsKey("fromDate")) {
                    jobParams.setFromDate(json.getString("fromDate"));
                }
                if (json.containsKey("toDate")) {
                    jobParams.setToDate(json.getString("toDate"));
                }
                if (json.containsKey("fromMonth")) {
                    jobParams.setFromMonth(json.getString("fromMonth"));
                }
                if (json.containsKey("toMonth")) {
                    jobParams.setToMonth(json.getString("toMonth"));
                }
                if (json.containsKey("mmsi")) {
                    jobParams.getMmsiList().add(String.valueOf(json.get("mmsi")));
                }
                JSONArray mmsiArray = json.getJSONArray("mmsiList");
                if (mmsiArray != null) {
                    for (int i = 0; i < mmsiArray.size(); i++) {
                        jobParams.getMmsiList().add(String.valueOf(mmsiArray.get(i)));
                    }
                }
            } else if (trimmed.contains("=")) {
                parseKeyValueParams(trimmed, jobParams);
            } else if (trimmed.matches("\\d{9}")) {
                jobParams.getMmsiList().add(trimmed);
            } else if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                jobParams.setWeek(trimmed);
            } else if (trimmed.matches("\\d{4}-\\d{2}")) {
                jobParams.setMonth(trimmed);
            } else {
                jobParams.getMmsiList().add(trimmed);
            }
        } catch (Exception ex) {
            log.warn("parse job params failed, raw={}", trimmed, ex);
        }
        return jobParams;
    }

    private void parseKeyValueParams(String params, JobParams jobParams) {
        String[] parts = params.split("[;,]");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            if ("mmsi".equalsIgnoreCase(key)) {
                jobParams.getMmsiList().add(value);
            } else if ("startTime".equalsIgnoreCase(key)) {
                jobParams.setStartTime(value);
            } else if ("endTime".equalsIgnoreCase(key)) {
                jobParams.setEndTime(value);
            } else if ("batchMonth".equalsIgnoreCase(key)) {
                jobParams.setBatchMonth(value);
            } else if ("month".equalsIgnoreCase(key)) {
                jobParams.setMonth(value);
            } else if ("week".equalsIgnoreCase(key)) {
                jobParams.setWeek(value);
            } else if ("fromDate".equalsIgnoreCase(key)) {
                jobParams.setFromDate(value);
            } else if ("toDate".equalsIgnoreCase(key)) {
                jobParams.setToDate(value);
            } else if ("fromMonth".equalsIgnoreCase(key)) {
                jobParams.setFromMonth(value);
            } else if ("toMonth".equalsIgnoreCase(key)) {
                jobParams.setToMonth(value);
            }
        }
    }

    public static final class AnalysisResult {
        private final int processed;
        private final int failed;
        private final int totalEvents;

        private AnalysisResult(int processed, int failed, int totalEvents) {
            this.processed = processed;
            this.failed = failed;
            this.totalEvents = totalEvents;
        }

        public static AnalysisResult success(int processed, int failed, int totalEvents) {
            return new AnalysisResult(processed, failed, totalEvents);
        }

        public static AnalysisResult failed(String message) {
            return new AnalysisResult(0, 1, 0);
        }

        public boolean isSuccess() {
            return failed == 0;
        }

        public int getProcessed() {
            return processed;
        }

        public int getFailed() {
            return failed;
        }

        public int getTotalEvents() {
            return totalEvents;
        }

        public String getSummary() {
            return "processed=" + processed + ", failed=" + failed + ", events=" + totalEvents;
        }
    }

    public static final class BackfillResult {
        private final boolean success;
        private final boolean allComplete;
        private final String lastBatchKey;
        private final int processed;
        private final int failed;
        private final int totalEvents;
        private final String message;

        private BackfillResult(boolean success, boolean allComplete, String lastBatchKey,
                               int processed, int failed, int totalEvents, String message) {
            this.success = success;
            this.allComplete = allComplete;
            this.lastBatchKey = lastBatchKey;
            this.processed = processed;
            this.failed = failed;
            this.totalEvents = totalEvents;
            this.message = message;
        }

        public static BackfillResult allComplete(String message) {
            return new BackfillResult(true, true, null, 0, 0, 0, message);
        }

        public static BackfillResult failed(String message) {
            return new BackfillResult(false, false, null, 0, 0, 0, message);
        }

        public static BackfillResult of(String lastBatchKey, int processed, int failed, int totalEvents, String message) {
            return new BackfillResult(failed == 0, false, lastBatchKey, processed, failed, totalEvents, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isAllComplete() {
            return allComplete;
        }

        public String getSummary() {
            return message + " [" + processed + " ships, failed=" + failed + ", events=" + totalEvents + "]";
        }
    }

    private static final class WeekSlice {
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final String batchKey;

        private WeekSlice(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
            this.batchKey = start.format(DATE_FORMATTER) + "-W";
        }

        private LocalDateTime getStart() {
            return start;
        }

        private LocalDateTime getEnd() {
            return end;
        }

        private String getBatchKey() {
            return batchKey;
        }
    }

    private static final class ZoneContext {
        private final ZoneSpatialMatcher straitMatcher;
        private final ZoneSpatialMatcher seaAreaMatcher;
        private final ZoneSpatialMatcher bridgeWindFarmMatcher;
        private final ZoneSpatialMatcher alertAreaMatcher;
        private final int alertAreaZoneCount;

        private ZoneContext(ZoneSpatialMatcher straitMatcher,
                            ZoneSpatialMatcher seaAreaMatcher,
                            ZoneSpatialMatcher bridgeWindFarmMatcher,
                            ZoneSpatialMatcher alertAreaMatcher,
                            int alertAreaZoneCount) {
            this.straitMatcher = straitMatcher;
            this.seaAreaMatcher = seaAreaMatcher;
            this.bridgeWindFarmMatcher = bridgeWindFarmMatcher;
            this.alertAreaMatcher = alertAreaMatcher;
            this.alertAreaZoneCount = alertAreaZoneCount;
        }

        private ZoneSpatialMatcher getStraitMatcher() {
            return straitMatcher;
        }

        private ZoneSpatialMatcher getSeaAreaMatcher() {
            return seaAreaMatcher;
        }

        private ZoneSpatialMatcher getBridgeWindFarmMatcher() {
            return bridgeWindFarmMatcher;
        }

        private ZoneSpatialMatcher getAlertAreaMatcher() {
            return alertAreaMatcher;
        }

        private int getAlertAreaZoneCount() {
            return alertAreaZoneCount;
        }
    }

    private static class JobParams {
        private final List<String> mmsiList = new ArrayList<String>();
        private String startTime;
        private String endTime;
        private String batchMonth;
        private String month;
        private String week;
        private String fromMonth;
        private String toMonth;
        private String fromDate;
        private String toDate;

        public List<String> getMmsiList() {
            return mmsiList;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public String getBatchMonth() {
            return batchMonth;
        }

        public void setBatchMonth(String batchMonth) {
            this.batchMonth = batchMonth;
        }

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }

        public String getWeek() {
            return week;
        }

        public void setWeek(String week) {
            this.week = week;
        }

        public String getFromMonth() {
            return fromMonth;
        }

        public void setFromMonth(String fromMonth) {
            this.fromMonth = fromMonth;
        }

        public String getToMonth() {
            return toMonth;
        }

        public void setToMonth(String toMonth) {
            this.toMonth = toMonth;
        }

        public String getFromDate() {
            return fromDate;
        }

        public void setFromDate(String fromDate) {
            this.fromDate = fromDate;
        }

        public String getToDate() {
            return toDate;
        }

        public void setToDate(String toDate) {
            this.toDate = toDate;
        }

        @Override
        public String toString() {
            return "JobParams{mmsiList=" + (mmsiList.isEmpty() ? "ALL" : mmsiList)
                    + ", week='" + week + '\''
                    + ", fromDate='" + fromDate + '\''
                    + ", toDate='" + toDate + '\''
                    + ", batchMonth='" + batchMonth + '\''
                    + ", startTime='" + startTime + '\''
                    + ", endTime='" + endTime + '\''
                    + '}';
        }
    }
}
