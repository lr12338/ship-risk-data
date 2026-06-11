package com.hifleet.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.beans.ShipForm;
import com.hifleet.domain.ZoneCrossingEvent;
import com.hifleet.domain.ZoneDefinition;
import com.hifleet.hbase.GetTrajectoryFromHbase;
import com.hifleet.hbase.NewOffersForm;
import com.hifleet.repository.AIS41GeneralQueryRepository;
import com.hifleet.route.beans.NewShipForm;
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
import java.util.List;
import java.util.Vector;

/**
 * 定时统计所有船舶区域进出事件：
 * 1. 船舶列表参考 AisAnalysisService#selectRecentShips
 * 2. HBase 轨迹 + 区域匹配参考 event / maintenance.tools
 * 3. 进入/离开各保存一条数据库记录
 */
@Service
public class ZoneCrossingStatsService {

    private static final Logger log = LoggerFactory.getLogger(ZoneCrossingStatsService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GetTrajectoryFromHbase getTrajectoryFromHbase = new GetTrajectoryFromHbase();
    private final ZoneCrossingDetector zoneCrossingDetector = new ZoneCrossingDetector();

    @Autowired
    private ZoneLoaderService zoneLoaderService;

    @Autowired
    private ZoneCrossingStoreService zoneCrossingStoreService;

    @Autowired
    private AIS41GeneralQueryRepository ais41GeneralQueryRepository;

    public void calculateZoneCrossingStats(String params) {
        JobParams jobParams = parseParams(params);
        log.info("zone crossing stats job started, params={}", jobParams);
        XxlJobLogger.log("zone crossing job params: " + jobParams);

        List<ZoneDefinition> zones = loadZones(jobParams);
        if (zones.isEmpty()) {
            log.warn("no zones loaded, job skipped");
            XxlJobLogger.log("no zones loaded, job skipped");
            return;
        }
        ZoneSpatialMatcher matcher = new ZoneSpatialMatcher(zones);

        List<String> mmsiList = resolveMmsiList(jobParams);
        if (mmsiList.isEmpty()) {
            log.warn("no ships to process, job skipped");
            XxlJobLogger.log("no ships to process, job skipped");
            return;
        }

        int totalEvents = 0;
        int processedShips = 0;
        for (String mmsi : mmsiList) {
            try {
                List<ZoneCrossingEvent> events = calculateForShip(mmsi, jobParams.getStartTime(),
                        jobParams.getEndTime(), matcher);
                for (ZoneCrossingEvent event : events) {
                if (StringUtils.isBlank(event.getEventCategory())) {
                    event.setEventCategory("PORT_ZONE");
                }
            }
            zoneCrossingStoreService.saveEvents(events);
                totalEvents += events.size();
                processedShips++;
                if (!events.isEmpty()) {
                    log.info("mmsi={} events={}", mmsi, events.size());
                }
                if (processedShips % 100 == 0) {
                    XxlJobLogger.log("processed ships=" + processedShips + "/" + mmsiList.size()
                            + ", events=" + totalEvents);
                }
            } catch (Exception ex) {
                log.error("process ship failed, mmsi={}", mmsi, ex);
                XxlJobLogger.log("process ship failed, mmsi=" + mmsi + ", error=" + ex.getMessage());
            }
        }

        log.info("zone crossing stats job finished, ships={}/{}, events={}",
                processedShips, mmsiList.size(), totalEvents);
        XxlJobLogger.log("job finished, ships=" + processedShips + "/" + mmsiList.size()
                + ", events=" + totalEvents);
    }

    public List<ZoneCrossingEvent> calculateForShip(String mmsi, String startTime, String endTime,
                                                    ZoneSpatialMatcher matcher) {
        NewOffersForm offers = getTrajectoryFromHbase.getOriginalVoffersFromHbase(mmsi, startTime, endTime);
        Vector<NewShipForm> points = offers.getOffers();
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<ZoneCrossingEvent> events = zoneCrossingDetector.detectEvents(mmsi, points, matcher);
        log.debug("mmsi={} trajectoryPoints={} events={}", mmsi, points.size(), events.size());
        return events;
    }

    /**
     * 未指定 mmsi 时，读取所有需要计算的船舶（与 AisAnalysisService 相同）。
     */
    private List<String> resolveMmsiList(JobParams jobParams) {
        if (!jobParams.getMmsiList().isEmpty()) {
            return jobParams.getMmsiList();
        }
        List<ShipForm> ships = ais41GeneralQueryRepository.selectRecentShips();
        List<String> mmsiList = new ArrayList<String>();
        if (ships == null) {
            return mmsiList;
        }
        for (ShipForm ship : ships) {
            if (ship != null && StringUtils.isNotBlank(ship.getMmsi())) {
                mmsiList.add(ship.getMmsi().trim());
            }
        }
        log.info("loaded ships from recentships, count={}", mmsiList.size());
        XxlJobLogger.log("loaded ships from recentships, count=" + mmsiList.size());
        return mmsiList;
    }

    private List<ZoneDefinition> loadZones(JobParams jobParams) {
        if ("port".equalsIgnoreCase(jobParams.getZoneSource())) {
            return zoneLoaderService.loadPortZones(jobParams.getZoneIds());
        }
        if ("alert".equalsIgnoreCase(jobParams.getZoneSource())) {
            return zoneLoaderService.loadAlertZones(jobParams.getZoneIds());
        }
        return zoneLoaderService.loadAllZones(jobParams.getZoneIds());
    }

    private JobParams parseParams(String params) {
        JobParams jobParams = new JobParams();
        if (StringUtils.isNotBlank(params)) {
            parseParamsContent(params.trim(), jobParams);
        }
        applyDefaultTimeRange(jobParams);
        return jobParams;
    }

    private void parseParamsContent(String trimmed, JobParams jobParams) {
        try {
            if (trimmed.startsWith("{")) {
                JSONObject json = JSON.parseObject(trimmed);
                jobParams.setStartTime(json.getString("startTime"));
                jobParams.setEndTime(json.getString("endTime"));
                jobParams.setZoneSource(json.getString("zoneSource"));
                jobParams.setTimeRange(json.getString("timeRange"));
                if (json.containsKey("days")) {
                    jobParams.setDays(json.getInteger("days"));
                }
                if (json.containsKey("months")) {
                    jobParams.setMonths(json.getInteger("months"));
                }
                if (json.containsKey("years")) {
                    jobParams.setYears(json.getInteger("years"));
                }
                JSONArray mmsiArray = json.getJSONArray("mmsiList");
                if (mmsiArray != null) {
                    for (int i = 0; i < mmsiArray.size(); i++) {
                        jobParams.getMmsiList().add(String.valueOf(mmsiArray.get(i)));
                    }
                }
                if (json.containsKey("mmsi")) {
                    jobParams.getMmsiList().add(String.valueOf(json.get("mmsi")));
                }
                JSONArray zoneIdArray = json.getJSONArray("zoneIds");
                if (zoneIdArray != null) {
                    for (int i = 0; i < zoneIdArray.size(); i++) {
                        jobParams.getZoneIds().add(zoneIdArray.getInteger(i));
                    }
                }
            } else {
                parseKeyValueParams(trimmed, jobParams);
            }
        } catch (Exception ex) {
            log.warn("parse job params failed, raw={}", trimmed, ex);
            parseKeyValueParams(trimmed, jobParams);
        }
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
            } else if ("zoneSource".equalsIgnoreCase(key)) {
                jobParams.setZoneSource(value);
            } else if ("timeRange".equalsIgnoreCase(key)) {
                jobParams.setTimeRange(value);
            } else if ("days".equalsIgnoreCase(key)) {
                jobParams.setDays(Integer.valueOf(value));
            } else if ("months".equalsIgnoreCase(key)) {
                jobParams.setMonths(Integer.valueOf(value));
            } else if ("years".equalsIgnoreCase(key)) {
                jobParams.setYears(Integer.valueOf(value));
            } else if ("zoneIds".equalsIgnoreCase(key)) {
                for (String zoneId : value.split("\\|")) {
                    if (StringUtils.isNotBlank(zoneId)) {
                        jobParams.getZoneIds().add(Integer.valueOf(zoneId.trim()));
                    }
                }
            }
        }
    }

    private void applyDefaultTimeRange(JobParams jobParams) {
        if (StringUtils.isNotBlank(jobParams.getStartTime()) && StringUtils.isNotBlank(jobParams.getEndTime())) {
            return;
        }
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = resolveStartTime(jobParams, end);
        if (StringUtils.isBlank(jobParams.getStartTime())) {
            jobParams.setStartTime(start.format(TIME_FORMATTER));
        }
        if (StringUtils.isBlank(jobParams.getEndTime())) {
            jobParams.setEndTime(end.format(TIME_FORMATTER));
        }
    }

    private LocalDateTime resolveStartTime(JobParams jobParams, LocalDateTime end) {
        if (jobParams.getDays() != null && jobParams.getDays() > 0) {
            return end.minusDays(jobParams.getDays());
        }
        if (jobParams.getMonths() != null && jobParams.getMonths() > 0) {
            return end.minusMonths(jobParams.getMonths());
        }
        if (jobParams.getYears() != null && jobParams.getYears() > 0) {
            return end.minusYears(jobParams.getYears());
        }
        String timeRange = StringUtils.defaultIfBlank(jobParams.getTimeRange(), "1y").toLowerCase();
        if ("1d".equals(timeRange) || "day".equals(timeRange)) {
            return end.minusDays(1);
        }
        if ("1m".equals(timeRange) || "month".equals(timeRange)) {
            return end.minusMonths(1);
        }
        return end.minusYears(1);
    }

    private static class JobParams {
        private final List<String> mmsiList = new ArrayList<String>();
        private final List<Integer> zoneIds = new ArrayList<Integer>();
        private String startTime;
        private String endTime;
        private String zoneSource = "all";
        private String timeRange = "1y";
        private Integer days;
        private Integer months;
        private Integer years;

        public List<String> getMmsiList() {
            return mmsiList;
        }

        public List<Integer> getZoneIds() {
            return zoneIds;
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

        public String getZoneSource() {
            return zoneSource;
        }

        public void setZoneSource(String zoneSource) {
            this.zoneSource = zoneSource;
        }

        public String getTimeRange() {
            return timeRange;
        }

        public void setTimeRange(String timeRange) {
            this.timeRange = timeRange;
        }

        public Integer getDays() {
            return days;
        }

        public void setDays(Integer days) {
            this.days = days;
        }

        public Integer getMonths() {
            return months;
        }

        public void setMonths(Integer months) {
            this.months = months;
        }

        public Integer getYears() {
            return years;
        }

        public void setYears(Integer years) {
            this.years = years;
        }

        @Override
        public String toString() {
            return "JobParams{" +
                    "mmsiList=" + (mmsiList.isEmpty() ? "ALL" : mmsiList) +
                    ", zoneIds=" + zoneIds +
                    ", startTime='" + startTime + '\'' +
                    ", endTime='" + endTime + '\'' +
                    ", zoneSource='" + zoneSource + '\'' +
                    ", timeRange='" + timeRange + '\'' +
                    ", days=" + days +
                    ", months=" + months +
                    ", years=" + years +
                    '}';
        }
    }
}
