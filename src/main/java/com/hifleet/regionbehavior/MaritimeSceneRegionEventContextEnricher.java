package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.maritime.MaritimeSceneClass;
import com.hifleet.maritime.MaritimeSceneDecision;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.maritime.SceneConfidence;
import com.hifleet.maritime.SceneEpisodeAggregator;
import com.hifleet.maritime.TimedSceneObservation;
import com.hifleet.tankeropensea.AlongsidePairGeometryEvaluator;
import com.hifleet.tankeropensea.LoiteringShapeEvaluator;
import com.hifleet.trajectory.TrajectoryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Primary
public class MaritimeSceneRegionEventContextEnricher implements RegionEventContextEnricher {
    private final MaritimeSceneService sceneService;
    private final ShadowRulesConfig shadowConfig;
    private final OperationalAreaContextProvider operationalAreaProvider;
    private final ShipDimensionProvider shipDimensionProvider;
    private final CombinedShadowDecisionEngine combinedEngine = new CombinedShadowDecisionEngine();

    public MaritimeSceneRegionEventContextEnricher(MaritimeSceneService sceneService) {
        this(sceneService, new ShadowRulesConfig(), new OperationalAreaContextProvider() {
            @Override
            public OperationalAreaContext classify(List<TrajectoryPoint> points) {
                return OperationalAreaContext.unavailable();
            }
        }, (ownMmsi, targetMmsi) -> Collections.<String, Double>emptyMap());
    }

    public MaritimeSceneRegionEventContextEnricher(MaritimeSceneService sceneService,
                                                   ShadowRulesConfig shadowConfig,
                                                   OperationalAreaContextProvider operationalAreaProvider) {
        this(sceneService, shadowConfig, operationalAreaProvider,
                (ownMmsi, targetMmsi) -> Collections.<String, Double>emptyMap());
    }

    @Autowired
    public MaritimeSceneRegionEventContextEnricher(MaritimeSceneService sceneService,
                                                   ShadowRulesConfig shadowConfig,
                                                   OperationalAreaContextProvider operationalAreaProvider,
                                                   ShipDimensionProvider shipDimensionProvider) {
        this.sceneService = sceneService;
        this.shadowConfig = shadowConfig;
        this.operationalAreaProvider = operationalAreaProvider;
        this.shipDimensionProvider = shipDimensionProvider;
    }

    @Override
    public RegionMatchedEvent enrich(RegionMatchedEvent event, ShipBehaviorTrace trace) {
        boolean shadowV2 = event != null && shadowConfig.isEnabled()
                && event.isShadowRulesRequested();
        boolean diagnosticStatus = event != null
                && (event.getRegionMatchStatus() == RegionMatchStatus.STRICT_CORE
                || shadowV2
                && event.getRegionMatchStatus() == RegionMatchStatus.REGION_BOUNDARY_PARTIAL);
        if (event == null || trace == null || !diagnosticStatus
                || !sceneService.isEnabled() || !"SHADOW".equalsIgnoreCase(sceneService.getMode())) {
            return event;
        }
        try {
            List<TimedSceneObservation> own = sceneService.classifyTimed(trace.getOwnPoints());
            if ("ALONGSIDE".equals(event.getBehaviorType())) {
                enrichAlongside(event, trace, own,
                        sceneService.classifyTimed(trace.getTargetPoints()), shadowV2);
            } else {
                enrichLoitering(event, trace, own, shadowV2);
            }
        } catch (RuntimeException ex) {
            unknown(event);
        }
        return event;
    }

    @Override
    public String name() {
        if (!sceneService.isEnabled() || !"SHADOW".equalsIgnoreCase(sceneService.getMode())) return "NO_OP";
        return shadowConfig.isEnabled() ? "BEHAVIOR_MARITIME_SCENE_SHADOW_V2" : "MARITIME_SCENE_SHADOW";
    }

