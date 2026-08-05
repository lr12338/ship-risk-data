package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.TargetShip;
import com.hifleet.job.services.RegionProductionEventFilterAdapter;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.service.ShipTypeClassifier;
import com.hifleet.trajectory.TrajectoryLoaderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;

@Service
public final class FleetBehaviorScopeExecutor implements BehaviorScopeExecutor {
    private final InlineShipTargetResolver inline;
    private final DatabaseListTargetResolver lists;
    private final ShipBehaviorAnalysisService analysis;
    private final TrajectoryLoaderService trajectories;
    private final MaritimeSceneService sceneService;
    private final ShipTypePolicyEvaluator shipTypes;
    @Value("${ship.behavior.trajectory.simplify-zoom:15}") private int simplifyZoom;
    @Value("${ship.behavior.audit.trajectory-slice-hours:6}") private int trajectorySliceHours;
    @Value("${ship.behavior.audit.max-points-per-ship:500000}") private int maxPointsPerShip;

    public FleetBehaviorScopeExecutor(InlineShipTargetResolver inline, DatabaseListTargetResolver lists,
                                      ShipBehaviorAnalysisService analysis, TrajectoryLoaderService trajectories,
                                      MaritimeSceneService sceneService, ShipTypeClassifier classifier) {
        this.inline = inline; this.lists = lists; this.analysis = analysis; this.trajectories = trajectories;
        this.sceneService = sceneService; this.shipTypes = new ShipTypePolicyEvaluator(classifier);
    }
    public boolean supports(BehaviorTargetSourceType type) {
        return type == BehaviorTargetSourceType.SHIPS || type == BehaviorTargetSourceType.LIST;
    }
    public JSONObject preflight(BehaviorScopeContext context) {
        BehaviorJobEnvelope envelope = context.getEnvelope();
        JSONObject result = new JSONObject(true);
        if (envelope.getSourceType() == BehaviorTargetSourceType.SHIPS) {
            result.put("sourceResolvedCount", envelope.getShips().size());
            result.put("inlineShipCount", envelope.getShips().size());
        } else {
            TargetListSnapshot snapshot = lists.snapshot(envelope.getListBatchNo());
            if (snapshot.getEnabledCount() == 0) throw new IllegalStateException("target list is empty or missing");
            List<TargetShip> resolved = lists.resolve(envelope.getListBatchNo(), envelope.getStartSeq(),
                    envelope.getShipLimit(), envelope.getPageSize());
            if (resolved.size() < envelope.getShipLimit())
                throw new IllegalStateException("target list has fewer rows with seq_no > startSeq than limit");
            result.put("sourceResolvedCount", resolved.size());
            result.put("listTotalCount", snapshot.getTotalCount());
            result.put("listEnabledCount", snapshot.getEnabledCount());
            result.put("listMinSeq", snapshot.getMinSeq()); result.put("listMaxSeq", snapshot.getMaxSeq());
            result.put("listDistinctSeq", snapshot.getDistinctSeq()); result.put("listDistinctMmsi", snapshot.getDistinctMmsi());
        }
        return result;
    }
    public BehaviorScopeExecutionResult execute(BehaviorScopeContext context) {
        BehaviorJobEnvelope envelope = context.getEnvelope();
        List<TargetShip> targets = envelope.getSourceType() == BehaviorTargetSourceType.SHIPS
                ? inline.resolve(envelope.getShips()) : lists.resolve(envelope.getListBatchNo(),
                envelope.getStartSeq(), envelope.getShipLimit(), envelope.getPageSize());
        BehaviorScopeExecutionResult result = new BehaviorScopeExecutionResult();
        int prePolicyCandidates = 0, postPolicyCandidates = 0;
        for (TargetShip ship : targets) {
            String key = ship.getMmsi();
            if (envelope.isResume() && context.getCheckpoints().skipSuccess(key)) {
                result.skipped(); continue;
            }
            int attempt = context.getCheckpoints().nextAttempt(key);
            if (attempt > 1) result.retried();
            result.processed();
            if (!shipTypes.primaryAllowed(ship.getShipType(), context.getProfile().getPrimaryShipTypePolicy())) {
                context.getCheckpoints().record(key, "SKIPPED", attempt); result.skipped();
                result.getFailures().add(failure(ship.getMmsi(), envelope, "PRIMARY_METADATA",
                        "PRIMARY_SHIP_TYPE_UNKNOWN_OR_OUT_OF_PROFILE", "primary ship is not eligible", attempt));
                continue;
            }
            try {
                List<ShipBehaviorTrace> traces;
                if (context.getProfile().getBehaviorType() == BehaviorType.ALONGSIDE) {
                    final BehaviorRunProfile profile = context.getProfile();
                    Predicate<String> policy = type -> shipTypes.counterparty(type,
                            profile.getCounterpartyShipTypePolicy(), profile.getUnknownShipTypeAction()).isAllowed();
                    ShipBehaviorAnalysisService.AlongsideWindowResult computed = analysis.analysisAlongsideProductionWindow(
                            ship.getMmsi(), envelope.getStartTime(), envelope.getEndTime(), envelope.getBatchNo(),
                            AlongsideRuleConfig.from(context.getBehaviorRules()).detectorRule(), policy);
                    prePolicyCandidates += computed.getPrePolicyCandidateTargets();
                    postPolicyCandidates += computed.getCandidateTargets();
                    if (computed.getFailed() > 0 || computed.getReplayFailures() > 0 || computed.getTruncatedQueries() > 0)
                        throw new IllegalStateException("incomplete alongside replay failures=" + computed.getReplayFailures()
                                + " truncated=" + computed.getTruncatedQueries());
                    traces = RegionProductionEventFilterAdapter.validNonWeakAlongside(computed.getTraces());
                } else {
                    List<com.hifleet.trajectory.TrajectoryPoint> points = trajectories.loadSafely(ship.getMmsi(),
                            envelope.getStartTime(), envelope.getEndTime(), simplifyZoom,
                            trajectorySliceHours, maxPointsPerShip);
                    traces = RegionProductionEventFilterAdapter.validNonWeakLoitering(
                            analysis.analysisLoiteringV2Production(ship.getMmsi(), points, envelope.getBatchNo(),
                                    LoiteringRuleConfig.from(context.getBehaviorRules()).detectorConfig()));
                }
                BehaviorOrchestrationResult evaluated = new BehaviorRunOrchestrator().evaluate(
                        context.getProfile().getBehaviorType(), context.getSceneMode(), context.getBehaviorRules(),
                        traces, new MaritimeSceneTraceEvaluator(sceneService,
                                SceneRuleConfig.from(context.getSceneRules())), shipTypes, context.getProfile());
                result.getAuditResults().addAll(evaluated.getAuditResults());
                result.addCandidates(evaluated.getComparison().getControlCandidateCount());
                result.addRetained(evaluated.getComparison().getRetainedCount());
                result.addReviews(evaluated.getComparison().getReviewCount());
                result.successful(); context.getCheckpoints().record(key, "SUCCESS", attempt);
            } catch (RuntimeException ex) {
                context.getCheckpoints().record(key, "FAILED", attempt);
                result.getFailures().add(failure(ship.getMmsi(), envelope, "BEHAVIOR_COMPUTE",
                        "BEHAVIOR_SCOPE_FAILED", ex.getMessage(), attempt));
            }
        }
        result.getMetrics().put("counterpartyCandidatesBeforePolicy", prePolicyCandidates);
        result.getMetrics().put("counterpartyCandidatesAfterPolicy", postPolicyCandidates);
        return result;
    }
    private BehaviorFailureRecord failure(String mmsi, BehaviorJobEnvelope envelope, String stage,
                                          String code, String message, int attempt) {
        return new BehaviorFailureRecord(mmsi, null, envelope.getSourceType(), stage, code, message,
                0, 0, envelope.getStartTime() + "/" + envelope.getEndTime(), attempt);
    }
}
