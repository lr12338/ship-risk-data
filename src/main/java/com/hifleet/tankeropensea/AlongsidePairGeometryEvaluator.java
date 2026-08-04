package com.hifleet.tankeropensea;

import com.hifleet.regionbehavior.ShadowRulesConfig;
import com.hifleet.regionbehavior.SynchronizedShipPairPoint;
import com.hifleet.regionbehavior.TrajectoryPairSynchronizer;
import com.hifleet.trajectory.TrajectoryPoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlongsidePairGeometryEvaluator {
    private final ShadowRulesConfig config;

    public AlongsidePairGeometryEvaluator(ShadowRulesConfig config) {
        this.config = config;
    }

    public Evaluation evaluate(TrajectoryPairSynchronizer.Result synchronizedTrack,
                               Double ownLengthMeters, Double targetLengthMeters) {
        List<SynchronizedShipPairPoint> pairs = synchronizedTrack == null
                ? Collections.<SynchronizedShipPairPoint>emptyList() : synchronizedTrack.getPairs();
        List<TrajectoryPoint> own = new ArrayList<TrajectoryPoint>();
        List<TrajectoryPoint> target = new ArrayList<TrajectoryPoint>();
        List<Double> distances = new ArrayList<Double>();
        List<Double> alongOffsets = new ArrayList<Double>();
        List<Double> crossOffsets = new ArrayList<Double>();
        List<Double> bearings = new ArrayList<Double>();
        List<Double> relativeSpeeds = new ArrayList<Double>();
        List<Double> headingDifferences = new ArrayList<Double>();
        int following = 0;
        int overtaking = 0;
        int crossing = 0;
        for (SynchronizedShipPairPoint pair : pairs) {
            TrajectoryPoint ownPoint = pair.getOwnPoint();
            TrajectoryPoint targetPoint = pair.getTargetPoint();
            own.add(ownPoint);
            target.add(targetPoint);
            double distance = GeoMath.distanceNm(ownPoint, targetPoint);
            double bearing = GeoMath.signedDifference(GeoMath.bearingDegree(ownPoint, targetPoint),
                    ownPoint.getCourse());
            double heading = GeoMath.circularDifference(ownPoint.getCourse(), targetPoint.getCourse());
            distances.add(distance);
            bearings.add(bearing);
            alongOffsets.add(Math.abs(distance * Math.cos(Math.toRadians(bearing))));
            crossOffsets.add(Math.abs(distance * Math.sin(Math.toRadians(bearing))));
            relativeSpeeds.add(Math.abs(ownPoint.getSpeed() - targetPoint.getSpeed()));
            headingDifferences.add(heading);
            if (heading > 30.0) crossing++;
            else if (Math.abs(bearing) < 45.0) following++;
            else if (Math.abs(bearing) > 135.0) overtaking++;
        }
        RuleResult strict = strictRule(own, target, ownLengthMeters, targetLengthMeters);
        Map<String, Object> values = new LinkedHashMap<String, Object>(strict.getFeatures());
        int count = pairs.size();
        double denominator = Math.max(1, count);
        double duration = count < 2 ? 0.0 : Duration.between(own.get(0).getTime(),
                own.get(count - 1).getTime()).toMillis() / 60_000.0;
        values.put("durationMinutes", duration);
        values.put("synchronizedPointCount", count);
        values.put("synchronizedCoverageRatio", synchronizedTrack == null
                ? 0.0 : synchronizedTrack.getCoverageRatio());
        values.put("relativeBearingP50", GeoMath.percentile(bearings, 0.50));
        values.put("alongTrackOffsetP50Nm", GeoMath.percentile(alongOffsets, 0.50));
        values.put("alongTrackOffsetP90Nm", GeoMath.percentile(alongOffsets, 0.90));
        values.put("crossTrackOffsetP50Nm", GeoMath.percentile(crossOffsets, 0.50));
        values.put("crossTrackOffsetP90Nm", GeoMath.percentile(crossOffsets, 0.90));
        values.put("distanceP10Nm", GeoMath.percentile(distances, 0.10));
        values.put("distanceP50Nm", GeoMath.percentile(distances, 0.50));
        values.put("distanceP90Nm", GeoMath.percentile(distances, 0.90));
        values.put("relativeSpeedP90Kn", GeoMath.percentile(relativeSpeeds, 0.90));
        values.put("headingDifferenceP90Deg", GeoMath.percentile(headingDifferences, 0.90));
        values.put("followingRatio", following / denominator);
        values.put("overtakingRatio", overtaking / denominator);
        values.put("crossingRatio", crossing / denominator);
        double observedMinutes = 0.0;
        double speedInRangeMinutes = 0.0;
        double stationaryMinutes = 0.0;
        for (int i = 0; i + 1 < pairs.size(); i++) {
            double gap = Math.max(0.0, (pairs.get(i + 1).getSynchronizedEpochMillis()
                    - pairs.get(i).getSynchronizedEpochMillis()) / 60_000.0);
            double weight = Math.min(gap, config.getSceneGapCapMinutes());
            observedMinutes += weight;
            TrajectoryPoint ownPoint = pairs.get(i).getOwnPoint();
            TrajectoryPoint targetPoint = pairs.get(i).getTargetPoint();
            if (ownPoint.getSpeed() >= 1.0 && ownPoint.getSpeed() <= 3.0
                    && targetPoint.getSpeed() >= 1.0 && targetPoint.getSpeed() <= 3.0) {
                speedInRangeMinutes += weight;
            }
            if (ownPoint.getSpeed() <= 0.5 && targetPoint.getSpeed() <= 0.5) {
                stationaryMinutes += weight;
            }
        }
        double timeDenominator = observedMinutes <= 0.0 ? 1.0 : observedMinutes;
        values.put("speedInRangeTimeRatio", speedInRangeMinutes / timeDenominator);
        values.put("stationaryTimeRatio", stationaryMinutes / timeDenominator);
        values.put("strictRuleAccepted", strict.isAccepted());
        values.put("strictRuleReason", strict.getReason());
        AlongsideGeometryEvidence evidence = new AlongsideGeometryEvidence(values);
        return new Evaluation(decide(evidence, strict), evidence);
    }

    private RuleResult strictRule(List<TrajectoryPoint> own, List<TrajectoryPoint> target,
                                  Double ownLengthMeters, Double targetLengthMeters) {
        ShipEligibilityDecision eligible = ShipEligibilityDecision.eligible(
                StandardShipType.TANKER_OIL, "SHADOW_INPUT_ELIGIBLE", "SHADOW");
        ExclusionDecision exclusion = ExclusionDecision.accepted("SHADOW",
                Collections.<String, Integer>emptyMap());
        ShipMetadata ownMetadata = metadata(own, ownLengthMeters);
        ShipMetadata targetMetadata = metadata(target, targetLengthMeters);
        return new TankerOpenSeaAlongsideRule(TankerOpenSeaAlongsideRule.Config.defaults())
                .evaluate(own, target, eligible, eligible, ownMetadata, targetMetadata,
                        exclusion, ReplayCompleteness.COMPLETE);
    }

    private ShipMetadata metadata(List<TrajectoryPoint> points, Double length) {
        String mmsi = points.isEmpty() ? null : points.get(0).getMmsi();
        return new ShipMetadata(mmsi, null, null, "oil tanker", null, null,
                length, null, null);
    }

    private AlongsideGeometryDecision decide(AlongsideGeometryEvidence evidence, RuleResult strict) {
        if (evidence.getSynchronizedPointCount() < config.getAlongsideMinSynchronizedPoints()) {
            return AlongsideGeometryDecision.INSUFFICIENT_SYNCHRONIZED_POINTS;
        }
        if (evidence.getSynchronizedCoverageRatio() < config.getAlongsideMinSyncCoverage()
                || evidence.getDurationMinutes() < config.getAlongsideMinDurationMinutes()) {
            return AlongsideGeometryDecision.INSUFFICIENT_GEOMETRY_EVIDENCE;
        }
        if (evidence.getStationaryTimeRatio() >= 0.75) {
            return AlongsideGeometryDecision.STATIONARY_ALONGSIDE_CANDIDATE;
        }
        if (evidence.getSpeedInRangeTimeRatio() < 0.75) {
            return AlongsideGeometryDecision.INSUFFICIENT_GEOMETRY_EVIDENCE;
        }
        if (evidence.getCrossingRatio() >= 0.25) return AlongsideGeometryDecision.WOULD_REJECT_CROSSING;
        if (evidence.getFollowingRatio() >= 0.25) return AlongsideGeometryDecision.WOULD_REJECT_FOLLOWING;
        if (evidence.getOvertakingRatio() >= 0.25) return AlongsideGeometryDecision.WOULD_REJECT_OVERTAKING;
        if (evidence.getRelativeSpeedP90Kn() > config.getAlongsideMaxRelativeSpeedP90Kn()) {
            return AlongsideGeometryDecision.WOULD_REJECT_RELATIVE_SPEED;
        }
        if (evidence.getHeadingDifferenceP90Deg() > config.getAlongsideMaxHeadingDifferenceP90Deg()) {
            return AlongsideGeometryDecision.WOULD_REJECT_HEADING_DIFFERENCE;
        }
        if (evidence.getDistanceStabilityRatio() < 0.75) {
            return AlongsideGeometryDecision.WOULD_REJECT_UNSTABLE_DISTANCE;
        }
        if (strict.isAccepted()
                && evidence.getSideBySideRatio() >= config.getAlongsideMinSideBySideRatio()
                && evidence.getRelativeBearingStability() >= config.getAlongsideMinSideStabilityRatio()) {
            return AlongsideGeometryDecision.SIDE_BY_SIDE_CONFIRMED;
        }
        return AlongsideGeometryDecision.INSUFFICIENT_GEOMETRY_EVIDENCE;
    }

    public static final class Evaluation {
        private final AlongsideGeometryDecision decision;
        private final AlongsideGeometryEvidence evidence;

        Evaluation(AlongsideGeometryDecision decision, AlongsideGeometryEvidence evidence) {
            this.decision = decision;
            this.evidence = evidence;
        }

        public AlongsideGeometryDecision getDecision() { return decision; }
        public AlongsideGeometryEvidence getEvidence() { return evidence; }
    }
}