    private void enrichLoitering(RegionMatchedEvent event, ShipBehaviorTrace trace,
                                 List<TimedSceneObservation> observations, boolean shadowV2) {
        SceneEpisodeAggregator.Summary summary = aggregate(observations, shadowV2);
        event.setDominantMaritimeScene(summary.getDominantScene().name());
        event.setSceneConfidence(summary.getDominantSceneConfidence().name());
        if (!shadowV2) {
            event.setShadowDecision(sceneService.shadowDecision(summary));
            setCommon(event, summary, observations, false);
            return;
        }
        String sceneDecision = sceneDecision(summary);
        LoiteringShapeEvaluator.Evaluation behavior = shadowConfig.isLoiteringEnabled()
                ? new LoiteringShapeEvaluator(shadowConfig).evaluate(trace.getOwnPoints()) : null;
        OperationalAreaContext operational = operationalAreaProvider.classify(trace.getOwnPoints());
        applyV2(event, sceneDecision, behavior == null ? "INSUFFICIENT_SHAPE_EVIDENCE"
                        : behavior.getDecision().name(), operational,
                behavior == null ? new JSONObject(true)
                        : JSON.parseObject(JSON.toJSONString(behavior.getEvidence())),
                ShadowRulesConfig.LOITERING_BEHAVIOR_RULE_VERSION,
                ShadowRulesConfig.LOITERING_COMBINED_RULE_VERSION);
        event.setOwnSceneSummary(summaryJson(summary));
        setCommon(event, summary, observations, true);
    }

    private void enrichAlongside(RegionMatchedEvent event, ShipBehaviorTrace trace,
                                 List<TimedSceneObservation> own,
                                 List<TimedSceneObservation> target, boolean shadowV2) {
        SceneEpisodeAggregator.Summary ownSummary = aggregate(own, shadowV2);
        SceneEpisodeAggregator.Summary targetSummary = aggregate(target, shadowV2);
        if (!shadowV2) {
            MaritimeSceneClass synchronizedScene = synchronizedScene(ownSummary, targetSummary);
            event.setOwnScene(ownSummary.getDominantScene().name());
            event.setTargetScene(targetSummary.getDominantScene().name());
            event.setSynchronizedScene(synchronizedScene.name());
            event.setSceneConfidence(synchronizedConfidence(synchronizedScene,
                    ownSummary.getDominantSceneConfidence(), targetSummary.getDominantSceneConfidence()).name());
            event.setShadowDecision(synchronizedDecision(synchronizedScene, ownSummary, targetSummary));
            List<TimedSceneObservation> combined = new ArrayList<TimedSceneObservation>(own);
            combined.addAll(target);
            setCommon(event, aggregate(combined, false), combined, false);
            return;
        }
        TrajectoryPairSynchronizer.Result synchronizedTrack =
                new TrajectoryPairSynchronizer(shadowConfig.getAlongsideMaxSyncGapMinutes())
                        .synchronize(trace.getOwnPoints(), trace.getTargetPoints());
        List<TimedSceneObservation> midpoint = classifyMidpoints(synchronizedTrack.getPairs());
        SceneEpisodeAggregator.Summary pairSummary = aggregate(midpoint, true);
        String sceneDecision = alongsideSceneDecision(ownSummary, targetSummary, pairSummary);
        java.util.Map<String, Double> lengths = shipDimensionProvider.lengths(
                event.getMmsi(), event.getTargetMmsi());
        AlongsidePairGeometryEvaluator.Evaluation behavior = shadowConfig.isAlongsideEnabled()
                ? new AlongsidePairGeometryEvaluator(shadowConfig).evaluate(synchronizedTrack,
                lengths.get(event.getMmsi()), lengths.get(event.getTargetMmsi())) : null;
        List<TrajectoryPoint> operationalPoints = new ArrayList<TrajectoryPoint>();
        operationalPoints.addAll(safe(trace.getOwnPoints()));
        operationalPoints.addAll(safe(trace.getTargetPoints()));
        OperationalAreaContext operational = operationalAreaProvider.classify(operationalPoints);
        applyV2(event, sceneDecision, behavior == null ? "INSUFFICIENT_GEOMETRY_EVIDENCE"
                        : behavior.getDecision().name(), operational,
                behavior == null ? new JSONObject(true)
                        : JSON.parseObject(JSON.toJSONString(behavior.getEvidence())),
                ShadowRulesConfig.ALONGSIDE_BEHAVIOR_RULE_VERSION,
                ShadowRulesConfig.ALONGSIDE_COMBINED_RULE_VERSION);
        event.setOwnScene(ownSummary.getDominantScene().name());
        event.setTargetScene(targetSummary.getDominantScene().name());
        event.setSynchronizedScene(pairSummary.getDominantScene().name());
        event.setSceneConfidence(pairSummary.getDominantSceneConfidence().name());
        event.setOwnSceneSummary(summaryJson(ownSummary));
        event.setTargetSceneSummary(summaryJson(targetSummary));
        event.setPairMidpointSceneSummary(summaryJson(pairSummary));
        setCommon(event, pairSummary, midpoint, true);
    }

