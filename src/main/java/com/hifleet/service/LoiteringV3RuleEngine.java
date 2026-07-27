package com.hifleet.service;

import com.hifleet.service.LoiteringV3FeatureExtractor.Features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loitering V3 shadow 规则内核。只依赖已计算特征和显式场景上下文。
 */
public class LoiteringV3RuleEngine {

    public static final String STATUS_REVIEW = "REVIEW";
    public static final String STATUS_FINAL = "FINAL";
    public static final String STATUS_EXCLUDED = "EXCLUDED";

    public static final String PATTERN_CIRCULAR = "CIRCULAR";
    public static final String PATTERN_ORBITAL = "ORBITAL";
    public static final String PATTERN_SERPENTINE = "SERPENTINE";
    public static final String PATTERN_BACK_AND_FORTH = "BACK_AND_FORTH";
    public static final String PATTERN_WAITING_DRIFT = "WAITING_DRIFT";
    public static final String PATTERN_NONE = "NONE";

    public static final String REASON_LOITERING_PATTERN_CONFIRMED = "LOITERING_PATTERN_CONFIRMED";
    public static final String REASON_NON_MERCHANT_SHIP = "NON_MERCHANT_SHIP";
    public static final String REASON_SHIP_TYPE_UNKNOWN = "SHIP_TYPE_UNKNOWN";
    public static final String REASON_INSUFFICIENT_DURATION = "INSUFFICIENT_DURATION";
    public static final String REASON_OUTSIDE_TARGET_AREA = "OUTSIDE_TARGET_AREA";
    public static final String REASON_NORMAL_BERTHING = "NORMAL_BERTHING";
    public static final String REASON_NORMAL_BERTHING_APPROACH = "NORMAL_BERTHING_APPROACH";
    public static final String REASON_STRAIGHT_LOW_SPEED_TRANSIT = "STRAIGHT_LOW_SPEED_TRANSIT";
    public static final String REASON_SCENE_DATA_UNAVAILABLE = "SCENE_DATA_UNAVAILABLE";
    public static final String REASON_NO_LOITERING_PATTERN = "NO_LOITERING_PATTERN";
    public static final String REASON_FORMAL_GATE_NOT_MET = "FORMAL_GATE_NOT_MET";

    private final Config config;

    public LoiteringV3RuleEngine() {
        this(Config.shadowDefaults());
    }

    public LoiteringV3RuleEngine(Config config) {
        this.config = config == null ? Config.shadowDefaults() : config;
    }

    public Decision evaluate(Features features, Context context) {
        if (features == null) {
            throw new IllegalArgumentException("features must not be null");
        }
        Context effectiveContext = context == null ? Context.builder().build() : context;
        double confidenceScore = calculateConfidence(features, effectiveContext);

        if (effectiveContext.getMerchantShip() == null) {
            return Decision.excluded(REASON_SHIP_TYPE_UNKNOWN, confidenceScore);
        }
        if (!effectiveContext.getMerchantShip()) {
            return Decision.excluded(REASON_NON_MERCHANT_SHIP, confidenceScore);
        }
        if (features.getDurationMinutes() < config.getReviewMinDurationMinutes()) {
            return Decision.excluded(REASON_INSUFFICIENT_DURATION, confidenceScore);
        }
        if (!effectiveContext.isSceneDataAvailable()) {
            List<String> patterns = matchPatterns(features, effectiveContext);
            return Decision.review(primaryPattern(patterns), patterns,
                    REASON_SCENE_DATA_UNAVAILABLE, confidenceScore);
        }
        if (!effectiveContext.isNearPort() && !effectiveContext.isInsidePort()
                && !effectiveContext.isNearChannel()) {
            return Decision.excluded(REASON_OUTSIDE_TARGET_AREA, confidenceScore);
        }
        if ((effectiveContext.isInsideBerth() || effectiveContext.isNearBerthPoint())
                && effectiveContext.getBerthStationaryMinutes() >= config.getBerthStayMinutes()) {
            return Decision.excluded(REASON_NORMAL_BERTHING, confidenceScore);
        }
        if (effectiveContext.isBerthEntryFollowedByStay()
                && effectiveContext.getMinutesToBerthEntry() >= 0
                && effectiveContext.getMinutesToBerthEntry() <= config.getBerthingApproachLookAheadMinutes()
                && effectiveContext.getFinalApproachDirectionEfficiency()
                >= config.getNormalApproachMinDirectionEfficiency()
                && effectiveContext.getFinalApproachDirectionChangePerHour()
                < config.getStraightMaxDirectionChangePerHour()) {
            return Decision.excluded(REASON_NORMAL_BERTHING_APPROACH, confidenceScore);
        }
        if (isStraightTransit(features)) {
            return Decision.excluded(REASON_STRAIGHT_LOW_SPEED_TRANSIT, confidenceScore);
        }

        List<String> patterns = matchPatterns(features, effectiveContext);
        String primaryPattern = primaryPattern(patterns);
        if (patterns.isEmpty()) {
            return Decision.review(primaryPattern, patterns, REASON_NO_LOITERING_PATTERN, confidenceScore);
        }
        if (!passesFormalGates(features, confidenceScore)) {
            return Decision.review(primaryPattern, patterns, REASON_FORMAL_GATE_NOT_MET, confidenceScore);
        }

        double riskScore = calculateRiskScore(primaryPattern, features, effectiveContext, confidenceScore);
        return Decision.finalEvent(primaryPattern, patterns, confidenceScore, riskScore);
    }

