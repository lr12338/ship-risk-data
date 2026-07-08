package com.hifleet.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.beans.ShipForm;
import com.hifleet.domain.ZoneCrossingEvent;
import com.hifleet.domain.ZoneDefinition;
import com.hifleet.job.services.ZonePassageAnalysisService;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ZonePassageQualityRepository;
import com.hifleet.repository.ZonePassageQualityRepository.BatchKey;
import com.hifleet.repository.ZonePassageQualityRepository.CategoryStatRecord;
import com.hifleet.repository.ZonePassageQualityRepository.CategoryStatRow;
import com.hifleet.repository.ZonePassageQualityRepository.CheckRecord;
import com.hifleet.repository.ZonePassageQualityRepository.DuplicateEvent;
import com.hifleet.repository.ZonePassageQualityRepository.EventCountMismatch;
import com.hifleet.repository.ZonePassageQualityRepository.QualityIssue;
import com.hifleet.repository.ZonePassageQualityRepository.QualityReport;
import com.hifleet.repository.ZonePassageQualityRepository.StoredEvent;
import com.hifleet.repository.ZonePassageQualityRepository.ShipProfile;
import com.hifleet.repository.ZonePassageQualityRepository.UnbalancedPair;
import com.hifleet.repository.ZonePassageQualityRepository.ZoneStatRecord;
import com.hifleet.repository.ZonePassageQualityRepository.ZoneStatRow;
import com.xxl.job.core.log.XxlJobLogger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 区域通行数据质量评估引擎。
 * <p>
 * 针对 zonePassageHistory2025JobHandler 产出的两张表：
 * <ul>
 *   <li>ship_zone_passage_status — 计算进度</li>
 *   <li>ship_zone_crossing_event — 各区域进出事件</li>
 * </ul>
 * 支持：独立 Job 评估已完成数据 / 已评估批次跳过（不与补算 Job 耦合）。
 */
@Service
public class ZonePassageQualityService {