    private void applyV2(RegionMatchedEvent event, String sceneDecision, String behaviorDecision,
                         OperationalAreaContext operational, JSONObject evidence,
                         String behaviorRuleVersion, String combinedRuleVersion) {
        event.setShadowRulesExecuted(true);
        event.setBaseRuleDecision("VALID_NON_WEAK");
        event.setScenePolicyVersion(ShadowRulesConfig.SCENE_POLICY_VERSION);
        event.setBehaviorRuleVersion(behaviorRuleVersion);
        event.setCombinedRuleVersion(combinedRuleVersion);
        event.setSceneShadowDecision(sceneDecision);
        event.setBehaviorShadowDecision(behaviorDecision);
        event.setBehaviorEvidence(evidence);
        event.setOperationalArea(operational);
        CombinedShadowDecisionEngine.Result combined = combinedEngine.decide(
                event.getRegionMatchStatus(), event.getQueryCompleteness(),
                sceneDecision, behaviorDecision, operational);
        event.setCombinedShadowDecision(combined.getDecision());
        event.setShadowRejectReasons(combined.getRejectReasons());
        if (Boolean.FALSE.equals(event.getShipTypePolicyPassed())
                && event.getQueryCompleteness() == RegionBehaviorStatus.COMPLETE
                && event.getRegionMatchStatus() == RegionMatchStatus.STRICT_CORE
                && !"WOULD_REJECT_INLAND".equals(sceneDecision)) {
            event.setCombinedShadowDecision("SHIP_TYPE_REVIEW_REQUIRED");
            event.setShadowRejectReasons(Collections.singletonList(event.getShipTypePolicyReason()));
        }
        event.setShadowDecision(sceneDecision);
    }

    private String alongsideSceneDecision(SceneEpisodeAggregator.Summary own,
                                          SceneEpisodeAggregator.Summary target,
                                          SceneEpisodeAggregator.Summary pair) {
        if (conflict(own) || conflict(target) || conflict(pair)) return "SCENE_CONFLICT";
        if (pair.getDominantScene() != MaritimeSceneClass.SCENE_UNKNOWN
                && (own.getDominantScene() != pair.getDominantScene()
                || target.getDominantScene() != pair.getDominantScene())) return "SCENE_CONFLICT";
        String pairDecision = sceneDecision(pair);
        if ("WOULD_REJECT_INLAND".equals(pairDecision)) return pairDecision;
        if ("OPEN_WATER_CONFIRMED".equals(pairDecision)
                && own.getHighConfidenceOpenWaterTimeRatio() >= 0.85
                && target.getHighConfidenceOpenWaterTimeRatio() >= 0.85
                && own.getUnknownTimeRatio() <= shadowConfig.getSceneMaxUnknownTimeRatio()
                && target.getUnknownTimeRatio() <= shadowConfig.getSceneMaxUnknownTimeRatio()) {
            return pairDecision;
        }
        if ("COASTAL_OPERATION_REVIEW".equals(pairDecision)) return pairDecision;
        if (own.getDominantScene() != target.getDominantScene()
                && own.getDominantScene() != MaritimeSceneClass.SCENE_UNKNOWN
                && target.getDominantScene() != MaritimeSceneClass.SCENE_UNKNOWN) return "SCENE_CONFLICT";
        return "SCENE_REVIEW_REQUIRED";
    }

