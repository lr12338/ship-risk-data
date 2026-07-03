package com.hifleet.analysis.portcall.config;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.hifleet.analysis.alongside.core.model.AlongsideRuleConfig;
import com.hifleet.analysis.alongside.core.model.VesselType;

/**
 * Runtime configuration for the independent realtime alongside branch.
 */
public class AlongsideConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled;
    private String outputTopic;
    private boolean preFilterEnabled;
    private boolean preFilterExcludeInvalidMmsi;
    private double preFilterMaxSpeedKnots;
    private boolean shipTypeFilterEnabled;
    private Set<VesselType> highPriorityShipTypes = Collections.emptySet();
    private Set<VesselType> normalServiceShipTypes = Collections.emptySet();
    private Set<VesselType> lowPriorityShipTypes = Collections.emptySet();
    private double bucketSizeDegrees;
    private double candidateMaxDistanceNm;
    private double candidateMaxSpeedKnots;
    private long candidateMaxTimeGapMinutes;
    private int candidateMinPoints;
    private long candidateMinDurationMinutes;
    private double confirmMaxDistanceNm;
    private double confirmMaxSpeedKnots;
    private double confirmMaxRelativeSpeedKnots;
    private long confirmMaxTimeGapMinutes;
    private long confirmHighConfidenceTimeGapMinutes;
    private long confirmMinDurationMinutes;
    private int confirmMinPoints;
    private boolean headingEnabled;
    private double headingParallelThresholdDegrees;
    private boolean mergeEnabled;
    private long mergeMaxGapMinutes;
    private double mergeMaxCenterDistanceNm;
    private long aisGapMinutes;
    private int stateTtlHours;
    private long bucketPointTtlMinutes;
    private long pairTtlMinutes;
    private long endedPairTtlHours;
    private long outputUpdateIntervalMinutes;
    private boolean metricsEnabled;
    private boolean navStatusDowngradeEnabled;
    private String ruleVersion;
    private String severityCapNoSceneDataMaxLevel;
    private boolean sceneDataAvailable;
    private boolean counterpartyRiskAvailable;
    private boolean autoL4Enabled;

    public static AlongsideConfig fromProperties(Properties props) {
        AlongsideConfig config = new AlongsideConfig();
        config.setEnabled(toBoolean(value(props, "alongside.enabled"), false));
        config.setOutputTopic(firstValue(props, "alongside.output.topic", "kafka.alongside.topic"));
        config.setPreFilterEnabled(toBoolean(value(props, "alongside.pre-filter.enabled"), true));
        config.setPreFilterExcludeInvalidMmsi(toBoolean(value(props, "alongside.pre-filter.exclude-invalid-mmsi"), true));
        config.setPreFilterMaxSpeedKnots(toDouble(value(props, "alongside.pre-filter.max-speed"), 4.0d));
        config.setShipTypeFilterEnabled(toBoolean(value(props, "alongside.ship-type-filter.enabled"), false));
        config.setHighPriorityShipTypes(parseTypes(value(props, "alongside.ship-type.high-priority")));
        config.setNormalServiceShipTypes(parseTypes(value(props, "alongside.ship-type.normal-service")));
        config.setLowPriorityShipTypes(parseTypes(value(props, "alongside.ship-type.low-priority")));
        config.setBucketSizeDegrees(toDouble(value(props, "alongside.spatial.bucket-size-degrees"), 0.02d));
        config.setCandidateMaxDistanceNm(toDouble(value(props, "alongside.candidate.max-distance-nm"), 0.5d));
        config.setCandidateMaxSpeedKnots(toDouble(firstValue(props, "alongside.candidate.max-speed", "alongside.candidate.max-speed-knots"), 4.0d));
        config.setCandidateMaxTimeGapMinutes(toLong(value(props, "alongside.candidate.max-time-gap-minutes"), 10L));
        config.setCandidateMinPoints(toInt(value(props, "alongside.candidate.min-points"), 3));
        config.setCandidateMinDurationMinutes(toLong(value(props, "alongside.candidate.min-duration-minutes"), 20L));
        config.setConfirmMaxDistanceNm(toDouble(value(props, "alongside.confirm.max-distance-nm"), 0.15d));
        config.setConfirmMaxSpeedKnots(toDouble(firstValue(props, "alongside.confirm.max-speed", "alongside.confirm.max-speed-knots"), 2.0d));
        config.setConfirmMaxRelativeSpeedKnots(toDouble(firstValue(props, "alongside.confirm.max-relative-speed", "alongside.confirm.max-relative-speed-knots"), 1.0d));
        config.setConfirmMaxTimeGapMinutes(toLong(value(props, "alongside.confirm.max-time-gap-minutes"), 10L));
        config.setConfirmHighConfidenceTimeGapMinutes(toLong(value(props, "alongside.confirm.high-confidence-time-gap-minutes"), 5L));
        config.setConfirmMinDurationMinutes(toLong(value(props, "alongside.confirm.min-duration-minutes"), 30L));
        config.setConfirmMinPoints(toInt(value(props, "alongside.confirm.min-points"), 6));
        config.setHeadingEnabled(toBoolean(value(props, "alongside.heading.enabled"), true));
        config.setHeadingParallelThresholdDegrees(toDouble(firstValue(props, "alongside.heading.parallel-threshold-degree", "alongside.heading.parallel-threshold-degrees"), 30.0d));
        config.setMergeEnabled(toBoolean(value(props, "alongside.merge.enabled"), true));
        config.setMergeMaxGapMinutes(toLong(value(props, "alongside.merge.max-gap-minutes"), 30L));
        config.setMergeMaxCenterDistanceNm(toDouble(value(props, "alongside.merge.max-center-distance-nm"), 1.0d));
        config.setAisGapMinutes(toLong(value(props, "alongside.ais-gap-minutes"), 30L));
        config.setStateTtlHours(toInt(value(props, "alongside.state.ttl-hours"), 48));
        config.setBucketPointTtlMinutes(toLong(value(props, "alongside.state.bucket-point-ttl-minutes"), 15L));
        config.setPairTtlMinutes(toLong(value(props, "alongside.state.pair-ttl-minutes"), 120L));
        config.setEndedPairTtlHours(toLong(value(props, "alongside.state.ended-pair-ttl-hours"), 6L));
        config.setRuleVersion(value(props, "alongside.rule-version"));
        config.setSeverityCapNoSceneDataMaxLevel(firstValue(props, "alongside.scene.no-scene-data.max-level", "alongside.severity-cap.no-scene-data.max-level"));
        config.setSceneDataAvailable(toBoolean(firstValue(props, "alongside.scene-data.available", "alongside.scene.data-available"), false));
        config.setCounterpartyRiskAvailable(toBoolean(value(props, "alongside.counterparty-risk.available"), false));
        config.setAutoL4Enabled(toBoolean(firstValue(props, "alongside.scene.auto-l4-enabled", "alongside.auto-l4-enabled"), false));
        config.setNavStatusDowngradeEnabled(toBoolean(value(props, "alongside.scene.nav-status-downgrade.enabled"), true));
        config.setOutputUpdateIntervalMinutes(toLong(value(props, "alongside.output.update-interval-minutes"), 5L));
        config.setMetricsEnabled(toBoolean(value(props, "alongside.metrics.enabled"), true));
        return config;
    }

    public static void addDefaults(Properties defaults) {
        defaults.setProperty("alongside.enabled", "false");
        defaults.setProperty("kafka.alongside.topic", "RELIABLE_ALONGSIDE_EVENTS");
        defaults.setProperty("alongside.output.topic", "RELIABLE_ALONGSIDE_EVENTS");
        defaults.setProperty("alongside.spatial.bucket-size-degrees", "0.02");
        defaults.setProperty("alongside.pre-filter.enabled", "true");
        defaults.setProperty("alongside.pre-filter.max-speed", "4.0");
        defaults.setProperty("alongside.pre-filter.exclude-invalid-mmsi", "true");
        defaults.setProperty("alongside.ship-type-filter.enabled", "false");
        defaults.setProperty("alongside.ship-type.high-priority", "TANKER,CHEMICAL_TANKER,LNG,LPG,CARGO,BULK_CARRIER,CONTAINER,REEFER,FISH_CARRIER,BUNKER,SUPPLY,OFFSHORE_SUPPLY,UNKNOWN");
        defaults.setProperty("alongside.ship-type.normal-service", "TUG,PILOT,LAW_ENFORCEMENT,SAR,PORT_SERVICE,DREDGER,WORKBOAT");
        defaults.setProperty("alongside.ship-type.low-priority", "PLEASURE_CRAFT,SAILING,LOCAL_PASSENGER");
        defaults.setProperty("alongside.candidate.max-distance-nm", "0.5");
        defaults.setProperty("alongside.candidate.max-speed", "4.0");
        defaults.setProperty("alongside.candidate.max-speed-knots", "4.0");
        defaults.setProperty("alongside.candidate.max-time-gap-minutes", "10");
        defaults.setProperty("alongside.candidate.min-points", "3");
        defaults.setProperty("alongside.candidate.min-duration-minutes", "20");
        defaults.setProperty("alongside.confirm.max-distance-nm", "0.15");
        defaults.setProperty("alongside.confirm.max-speed", "2.0");
        defaults.setProperty("alongside.confirm.max-speed-knots", "2.0");
        defaults.setProperty("alongside.confirm.max-relative-speed", "1.0");
        defaults.setProperty("alongside.confirm.max-relative-speed-knots", "1.0");
        defaults.setProperty("alongside.confirm.max-time-gap-minutes", "10");
        defaults.setProperty("alongside.confirm.high-confidence-time-gap-minutes", "5");
        defaults.setProperty("alongside.confirm.min-duration-minutes", "30");
        defaults.setProperty("alongside.confirm.min-points", "6");
        defaults.setProperty("alongside.heading.enabled", "true");
        defaults.setProperty("alongside.heading.parallel-threshold-degree", "30");
        defaults.setProperty("alongside.heading.parallel-threshold-degrees", "30");
        defaults.setProperty("alongside.merge.enabled", "true");
        defaults.setProperty("alongside.merge.max-gap-minutes", "30");
        defaults.setProperty("alongside.merge.max-center-distance-nm", "1.0");
        defaults.setProperty("alongside.ais-gap-minutes", "30");
        defaults.setProperty("alongside.state.ttl-hours", "48");
        defaults.setProperty("alongside.state.bucket-point-ttl-minutes", "15");
        defaults.setProperty("alongside.state.pair-ttl-minutes", "120");
        defaults.setProperty("alongside.state.ended-pair-ttl-hours", "6");
        defaults.setProperty("alongside.rule-version", "ALONGSIDE_V2_INSURANCE");
        defaults.setProperty("alongside.scene.nav-status-downgrade.enabled", "true");
        defaults.setProperty("alongside.scene.no-scene-data.max-level", "L2");
        defaults.setProperty("alongside.scene.auto-l4-enabled", "false");
        defaults.setProperty("alongside.severity-cap.no-scene-data.max-level", "L2");
        defaults.setProperty("alongside.scene-data.available", "false");
        defaults.setProperty("alongside.counterparty-risk.available", "false");
        defaults.setProperty("alongside.auto-l4-enabled", "false");
        defaults.setProperty("alongside.output.update-interval-minutes", "5");
        defaults.setProperty("alongside.metrics.enabled", "true");
    }

    public AlongsideRuleConfig toRuleConfig() {
        AlongsideRuleConfig ruleConfig = new AlongsideRuleConfig();
        ruleConfig.setCandidateMaxDistanceNm(candidateMaxDistanceNm);
        ruleConfig.setCandidateMaxSpeedKnots(candidateMaxSpeedKnots);
        ruleConfig.setCandidateMaxTimeGapMinutes(candidateMaxTimeGapMinutes);
        ruleConfig.setCandidateMinPoints(candidateMinPoints);
        ruleConfig.setCandidateMinDurationMinutes(candidateMinDurationMinutes);
        ruleConfig.setConfirmMaxDistanceNm(confirmMaxDistanceNm);
        ruleConfig.setConfirmMaxSpeedKnots(confirmMaxSpeedKnots);
        ruleConfig.setConfirmMaxRelativeSpeedKnots(confirmMaxRelativeSpeedKnots);
        ruleConfig.setConfirmMaxTimeGapMinutes(confirmMaxTimeGapMinutes);
        ruleConfig.setConfirmHighConfidenceTimeGapMinutes(confirmHighConfidenceTimeGapMinutes);
        ruleConfig.setConfirmMinDurationMinutes(confirmMinDurationMinutes);
        ruleConfig.setConfirmMinPoints(confirmMinPoints);
        ruleConfig.setHeadingEnabled(headingEnabled);
        ruleConfig.setHeadingParallelThresholdDegrees(headingParallelThresholdDegrees);
        ruleConfig.setNoSceneDataMaxLevel(severityCapNoSceneDataMaxLevel);
        ruleConfig.setAutoL4Enabled(autoL4Enabled);
        ruleConfig.setRuleVersion(ruleVersion);
        return ruleConfig;
    }

    private static String value(Properties props, String key) {
        return props.getProperty(key);
    }

    private static String firstValue(Properties props, String key, String fallbackKey) {
        String first = props.getProperty(key);
        return first == null ? props.getProperty(fallbackKey) : first;
    }

    private static Set<VesselType> parseTypes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<VesselType> types = new HashSet<VesselType>();
        String[] parts = value.split(",");
        for (String part : parts) {
            try {
                types.add(VesselType.valueOf(part.trim()));
            } catch (RuntimeException ignored) {
                // Invalid config entries are ignored so one typo does not stop the job.
            }
        }
        return types;
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static long toLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double toDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static boolean toBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOutputTopic() {
        return outputTopic;
    }

    public void setOutputTopic(String outputTopic) {
        this.outputTopic = outputTopic;
    }

    public boolean isPreFilterEnabled() {
        return preFilterEnabled;
    }

    public void setPreFilterEnabled(boolean preFilterEnabled) {
        this.preFilterEnabled = preFilterEnabled;
    }

    public boolean isPreFilterExcludeInvalidMmsi() {
        return preFilterExcludeInvalidMmsi;
    }

    public void setPreFilterExcludeInvalidMmsi(boolean preFilterExcludeInvalidMmsi) {
        this.preFilterExcludeInvalidMmsi = preFilterExcludeInvalidMmsi;
    }

    public double getPreFilterMaxSpeedKnots() {
        return preFilterMaxSpeedKnots;
    }

    public void setPreFilterMaxSpeedKnots(double preFilterMaxSpeedKnots) {
        this.preFilterMaxSpeedKnots = preFilterMaxSpeedKnots;
    }

    public boolean isShipTypeFilterEnabled() {
        return shipTypeFilterEnabled;
    }

    public void setShipTypeFilterEnabled(boolean shipTypeFilterEnabled) {
        this.shipTypeFilterEnabled = shipTypeFilterEnabled;
    }

    public Set<VesselType> getHighPriorityShipTypes() {
        return highPriorityShipTypes;
    }

    public void setHighPriorityShipTypes(Set<VesselType> highPriorityShipTypes) {
        this.highPriorityShipTypes = highPriorityShipTypes == null ? Collections.<VesselType>emptySet() : highPriorityShipTypes;
    }

    public Set<VesselType> getNormalServiceShipTypes() {
        return normalServiceShipTypes;
    }

    public void setNormalServiceShipTypes(Set<VesselType> normalServiceShipTypes) {
        this.normalServiceShipTypes = normalServiceShipTypes == null ? Collections.<VesselType>emptySet() : normalServiceShipTypes;
    }

    public Set<VesselType> getLowPriorityShipTypes() {
        return lowPriorityShipTypes;
    }

    public void setLowPriorityShipTypes(Set<VesselType> lowPriorityShipTypes) {
        this.lowPriorityShipTypes = lowPriorityShipTypes == null ? Collections.<VesselType>emptySet() : lowPriorityShipTypes;
    }

    public double getBucketSizeDegrees() {
        return bucketSizeDegrees;
    }

    public void setBucketSizeDegrees(double bucketSizeDegrees) {
        this.bucketSizeDegrees = bucketSizeDegrees;
    }

    public double getCandidateMaxDistanceNm() {
        return candidateMaxDistanceNm;
    }

    public void setCandidateMaxDistanceNm(double candidateMaxDistanceNm) {
        this.candidateMaxDistanceNm = candidateMaxDistanceNm;
    }

    public double getCandidateMaxSpeedKnots() {
        return candidateMaxSpeedKnots;
    }

    public void setCandidateMaxSpeedKnots(double candidateMaxSpeedKnots) {
        this.candidateMaxSpeedKnots = candidateMaxSpeedKnots;
    }

    public long getCandidateMaxTimeGapMinutes() {
        return candidateMaxTimeGapMinutes;
    }

    public void setCandidateMaxTimeGapMinutes(long candidateMaxTimeGapMinutes) {
        this.candidateMaxTimeGapMinutes = candidateMaxTimeGapMinutes;
    }

    public int getCandidateMinPoints() {
        return candidateMinPoints;
    }

    public void setCandidateMinPoints(int candidateMinPoints) {
        this.candidateMinPoints = candidateMinPoints;
    }

    public long getCandidateMinDurationMinutes() {
        return candidateMinDurationMinutes;
    }

    public void setCandidateMinDurationMinutes(long candidateMinDurationMinutes) {
        this.candidateMinDurationMinutes = candidateMinDurationMinutes;
    }

    public double getConfirmMaxDistanceNm() {
        return confirmMaxDistanceNm;
    }

    public void setConfirmMaxDistanceNm(double confirmMaxDistanceNm) {
        this.confirmMaxDistanceNm = confirmMaxDistanceNm;
    }

    public double getConfirmMaxSpeedKnots() {
        return confirmMaxSpeedKnots;
    }

    public void setConfirmMaxSpeedKnots(double confirmMaxSpeedKnots) {
        this.confirmMaxSpeedKnots = confirmMaxSpeedKnots;
    }

    public double getConfirmMaxRelativeSpeedKnots() {
        return confirmMaxRelativeSpeedKnots;
    }

    public void setConfirmMaxRelativeSpeedKnots(double confirmMaxRelativeSpeedKnots) {
        this.confirmMaxRelativeSpeedKnots = confirmMaxRelativeSpeedKnots;
    }

    public long getConfirmMaxTimeGapMinutes() {
        return confirmMaxTimeGapMinutes;
    }

    public void setConfirmMaxTimeGapMinutes(long confirmMaxTimeGapMinutes) {
        this.confirmMaxTimeGapMinutes = confirmMaxTimeGapMinutes;
    }

    public long getConfirmHighConfidenceTimeGapMinutes() {
        return confirmHighConfidenceTimeGapMinutes;
    }

    public void setConfirmHighConfidenceTimeGapMinutes(long confirmHighConfidenceTimeGapMinutes) {
        this.confirmHighConfidenceTimeGapMinutes = confirmHighConfidenceTimeGapMinutes;
    }

    public long getConfirmMinDurationMinutes() {
        return confirmMinDurationMinutes;
    }

    public void setConfirmMinDurationMinutes(long confirmMinDurationMinutes) {
        this.confirmMinDurationMinutes = confirmMinDurationMinutes;
    }

    public int getConfirmMinPoints() {
        return confirmMinPoints;
    }

    public void setConfirmMinPoints(int confirmMinPoints) {
        this.confirmMinPoints = confirmMinPoints;
    }

    public boolean isHeadingEnabled() {
        return headingEnabled;
    }

    public void setHeadingEnabled(boolean headingEnabled) {
        this.headingEnabled = headingEnabled;
    }

    public double getHeadingParallelThresholdDegrees() {
        return headingParallelThresholdDegrees;
    }

    public void setHeadingParallelThresholdDegrees(double headingParallelThresholdDegrees) {
        this.headingParallelThresholdDegrees = headingParallelThresholdDegrees;
    }

    public boolean isMergeEnabled() {
        return mergeEnabled;
    }

    public void setMergeEnabled(boolean mergeEnabled) {
        this.mergeEnabled = mergeEnabled;
    }

    public long getMergeMaxGapMinutes() {
        return mergeMaxGapMinutes;
    }

    public void setMergeMaxGapMinutes(long mergeMaxGapMinutes) {
        this.mergeMaxGapMinutes = mergeMaxGapMinutes;
    }

    public double getMergeMaxCenterDistanceNm() {
        return mergeMaxCenterDistanceNm;
    }

    public void setMergeMaxCenterDistanceNm(double mergeMaxCenterDistanceNm) {
        this.mergeMaxCenterDistanceNm = mergeMaxCenterDistanceNm;
    }

    public long getAisGapMinutes() {
        return aisGapMinutes;
    }

    public void setAisGapMinutes(long aisGapMinutes) {
        this.aisGapMinutes = aisGapMinutes;
    }

    public int getStateTtlHours() {
        return stateTtlHours;
    }

    public void setStateTtlHours(int stateTtlHours) {
        this.stateTtlHours = stateTtlHours;
    }

    public long getBucketPointTtlMinutes() {
        return bucketPointTtlMinutes;
    }

    public void setBucketPointTtlMinutes(long bucketPointTtlMinutes) {
        this.bucketPointTtlMinutes = bucketPointTtlMinutes;
    }

    public long getPairTtlMinutes() {
        return pairTtlMinutes;
    }

    public void setPairTtlMinutes(long pairTtlMinutes) {
        this.pairTtlMinutes = pairTtlMinutes;
    }

    public long getEndedPairTtlHours() {
        return endedPairTtlHours;
    }

    public void setEndedPairTtlHours(long endedPairTtlHours) {
        this.endedPairTtlHours = endedPairTtlHours;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getSeverityCapNoSceneDataMaxLevel() {
        return severityCapNoSceneDataMaxLevel;
    }

    public void setSeverityCapNoSceneDataMaxLevel(String severityCapNoSceneDataMaxLevel) {
        this.severityCapNoSceneDataMaxLevel = severityCapNoSceneDataMaxLevel;
    }

    public boolean isSceneDataAvailable() {
        return sceneDataAvailable;
    }

    public void setSceneDataAvailable(boolean sceneDataAvailable) {
        this.sceneDataAvailable = sceneDataAvailable;
    }

    public boolean isCounterpartyRiskAvailable() {
        return counterpartyRiskAvailable;
    }

    public void setCounterpartyRiskAvailable(boolean counterpartyRiskAvailable) {
        this.counterpartyRiskAvailable = counterpartyRiskAvailable;
    }

    public boolean isAutoL4Enabled() {
        return autoL4Enabled;
    }

    public void setAutoL4Enabled(boolean autoL4Enabled) {
        this.autoL4Enabled = autoL4Enabled;
    }

    public long getOutputUpdateIntervalMinutes() {
        return outputUpdateIntervalMinutes;
    }

    public void setOutputUpdateIntervalMinutes(long outputUpdateIntervalMinutes) {
        this.outputUpdateIntervalMinutes = outputUpdateIntervalMinutes;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public boolean isNavStatusDowngradeEnabled() {
        return navStatusDowngradeEnabled;
    }

    public void setNavStatusDowngradeEnabled(boolean navStatusDowngradeEnabled) {
        this.navStatusDowngradeEnabled = navStatusDowngradeEnabled;
    }
}