    private boolean isStraightTransit(Features features) {
        return features.getDirectionEfficiency() >= config.getStraightMinDirectionEfficiency()
                && features.getDirectionChangePerHour() < config.getStraightMaxDirectionChangePerHour()
                && features.getStartEndDistanceNm() > config.getStraightMinStartEndDistanceNm();
    }

    private List<String> matchPatterns(Features features, Context context) {
        List<String> patterns = new ArrayList<String>();
        if (features.getClosureScore() >= config.getCircularMinClosureScore()
                && features.getStartEndDistanceNm() <= config.getCircularMaxStartEndDistanceNm()
                && Math.abs(features.getSignedTurnDegree()) >= config.getCircularMinSignedTurnDegree()
                && features.getRadialCv() <= config.getCircularMaxRadialCv()
                && features.getRadiusNm() <= config.getFormalMaxRadiusNm()) {
            patterns.add(PATTERN_CIRCULAR);
        }
        if (Math.abs(features.getSignedTurnDegree()) >= config.getOrbitalMinSignedTurnDegree()
                && features.getSameTurnDirectionRatio() >= config.getOrbitalMinSameTurnDirectionRatio()
                && features.getDirectionEfficiency() <= config.getPatternMaxDirectionEfficiency()
                && features.getReversalCount() < config.getBackAndForthMinReversals()
                && features.getRadiusNm() <= config.getFormalMaxRadiusNm()) {
            patterns.add(PATTERN_ORBITAL);
        }
        if (features.getDirectionChangePerHour() >= config.getSerpentineMinDirectionChangePerHour()
                && features.getTurnSignChangeCount() >= config.getSerpentineMinTurnSignChanges()
                && features.getTortuosity() >= config.getSerpentineMinTortuosity()
                && features.getRadiusNm() <= config.getFormalMaxRadiusNm()) {
            patterns.add(PATTERN_SERPENTINE);
        }
        if (features.getReversalCount() >= config.getBackAndForthMinReversals()
                && features.getDirectionEfficiency() <= config.getPatternMaxDirectionEfficiency()
                && features.getRadiusNm() <= config.getBackAndForthMaxRadiusNm()) {
            patterns.add(PATTERN_BACK_AND_FORTH);
        }
        if (features.getDurationMinutes() >= config.getWaitingMinDurationMinutes()
                && features.getAverageSpeed() >= config.getWaitingMinSpeed()
                && features.getAverageSpeed() <= config.getWaitingMaxSpeed()
                && features.getRadiusNm() <= config.getWaitingMaxRadiusNm()
                && !context.isInsideBerth() && !context.isNearBerthPoint()) {
            patterns.add(PATTERN_WAITING_DRIFT);
        }
        return patterns;
    }

    private String primaryPattern(List<String> patterns) {
        return patterns == null || patterns.isEmpty() ? PATTERN_NONE : patterns.get(0);
    }

    private boolean passesFormalGates(Features features, double confidenceScore) {
        return features.getDurationMinutes() >= config.getFormalMinDurationMinutes()
                && features.getLowSpeedPointRatio() >= config.getFormalMinLowSpeedPointRatio()
                && features.getP90Speed() <= config.getFormalMaxP90Speed()
                && features.getRadiusNm() <= config.getFormalMaxRadiusNm()
                && features.getTrajectoryAreaNm2() <= config.getFormalMaxAreaNm2()
                && confidenceScore >= config.getFormalMinConfidenceScore();
    }