    private boolean conflict(SceneEpisodeAggregator.Summary summary) {
        return summary.getConflictPointRatio() > 0.0 || summary.getConflictTimeRatio() > 0.0
                || summary.getDominantScene() == MaritimeSceneClass.SCENE_CONFLICT;
    }

    private String sceneDecision(SceneEpisodeAggregator.Summary summary) {
        return sceneService.shadowDecisionV2(summary, shadowConfig.getSceneMinTemporalCoverage(),
                shadowConfig.getSceneInlandTimeRatio(),
                shadowConfig.getSceneInlandMinDurationMinutes(),
                shadowConfig.getSceneOpenWaterTimeRatio(),
                shadowConfig.getSceneMaxUnknownTimeRatio());
    }

    private SceneEpisodeAggregator.Summary aggregate(List<TimedSceneObservation> observations,
                                                     boolean shadowV2) {
        if (!shadowV2) {
            List<MaritimeSceneDecision> decisions = new ArrayList<MaritimeSceneDecision>();
            for (TimedSceneObservation observation : observations) decisions.add(observation.getDecision());
            return sceneService.aggregate(decisions);
        }
        return sceneService.aggregateTimed(observations, shadowConfig.getSceneGapCapMinutes());
    }

    private List<TimedSceneObservation> classifyMidpoints(List<SynchronizedShipPairPoint> pairs) {
        List<TimedSceneObservation> result = new ArrayList<TimedSceneObservation>();
        for (SynchronizedShipPairPoint pair : pairs) {
            MaritimeSceneDecision decision = sceneService.classify(pair.getMidpointLon(), pair.getMidpointLat());
            result.add(new TimedSceneObservation(pair.getSynchronizedEpochMillis(), decision));
        }
        return result;
    }

    private void setCommon(RegionMatchedEvent event, SceneEpisodeAggregator.Summary summary,
                           List<TimedSceneObservation> observations, boolean shadowV2) {
        event.setSceneSnapshotVersion(commonSnapshot(observations, true));
        event.setSceneSnapshotFingerprint(commonSnapshot(observations, false));
        event.setCoastDistanceMinNm(summary.getCoastDistanceMinNm());
        event.setCoastDistanceP50Nm(summary.getCoastDistanceP50Nm());
        event.setCoastDistanceMaxNm(summary.getCoastDistanceMaxNm());
        event.setSceneUnknownRatio(summary.getUnknownPointRatio());
        event.setSceneConflictRatio(summary.getConflictPointRatio());
        if (shadowV2) {
            event.setSceneOpenWaterTimeRatio(summary.getHighConfidenceOpenWaterTimeRatio());
            event.setSceneInlandTimeRatio(summary.getHighConfidenceInlandTimeRatio());
            event.setSceneUnknownTimeRatio(summary.getUnknownTimeRatio());
            event.setSceneConflictTimeRatio(summary.getConflictTimeRatio());
            event.setSceneObservedDurationMinutes(summary.getObservedDurationMinutes());
            event.setSceneEventSpanMinutes(summary.getEventSpanMinutes());
            event.setSceneTemporalCoverageRatio(summary.getTemporalCoverageRatio());
            event.setSceneInlandDurationMinutes(summary.getInlandDurationMinutes());
            event.setSceneMaxGapMinutes(summary.getMaxGapMinutes());
            event.setSceneLargeGapCount(summary.getLargeGapCount());
        }
    }

