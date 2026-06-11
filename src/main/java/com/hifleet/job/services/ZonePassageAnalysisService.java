package com.hifleet.job.services;



import com.alibaba.fastjson.JSON;

import com.alibaba.fastjson.JSONArray;

import com.alibaba.fastjson.JSONObject;

import com.hifleet.beans.ShipForm;
import com.hifleet.job.ZonePassagePeriod;

import com.hifleet.domain.ZoneCrossingEvent;

import com.hifleet.domain.ZoneDefinition;

import com.hifleet.hbase.GetTrajectoryFromHbase;

import com.hifleet.hbase.NewOffersForm;

import com.hifleet.repository.AIS41GeneralQueryRepository;

import com.hifleet.repository.ZonePassageStatusRepository;

import com.hifleet.route.beans.NewShipForm;

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

import org.springframework.stereotype.Service;



import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;

import java.util.Collections;

import java.util.HashSet;

import java.util.List;

import java.util.Set;

import java.util.Vector;



/**

 * 区域通行分析主服务。

 * <p>

 * 参考 {@link AisAnalysisService} 的船舶循环方式，对每条船依次执行：

 * <ol>

 *   <li>大风浪/大风区域进出（maintenance.tools StormAreaIntersectService）</li>

 *   <li>海峡进出（event StraitPassDetector + ais41.alertzones）</li>

 *   <li>海区进出（event SeaAreaPassDetector + ais41.world_sea）</li>

 * </ol>

 * <p>

 * 支持断点续跑：每月全船任务执行过的船舶记录在 ship_zone_passage_status 表，

 * 任务中断后重启会自动跳过已完成的船，只计算未完成的。

 */

@Service

public class ZonePassageAnalysisService {