    private double calculateConfidence(Features features, Context context) {
        double expectedPoints = features.getDurationMinutes() / 5.0 + 1.0;
        double coverageScore = Math.min(1.0, features.getPointCount() / Math.max(1.0, expectedPoints));
        double gapScore = Math.max(0.0, 1.0 - features.getMaxGapMinutes() / 30.0);
        double courseScore = Math.max(0.0, Math.min(1.0, features.getValidDerivedCourseRatio()));
        double sceneScore = context.isPortDataAvailable() && context.isBerthDataAvailable()
                ? 1.0 : context.isPortDataAvailable() ? 0.5 : 0.0;
        return clamp(0.40 * coverageScore + 0.20 * gapScore
                + 0.20 * courseScore + 0.20 * sceneScore, 0.0, 1.0);
    }

    private double calculateRiskScore(String pattern, Features features,
                                      Context context, double confidenceScore) {
        int patternScore;
        if (PATTERN_CIRCULAR.equals(pattern) || PATTERN_ORBITAL.equals(pattern)) {
            patternScore = 40;
        } else if (PATTERN_SERPENTINE.equals(pattern)) {
            patternScore = 35;
        } else if (PATTERN_BACK_AND_FORTH.equals(pattern)) {
            patternScore = 30;
        } else {
            patternScore = 20;
        }

        int durationScore = features.getDurationMinutes() >= 480 ? 20
                : features.getDurationMinutes() >= 240 ? 14 : 8;
        int confinementScore = features.getRadiusNm() <= 1.0 ? 15
                : features.getRadiusNm() <= 3.0 ? 10 : 5;
        int contextScore = context.isInsidePort() ? 10 : 15;
        int noBerthScore = 0;
        if (!context.isInsideBerth()) {
            noBerthScore += 6;
        }
        if (!context.isNearBerthPoint()) {
            noBerthScore += 4;
        }
        double rawScore = patternScore + durationScore + confinementScore + contextScore + noBerthScore;
        return Math.round(clamp(rawScore * confidenceScore, 0.0, 100.0) * 100.0) / 100.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static class Decision {
        private final String status;
        private final String trajectoryPattern;
        private final List<String> matchedPatterns;
        private final String reasonCode;
        private final double confidenceScore;
        private final double riskScore;

        private Decision(String status, String trajectoryPattern, List<String> matchedPatterns,
                         String reasonCode, double confidenceScore, double riskScore) {
            this.status = status;
            this.trajectoryPattern = trajectoryPattern;
            this.matchedPatterns = matchedPatterns == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(matchedPatterns));
            this.reasonCode = reasonCode;
            this.confidenceScore = confidenceScore;
            this.riskScore = riskScore;
        }

        private static Decision excluded(String reasonCode, double confidenceScore) {
            return new Decision(STATUS_EXCLUDED, PATTERN_NONE, Collections.<String>emptyList(),
                    reasonCode, confidenceScore, 0.0);
        }

        private static Decision review(String pattern, List<String> patterns,
                                       String reasonCode, double confidenceScore) {
            return new Decision(STATUS_REVIEW, pattern, patterns, reasonCode, confidenceScore, 0.0);
        }

        private static Decision finalEvent(String pattern, List<String> patterns,
                                           double confidenceScore, double riskScore) {
            return new Decision(STATUS_FINAL, pattern, patterns,
                    REASON_LOITERING_PATTERN_CONFIRMED, confidenceScore, riskScore);
        }

        public String getStatus() {
            return status;
        }

        public String getTrajectoryPattern() {
            return trajectoryPattern;
        }

        public List<String> getMatchedPatterns() {
            return matchedPatterns;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }

        public double getRiskScore() {
            return riskScore;
        }
    }

    public static class Context {
        private final Boolean merchantShip;
        private final boolean portDataAvailable;
        private final boolean berthDataAvailable;
        private final boolean nearPort;
        private final boolean insidePort;
        private final boolean insideBerth;
        private final boolean nearBerthPoint;
        private final boolean nearChannel;
        private final boolean berthEntryFollowedByStay;
        private final long berthStationaryMinutes;
        private final long minutesToBerthEntry;
        private final double finalApproachDirectionEfficiency;
        private final double finalApproachDirectionChangePerHour;