    private static final Logger log = LoggerFactory.getLogger(ZonePassageQualityService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_WARN = "WARN";
    public static final String STATUS_FAIL = "FAIL";
    public static final String SEV_ERROR = "ERROR";
    public static final String SEV_WARN = "WARN";
    public static final String SEV_PASS = "PASS";

    public static final String CHK_COMPLETENESS = "TASK_COMPLETENESS";
    public static final String CHK_EVENT_RECONCILE = "EVENT_COUNT_RECONCILE";
    public static final String CHK_TIME_RANGE = "EVENT_TIME_RANGE";
    public static final String CHK_REQUIRED_FIELDS = "REQUIRED_FIELDS";
    public static final String CHK_ENTER_EXIT_BALANCE = "ENTER_EXIT_BALANCE";
    public static final String CHK_ENTER_BEFORE_EXIT = "ENTER_BEFORE_EXIT";
    public static final String CHK_DUPLICATE = "DUPLICATE_EVENT";
    public static final String CHK_INVALID_CATEGORY = "INVALID_CATEGORY";
    public static final String CHK_REPLAY_SAMPLE = "REPLAY_SAMPLE";
    public static final String CHK_SPATIAL = "SPATIAL_SPOT_CHECK";

    @Value("${zone.passage.backfill.from-date:2025-01-01}")
    private String backfillFromDate;

    @Value("${zone.passage.backfill.to-date:2026-07-01}")
    private String backfillToDate;

    @Value("${zone.passage.quality.samples-per-stratum:2}")
    private int samplesPerStratum;

    @Value("${zone.passage.quality.max-replay-ships:60}")
    private int maxReplayShips;

    @Value("${zone.passage.quality.max-spatial-ships:30}")
    private int maxSpatialShips;

    @Value("${zone.passage.quality.max-spatial-events:60}")
    private int maxSpatialEvents;

    /** replay 是否启用（max-replay-ships > 0） */
    private boolean replayEnabledConfig() {
        return maxReplayShips > 0;
    }

    private boolean spatialEnabledConfig() {
        return maxSpatialEvents > 0;
    }

    @Autowired
    private ZonePassageQualityRepository repo;

    @Autowired
    private AIS41GeneralQueryRepository ais41Repo;

    @Autowired
    private ZonePassageAnalysisService analysisService;

    @Autowired
    private ZoneLoaderService zoneLoaderService;

    /**
     * 独立评估 Job 入口：只评估已计算完成的批次。
     */
    public EvaluateResult evaluateCompletedData(String params) {
        return evaluate(params);
    }

    public EvaluateResult evaluate(String params) {
        EvaluateParams p = parseParams(params);
        String rangeStart = p.getFromDate().atStartOfDay().format(TIME_FMT);
        String rangeEnd = p.getToDate().atStartOfDay().format(TIME_FMT);
        int currentShipCount = resolveCurrentShipCount();

        List<BatchKey> candidates = resolveBatchCandidates(p, rangeStart, rangeEnd);
        if (candidates.isEmpty()) {
            String msg = "范围内没有可评估的已完成批次（进行中或未算完的周会自动跳过）";
            XxlJobLogger.log(msg);
            return EvaluateResult.of(0, 0, 0, msg);
        }

        int evaluated = 0, skipped = 0, failCount = 0;
        for (BatchKey batch : candidates) {
            if (!p.isForce() && repo.existsReport(batch.getBatchMonth(), batch.getStartTime(), batch.getEndTime())) {
                skipped++;
                XxlJobLogger.log("跳过已评估 " + batch.getBatchMonth());
                continue;
            }
            if (p.isForce() && repo.existsReport(batch.getBatchMonth(), batch.getStartTime(), batch.getEndTime())) {
                repo.deleteReport(batch.getBatchMonth(), batch.getStartTime(), batch.getEndTime());
            }

            int shipsDone = repo.countShipsByStatus(batch.getBatchMonth(), batch.getStartTime(), batch.getEndTime(), "DONE");
            int shipsFailed = repo.countShipsByStatus(batch.getBatchMonth(), batch.getStartTime(), batch.getEndTime(), "FAILED");
            if (shipsFailed > 0) {
                skipped++;
                XxlJobLogger.log("跳过有失败船舶的批次 " + batch.getBatchMonth() + " failed=" + shipsFailed);
                continue;
            }
            if (!p.isWeekSpecified() && !isBatchFullyComputed(shipsDone, currentShipCount)) {
                skipped++;
                XxlJobLogger.log("跳过未完成批次 " + batch.getBatchMonth()
                        + " done=" + shipsDone + " currentTotal=" + currentShipCount);
                continue;
            }

            int expectedShips = resolveBatchExpectedShips(p, shipsDone, shipsFailed, currentShipCount);
            BatchEvaluateResult r = evaluateBatch(batch, expectedShips, p.isReplayEnabled(), p.isSpatialEnabled());
            evaluated++;
            if (STATUS_FAIL.equals(r.getQualityStatus())) {
                failCount++;
            }
            XxlJobLogger.log(r.getSummary());
        }
        String summary = "评估完成：候选=" + candidates.size() + " 新评=" + evaluated
                + " 跳过=" + skipped + " FAIL=" + failCount;
        XxlJobLogger.log(summary);
        return EvaluateResult.of(evaluated, skipped, failCount, summary);
    }

    public BatchEvaluateResult evaluateBatch(BatchKey batch, int expectedShips,
                                             boolean doReplay, boolean doSpatial) {
        String batchMonth = batch.getBatchMonth();
        String startTime = batch.getStartTime();
        String endTime = batch.getEndTime();

        int shipsDone = repo.countShipsByStatus(batchMonth, startTime, endTime, "DONE");
        int shipsFailed = repo.countShipsByStatus(batchMonth, startTime, endTime, "FAILED");
        int eventsInStatus = repo.sumEventCountInStatus(batchMonth, startTime, endTime);
        int eventsActual = repo.countActualEvents(startTime, endTime);

        List<CheckRecord> checks = new ArrayList<CheckRecord>();
        List<QualityIssue> issues = new ArrayList<QualityIssue>();

        // --- 检查项 1：任务完整性 ---
        BigDecimal completionRate = expectedShips > 0
                ? BigDecimal.valueOf(shipsDone * 100.0 / expectedShips).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String completenessDetail = "expected=" + expectedShips + " done=" + shipsDone + " failed=" + shipsFailed
                + " rate=" + completionRate + "%";
        String completenessSev = shipsFailed > 0 || shipsDone < expectedShips ? SEV_ERROR : SEV_PASS;
        int completenessIssues = shipsFailed + Math.max(0, expectedShips - shipsDone);
        checks.add(new CheckRecord(0, CHK_COMPLETENESS, "任务完整性", completenessSev, completenessIssues, completenessDetail));
        if (shipsFailed > 0) {
            for (String mmsi : repo.findFailedMmsi(batchMonth, startTime, endTime)) {
                issues.add(new QualityIssue(0, CHK_COMPLETENESS, "SHIP_FAILED", SEV_ERROR,
                        mmsi, null, null, null, "status=FAILED"));
            }
        }

        // --- 检查项 2：event_count 对账 ---
        List<EventCountMismatch> mismatches = repo.findEventCountMismatches(batchMonth, startTime, endTime);
        checks.add(new CheckRecord(0, CHK_EVENT_RECONCILE, "事件数量对账",
                mismatches.isEmpty() ? SEV_PASS : SEV_ERROR, mismatches.size(),
                "不一致船舶=" + mismatches.size()));
        for (EventCountMismatch m : mismatches) {
            issues.add(new QualityIssue(0, CHK_EVENT_RECONCILE, "EVENT_COUNT_MISMATCH", SEV_ERROR,
                    m.getMmsi(), null, null, null,
                    "status=" + m.getStatusCount() + " actual=" + m.getActualCount()));
        }

        // --- 检查项 3：时间范围 ---
        int outOfRange = repo.countOutOfRangeEvents(batchMonth, startTime, endTime);
        checks.add(new CheckRecord(0, CHK_TIME_RANGE, "事件时间范围",
                outOfRange == 0 ? SEV_PASS : SEV_ERROR, outOfRange,
                outOfRange == 0 ? "全部在批次范围内" : "超范围=" + outOfRange));
        if (outOfRange > 0) {
            issues.add(new QualityIssue(0, CHK_TIME_RANGE, "OUT_OF_RANGE", SEV_ERROR,
                    null, null, null, null, "超范围事件=" + outOfRange));
        }

        // --- 检查项 4：必填字段 ---
        int nullFields = repo.countNullFieldEvents(startTime, endTime);
        checks.add(new CheckRecord(0, CHK_REQUIRED_FIELDS, "必填字段",
                nullFields == 0 ? SEV_PASS : SEV_ERROR, nullFields,
                nullFields == 0 ? "无空字段" : "空字段事件=" + nullFields));
        if (nullFields > 0) {
            issues.add(new QualityIssue(0, CHK_REQUIRED_FIELDS, "NULL_FIELD", SEV_ERROR,
                    null, null, null, null, "空字段事件=" + nullFields));
        }

        // --- 检查项 5：ENTER/EXIT 平衡 ---
        int unbalancedGroups = repo.countUnbalancedPairGroups(startTime, endTime);
        checks.add(new CheckRecord(0, CHK_ENTER_EXIT_BALANCE, "ENTER/EXIT 成对",
                unbalancedGroups == 0 ? SEV_PASS : SEV_WARN, unbalancedGroups,
                "不成对组合=" + unbalancedGroups + "（周边界常见）"));
        if (unbalancedGroups > 0) {
            for (UnbalancedPair pair : repo.findUnbalancedPairs(startTime, endTime)) {
                issues.add(new QualityIssue(0, CHK_ENTER_EXIT_BALANCE, "UNBALANCED_PAIR", SEV_WARN,
                        pair.getMmsi(), pair.getZoneId(), pair.getZoneName(), pair.getEventCategory(),
                        "ENTER=" + pair.getEnterCount() + " EXIT=" + pair.getExitCount()));
            }
        }

        // --- 检查项 6：ENTER 应早于 EXIT ---
        EnterExitOrderResult orderResult = checkEnterBeforeExit(startTime, endTime);
        checks.add(new CheckRecord(0, CHK_ENTER_BEFORE_EXIT, "ENTER 早于 EXIT",
                orderResult.getReversedPairs() == 0 ? SEV_PASS : SEV_ERROR, orderResult.getReversedPairs(),
                "时间逆序通行对=" + orderResult.getReversedPairs()));
        issues.addAll(orderResult.getIssues());

        // --- 检查项 7：重复事件 ---
        int dupGroups = repo.countDuplicateEventGroups(startTime, endTime);
        checks.add(new CheckRecord(0, CHK_DUPLICATE, "重复事件",
                dupGroups == 0 ? SEV_PASS : SEV_ERROR, dupGroups, "重复组=" + dupGroups));
        if (dupGroups > 0) {
            for (DuplicateEvent dup : repo.findDuplicateEvents(startTime, endTime)) {
                issues.add(new QualityIssue(0, CHK_DUPLICATE, "DUPLICATE_EVENT", SEV_ERROR,
                        dup.getMmsi(), dup.getZoneId(), null, dup.getEventCategory(),
                        dup.getEventType() + "@" + dup.getEventTime() + " x" + dup.getDupCount()));
            }
        }

        // --- 检查项 8：合法 category ---
        int invalidCat = repo.countInvalidCategoryEvents(startTime, endTime);
        checks.add(new CheckRecord(0, CHK_INVALID_CATEGORY, "合法区域类型",
                invalidCat == 0 ? SEV_PASS : SEV_ERROR, invalidCat, "未知类型=" + invalidCat));
        if (invalidCat > 0) {
            issues.add(new QualityIssue(0, CHK_INVALID_CATEGORY, "INVALID_CATEGORY", SEV_ERROR,
                    null, null, null, null, "未知 event_category=" + invalidCat));
        }

        // --- 检查项 9：抽样回放 ---
        int replaySample = 0, replayMismatch = 0;
        if (doReplay && replayEnabledConfig()) {
            ReplayResult replay = runReplaySample(batchMonth, startTime, endTime);
            replaySample = replay.getSampleSize();
            replayMismatch = replay.getMismatchSize();
            checks.add(new CheckRecord(0, CHK_REPLAY_SAMPLE, "抽样轨迹回放",
                    replayMismatch == 0 ? SEV_PASS : SEV_WARN, replayMismatch,
                    "分层抽样=" + replaySample + " 不一致=" + replayMismatch + " | " + replay.getSampleDesc()));
            issues.addAll(replay.getIssues());
        } else {
            checks.add(new CheckRecord(0, CHK_REPLAY_SAMPLE, "抽样轨迹回放", SEV_PASS, 0, "未启用"));
        }

        // --- 检查项 10：空间抽检 ---
        int spatialCheck = 0, spatialMismatch = 0;
        if (doSpatial && spatialEnabledConfig()) {
            SpatialResult spatial = runSpatialSample(batchMonth, startTime, endTime);
            spatialCheck = spatial.getCheckCount();
            spatialMismatch = spatial.getMismatchCount();
            checks.add(new CheckRecord(0, CHK_SPATIAL, "坐标空间抽检",
                    spatialMismatch == 0 ? SEV_PASS : SEV_WARN, spatialMismatch,
                    "分层抽检=" + spatialCheck + " 不匹配=" + spatialMismatch + " | " + spatial.getSampleDesc()));
            issues.addAll(spatial.getIssues());
        } else {
            checks.add(new CheckRecord(0, CHK_SPATIAL, "坐标空间抽检", SEV_PASS, 0, "未启用"));
        }

        // --- 汇总 ---
        int errorCount = countBySeverity(checks, SEV_ERROR);
        int warnCount = countBySeverity(checks, SEV_WARN);
        String qualityStatus = resolveStatus(checks);
        int qualityScore = calcScore(qualityStatus, completionRate, errorCount, warnCount);
        String issueSummary = buildSummary(shipsFailed, mismatches.size(), unbalancedGroups,
                nullFields, outOfRange, dupGroups, orderResult.getReversedPairs(),
                invalidCat, replayMismatch, spatialMismatch);

        QualityReport report = new QualityReport();
        report.setBatchMonth(batchMonth);
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        report.setQualityStatus(qualityStatus);
        report.setQualityScore(qualityScore);
        report.setTotalShipsExpected(expectedShips);
        report.setTotalShipsDone(shipsDone);
        report.setTotalShipsFailed(shipsFailed);
        report.setCompletionRate(completionRate);
        report.setTotalEventsInStatus(eventsInStatus);
        report.setTotalEventsActual(eventsActual);
        report.setErrorCount(errorCount);
        report.setWarnCount(warnCount);
        report.setEventCountMismatchShips(mismatches.size());
        report.setUnbalancedPairGroups(unbalancedGroups);
        report.setNullFieldEvents(nullFields);
        report.setOutOfRangeEvents(outOfRange);
        report.setDuplicateEventGroups(dupGroups);
        report.setEnterAfterExitPairs(orderResult.getReversedPairs());
        report.setInvalidCategoryEvents(invalidCat);
        report.setReplaySampleShips(replaySample);
        report.setReplayMismatchShips(replayMismatch);
        report.setSpatialCheckEvents(spatialCheck);
        report.setSpatialMismatchEvents(spatialMismatch);
        report.setCheckResultsJson(buildCheckJson(checks));
        report.setIssueSummary(issueSummary);

        long reportId = repo.insertReport(report);

        for (CheckRecord c : checks) {
            c = new CheckRecord(reportId, c.getCheckCode(), c.getCheckName(), c.getSeverity(), c.getIssueCount(), c.getDetail());
        }
        repo.insertChecks(wrapChecks(reportId, checks));

        List<CategoryStatRecord> catStats = buildCategoryStats(reportId, startTime, endTime);
        repo.insertCategoryStats(catStats);

        List<ZoneStatRecord> zoneStats = buildZoneStats(reportId, startTime, endTime);
        repo.insertZoneStats(zoneStats);

        for (int i = 0; i < issues.size(); i++) {
            QualityIssue old = issues.get(i);
            issues.set(i, new QualityIssue(reportId, old.getCheckCode(), old.getIssueType(), old.getSeverity(),
                    old.getMmsi(), old.getZoneId(), old.getZoneName(), old.getEventCategory(), old.getDetail()));
        }
        repo.insertIssues(issues);

        return new BatchEvaluateResult(batchMonth, startTime, endTime, qualityStatus, qualityScore,
                shipsDone, shipsFailed, eventsActual, errorCount, warnCount, issueSummary);
    }

    private List<CheckRecord> wrapChecks(long reportId, List<CheckRecord> checks) {
        List<CheckRecord> wrapped = new ArrayList<CheckRecord>();
        for (CheckRecord c : checks) {
            wrapped.add(new CheckRecord(reportId, c.getCheckCode(), c.getCheckName(),
                    c.getSeverity(), c.getIssueCount(), c.getDetail()));
        }
        return wrapped;
    }

    private EnterExitOrderResult checkEnterBeforeExit(String startTime, String endTime) {
        List<StoredEvent> events = repo.findEventsForOrderingCheck(startTime, endTime);
        List<QualityIssue> issues = new ArrayList<QualityIssue>();
        int reversed = 0;

        String prevKey = null;
        StoredEvent pendingEnter = null;

        for (StoredEvent e : events) {
            String key = e.getMmsi() + "|" + e.getZoneId() + "|" + e.getEventCategory();
            if (!key.equals(prevKey)) {
                pendingEnter = null;
                prevKey = key;
            }
            if (ZoneCrossingEvent.ENTER.equals(e.getEventType())) {
                pendingEnter = e;
            } else if (ZoneCrossingEvent.EXIT.equals(e.getEventType()) && pendingEnter != null) {
                LocalDateTime enterTime = LocalDateTime.parse(pendingEnter.getEventTime(), TIME_FMT);
                LocalDateTime exitTime = LocalDateTime.parse(e.getEventTime(), TIME_FMT);
                if (!exitTime.isAfter(enterTime)) {
                    reversed++;
                    if (issues.size() < 200) {
                        issues.add(new QualityIssue(0, CHK_ENTER_BEFORE_EXIT, "ENTER_AFTER_EXIT", SEV_ERROR,
                                e.getMmsi(), e.getZoneId(), e.getZoneName(), e.getEventCategory(),
                                "ENTER=" + pendingEnter.getEventTime() + " EXIT=" + e.getEventTime()));
                    }
                }
                pendingEnter = null;
            }
        }
        return new EnterExitOrderResult(reversed, issues);
    }

    private ReplayResult runReplaySample(String batchMonth, String startTime, String endTime) {
        List<ShipProfile> profiles = repo.findDoneShipProfiles(batchMonth, startTime, endTime);
        List<String> sample = QualityStratifiedSampler.selectStratifiedShips(
                profiles, samplesPerStratum, maxReplayShips);
        String sampleDesc = QualityStratifiedSampler.describeStratumSummary(profiles, sample)
                + " | " + QualityStratifiedSampler.describeSample(profiles, sample);

        List<QualityIssue> issues = new ArrayList<QualityIssue>();
        int mismatch = 0;
        for (String mmsi : sample) {
            try {
                List<ZoneCrossingEvent> recalc = analysisService.recalculateEventsForShip(mmsi, startTime, endTime);
                List<StoredEvent> stored = repo.findStoredEventsByMmsi(mmsi, startTime, endTime);
                if (!compareEventSets(recalc, stored)) {
                    mismatch++;
                    issues.add(new QualityIssue(0, CHK_REPLAY_SAMPLE, "REPLAY_MISMATCH", SEV_WARN,
                            mmsi, null, null, null,
                            buildReplayMismatchDetail(mmsi, profiles, stored.size(), recalc.size(), recalc, stored)));
                }
            } catch (Exception ex) {
                mismatch++;
                issues.add(new QualityIssue(0, CHK_REPLAY_SAMPLE, "REPLAY_ERROR", SEV_WARN,
                        mmsi, null, null, null,
                        buildShipProfileTag(mmsi, profiles) + " error=" + ex.getMessage()));
            }
        }
        return new ReplayResult(sample.size(), mismatch, issues, sampleDesc);
    }

    private String buildReplayMismatchDetail(String mmsi, List<ShipProfile> profiles,
                                             int storedSize, int recalcSize,
                                             List<ZoneCrossingEvent> recalc, List<StoredEvent> stored) {
        return buildShipProfileTag(mmsi, profiles)
                + " stored=" + storedSize + " recalc=" + recalcSize
                + " categoryDiff=" + categoryDiffSummary(recalc, stored);
    }

    private String buildShipProfileTag(String mmsi, List<ShipProfile> profiles) {
        for (ShipProfile p : profiles) {
            if (mmsi.equals(p.getMmsi())) {
                return mmsi + "[" + QualityStratifiedSampler.formatProfileTag(p) + "]";
            }
        }
        return mmsi + "[未知]";
    }

    private boolean compareEventSets(List<ZoneCrossingEvent> recalc, List<StoredEvent> stored) {
        if (recalc.size() != stored.size()) {
            return false;
        }
        Map<String, Integer> recalcCat = categoryCountFromRecalc(recalc);
        Map<String, Integer> storedCat = categoryCountFromStored(stored);
        return recalcCat.equals(storedCat);
    }

    private Map<String, Integer> categoryCountFromRecalc(List<ZoneCrossingEvent> events) {
        Map<String, Integer> map = new HashMap<String, Integer>();
        for (ZoneCrossingEvent e : events) {
            String cat = e.getEventCategory();
            map.put(cat, map.containsKey(cat) ? map.get(cat) + 1 : 1);
        }
        return map;
    }

    private Map<String, Integer> categoryCountFromStored(List<StoredEvent> events) {
        Map<String, Integer> map = new HashMap<String, Integer>();
        for (StoredEvent e : events) {
            String cat = e.getEventCategory();
            map.put(cat, map.containsKey(cat) ? map.get(cat) + 1 : 1);
        }
        return map;
    }

    private String categoryDiffSummary(List<ZoneCrossingEvent> recalc, List<StoredEvent> stored) {
        return "stored=" + categoryCountFromStored(stored) + " recalc=" + categoryCountFromRecalc(recalc);
    }

    private SpatialResult runSpatialSample(String batchMonth, String startTime, String endTime) {
        Map<String, ZoneSpatialMatcher> matchers = buildCategoryMatchers();
        List<ShipProfile> profiles = repo.findDoneShipProfiles(batchMonth, startTime, endTime);

        List<String> sampleShips = QualityStratifiedSampler.selectStratifiedShips(
                profiles, samplesPerStratum, maxSpatialShips);
        Map<String, Integer> quota = QualityStratifiedSampler.allocateEventQuota(sampleShips, maxSpatialEvents);
        String sampleDesc = QualityStratifiedSampler.describeStratumSummary(profiles, sampleShips)
                + " | " + QualityStratifiedSampler.describeSample(profiles, sampleShips);

        List<StoredEvent> sample = new ArrayList<StoredEvent>();
        for (Map.Entry<String, Integer> entry : quota.entrySet()) {
            sample.addAll(repo.findRandomEventsByMmsi(entry.getKey(), startTime, endTime, entry.getValue()));
        }

        List<QualityIssue> issues = new ArrayList<QualityIssue>();
        int mismatch = 0;
        for (StoredEvent e : sample) {
            ZoneSpatialMatcher matcher = matchers.get(e.getEventCategory());
            if (matcher == null) {
                continue;
            }
            ZoneDefinition matched = matcher.match(e.getLon(), e.getLat());
            int matchedId = matched == null ? -1 : matched.getId();
            if (matchedId != e.getZoneId()) {
                mismatch++;
                if (issues.size() < 100) {
                    issues.add(new QualityIssue(0, CHK_SPATIAL, "SPATIAL_MISMATCH", SEV_WARN,
                            e.getMmsi(), e.getZoneId(), e.getZoneName(), e.getEventCategory(),
                            buildShipProfileTag(e.getMmsi(), profiles)
                                    + " eventZone=" + e.getZoneId() + " pointMatch=" + matchedId
                                    + " @(" + e.getLon() + "," + e.getLat() + ")"));
                }
            }
        }
        return new SpatialResult(sample.size(), mismatch, issues, sampleDesc);
    }

    private Map<String, ZoneSpatialMatcher> buildCategoryMatchers() {
        Map<String, ZoneSpatialMatcher> map = new HashMap<String, ZoneSpatialMatcher>();
        putMatcher(map, ZoneCrossingEvent.CATEGORY_STRAIT, zoneLoaderService.loadStraits());
        putMatcher(map, ZoneCrossingEvent.CATEGORY_SEA_AREA, zoneLoaderService.loadSeaAreas());
        putMatcher(map, ZoneCrossingEvent.CATEGORY_BRIDGE, zoneLoaderService.loadBridgeAndWindFarmZones());
        putMatcher(map, ZoneCrossingEvent.CATEGORY_WIND_FARM, zoneLoaderService.loadBridgeAndWindFarmZones());
        putMatcher(map, ZoneCrossingEvent.CATEGORY_HIGH_RISK, zoneLoaderService.loadAlertAreaZones());
        return map;
    }

    private void putMatcher(Map<String, ZoneSpatialMatcher> map, String category, List<ZoneDefinition> zones) {
        if (zones != null && !zones.isEmpty()) {
            map.put(category, new ZoneSpatialMatcher(zones));
        }
    }

    private List<CategoryStatRecord> buildCategoryStats(long reportId, String startTime, String endTime) {
        List<CategoryStatRecord> records = new ArrayList<CategoryStatRecord>();
        for (CategoryStatRow row : repo.findCategoryStats(startTime, endTime)) {
            int unbalanced = repo.countUnbalancedByCategory(startTime, endTime, row.getEventCategory());
            int pairs = Math.min(row.getEnterCount(), row.getExitCount()) / 2;
            records.add(new CategoryStatRecord(reportId, row.getEventCategory(),
                    row.getEventCount(), row.getShipCount(), row.getEnterCount(), row.getExitCount(),
                    pairs, unbalanced, null));
        }
        return records;
    }

    private List<ZoneStatRecord> buildZoneStats(long reportId, String startTime, String endTime) {
        List<ZoneStatRecord> records = new ArrayList<ZoneStatRecord>();
        for (ZoneStatRow row : repo.findZoneStats(startTime, endTime)) {
            int unbalanced = repo.countUnbalancedByZone(startTime, endTime, row.getZoneId(), row.getEventCategory());
            records.add(new ZoneStatRecord(reportId, row.getZoneId(), row.getZoneName(), row.getEventCategory(),
                    row.getEventCount(), row.getShipCount(), row.getEnterCount(), row.getExitCount(), unbalanced));
        }
        return records;
    }

    private int resolveCurrentShipCount() {
        List<ShipForm> ships = ais41Repo.selectRecentShips();
        return ships == null ? 0 : ships.size();
    }

    /** 批量模式：DONE 数须达到当前全船数，才视为该周已算完。 */
    private boolean isBatchFullyComputed(int shipsDone, int currentShipCount) {
        return currentShipCount > 0 && shipsDone >= currentShipCount;
    }

    /**
     * 指定 week 时用该批次实际 DONE 数；批量模式用当前全船数。
     */
    private int resolveBatchExpectedShips(EvaluateParams p, int shipsDone, int shipsFailed, int currentShipCount) {
        if (p.isWeekSpecified()) {
            return shipsDone + shipsFailed;
        }
        return currentShipCount;
    }

    private List<BatchKey> resolveBatchCandidates(EvaluateParams p, String rangeStart, String rangeEnd) {
        if (p.isWeekSpecified()) {
            LocalDate weekStart = LocalDate.parse(p.getWeek().trim(), DATE_FMT);
            return java.util.Collections.singletonList(new BatchKey(
                    weekStart.format(DATE_FMT) + "-W",
                    weekStart.atStartOfDay().format(TIME_FMT),
                    weekStart.plusDays(7).atStartOfDay().format(TIME_FMT), 0, 0));
        }
        return repo.findComputedBatches(rangeStart, rangeEnd);
    }

    private String resolveStatus(List<CheckRecord> checks) {
        for (CheckRecord c : checks) {
            if (SEV_ERROR.equals(c.getSeverity()) && c.getIssueCount() > 0) {
                return STATUS_FAIL;
            }
        }
        for (CheckRecord c : checks) {
            if (SEV_WARN.equals(c.getSeverity()) && c.getIssueCount() > 0) {
                return STATUS_WARN;
            }
        }
        return STATUS_PASS;
    }

    private int calcScore(String status, BigDecimal completionRate, int errors, int warns) {
        int score = 100;
        if (completionRate != null && completionRate.compareTo(BigDecimal.valueOf(100)) < 0) {
            score -= (100 - completionRate.intValue()) / 2;
        }
        score -= errors * 10;
        score -= warns * 2;
        if (STATUS_FAIL.equals(status)) {
            score = Math.min(score, 59);
        } else if (STATUS_WARN.equals(status)) {
            score = Math.min(score, 79);
        }
        return Math.max(0, Math.min(100, score));
    }

    private int countBySeverity(List<CheckRecord> checks, String severity) {
        int sum = 0;
        for (CheckRecord c : checks) {
            if (severity.equals(c.getSeverity())) {
                sum += c.getIssueCount();
            }
        }
        return sum;
    }

    private String buildSummary(int failed, int mismatch, int unbalanced, int nullFields,
                                int outOfRange, int dup, int reversed, int invalidCat,
                                int replayMismatch, int spatialMismatch) {
        List<String> parts = new ArrayList<String>();
        if (failed > 0) parts.add("失败船=" + failed);
        if (mismatch > 0) parts.add("对账不一致=" + mismatch);
        if (nullFields > 0) parts.add("空字段=" + nullFields);
        if (outOfRange > 0) parts.add("超范围=" + outOfRange);
        if (dup > 0) parts.add("重复=" + dup);
        if (reversed > 0) parts.add("时间逆序=" + reversed);
        if (invalidCat > 0) parts.add("未知类型=" + invalidCat);
        if (unbalanced > 0) parts.add("不成对=" + unbalanced + "(WARN)");
        if (replayMismatch > 0) parts.add("回放不一致=" + replayMismatch + "(WARN)");
        if (spatialMismatch > 0) parts.add("坐标不匹配=" + spatialMismatch + "(WARN)");
        return parts.isEmpty() ? "全部检查通过" : StringUtils.join(parts, "; ");
    }

    private String buildCheckJson(List<CheckRecord> checks) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (CheckRecord c : checks) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("code", c.getCheckCode());
            row.put("name", c.getCheckName());
            row.put("severity", c.getSeverity());
            row.put("issueCount", c.getIssueCount());
            row.put("detail", c.getDetail());
            rows.add(row);
        }
        return JSON.toJSONString(rows);
    }

    private EvaluateParams parseParams(String params) {
        EvaluateParams r = new EvaluateParams();
        r.setFromDate(LocalDate.parse(backfillFromDate, DATE_FMT));
        r.setToDate(LocalDate.parse(backfillToDate, DATE_FMT));
        r.setReplayEnabled(replayEnabledConfig());
        r.setSpatialEnabled(spatialEnabledConfig());
        if (StringUtils.isBlank(params)) {
            return r;
        }
        String trimmed = params.trim();
        try {
            if (trimmed.startsWith("{")) {
                JSONObject json = JSON.parseObject(trimmed);
                if (json.containsKey("week")) r.setWeek(json.getString("week"));
                if (json.containsKey("fromDate")) r.setFromDate(LocalDate.parse(json.getString("fromDate"), DATE_FMT));
                if (json.containsKey("toDate")) r.setToDate(LocalDate.parse(json.getString("toDate"), DATE_FMT));
                if (json.containsKey("force")) r.setForce(json.getBooleanValue("force"));
                if (json.containsKey("replay")) r.setReplayEnabled(json.getBooleanValue("replay"));
                if (json.containsKey("spatial")) r.setSpatialEnabled(json.getBooleanValue("spatial"));
            } else if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                r.setWeek(trimmed);
            } else {
                parseKv(trimmed, r);
            }
        } catch (Exception ex) {
            log.warn("parse evaluate params failed: {}", trimmed, ex);
        }
        return r;
    }

    private void parseKv(String params, EvaluateParams r) {
        for (String part : params.split("[;,]")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim(), v = kv[1].trim();
            if ("week".equalsIgnoreCase(k)) r.setWeek(v);
            else if ("fromDate".equalsIgnoreCase(k)) r.setFromDate(LocalDate.parse(v, DATE_FMT));
            else if ("toDate".equalsIgnoreCase(k)) r.setToDate(LocalDate.parse(v, DATE_FMT));
            else if ("force".equalsIgnoreCase(k)) r.setForce("true".equalsIgnoreCase(v) || "1".equals(v));
            else if ("replay".equalsIgnoreCase(k)) r.setReplayEnabled(!"false".equalsIgnoreCase(v) && !"0".equals(v));
            else if ("spatial".equalsIgnoreCase(k)) r.setSpatialEnabled(!"false".equalsIgnoreCase(v) && !"0".equals(v));
        }
    }

    // ---- inner types ----

    private static final class EnterExitOrderResult {
        private final int reversedPairs;
        private final List<QualityIssue> issues;
        EnterExitOrderResult(int reversedPairs, List<QualityIssue> issues) {
            this.reversedPairs = reversedPairs; this.issues = issues;
        }
        int getReversedPairs() { return reversedPairs; }
        List<QualityIssue> getIssues() { return issues; }
    }

    private static final class ReplayResult {
        private final int sampleSize, mismatchSize;
        private final List<QualityIssue> issues;
        private final String sampleDesc;

        ReplayResult(int sampleSize, int mismatchSize, List<QualityIssue> issues, String sampleDesc) {
            this.sampleSize = sampleSize;
            this.mismatchSize = mismatchSize;
            this.issues = issues;
            this.sampleDesc = sampleDesc;
        }

        int getSampleSize() { return sampleSize; }
        int getMismatchSize() { return mismatchSize; }
        List<QualityIssue> getIssues() { return issues; }
        String getSampleDesc() { return sampleDesc; }
    }

    private static final class SpatialResult {
        private final int checkCount, mismatchCount;
        private final List<QualityIssue> issues;
        private final String sampleDesc;

        SpatialResult(int checkCount, int mismatchCount, List<QualityIssue> issues, String sampleDesc) {
            this.checkCount = checkCount;
            this.mismatchCount = mismatchCount;
            this.issues = issues;
            this.sampleDesc = sampleDesc;
        }

        int getCheckCount() { return checkCount; }
        int getMismatchCount() { return mismatchCount; }
        List<QualityIssue> getIssues() { return issues; }
        String getSampleDesc() { return sampleDesc; }
    }

    public static final class EvaluateParams {
        private LocalDate fromDate, toDate;
        private String week;
        private boolean force, replayEnabled, spatialEnabled;

        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate v) { fromDate = v; }
        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate v) { toDate = v; }
        public String getWeek() { return week; }
        public void setWeek(String v) { week = v; }
        public boolean isWeekSpecified() { return StringUtils.isNotBlank(week); }
        public boolean isForce() { return force; }
        public void setForce(boolean v) { force = v; }
        public boolean isReplayEnabled() { return replayEnabled; }
        public void setReplayEnabled(boolean v) { replayEnabled = v; }
        public boolean isSpatialEnabled() { return spatialEnabled; }
        public void setSpatialEnabled(boolean v) { spatialEnabled = v; }
    }

    public static final class EvaluateResult {
        private final int evaluated, skipped, failCount;
        private final String message;
        private EvaluateResult(int e, int s, int f, String m) { evaluated = e; skipped = s; failCount = f; message = m; }
        public static EvaluateResult of(int e, int s, int f, String m) { return new EvaluateResult(e, s, f, m); }
        public int getEvaluated() { return evaluated; }
        public int getSkipped() { return skipped; }
        public int getFailCount() { return failCount; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return failCount == 0; }
    }

    public static final class BatchEvaluateResult {
        private final String batchMonth, startTime, endTime, qualityStatus, issueSummary;
        private final int qualityScore, shipsDone, shipsFailed, eventsActual, errorCount, warnCount;

        public BatchEvaluateResult(String batchMonth, String startTime, String endTime,
                                   String qualityStatus, int qualityScore,
                                   int shipsDone, int shipsFailed, int eventsActual,
                                   int errorCount, int warnCount, String issueSummary) {
            this.batchMonth = batchMonth; this.startTime = startTime; this.endTime = endTime;
            this.qualityStatus = qualityStatus; this.qualityScore = qualityScore;
            this.shipsDone = shipsDone; this.shipsFailed = shipsFailed;
            this.eventsActual = eventsActual; this.errorCount = errorCount;
            this.warnCount = warnCount; this.issueSummary = issueSummary;
        }

        public String getQualityStatus() { return qualityStatus; }

        public String getSummary() {
            return batchMonth + " [" + startTime + "~" + endTime + "] "
                    + qualityStatus + " score=" + qualityScore
                    + " | ships=" + shipsDone + "(failed=" + shipsFailed + ")"
                    + " | events=" + eventsActual
                    + " | err=" + errorCount + " warn=" + warnCount
                    + " | " + issueSummary;
        }
    }
}