    private JSONObject summaryJson(SceneEpisodeAggregator.Summary summary) {
        return JSON.parseObject(JSON.toJSONString(summary));
    }

    private String commonSnapshot(List<TimedSceneObservation> observations, boolean version) {
        String common = null;
        for (TimedSceneObservation observation : observations) {
            MaritimeSceneDecision decision = observation.getDecision();
            String value = version ? decision.getSnapshotVersion() : decision.getSnapshotFingerprint();
            if (value == null) continue;
            if (common != null && !common.equals(value)) return null;
            common = value;
        }
        return common;
    }

    private MaritimeSceneClass synchronizedScene(SceneEpisodeAggregator.Summary own,
                                                  SceneEpisodeAggregator.Summary target) {
        if (conflict(own) || conflict(target)) return MaritimeSceneClass.SCENE_CONFLICT;
        if (own.getDominantScene() == MaritimeSceneClass.SCENE_UNKNOWN
                || target.getDominantScene() == MaritimeSceneClass.SCENE_UNKNOWN) {
            return MaritimeSceneClass.SCENE_UNKNOWN;
        }
        return own.getDominantScene() == target.getDominantScene()
                ? own.getDominantScene() : MaritimeSceneClass.SCENE_CONFLICT;
    }

    private SceneConfidence synchronizedConfidence(MaritimeSceneClass scene,
                                                    SceneConfidence own, SceneConfidence target) {
        if (scene == MaritimeSceneClass.SCENE_UNKNOWN || scene == MaritimeSceneClass.SCENE_CONFLICT) {
            return SceneConfidence.UNKNOWN;
        }
        return own.ordinal() >= target.ordinal() ? own : target;
    }

    private String synchronizedDecision(MaritimeSceneClass scene,
                                        SceneEpisodeAggregator.Summary own,
                                        SceneEpisodeAggregator.Summary target) {
        if (scene == MaritimeSceneClass.SCENE_CONFLICT) return "SCENE_CONFLICT";
        if (scene == MaritimeSceneClass.SCENE_UNKNOWN) return "SCENE_UNKNOWN";
        String ownDecision = sceneService.shadowDecision(own);
        String targetDecision = sceneService.shadowDecision(target);
        return ownDecision.equals(targetDecision) ? ownDecision : "SCENE_UNKNOWN";
    }

    private List<TrajectoryPoint> safe(List<TrajectoryPoint> points) {
        return points == null ? Collections.<TrajectoryPoint>emptyList() : points;
    }

    private void unknown(RegionMatchedEvent event) {
        if (event == null) return;
        if ("ALONGSIDE".equals(event.getBehaviorType())) {
            event.setOwnScene(MaritimeSceneClass.SCENE_UNKNOWN.name());
            event.setTargetScene(MaritimeSceneClass.SCENE_UNKNOWN.name());
            event.setSynchronizedScene(MaritimeSceneClass.SCENE_UNKNOWN.name());
        } else {
            event.setDominantMaritimeScene(MaritimeSceneClass.SCENE_UNKNOWN.name());
        }
        event.setSceneConfidence(SceneConfidence.UNKNOWN.name());
        event.setShadowDecision("SCENE_UNKNOWN");
        event.setSceneUnknownRatio(1.0);
        event.setSceneConflictRatio(0.0);
        if (shadowConfig.isEnabled() && event.isShadowRulesRequested()) {
            event.setShadowRulesExecuted(true);
            event.setBaseRuleDecision("VALID_NON_WEAK");
            event.setSceneShadowDecision("SCENE_REVIEW_REQUIRED");
            event.setCombinedShadowDecision(event.getRegionMatchStatus()
                    == RegionMatchStatus.REGION_BOUNDARY_PARTIAL
                    ? "REVIEW_REGION_BOUNDARY" : "SCENE_REVIEW_REQUIRED");
            event.setShadowRejectReasons(Collections.singletonList("SCENE_UNKNOWN"));
        }
    }
}
