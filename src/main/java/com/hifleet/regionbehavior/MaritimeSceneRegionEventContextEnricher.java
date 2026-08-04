package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.maritime.MaritimeSceneClass;
import com.hifleet.maritime.MaritimeSceneDecision;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.maritime.SceneConfidence;
import com.hifleet.maritime.SceneEpisodeAggregator;
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

    @Autowired
    public MaritimeSceneRegionEventContextEnricher(MaritimeSceneService sceneService) {
        this.sceneService = sceneService;
    }

    @Override
    public RegionMatchedEvent enrich(RegionMatchedEvent event, ShipBehaviorTrace trace) {
        if (event == null || trace == null
                || event.getRegionMatchStatus() != RegionMatchStatus.STRICT_CORE
                || !sceneService.isEnabled() || !"SHADOW".equalsIgnoreCase(sceneService.getMode())) {
            return event;
        }
        try {
            List<MaritimeSceneDecision> own = classify(trace.getOwnPoints());
            if ("ALONGSIDE".equals(event.getBehaviorType())) {
                enrichAlongside(event, own, classify(trace.getTargetPoints()));
            } else {
                enrichLoitering(event, own);
            }
        } catch (RuntimeException ex) {
            unknown(event);
        }
        return event;
    }

    @Override
    public String name() {
        return sceneService.isEnabled() && "SHADOW".equalsIgnoreCase(sceneService.getMode())
                ? "MARITIME_SCENE_SHADOW" : "NO_OP";
    }

    private void enrichLoitering(RegionMatchedEvent event, List<MaritimeSceneDecision> decisions) {
        SceneEpisodeAggregator.Summary summary = sceneService.aggregate(decisions);
        event.setDominantMaritimeScene(summary.getDominantScene().name());
        event.setSceneConfidence(summary.getDominantSceneConfidence().name());
        event.setShadowDecision(sceneService.shadowDecision(summary));
        setCommon(event, summary, decisions);
    }

    private void enrichAlongside(RegionMatchedEvent event, List<MaritimeSceneDecision> own,
                                 List<MaritimeSceneDecision> target) {
        SceneEpisodeAggregator.Summary ownSummary = sceneService.aggregate(own);
        SceneEpisodeAggregator.Summary targetSummary = sceneService.aggregate(target);
        MaritimeSceneClass ownScene = ownSummary.getDominantScene();
        MaritimeSceneClass targetScene = targetSummary.getDominantScene();
        MaritimeSceneClass synchronizedScene = synchronizedScene(ownSummary, targetSummary);
        event.setOwnScene(ownScene.name());
        event.setTargetScene(targetScene.name());
        event.setSynchronizedScene(synchronizedScene.name());
        event.setSceneConfidence(synchronizedConfidence(
                synchronizedScene, ownSummary.getDominantSceneConfidence(),
                targetSummary.getDominantSceneConfidence()).name());
        event.setShadowDecision(synchronizedDecision(synchronizedScene, ownSummary, targetSummary));
        List<MaritimeSceneDecision> combined = new ArrayList<MaritimeSceneDecision>(own);
        combined.addAll(target);
        setCommon(event, sceneService.aggregate(combined), combined);
    }

    private MaritimeSceneClass synchronizedScene(SceneEpisodeAggregator.Summary own,
                                                  SceneEpisodeAggregator.Summary target) {
        if (own.getConflictPointRatio() > 0.0 || target.getConflictPointRatio() > 0.0
                || own.getDominantScene() == MaritimeSceneClass.SCENE_CONFLICT
                || target.getDominantScene() == MaritimeSceneClass.SCENE_CONFLICT) {
            return MaritimeSceneClass.SCENE_CONFLICT;
        }
        if (own.getDominantScene() == MaritimeSceneClass.SCENE_UNKNOWN
                || target.getDominantScene() == MaritimeSceneClass.SCENE_UNKNOWN) {
            return MaritimeSceneClass.SCENE_UNKNOWN;
        }
        return own.getDominantScene() == target.getDominantScene()
                ? own.getDominantScene() : MaritimeSceneClass.SCENE_CONFLICT;
    }

    private SceneConfidence synchronizedConfidence(MaritimeSceneClass synchronizedScene,
                                                    SceneConfidence own, SceneConfidence target) {
        if (synchronizedScene == MaritimeSceneClass.SCENE_UNKNOWN
                || synchronizedScene == MaritimeSceneClass.SCENE_CONFLICT) {
            return SceneConfidence.UNKNOWN;
        }
        return own.ordinal() >= target.ordinal() ? own : target;
    }

    private String synchronizedDecision(MaritimeSceneClass synchronizedScene,
                                        SceneEpisodeAggregator.Summary own,
                                        SceneEpisodeAggregator.Summary target) {
        if (synchronizedScene == MaritimeSceneClass.SCENE_CONFLICT) {
            return "SCENE_CONFLICT";
        }
        if (synchronizedScene == MaritimeSceneClass.SCENE_UNKNOWN) {
            return "SCENE_UNKNOWN";
        }
        String ownDecision = sceneService.shadowDecision(own);
        String targetDecision = sceneService.shadowDecision(target);
        return ownDecision.equals(targetDecision) ? ownDecision : "SCENE_UNKNOWN";
    }

    private void setCommon(RegionMatchedEvent event, SceneEpisodeAggregator.Summary summary,
                           List<MaritimeSceneDecision> decisions) {
        event.setSceneSnapshotVersion(commonSnapshot(decisions, true));
        event.setSceneSnapshotFingerprint(commonSnapshot(decisions, false));
        event.setCoastDistanceMinNm(summary.getCoastDistanceMinNm());
        event.setCoastDistanceP50Nm(summary.getCoastDistanceP50Nm());
        event.setCoastDistanceMaxNm(summary.getCoastDistanceMaxNm());
        event.setSceneUnknownRatio(summary.getUnknownPointRatio());
        event.setSceneConflictRatio(summary.getConflictPointRatio());
    }

    private List<MaritimeSceneDecision> classify(List<TrajectoryPoint> points) {
        List<TrajectoryPoint> values = points == null
                ? Collections.<TrajectoryPoint>emptyList() : points;
        List<MaritimeSceneDecision> decisions = new ArrayList<MaritimeSceneDecision>();
        for (TrajectoryPoint point : values) {
            decisions.add(sceneService.classify(point.getLon(), point.getLat()));
        }
        return decisions;
    }

    private String commonSnapshot(List<MaritimeSceneDecision> decisions, boolean version) {
        String common = null;
        for (MaritimeSceneDecision decision : decisions) {
            String value = version ? decision.getSnapshotVersion() : decision.getSnapshotFingerprint();
            if (value == null) continue;
            if (common != null && !common.equals(value)) return null;
            common = value;
        }
        return common;
    }

    private void unknown(RegionMatchedEvent event) {
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
    }
}
