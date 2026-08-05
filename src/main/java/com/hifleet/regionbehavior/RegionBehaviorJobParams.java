package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RegionBehaviorJobParams {
    public static final String MODE = "region_behavior_v1";
    private static final Set<String> ALLOWED_BEHAVIORS = new HashSet<String>();
    private static final String[] CREDENTIAL_KEYS = {
            "password", "passwd", "secret", "credential", "accesskey", "token", "jdbcurl"
    };

    static {
        ALLOWED_BEHAVIORS.add("LOITERING");
        ALLOWED_BEHAVIORS.add("ALONGSIDE");
    }

    private String mode;
    private String regionId;
    private String regionName;
    private String polygonWkt;
    private String startTime;
    private String endTime;
    private final List<String> behaviorTypes = new ArrayList<String>();
    private String shipTypePolicy = "EXISTING_MERCHANT_POLICY";
    private Integer discoveryTimeSliceMinutes;
    private Integer analysisContextBeforeHours;
    private Integer analysisContextAfterHours;
    private Double regionBufferNm;
    private Integer maxCandidateVessels;
    private String batchNo;
    private boolean dryRun;
    private boolean storeCandidates;

    public static RegionBehaviorJobParams parse(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new IllegalArgumentException("region behavior params must not be blank");
        }
        JSONObject json;
        try {
            json = JSON.parseObject(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid region behavior JSON", ex);
        }
        rejectCredentials(json);
        RegionBehaviorJobParams params = new RegionBehaviorJobParams();
        params.mode = json.getString("mode");
        params.regionId = json.getString("regionId");
        params.regionName = json.getString("regionName");
        params.polygonWkt = json.getString("polygonWkt");
        params.startTime = json.getString("startTime");
        params.endTime = json.getString("endTime");
        params.shipTypePolicy = defaultIfBlank(json.getString("shipTypePolicy"),
                "EXISTING_MERCHANT_POLICY");
        params.discoveryTimeSliceMinutes = json.getInteger("discoveryTimeSliceMinutes");
        params.analysisContextBeforeHours = json.getInteger("analysisContextBeforeHours");
        params.analysisContextAfterHours = json.getInteger("analysisContextAfterHours");
        params.regionBufferNm = json.getDouble("regionBufferNm");
        params.maxCandidateVessels = json.getInteger("maxCandidateVessels");
        params.batchNo = json.getString("batchNo");
        params.dryRun = json.getBooleanValue("dryRun");
        params.storeCandidates = json.getBooleanValue("storeCandidates");
        JSONArray types = json.getJSONArray("behaviorTypes");
        if (types != null) {
            for (Object type : types) {
                if (type != null) {
                    params.behaviorTypes.add(String.valueOf(type).trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return params;
    }

    public void validate(RegionBehaviorConfig config) {
        if (!MODE.equals(mode)) {
            throw new IllegalArgumentException("mode must be " + MODE);
        }
        requireBounded(regionId, "regionId", 128);
        requireBounded(batchNo, "batchNo", 128);
        if (StringUtils.isBlank(regionName) || regionName.length() > 256) {
            throw new IllegalArgumentException("regionName is required and must be <= 256 characters");
        }
        if (StringUtils.isBlank(polygonWkt)) {
            throw new IllegalArgumentException("polygonWkt is required");
        }
        if (!"EXISTING_MERCHANT_POLICY".equals(shipTypePolicy)) {
            throw new IllegalArgumentException("unsupported shipTypePolicy");
        }
        if (behaviorTypes.isEmpty()) {
            throw new IllegalArgumentException("behaviorTypes must not be empty");
        }
        for (String type : behaviorTypes) {
            if (!ALLOWED_BEHAVIORS.contains(type)) {
                throw new IllegalArgumentException("unsupported behaviorType: " + type);
            }
        }
        if (!dryRun || storeCandidates) {
            throw new IllegalArgumentException("V1 requires dryRun=true and storeCandidates=false");
        }
        LocalDateTime start;
        LocalDateTime end;
        try {
            start = RegionBehaviorTimes.parse(startTime);
            end = RegionBehaviorTimes.parse(endTime);
        } catch (Exception ex) {
            throw new IllegalArgumentException("startTime/endTime must use yyyy-MM-dd HH:mm:ss", ex);
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        if (Duration.between(start, end).toMinutes() > config.getMaxRunHours() * 60L) {
            throw new IllegalArgumentException("time range exceeds maxRunHours=" + config.getMaxRunHours());
        }
        if (effectiveTimeSliceMinutes(config) < config.getMinTimeSliceMinutes()
                || effectiveTimeSliceMinutes(config) > config.getTimeSliceMinutes()) {
            throw new IllegalArgumentException("discoveryTimeSliceMinutes is outside configured boundary");
        }
        if (effectiveMaxCandidateVessels(config) > config.getMaxCandidateVessels()
                || effectiveMaxCandidateVessels(config) < 1) {
            throw new IllegalArgumentException("maxCandidateVessels exceeds configured boundary");
        }
        if (effectiveRegionBufferNm(config) < 0.0
                || effectiveRegionBufferNm(config) > config.getRegionBufferNm()) {
            throw new IllegalArgumentException("regionBufferNm is outside configured boundary");
        }
        if (effectiveContextBeforeHours(config) < 0
                || effectiveContextBeforeHours(config) > config.getAnalysisContextBeforeHours()
                || effectiveContextAfterHours(config) < 0
                || effectiveContextAfterHours(config) > config.getAnalysisContextAfterHours()) {
            throw new IllegalArgumentException("analysis context is outside configured boundary");
        }
    }

    public long runHours() {
        return Duration.between(RegionBehaviorTimes.parse(startTime), RegionBehaviorTimes.parse(endTime)).toHours();
    }

    public int effectiveTimeSliceMinutes(RegionBehaviorConfig config) {
        return discoveryTimeSliceMinutes == null ? config.getTimeSliceMinutes() : discoveryTimeSliceMinutes;
    }
    public int effectiveContextBeforeHours(RegionBehaviorConfig config) {
        return analysisContextBeforeHours == null ? config.getAnalysisContextBeforeHours() : analysisContextBeforeHours;
    }
    public int effectiveContextAfterHours(RegionBehaviorConfig config) {
        return analysisContextAfterHours == null ? config.getAnalysisContextAfterHours() : analysisContextAfterHours;
    }
    public double effectiveRegionBufferNm(RegionBehaviorConfig config) {
        return regionBufferNm == null ? config.getRegionBufferNm() : regionBufferNm;
    }
    public int effectiveMaxCandidateVessels(RegionBehaviorConfig config) {
        return maxCandidateVessels == null ? config.getMaxCandidateVessels() : maxCandidateVessels;
    }

    private static void requireBounded(String value, String name, int maxLength) {
        if (StringUtils.isBlank(value) || value.length() > maxLength
                || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(name + " is required, safe, and <= " + maxLength + " characters");
        }
    }

    private static void rejectCredentials(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : object.keySet()) {
                String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                for (String forbidden : CREDENTIAL_KEYS) {
                    if (normalized.contains(forbidden)) {
                        throw new IllegalArgumentException("inline credentials are forbidden: " + key);
                    }
                }
                rejectCredentials(object.get(key));
            }
        } else if (value instanceof JSONArray) {
            for (Object item : (JSONArray) value) {
                rejectCredentials(item);
            }
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    public String getMode() { return mode; }
    public String getRegionId() { return regionId; }
    public String getRegionName() { return regionName; }
    public String getPolygonWkt() { return polygonWkt; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public List<String> getBehaviorTypes() { return behaviorTypes; }
    public String getShipTypePolicy() { return shipTypePolicy; }
    public String getBatchNo() { return batchNo; }
    public boolean isDryRun() { return dryRun; }
    public boolean isStoreCandidates() { return storeCandidates; }
    public void setMode(String value) { this.mode = value; }
    public void setRegionId(String value) { this.regionId = value; }
    public void setRegionName(String value) { this.regionName = value; }
    public void setPolygonWkt(String value) { this.polygonWkt = value; }
    public void setStartTime(String value) { this.startTime = value; }
    public void setEndTime(String value) { this.endTime = value; }
    public void setBatchNo(String value) { this.batchNo = value; }
    public void setDryRun(boolean value) { this.dryRun = value; }
    public void setStoreCandidates(boolean value) { this.storeCandidates = value; }
}
