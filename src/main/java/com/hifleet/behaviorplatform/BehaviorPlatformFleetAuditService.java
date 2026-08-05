package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.TargetShip;
import com.hifleet.job.services.RegionProductionEventFilterAdapter;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.maritime.ScenePreflightResult;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.service.ShipTypeClassifier;
import com.hifleet.trajectory.TrajectoryLoaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public final class BehaviorPlatformFleetAuditService {
    private final BehaviorPlatformRegistries registries;
    private final TargetShipRepository targetShips;
    private final ShipBehaviorAnalysisService analysis;
    private final TrajectoryLoaderService trajectories;
    private final MaritimeSceneService sceneService;
    private final ShipTypePolicyEvaluator shipTypes;
    private final ShadowBehaviorResultSink shadowSink;

    @Value("${ship.behavior.audit-output-dir:var/behavior-runs}") private String outputDirectory;
    @Value("${ship.behavior.shadow-write-enabled:false}") private boolean shadowWriteEnabled;
    @Value("${ship.behavior.formal-write-enabled:false}") private boolean formalWriteEnabled;
    @Value("${ship.behavior.trajectory.simplify-zoom:15}") private int simplifyZoom;
    @Value("${ship.behavior.audit.trajectory-slice-hours:6}") private int trajectorySliceHours;
    @Value("${ship.behavior.audit.max-points-per-ship:500000}") private int maxPointsPerShip;
    @Value("${ship.behavior.build.git-commit:UNKNOWN}") private String gitCommit;
    @Value("${ship.behavior.build.jar-sha256:UNKNOWN}") private String jarSha256;

    @Autowired
    public BehaviorPlatformFleetAuditService(BehaviorPlatformRegistries registries,
                                              TargetShipRepository targetShips,
                                              ShipBehaviorAnalysisService analysis,
                                              TrajectoryLoaderService trajectories,
                                              MaritimeSceneService sceneService,
                                              ShipTypeClassifier shipTypeClassifier,
                                              ShadowBehaviorResultSink shadowSink) {
        this.registries = registries;
        this.targetShips = targetShips;
        this.analysis = analysis;
        this.trajectories = trajectories;
        this.sceneService = sceneService;
        this.shipTypes = new ShipTypePolicyEvaluator(shipTypeClassifier);
        this.shadowSink = shadowSink;
    }

    public JSONObject preflight(String raw, boolean dedicatedFormalHandler) {
        BehaviorJobEnvelope envelope = BehaviorJobEnvelope.parse(raw);
        BehaviorRunProfile profile = registries.profiles().require(envelope.getProfileId());
        RuleSetDefinition rules = registries.ruleSets().require(
                profile.getRuleSetId(), profile.getRuleSetVersion());
        SceneMode sceneMode = BehaviorPermissionResolver.resolveScene(
                profile.getSceneMode(), envelope.getSceneModeOverride());
        WriteMode writeMode = BehaviorPermissionResolver.resolveWrite(
                profile.getWriteMode(), envelope.getWriteModeOverride(), envelope.isDryRun());
        int targetCount = targetShips.countEnabled(envelope.getListBatchNo());
        if (targetCount <= envelope.getStartSeq())
            throw new IllegalStateException("target list has no rows after startSeq");
        if (targetCount - envelope.getStartSeq() < envelope.getShipLimit())
            throw new IllegalStateException("target list is shorter than requested shipLimit after startSeq");
        ScenePreflightResult scene = sceneService.preflightResult();
        if (sceneMode != SceneMode.OFF && !scene.isAvailable())
            throw new IllegalStateException("scene snapshot is unavailable");
        if (writeMode == WriteMode.SHADOW_DB && !shadowWriteEnabled)
            throw new IllegalStateException("SHADOW_DB is disabled by server configuration");
        BehaviorWriteGate gate = new BehaviorWriteGate(formalWriteEnabled, dedicatedFormalHandler,
                rules.isProductionApproved(), sceneMode == SceneMode.OFF || scene.isAvailable(), true);
        BehaviorWriteGate.requireAllowed(writeMode, gate);
        JSONObject result = new JSONObject(true);
        result.put("status", "PASS");
        result.put("profileId", profile.getProfileId());
        result.put("profileHash", profile.getProfileHash());
        result.put("ruleSetId", rules.getRuleSetId());
        result.put("ruleSetVersion", rules.getRuleSetVersion());
        result.put("ruleSetHash", rules.getRuleSetHash());
        result.put("sceneMode", sceneMode.name());
        result.put("scene", scene.toLegacyMap());
        result.put("writeModeRequested", profile.getWriteMode().name());
        result.put("writeModeEffective", writeMode.name());
        result.put("targetShipCount", targetCount);
        result.put("estimatedOutput", Paths.get(outputDirectory, envelope.getBatchNo(),
                profile.getProfileId(), profile.getProfileHash()).toString());
        result.put("databaseWriteCount", 0);
        result.put("formalWriteCount", 0);
        return result;
    }

    public BehaviorRunManifest run(String raw, boolean dedicatedFormalHandler) {
        preflight(raw, dedicatedFormalHandler);
        BehaviorJobEnvelope envelope = BehaviorJobEnvelope.parse(raw);
        BehaviorRunProfile profile = registries.profiles().require(envelope.getProfileId());
        RuleSetDefinition rules = registries.ruleSets().require(profile.getRuleSetId(), profile.getRuleSetVersion());
        SceneMode sceneMode = BehaviorPermissionResolver.resolveScene(profile.getSceneMode(), envelope.getSceneModeOverride());
        WriteMode writeMode = BehaviorPermissionResolver.resolveWrite(profile.getWriteMode(), envelope.getWriteModeOverride(), envelope.isDryRun());
        BehaviorRunManifest manifest = manifest(envelope, profile, rules, sceneMode, writeMode);
        long started = System.currentTimeMillis();
        List<BehaviorAuditResult> audit = new ArrayList<BehaviorAuditResult>();
        int afterSeq = envelope.getStartSeq();
        int seen = 0, success = 0, failed = 0, candidates = 0, accepted = 0, reviews = 0;
        while (seen < envelope.getShipLimit()) {
            int limit = Math.min(envelope.getPageSize(), envelope.getShipLimit() - seen);
            List<TargetShip> page = targetShips.selectPage(envelope.getListBatchNo(), afterSeq, limit);
            if (page.isEmpty()) break;
            afterSeq = page.get(page.size() - 1).getSeqNo();
            for (ShipRun result : runPage(page, envelope.getParallelism(), profile,
                    envelope, sceneMode, rules)) {
                seen++;
                if (!result.success) { failed++; continue; }
                success++;
                audit.addAll(result.result.getAuditResults());
                BehaviorComparison comparison = result.result.getComparison();
                candidates += comparison.getControlCandidateCount();
                accepted += comparison.getRetainedCount();
                reviews += comparison.getReviewCount();
            }
            if (page.size() < limit) break;
        }
        manifest.setProcessedShips(seen);
        manifest.setSuccessfulShips(success);
        manifest.setFailedShips(failed);
        manifest.setCandidateCount(candidates);
        manifest.setAcceptedCount(accepted);
        manifest.setReviewCount(reviews);
        int writes = writeMode == WriteMode.SHADOW_DB || writeMode == WriteMode.FORMAL_DB
                ? writer(writeMode).write(manifest, audit) : 0;
        manifest.setDatabaseWriteCount(writes);
        manifest.setFormalWriteCount(writeMode == WriteMode.FORMAL_DB ? writes : 0);
        manifest.setFinishedAt(Instant.now().toString());
        manifest.setDurationMillis(System.currentTimeMillis() - started);
        FileAuditBehaviorResultWriter fileWriter = new FileAuditBehaviorResultWriter(Paths.get(outputDirectory));
        if (writeMode == WriteMode.FILE_AUDIT) fileWriter.write(manifest, audit);
        else fileWriter.writeManifestOnly(manifest);
        return manifest;
    }

    private List<ShipRun> runPage(List<TargetShip> page, int parallelism,
                                  final BehaviorRunProfile profile,
                                  final BehaviorJobEnvelope envelope,
                                  final SceneMode sceneMode, final RuleSetDefinition rules) {
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<Future<ShipRun>> futures = new ArrayList<Future<ShipRun>>();
        try {
            for (final TargetShip ship : page) futures.add(executor.submit(new Callable<ShipRun>() {
                @Override public ShipRun call() { return runShip(ship, profile, envelope, sceneMode, rules); }
            }));
            List<ShipRun> result = new ArrayList<ShipRun>();
            for (Future<ShipRun> future : futures) {
                try { result.add(future.get()); }
                catch (Exception ex) { result.add(ShipRun.failed()); }
            }
            return result;
        } finally {
            executor.shutdown();
        }
    }

    private ShipRun runShip(TargetShip ship, BehaviorRunProfile profile,
                            BehaviorJobEnvelope envelope, SceneMode sceneMode,
                            RuleSetDefinition rules) {
        if (!shipTypes.primaryAllowed(ship.getShipType(), profile.getPrimaryShipTypePolicy()))
            return ShipRun.success(new BehaviorOrchestrationResult(
                    new BehaviorComparison(), new ArrayList<BehaviorAuditResult>()));
        try {
            List<ShipBehaviorTrace> traces = compute(profile.getBehaviorType(), ship.getMmsi(), envelope);
            return ShipRun.success(new BehaviorRunOrchestrator().evaluate(
                    profile.getBehaviorType(), sceneMode, rules, traces,
                    new MaritimeSceneTraceEvaluator(sceneService)));
        } catch (RuntimeException ex) {
            return ShipRun.failed();
        }
    }

    private List<ShipBehaviorTrace> compute(BehaviorType type, String mmsi, BehaviorJobEnvelope envelope) {
        if (type == BehaviorType.ALONGSIDE) {
            ShipBehaviorAnalysisService.AlongsideWindowResult value = analysis.analysisAlongsideProductionWindow(
                    mmsi, envelope.getStartTime(), envelope.getEndTime(), envelope.getBatchNo());
            if (value.getFailed() > 0 || value.getReplayFailures() > 0 || value.getTruncatedQueries() > 0)
                throw new IllegalStateException("incomplete alongside replay");
            return RegionProductionEventFilterAdapter.validNonWeakAlongside(value.getTraces());
        }
        List<com.hifleet.trajectory.TrajectoryPoint> points = trajectories.loadSafely(mmsi,
                envelope.getStartTime(), envelope.getEndTime(), simplifyZoom,
                trajectorySliceHours, maxPointsPerShip);
        return RegionProductionEventFilterAdapter.validNonWeakLoitering(
                analysis.analysisLoiteringV2Production(mmsi, points, envelope.getBatchNo()));
    }

    private BehaviorResultWriter writer(WriteMode mode) {
        if (mode == WriteMode.NONE) return new NoopBehaviorResultWriter();
        if (mode == WriteMode.FILE_AUDIT) return new FileAuditBehaviorResultWriter(Paths.get(outputDirectory));
        if (mode == WriteMode.SHADOW_DB) return new ShadowDatabaseBehaviorResultWriter(shadowSink, value -> {
            throw new IllegalStateException("shadow writer cannot use formal sink");
        });
        throw new IllegalStateException("FORMAL_DB writer is not wired into Fleet Audit");
    }

    private BehaviorRunManifest manifest(BehaviorJobEnvelope envelope, BehaviorRunProfile profile,
                                         RuleSetDefinition rules, SceneMode sceneMode, WriteMode writeMode) {
        BehaviorRunManifest value = new BehaviorRunManifest();
        ScenePreflightResult scene = sceneService.preflightResult();
        value.setRunId(UUID.randomUUID().toString()); value.setBatchNo(envelope.getBatchNo());
        value.setBehaviorType(profile.getBehaviorType().name()); value.setProfileId(profile.getProfileId());
        value.setProfileHash(profile.getProfileHash()); value.setResolvedProfileSnapshot(profile.getResolvedSnapshot());
        value.setRuleSetId(rules.getRuleSetId()); value.setRuleSetVersion(rules.getRuleSetVersion());
        value.setRuleSetHash(rules.getRuleSetHash()); value.setSceneMode(sceneMode.name());
        value.setSceneSnapshotVersion(scene.getSnapshotVersion()); value.setSceneFingerprint(scene.getSnapshotFingerprint());
        value.setRuntimePolicyVersion(scene.getRuntimePolicyVersion()); value.setSnapshotPolicyVersion(scene.getSnapshotPolicyVersion());
        value.setWriteModeRequested(profile.getWriteMode().name()); value.setWriteModeEffective(writeMode.name());
        value.setGitCommit(gitCommit); value.setJarSha256(jarSha256); value.setListBatchNo(envelope.getListBatchNo());
        value.setStartTime(envelope.getStartTime()); value.setEndTime(envelope.getEndTime());
        value.setShipLimit(envelope.getShipLimit()); value.setStartedAt(Instant.now().toString());
        return value;
    }

    private static final class ShipRun {
        private final boolean success;
        private final BehaviorOrchestrationResult result;
        private ShipRun(boolean success, BehaviorOrchestrationResult result) {
            this.success = success; this.result = result;
        }
        static ShipRun success(BehaviorOrchestrationResult result) { return new ShipRun(true, result); }
        static ShipRun failed() { return new ShipRun(false, null); }
    }
}