        private Context(Builder builder) {
            this.merchantShip = builder.merchantShip;
            this.portDataAvailable = builder.portDataAvailable;
            this.berthDataAvailable = builder.berthDataAvailable;
            this.nearPort = builder.nearPort;
            this.insidePort = builder.insidePort;
            this.insideBerth = builder.insideBerth;
            this.nearBerthPoint = builder.nearBerthPoint;
            this.nearChannel = builder.nearChannel;
            this.berthEntryFollowedByStay = builder.berthEntryFollowedByStay;
            this.berthStationaryMinutes = builder.berthStationaryMinutes;
            this.minutesToBerthEntry = builder.minutesToBerthEntry;
            this.finalApproachDirectionEfficiency = builder.finalApproachDirectionEfficiency;
            this.finalApproachDirectionChangePerHour = builder.finalApproachDirectionChangePerHour;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Boolean getMerchantShip() {
            return merchantShip;
        }

        public boolean isPortDataAvailable() {
            return portDataAvailable;
        }

        public boolean isBerthDataAvailable() {
            return berthDataAvailable;
        }

        public boolean isSceneDataAvailable() {
            return portDataAvailable && berthDataAvailable;
        }

        public boolean isNearPort() {
            return nearPort;
        }

        public boolean isInsidePort() {
            return insidePort;
        }

        public boolean isInsideBerth() {
            return insideBerth;
        }

        public boolean isNearBerthPoint() {
            return nearBerthPoint;
        }

        public boolean isNearChannel() {
            return nearChannel;
        }

        public boolean isBerthEntryFollowedByStay() {
            return berthEntryFollowedByStay;
        }

        public long getBerthStationaryMinutes() {
            return berthStationaryMinutes;
        }

        public long getMinutesToBerthEntry() {
            return minutesToBerthEntry;
        }

        public double getFinalApproachDirectionEfficiency() {
            return finalApproachDirectionEfficiency;
        }

        public double getFinalApproachDirectionChangePerHour() {
            return finalApproachDirectionChangePerHour;
        }

        public static class Builder {
            private Boolean merchantShip;
            private boolean portDataAvailable;
            private boolean berthDataAvailable;
            private boolean nearPort;
            private boolean insidePort;
            private boolean insideBerth;
            private boolean nearBerthPoint;
            private boolean nearChannel;
            private boolean berthEntryFollowedByStay;
            private long berthStationaryMinutes;
            private long minutesToBerthEntry = -1L;
            private double finalApproachDirectionEfficiency;
            private double finalApproachDirectionChangePerHour;

            public Builder merchantShip(Boolean merchantShip) {
                this.merchantShip = merchantShip;
                return this;
            }

            public Builder portDataAvailable(boolean portDataAvailable) {
                this.portDataAvailable = portDataAvailable;
                return this;
            }

            public Builder berthDataAvailable(boolean berthDataAvailable) {
                this.berthDataAvailable = berthDataAvailable;
                return this;
            }

            public Builder nearPort(boolean nearPort) {
                this.nearPort = nearPort;
                return this;
            }

            public Builder insidePort(boolean insidePort) {
                this.insidePort = insidePort;
                return this;
            }

            public Builder insideBerth(boolean insideBerth) {
                this.insideBerth = insideBerth;
                return this;
            }

            public Builder nearBerthPoint(boolean nearBerthPoint) {
                this.nearBerthPoint = nearBerthPoint;
                return this;
            }

            public Builder nearChannel(boolean nearChannel) {
                this.nearChannel = nearChannel;
                return this;
            }

            public Builder berthEntryFollowedByStay(boolean berthEntryFollowedByStay) {
                this.berthEntryFollowedByStay = berthEntryFollowedByStay;
                return this;
            }

            public Builder berthStationaryMinutes(long berthStationaryMinutes) {
                this.berthStationaryMinutes = berthStationaryMinutes;
                return this;
            }

            public Builder minutesToBerthEntry(long minutesToBerthEntry) {
                this.minutesToBerthEntry = minutesToBerthEntry;
                return this;
            }

            public Builder finalApproachDirectionEfficiency(double value) {
                this.finalApproachDirectionEfficiency = value;
                return this;
            }

            public Builder finalApproachDirectionChangePerHour(double value) {
                this.finalApproachDirectionChangePerHour = value;
                return this;
            }

            public Context build() {
                return new Context(this);
            }
        }
    }

