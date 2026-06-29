package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.beans.ShipForm;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.service.ShipLoiteringDetector;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
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

    @Autowired
    private TrajectoryLoaderService trajectoryLoaderService;

    @Autowired
    private ShipBehaviorEventRepository shipBehaviorEventRepository;

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    public AnalysisResult analysisLoitering(String params) {
        JobParams jobParams = parseParams(params);
        applyDefaultTimeRange(jobParams);
        List<String> mmsiList = resolveMmsiList(jobParams);
        ShipLoiteringDetector.LoiteringRule rule = new ShipLoiteringDetector.LoiteringRule(
                loiteringMaxSpeed,
                loiteringMinDurationMinutes,
                loiteringMaxRadiusNm,
                loiteringMinPoints,
                "LOITERING_V1");

        int processed = 0;
        int failed = 0;
        int events = 0;
        for (String mmsi : mmsiList) {
            try {
                List<TrajectoryPoint> points = trajectoryLoaderService.load(
                        mmsi, jobParams.getStartTime(), jobParams.getEndTime(), trajectorySimplifyZoom);
                List<ShipBehaviorEvent> detected = loiteringDetector.detect(mmsi, points, rule);
                tagBatch(detected, jobParams);
                shipBehaviorEventRepository.saveEvents(detected);
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
            List<ShipBehaviorEvent> detected = alongsideDetector.detect(
                    jobParams.getMmsi(), ownPoints, jobParams.getTargetMmsi(), targetPoints, rule);
            tagBatch(detected, jobParams);
            shipBehaviorEventRepository.saveEvents(detected);
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
