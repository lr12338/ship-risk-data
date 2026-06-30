package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.beans.ShipForm;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorEventDetail;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipLoiteringDetector;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectoryUtils;
import com.xxl.job.core.log.XxlJobLogger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ShipBehaviorAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ShipBehaviorAnalysisService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShipLoiteringDetector loiteringDetector = new ShipLoiteringDetector();
    private final ShipAlongsideDetector alongsideDetector = new ShipAlongsideDetector();

    @Value("${ship.behavior.trajectory.simplify-zoom:15}")
    private int trajectorySimplifyZoom;

    @Value("${ship.behavior.default-hours:6}")
    private int defaultHours;

    @Value("${ship.behavior.loitering.max-speed:3.0}")
    private double loiteringMaxSpeed;

    @Value("${ship.behavior.loitering.min-duration-minutes:30}")
    private long loiteringMinDurationMinutes;

    @Value("${ship.behavior.loitering.max-radius-nm:1.0}")
    private double loiteringMaxRadiusNm;

    @Value("${ship.behavior.loitering.min-points:3}")
    private int loiteringMinPoints;

    @Value("${ship.behavior.loitering.rule-version:LOITERING_V2_INSURANCE}")
    private String loiteringRuleVersion;

    @Value("${ship.behavior.loitering.max-gap-minutes:30}")
    private long loiteringMaxGapMinutes;

    @Value("${ship.behavior.loitering.event-split-gap-minutes:120}")
    private long loiteringEventSplitGapMinutes;

    @Value("${ship.behavior.loitering.max-valid-speed:80.0}")
    private double loiteringMaxValidSpeed;

    @Value("${ship.behavior.loitering.min-warning-points:6}")
    private int loiteringMinWarningPoints;

    @Value("${ship.behavior.loitering.slow.max-speed:2.0}")
    private double loiteringSlowMaxSpeed;

    @Value("${ship.behavior.loitering.slow.min-duration-minutes:240}")
    private long loiteringSlowMinDurationMinutes;

    @Value("${ship.behavior.loitering.slow.max-radius-nm:3.0}")
    private double loiteringSlowMaxRadiusNm;

    @Value("${ship.behavior.loitering.slow.min-points:12}")
    private int loiteringSlowMinPoints;

    @Value("${ship.behavior.loitering.maneuver.min-speed:0.5}")
    private double loiteringManeuverMinSpeed;

    @Value("${ship.behavior.loitering.maneuver.max-speed:5.0}")
    private double loiteringManeuverMaxSpeed;

    @Value("${ship.behavior.loitering.maneuver.min-duration-minutes:120}")
    private long loiteringManeuverMinDurationMinutes;

    @Value("${ship.behavior.loitering.maneuver.max-radius-nm:5.0}")
    private double loiteringManeuverMaxRadiusNm;

    @Value("${ship.behavior.loitering.maneuver.min-turn-count-per-hour:3}")
    private double loiteringManeuverMinTurnCountPerHour;

    @Value("${ship.behavior.loitering.maneuver.min-cog-change-per-hour:120}")
    private double loiteringManeuverMinCogChangePerHour;

    @Value("${ship.behavior.loitering.maneuver.min-tortuosity:3.0}")
    private double loiteringManeuverMinTortuosity;

    @Value("${ship.behavior.loitering.maneuver.turn-threshold-degree:45}")
    private double loiteringTurnThresholdDegree;

    @Value("${ship.behavior.loitering.high-risk.enabled:false}")
    private boolean loiteringHighRiskEnabled;

    @Value("${ship.behavior.loitering.high-risk.min-duration-minutes:60}")
    private long loiteringHighRiskMinDurationMinutes;

    @Value("${ship.behavior.loitering.high-risk.max-radius-nm:5.0}")
    private double loiteringHighRiskMaxRadiusNm;

    @Value("${ship.behavior.alongside.max-distance-nm:0.1}")
    private double alongsideMaxDistanceNm;

    @Value("${ship.behavior.alongside.max-speed:2.0}")
    private double alongsideMaxSpeed;

    @Value("${ship.behavior.alongside.max-time-gap-minutes:10}")
    private long alongsideMaxTimeGapMinutes;

    @Value("${ship.behavior.alongside.min-duration-minutes:20}")
    private long alongsideMinDurationMinutes;

    @Value("${ship.behavior.alongside.min-points:3}")
    private int alongsideMinPoints;

    @Value("${ship.behavior.trace.log-max-events:20}")
    private int traceLogMaxEvents;

    @Autowired
    private TrajectoryLoaderService trajectoryLoaderService;

    @Autowired
    private ShipBehaviorEventRepository shipBehaviorEventRepository;

    @Autowired
    private ShipBehaviorEventDetailRepository shipBehaviorEventDetailRepository;

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    public AnalysisResult analysisLoitering(String params) {
        JobParams jobParams = parseParams(params);
        applyDefaultTimeRange(jobParams);
        List<String> mmsiList = resolveMmsiList(jobParams);
        ShipLoiteringDetector.LoiteringConfig config = buildLoiteringConfig();

        int processed = 0;
        int failed = 0;
        int events = 0;
        for (String mmsi : mmsiList) {
            try {
                XxlJobLogger.log("LOITERING_V2 start mmsi=" + mmsi
                        + " startTime=" + jobParams.getStartTime()
                        + " endTime=" + jobParams.getEndTime()
                        + " batchNo=" + jobParams.getBatchNo()
                        + " ruleVersion=" + config.getRuleVersion());
                List<TrajectoryPoint> points = trajectoryLoaderService.load(
                        mmsi, jobParams.getStartTime(), jobParams.getEndTime(), trajectorySimplifyZoom);
                List<ShipBehaviorTrace> traces = loiteringDetector.detectWithTrace(mmsi, points, config);
                ShipLoiteringDetector.DetectionStats stats = loiteringDetector.getLastStats();
                List<ShipBehaviorEvent> detected = extractEvents(traces);
                tagBatch(detected, jobParams);
                shipBehaviorEventRepository.saveEvents(detected);
                shipBehaviorEventDetailRepository.saveDetails(toDetails(traces, jobParams));
                logEventSummaries(traces);
                XxlJobLogger.log("LOITERING_V2 summary mmsi=" + mmsi
                        + " points=" + stats.getPoints()
                        + " cleanPoints=" + stats.getCleanPoints()
                        + " segments=" + stats.getSegments()
                        + " candidates=" + stats.getCandidates()
                        + " events=" + detected.size()
                        + " excludedEvents=" + stats.getExcludedEvents()
                        + " lowConfidenceEvents=" + stats.getLowConfidenceEvents()
                        + " batchNo=" + jobParams.getBatchNo());
                if (detected.isEmpty()) {
                    String reason = stats.getCandidates() == 0
                            ? "NO_CANDIDATE_AFTER_RULE_FILTER" : "ALL_CANDIDATES_EXCLUDED";
                    XxlJobLogger.log("LOITERING_V2 no_event mmsi=" + mmsi
                            + " reason=" + reason
                            + " points=" + stats.getPoints()
                            + " cleanPoints=" + stats.getCleanPoints()
                            + " segments=" + stats.getSegments()
                            + " batchNo=" + jobParams.getBatchNo());
                }
                processed++;
                events += detected.size();
                XxlJobLogger.log("徘徊 mmsi=" + mmsi + " points=" + points.size() + " events=" + detected.size());
            } catch (Exception ex) {
                failed++;
                log.error("loitering analysis failed, mmsi={}", mmsi, ex);
                XxlJobLogger.log("徘徊计算失败 mmsi=" + mmsi + " error=" + ex.getMessage());
            }
        }
        return new AnalysisResult(processed, failed, events);
    }

    public AnalysisResult analysisAlongside(String params) {
        JobParams jobParams = parseParams(params);
        applyDefaultTimeRange(jobParams);
        if (StringUtils.isBlank(jobParams.getMmsi()) || StringUtils.isBlank(jobParams.getTargetMmsi())) {
            XxlJobLogger.log("搭靠计算需要 mmsi 和 targetMmsi，首轮请用单船对短时间窗验证");
            return new AnalysisResult(0, 1, 0);
        }

        ShipAlongsideDetector.AlongsideRule rule = new ShipAlongsideDetector.AlongsideRule(
                alongsideMaxDistanceNm,
                alongsideMaxSpeed,
                alongsideMaxTimeGapMinutes,
                alongsideMinDurationMinutes,
                alongsideMinPoints,
                "ALONGSIDE_V1");

        try {
            List<TrajectoryPoint> ownPoints = trajectoryLoaderService.load(
                    jobParams.getMmsi(), jobParams.getStartTime(), jobParams.getEndTime(), trajectorySimplifyZoom);
            List<TrajectoryPoint> targetPoints = trajectoryLoaderService.load(
                    jobParams.getTargetMmsi(), jobParams.getStartTime(), jobParams.getEndTime(), trajectorySimplifyZoom);
            List<ShipBehaviorTrace> traces = alongsideDetector.detectWithTrace(
                    jobParams.getMmsi(), ownPoints, jobParams.getTargetMmsi(), targetPoints, rule);
            List<ShipBehaviorEvent> detected = extractEvents(traces);
            tagBatch(detected, jobParams);
            shipBehaviorEventRepository.saveEvents(detected);
            shipBehaviorEventDetailRepository.saveDetails(toDetails(traces, jobParams));
            logEventSummaries(traces);
            XxlJobLogger.log("搭靠 mmsi=" + jobParams.getMmsi()
                    + " targetMmsi=" + jobParams.getTargetMmsi()
                    + " ownPoints=" + ownPoints.size()
                    + " targetPoints=" + targetPoints.size()
                    + " events=" + detected.size());
            return new AnalysisResult(1, 0, detected.size());
        } catch (Exception ex) {
            log.error("alongside analysis failed, params={}", jobParams, ex);
            XxlJobLogger.log("搭靠计算失败 error=" + ex.getMessage());
            return new AnalysisResult(0, 1, 0);
        }
    }

    private void tagBatch(List<ShipBehaviorEvent> events, JobParams jobParams) {
        for (ShipBehaviorEvent event : events) {
            event.setBatchNo(jobParams.getBatchNo());
        }
    }

    private ShipLoiteringDetector.LoiteringConfig buildLoiteringConfig() {
        ShipLoiteringDetector.LoiteringConfig config = ShipLoiteringDetector.LoiteringConfig.defaultConfig();
        config.setRuleVersion(loiteringRuleVersion);
        config.setMaxGapMinutes(loiteringMaxGapMinutes);
        config.setEventSplitGapMinutes(loiteringEventSplitGapMinutes);
        config.setMaxValidSpeed(loiteringMaxValidSpeed);
        config.setMinWarningPoints(loiteringMinWarningPoints);
        config.setSlowMaxSpeed(loiteringSlowMaxSpeed);
        config.setSlowMinDurationMinutes(loiteringSlowMinDurationMinutes);
        config.setSlowMaxRadiusNm(loiteringSlowMaxRadiusNm);
        config.setSlowMinPoints(loiteringSlowMinPoints);
        config.setManeuverMinSpeed(loiteringManeuverMinSpeed);
        config.setManeuverMaxSpeed(loiteringManeuverMaxSpeed);
        config.setManeuverMinDurationMinutes(loiteringManeuverMinDurationMinutes);
        config.setManeuverMaxRadiusNm(loiteringManeuverMaxRadiusNm);
        config.setManeuverMinTurnCountPerHour(loiteringManeuverMinTurnCountPerHour);
        config.setManeuverMinCogChangePerHour(loiteringManeuverMinCogChangePerHour);
        config.setManeuverMinTortuosity(loiteringManeuverMinTortuosity);
        config.setTurnThresholdDegree(loiteringTurnThresholdDegree);
        config.setHighRiskEnabled(loiteringHighRiskEnabled);
        config.setHighRiskMinDurationMinutes(loiteringHighRiskMinDurationMinutes);
        config.setHighRiskMaxRadiusNm(loiteringHighRiskMaxRadiusNm);
        return config;
    }

    private List<ShipBehaviorEvent> extractEvents(List<ShipBehaviorTrace> traces) {
        List<ShipBehaviorEvent> events = new ArrayList<ShipBehaviorEvent>();
        for (ShipBehaviorTrace trace : traces) {
            if (trace != null && trace.getEvent() != null) {
                events.add(trace.getEvent());
            }
        }
        return events;
    }

    private List<ShipBehaviorEventDetail> toDetails(List<ShipBehaviorTrace> traces, JobParams jobParams) {
        List<ShipBehaviorEventDetail> details = new ArrayList<ShipBehaviorEventDetail>();
        for (ShipBehaviorTrace trace : traces) {
            ShipBehaviorEvent event = trace.getEvent();
            appendDetails(details, event, trace.getOwnPoints(), ShipBehaviorEventDetail.ROLE_OWN, jobParams);
            appendDetails(details, event, trace.getTargetPoints(), ShipBehaviorEventDetail.ROLE_TARGET, jobParams);
        }
        return details;
    }

    private void appendDetails(List<ShipBehaviorEventDetail> details, ShipBehaviorEvent event,
                               List<TrajectoryPoint> points, String pointRole, JobParams jobParams) {
        if (event == null || points == null || points.isEmpty()) {
            return;
        }
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            ShipBehaviorEventDetail detail = new ShipBehaviorEventDetail();
            detail.setBehaviorType(event.getBehaviorType());
            detail.setMmsi(event.getMmsi());
            detail.setTargetMmsi(event.getTargetMmsi());
            detail.setPointRole(pointRole);
            detail.setEventStartTime(event.getStartTime());
            detail.setEventEndTime(event.getEndTime());
            detail.setPointTime(TrajectoryUtils.formatTime(point));
            detail.setPointIndex(i + 1);
            detail.setLon(point.getLon());
            detail.setLat(point.getLat());
            detail.setSpeed(point.getSpeed());
            detail.setCourse(point.getCourse());
            detail.setNavStatus(point.getNavStatus());
            detail.setBatchNo(jobParams.getBatchNo());
            detail.setRuleVersion(event.getRuleVersion());
            details.add(detail);
        }
    }

    private void logEventSummaries(List<ShipBehaviorTrace> traces) {
        int maxCount = Math.max(0, traceLogMaxEvents);
        for (int i = 0; i < traces.size() && i < maxCount; i++) {
            ShipBehaviorEvent event = traces.get(i).getEvent();
            if (ShipBehaviorEvent.TYPE_LOITERING.equals(event.getBehaviorType())) {
                XxlJobLogger.log("event[" + (i + 1) + "] type=" + event.getBehaviorType()
                        + " subType=" + event.getEventSubType()
                        + " severity=" + event.getSeverityLevel()
                        + " confidence=" + event.getConfidenceLevel()
                        + " start=" + event.getStartTime()
                        + " end=" + event.getEndTime()
                        + " duration=" + event.getDurationMinutes()
                        + " radiusNm=" + event.getRadiusNm()
                        + " avgSpeed=" + event.getAvgSpeed()
                        + " pointCount=" + event.getPointCount()
                        + " maxGap=" + event.getMaxAisGapMinutes()
                        + " coverage=" + event.getTrackCoverageRatio()
                        + " score=" + event.getEventScore()
                        + " exclusion=" + event.getExclusionFlag()
                        + " reason=" + event.getExclusionReason()
                        + " batchNo=" + event.getBatchNo());
            } else {
                XxlJobLogger.log("event[" + (i + 1) + "] type=" + event.getBehaviorType()
                        + " start=" + event.getStartTime()
                        + " end=" + event.getEndTime()
                        + " duration=" + event.getDurationMinutes()
                        + " minDistanceNm=" + event.getMinDistanceNm()
                        + " maxDistanceNm=" + event.getMaxDistanceNm()
                        + " avgSpeed=" + event.getAvgSpeed()
                        + " pointCount=" + event.getPointCount()
                        + " targetMmsi=" + event.getTargetMmsi()
                        + " batchNo=" + event.getBatchNo());
            }
        }
        if (traces.size() > maxCount) {
            XxlJobLogger.log("event summaries truncated, total=" + traces.size() + ", logged=" + maxCount);
        }
    }

    private List<String> resolveMmsiList(JobParams jobParams) {
        if (!jobParams.getMmsiList().isEmpty()) {
            return jobParams.getMmsiList();
        }
        if (StringUtils.isNotBlank(jobParams.getMmsi())) {
            return Collections.singletonList(jobParams.getMmsi());
        }
        List<ShipForm> ships = ais41GeneralQueryRepository.selectRecentShips();
        List<String> result = new ArrayList<String>();
        if (ships != null) {
            for (ShipForm ship : ships) {
                if (ship != null && StringUtils.isNotBlank(ship.getMmsi())) {
                    result.add(ship.getMmsi().trim());
                }
            }
        }
        return result;
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
                jobParams.setMmsi(json.getString("mmsi"));
                jobParams.setTargetMmsi(json.getString("targetMmsi"));
                jobParams.setStartTime(json.getString("startTime"));
                jobParams.setEndTime(json.getString("endTime"));
                jobParams.setBatchNo(json.getString("batchNo"));
                if (json.getJSONArray("mmsiList") != null) {
                    for (Object item : json.getJSONArray("mmsiList")) {
                        jobParams.getMmsiList().add(String.valueOf(item));
                    }
                }
            } else {
                parseKeyValueParams(trimmed, jobParams);
            }
        } catch (Exception ex) {
            log.warn("parse ship behavior params failed, raw={}", trimmed, ex);
            parseKeyValueParams(trimmed, jobParams);
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
                jobParams.setMmsi(value);
            } else if ("targetMmsi".equalsIgnoreCase(key)) {
                jobParams.setTargetMmsi(value);
            } else if ("mmsiList".equalsIgnoreCase(key)) {
                for (String mmsi : value.split("\\|")) {
                    if (StringUtils.isNotBlank(mmsi)) {
                        jobParams.getMmsiList().add(mmsi.trim());
                    }
                }
            } else if ("startTime".equalsIgnoreCase(key)) {
                jobParams.setStartTime(value);
            } else if ("endTime".equalsIgnoreCase(key)) {
                jobParams.setEndTime(value);
            } else if ("batchNo".equalsIgnoreCase(key)) {
                jobParams.setBatchNo(value);
            }
        }
    }

    private void applyDefaultTimeRange(JobParams jobParams) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(Math.max(1, defaultHours));
        if (StringUtils.isBlank(jobParams.getStartTime())) {
            jobParams.setStartTime(start.format(TIME_FORMATTER));
        }
        if (StringUtils.isBlank(jobParams.getEndTime())) {
            jobParams.setEndTime(end.format(TIME_FORMATTER));
        }
        if (StringUtils.isBlank(jobParams.getBatchNo())) {
            jobParams.setBatchNo(jobParams.getStartTime().substring(0, 10));
        }
    }

    public static class AnalysisResult {
        private final int processed;
        private final int failed;
        private final int events;

        public AnalysisResult(int processed, int failed, int events) {
            this.processed = processed;
            this.failed = failed;
            this.events = events;
        }

        public boolean isSuccess() {
            return failed == 0;
        }

        public String getSummary() {
            return "processed=" + processed + ", failed=" + failed + ", events=" + events;
        }
    }

    private static class JobParams {
        private final List<String> mmsiList = new ArrayList<String>();
        private String mmsi;
        private String targetMmsi;
        private String startTime;
        private String endTime;
        private String batchNo;

        public List<String> getMmsiList() {
            return mmsiList;
        }

        public String getMmsi() {
            return mmsi;
        }

        public void setMmsi(String mmsi) {
            this.mmsi = mmsi;
        }

        public String getTargetMmsi() {
            return targetMmsi;
        }

        public void setTargetMmsi(String targetMmsi) {
            this.targetMmsi = targetMmsi;
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

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        @Override
        public String toString() {
            return "JobParams{"
                    + "mmsi='" + mmsi + '\''
                    + ", targetMmsi='" + targetMmsi + '\''
                    + ", startTime='" + startTime + '\''
                    + ", endTime='" + endTime + '\''
                    + ", batchNo='" + batchNo + '\''
                    + '}';
        }
    }
}