    public static class Config {
        private long reviewMinDurationMinutes = 60L;
        private long formalMinDurationMinutes = 120L;
        private double formalMinLowSpeedPointRatio = 0.60;
        private double formalMaxP90Speed = 4.0;
        private double formalMaxRadiusNm = 5.0;
        private double formalMaxAreaNm2 = 25.0;
        private double formalMinConfidenceScore = 0.65;
        private long berthStayMinutes = 30L;
        private long berthingApproachLookAheadMinutes = 120L;
        private double normalApproachMinDirectionEfficiency = 0.70;
        private double straightMinDirectionEfficiency = 0.75;
        private double straightMaxDirectionChangePerHour = 45.0;
        private double straightMinStartEndDistanceNm = 3.0;
        private double circularMinClosureScore = 0.65;
        private double circularMaxStartEndDistanceNm = 2.0;
        private double circularMinSignedTurnDegree = 270.0;
        private double circularMaxRadialCv = 0.60;
        private double orbitalMinSignedTurnDegree = 270.0;
        private double orbitalMinSameTurnDirectionRatio = 0.70;
        private double patternMaxDirectionEfficiency = 0.50;
        private double serpentineMinDirectionChangePerHour = 90.0;
        private int serpentineMinTurnSignChanges = 4;
        private double serpentineMinTortuosity = 1.8;
        private int backAndForthMinReversals = 3;
        private double backAndForthMaxRadiusNm = 3.0;
        private long waitingMinDurationMinutes = 240L;
        private double waitingMinSpeed = 1.0;
        private double waitingMaxSpeed = 3.0;
        private double waitingMaxRadiusNm = 1.0;

        public static Config shadowDefaults() {
            return new Config();
        }

        public long getReviewMinDurationMinutes() {
            return reviewMinDurationMinutes;
        }

        public long getFormalMinDurationMinutes() {
            return formalMinDurationMinutes;
        }

        public double getFormalMinLowSpeedPointRatio() {
            return formalMinLowSpeedPointRatio;
        }

        public double getFormalMaxP90Speed() {
            return formalMaxP90Speed;
        }

        public double getFormalMaxRadiusNm() {
            return formalMaxRadiusNm;
        }

        public double getFormalMaxAreaNm2() {
            return formalMaxAreaNm2;
        }

        public double getFormalMinConfidenceScore() {
            return formalMinConfidenceScore;
        }

        public long getBerthStayMinutes() {
            return berthStayMinutes;
        }

        public long getBerthingApproachLookAheadMinutes() {
            return berthingApproachLookAheadMinutes;
        }

        public double getNormalApproachMinDirectionEfficiency() {
            return normalApproachMinDirectionEfficiency;
        }

        public double getStraightMinDirectionEfficiency() {
            return straightMinDirectionEfficiency;
        }

        public double getStraightMaxDirectionChangePerHour() {
            return straightMaxDirectionChangePerHour;
        }

        public double getStraightMinStartEndDistanceNm() {
            return straightMinStartEndDistanceNm;
        }

        public double getCircularMinClosureScore() {
            return circularMinClosureScore;
        }

        public double getCircularMaxStartEndDistanceNm() {
            return circularMaxStartEndDistanceNm;
        }

        public double getCircularMinSignedTurnDegree() {
            return circularMinSignedTurnDegree;
        }

        public double getCircularMaxRadialCv() {
            return circularMaxRadialCv;
        }

        public double getOrbitalMinSignedTurnDegree() {
            return orbitalMinSignedTurnDegree;
        }

        public double getOrbitalMinSameTurnDirectionRatio() {
            return orbitalMinSameTurnDirectionRatio;
        }

        public double getPatternMaxDirectionEfficiency() {
            return patternMaxDirectionEfficiency;
        }

        public double getSerpentineMinDirectionChangePerHour() {
            return serpentineMinDirectionChangePerHour;
        }

        public int getSerpentineMinTurnSignChanges() {
            return serpentineMinTurnSignChanges;
        }

        public double getSerpentineMinTortuosity() {
            return serpentineMinTortuosity;
        }

        public int getBackAndForthMinReversals() {
            return backAndForthMinReversals;
        }

        public double getBackAndForthMaxRadiusNm() {
            return backAndForthMaxRadiusNm;
        }

        public long getWaitingMinDurationMinutes() {
            return waitingMinDurationMinutes;
        }

        public double getWaitingMinSpeed() {
            return waitingMinSpeed;
        }

        public double getWaitingMaxSpeed() {
            return waitingMaxSpeed;
        }

        public double getWaitingMaxRadiusNm() {
            return waitingMaxRadiusNm;
        }
    }
}