    private static final Logger log = LoggerFactory.getLogger(ZonePassageAnalysisService.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");



    private final GetTrajectoryFromHbase getTrajectoryFromHbase = new GetTrajectoryFromHbase();

    private final ZoneCrossingDetector zoneCrossingDetector = new ZoneCrossingDetector();



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
     * 按周期执行任务（年/月/日/周 Job 入口）。
     * <p>
     * 根据当前时间自动计算轨迹起止时间，再传入后续计算逻辑。
     *
     * @param period 周期类型：YEAR/MONTH/DAY/WEEK
     * @param params 可选，仅支持 mmsi（指定船舶），不传则算全船；时间范围由 period 决定
     */
    public boolean analysisZonePassageByPeriod(ZonePassagePeriod period, String params) {
        JobParams jobParams = parseParams(params);
        applyPeriodTimeRange(jobParams, period);
        log.info("区域通行分析任务开始, period={}, params={}", period, jobParams);
        XxlJobLogger.log("区域通行分析任务开始, period=" + period + ", " + jobParams);
        return executeAnalysis(jobParams);
    }

    /**
     * 根据周期类型和当前时间，计算轨迹查询起止时间及批次标识。
     */
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

    /**
     * 任务执行主逻辑：循环船舶，查轨迹，三种区域计算，断点续跑。
     */
    private boolean executeAnalysis(JobParams jobParams) {



        // 预加载海峡、海区维表（全船共用，只加载一次）

        List<ZoneDefinition> straits = zoneLoaderService.loadStraits();

        List<ZoneDefinition> seaAreas = zoneLoaderService.loadSeaAreas();

        ZoneSpatialMatcher straitMatcher = straits.isEmpty() ? null : new ZoneSpatialMatcher(straits);

        ZoneSpatialMatcher seaAreaMatcher = seaAreas.isEmpty() ? null : new ZoneSpatialMatcher(seaAreas);



        // 确定本次要处理的船舶列表

        List<String> allMmsiList = resolveMmsiList(jobParams);

        if (allMmsiList.isEmpty()) {

            XxlJobLogger.log("没有可处理的船舶，任务结束");

            return false;

        }



        // 断点续跑：查询本批次已完成的船舶，只处理未完成的

        List<String> pendingMmsiList = filterPendingShips(allMmsiList, jobParams);

        XxlJobLogger.log("本批次=" + jobParams.getBatchMonth()

                + "，总船舶=" + allMmsiList.size()

                + "，待处理=" + pendingMmsiList.size()

                + "，已跳过=" + (allMmsiList.size() - pendingMmsiList.size()));



        if (pendingMmsiList.isEmpty()) {

            XxlJobLogger.log("本批次所有船舶已计算完成，无需重复执行");

            return true;

        }



        int processed = 0;

        int totalEvents = 0;

        for (String mmsi : pendingMmsiList) {

            try {

                // 在 for 循环内统一查询轨迹，三个计算方法共用同一份轨迹数据

                Vector<NewShipForm> trajectoryPoints = loadTrajectoryPoints(

                        mmsi, jobParams.getStartTime(), jobParams.getEndTime());



                if (trajectoryPoints.isEmpty()) {

                    // 无轨迹也标记完成，避免下次重复查

                    zonePassageStatusRepository.markCompleted(jobParams.getBatchMonth(), mmsi,

                            jobParams.getStartTime(), jobParams.getEndTime(), 0);

                    processed++;

                    continue;

                }



                int shipEvents = 0;



                // 步骤1：大风浪 / 大风（传入轨迹，不再内部查 HBase）

                List<ZoneCrossingEvent> stormEvents = calculateStormWavePassage(mmsi, trajectoryPoints);

                zoneCrossingStoreService.saveEvents(stormEvents);

                shipEvents += stormEvents.size();



                // 步骤2：海峡（传入轨迹）

                List<ZoneCrossingEvent> straitEvents = calculateStraitPassage(mmsi, trajectoryPoints, straitMatcher);

                zoneCrossingStoreService.saveEvents(straitEvents);

                shipEvents += straitEvents.size();



                // 步骤3：海区（传入轨迹）

                List<ZoneCrossingEvent> seaAreaEvents = calculateSeaAreaPassage(mmsi, trajectoryPoints, seaAreaMatcher);

                zoneCrossingStoreService.saveEvents(seaAreaEvents);

                shipEvents += seaAreaEvents.size();



                // 记录本船本批次已完成，下次重启会跳过

                zonePassageStatusRepository.markCompleted(jobParams.getBatchMonth(), mmsi,

                        jobParams.getStartTime(), jobParams.getEndTime(), shipEvents);



                totalEvents += shipEvents;

                processed++;

                if (processed % 50 == 0) {

                    XxlJobLogger.log("已处理 " + processed + "/" + pendingMmsiList.size()

                            + "，累计事件 " + totalEvents);

                }

            } catch (Exception ex) {

                log.error("处理船舶失败, mmsi={}", mmsi, ex);

                zonePassageStatusRepository.markFailed(jobParams.getBatchMonth(), mmsi,

                        jobParams.getStartTime(), jobParams.getEndTime());

                XxlJobLogger.log("处理船舶失败 mmsi=" + mmsi + "，原因：" + ex.getMessage());

            }

        }



        log.info("区域通行分析任务结束, ships={}/{}, events={}", processed, pendingMmsiList.size(), totalEvents);

        XxlJobLogger.log("任务结束, 本次处理=" + processed + "/" + pendingMmsiList.size()

                + ", events=" + totalEvents);

        return true;

    }



    /**

     * 过滤出本批次尚未完成的船舶。

     */

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



    /**

     * 步骤1：计算大风浪/大风区域进出。

     */

    public List<ZoneCrossingEvent> calculateStormWavePassage(String mmsi, Vector<NewShipForm> trajectoryPoints) {

        return stormWavePassageService.calculateStormPassage(mmsi, trajectoryPoints);

    }



    /**

     * 步骤2：计算海峡进出。

     */

    public List<ZoneCrossingEvent> calculateStraitPassage(String mmsi, Vector<NewShipForm> trajectoryPoints,

                                                          ZoneSpatialMatcher straitMatcher) {

        if (straitMatcher == null || trajectoryPoints == null || trajectoryPoints.isEmpty()) {

            return Collections.emptyList();

        }

        List<ZoneCrossingEvent> events = zoneCrossingDetector.detectEvents(mmsi, trajectoryPoints, straitMatcher);

        tagEvents(events, ZoneCrossingEvent.CATEGORY_STRAIT, "ais41.alertzones");

        return events;

    }



    /**

     * 步骤3：计算海区进出。

     */

    public List<ZoneCrossingEvent> calculateSeaAreaPassage(String mmsi, Vector<NewShipForm> trajectoryPoints,

                                                           ZoneSpatialMatcher seaAreaMatcher) {

        if (seaAreaMatcher == null || trajectoryPoints == null || trajectoryPoints.isEmpty()) {

            return Collections.emptyList();

        }

        List<ZoneCrossingEvent> events = zoneCrossingDetector.detectEvents(mmsi, trajectoryPoints, seaAreaMatcher);

        tagEvents(events, ZoneCrossingEvent.CATEGORY_SEA_AREA, "ais41.world_sea");

        return events;

    }



    /**

     * 从 HBase 查询船舶轨迹（在 for 循环内调用一次，结果传给三个计算方法）。

     */

    private Vector<NewShipForm> loadTrajectoryPoints(String mmsi, String startTime, String endTime) {

        NewOffersForm offers = getTrajectoryFromHbase.getOriginalVoffersFromHbase(mmsi, startTime, endTime);

        if (offers.getOffers() == null) {

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



    /**
     * 解析 Job 参数，仅支持 mmsi。
     * <p>
     * 支持格式：
     * <ul>
     *   <li>空：全船</li>
     *   <li>413123456：纯 MMSI 字符串</li>
     *   <li>mmsi=413123456</li>
     *   <li>{"mmsi":"413123456"}</li>
     *   <li>{"mmsiList":["413123456","413123457"]}</li>
     * </ul>
     */
    private JobParams parseParams(String params) {

        JobParams jobParams = new JobParams();

        if (StringUtils.isBlank(params)) {

            return jobParams;

        }

        String trimmed = params.trim();

        try {

            if (trimmed.startsWith("{")) {

                JSONObject json = JSON.parseObject(trimmed);

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

                String[] kv = trimmed.split("=", 2);

                if (kv.length == 2 && "mmsi".equalsIgnoreCase(kv[0].trim())) {

                    jobParams.getMmsiList().add(kv[1].trim());

                }

            } else {

                jobParams.getMmsiList().add(trimmed);

            }

        } catch (Exception ex) {

            log.warn("parse mmsi failed, raw={}", trimmed, ex);

        }

        return jobParams;

    }



    private static class JobParams {

        /** 指定船舶 MMSI 列表，为空表示全船 */

        private final List<String> mmsiList = new ArrayList<String>();

        /** 轨迹开始时间，由周期 Job 自动计算 */

        private String startTime;

        /** 轨迹结束时间，由周期 Job 自动计算 */

        private String endTime;

        /** 批次标识，断点续跑分组键，由周期 Job 自动计算 */

        private String batchMonth;



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



        @Override

        public String toString() {

            return "JobParams{mmsiList=" + (mmsiList.isEmpty() ? "ALL" : mmsiList) +

                    ", batchMonth='" + batchMonth + '\'' +

                    ", startTime='" + startTime + '\'' +

                    ", endTime='" + endTime + '\'' +

                    '}';

        }

    }

}


