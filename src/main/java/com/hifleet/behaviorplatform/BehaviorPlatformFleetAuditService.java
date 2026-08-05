package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.maritime.ScenePreflightResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public final class BehaviorPlatformFleetAuditService {
    private final BehaviorPlatformRegistries registries;
    private final MaritimeSceneService sceneService;
    private final ShadowBehaviorResultSink shadowSink;
    private final List<BehaviorScopeExecutor> scopeExecutors;
    private final RegionDefinitionRegistryComponent regionDefinitions;

    @Value("${ship.behavior.audit-output-dir:var/behavior-runs}") private String outputDirectory;
    @Value("${ship.behavior.shadow-write-enabled:false}") private boolean shadowWriteEnabled;
    @Value("${ship.behavior.formal-write-enabled:false}") private boolean formalWriteEnabled;
    @Value("${ship.behavior.input.inline.max-ships:10}") private int maxInlineShips;
    @Value("${ship.behavior.input.list.max-ships:5000}") private int maxListShips;
    @Value("${ship.behavior.input.max-param-bytes:420}") private int maxParamBytes;
    @Value("${ship.behavior.audit.max-parallelism:1}") private int maxParallelism;
    @Value("${ship.behavior.build.git-commit:UNKNOWN}") private String gitCommit;
    @Value("${ship.behavior.build.jar-sha256:UNKNOWN}") private String jarSha256;

    public BehaviorPlatformFleetAuditService(BehaviorPlatformRegistries registries,
                                             MaritimeSceneService sceneService,
                                             ShadowBehaviorResultSink shadowSink,
                                             List<BehaviorScopeExecutor> scopeExecutors,
                                             RegionDefinitionRegistryComponent regionDefinitions) {
        this.registries = registries; this.sceneService = sceneService; this.shadowSink = shadowSink;
        this.scopeExecutors = scopeExecutors; this.regionDefinitions = regionDefinitions;
    }

    public JSONObject preflight(String raw, boolean dedicatedFormalHandler) {
        Resolved resolved = resolve(raw, dedicatedFormalHandler);
        JSONObject scope = resolved.executor.preflight(resolved.context(null));
        JSONObject result = new JSONObject(true);
        result.put("status", "PASS");
        result.put("sourceType", resolved.envelope.getSourceType().name());
        result.put("profileId", resolved.profile.getProfileId());
        result.put("profileHash", resolved.profile.getProfileHash());
        result.put("behaviorRuleSetHash", resolved.behaviorRules.getRuleSetHash());
        result.put("sceneRuleSetHash", resolved.sceneRules.getRuleSetHash());
        result.put("ruleSetHash", resolved.behaviorRules.getRuleSetHash());
        result.put("sceneMode", resolved.sceneMode.name());
        result.put("scene", resolved.scene.toLegacyMap());
        result.put("writeModeRequested", resolved.profile.getWriteMode().name());
        result.put("writeModeEffective", resolved.writeMode.name());
        result.put("gitCommit", gitCommit); result.put("jarSha256", jarSha256);
        result.putAll(scope);
        result.put("estimatedOutput", Paths.get(outputDirectory, resolved.envelope.getBatchNo(),
                resolved.profile.getProfileId(), resolved.profile.getProfileHash()).toString());
        result.put("databaseWriteCount", 0); result.put("formalWriteCount", 0);
        return result;
    }

    public BehaviorRunManifest run(String raw, boolean dedicatedFormalHandler) {
        Resolved resolved = resolve(raw, dedicatedFormalHandler);
        JSONObject scopePreflight = resolved.executor.preflight(resolved.context(null));
        BehaviorRunManifest manifest = manifest(resolved, scopePreflight);
        FileAuditBehaviorResultWriter fileWriter = new FileAuditBehaviorResultWriter(Paths.get(outputDirectory));
        Path directory = fileWriter.outputDirectory(manifest);
        BehaviorRunCheckpointStore checkpoints = new BehaviorRunCheckpointStore();
        checkpoints.load(directory, manifest.getParameterHash(), resolved.envelope.isResume());
        long started = System.currentTimeMillis();
        BehaviorScopeExecutionResult executed = resolved.executor.execute(resolved.context(checkpoints));
        manifest.setProcessedShips(executed.getProcessed());
        manifest.setSuccessfulShips(executed.getSuccessful());
        manifest.setFailedShips(executed.getFailures().size());
        manifest.setSkippedShips(executed.getSkipped()); manifest.setRetriedShips(executed.getRetried());
        manifest.setCandidateCount(executed.getCandidates()); manifest.setAcceptedCount(executed.getRetained());
        manifest.setReviewCount(executed.getReviews());
        applyScopeMetrics(manifest, executed.getMetrics());
        Map<String, Integer> failures = new LinkedHashMap<String, Integer>();
        for (BehaviorFailureRecord value : executed.getFailures())
            failures.put(value.getStage(), failures.containsKey(value.getStage())
                    ? failures.get(value.getStage()) + 1 : 1);
        manifest.setFailureCountByStage(failures);
        int writes = resolved.writeMode == WriteMode.SHADOW_DB
                ? writer(resolved.writeMode).write(manifest, executed.getAuditResults()) : 0;
        manifest.setDatabaseWriteCount(writes); manifest.setFormalWriteCount(0);
        manifest.setFinishedAt(Instant.now().toString());
        manifest.setDurationMillis(System.currentTimeMillis() - started);
        if (resolved.writeMode == WriteMode.FILE_AUDIT) fileWriter.write(manifest,
                executed.getAuditResults(), executed.getFailures(), checkpoints.records());
        else fileWriter.writeManifestOnly(manifest);
        return manifest;
    }

    private Resolved resolve(String raw, boolean dedicatedFormalHandler) {
        if (dedicatedFormalHandler) throw new IllegalStateException("shipBehaviorFormalJobHandler remains disabled");
        BehaviorJobEnvelope envelope = BehaviorJobEnvelope.parse(raw, maxInlineShips, maxListShips, maxParamBytes);
        if (envelope.getParallelism() > maxParallelism)
            throw new IllegalArgumentException("parallelism exceeds server max=" + maxParallelism);
        BehaviorRunProfile profile = registries.profiles().require(envelope.getProfileId());
        RuleSetDefinition behaviorRules = registries.ruleSets().require(
                profile.getBehaviorRuleSetId(), profile.getBehaviorRuleSetVersion());
        RuleSetDefinition sceneRules = registries.ruleSets().require(
                profile.getSceneRuleSetId(), profile.getSceneRuleSetVersion());
        SceneRuleConfig.from(sceneRules);
        SceneMode sceneMode = BehaviorPermissionResolver.resolveScene(profile.getSceneMode(), envelope.getSceneModeOverride());
        WriteMode writeMode = BehaviorPermissionResolver.resolveWrite(
                profile.getWriteMode(), envelope.getWriteModeOverride(), envelope.isDryRun());
        ScenePreflightResult scene = sceneService.preflightResult();
        if (sceneMode != SceneMode.OFF && !scene.isAvailable()) throw new IllegalStateException("scene snapshot is unavailable");
        if (writeMode == WriteMode.SHADOW_DB && !shadowWriteEnabled)
            throw new IllegalStateException("SHADOW_DB is disabled by server configuration");
        BehaviorWriteGate.requireAllowed(writeMode, new BehaviorWriteGate(formalWriteEnabled, false,
                behaviorRules.isProductionApproved(), sceneMode == SceneMode.OFF || scene.isAvailable(), true));
        BehaviorScopeExecutor executor = null;
        for (BehaviorScopeExecutor value : scopeExecutors) if (value.supports(envelope.getSourceType())) executor = value;
        if (executor == null) throw new IllegalStateException("no scope executor for " + envelope.getSourceType());
        return new Resolved(envelope, profile, behaviorRules, sceneRules, sceneMode, writeMode, scene, executor);
    }

    private BehaviorRunManifest manifest(Resolved resolved, JSONObject scope) {
        BehaviorRunManifest value = new BehaviorRunManifest();
        RegionDefinition region = resolved.envelope.getSourceType() == BehaviorTargetSourceType.REGION
                ? regionDefinitions.resolve(resolved.envelope) : null;
        BehaviorInputSpec input = BehaviorInputSpec.from(resolved.envelope, region);
        value.setRunId(UUID.randomUUID().toString()); value.setBatchNo(resolved.envelope.getBatchNo());
        value.setBehaviorType(resolved.profile.getBehaviorType().name()); value.setProfileId(resolved.profile.getProfileId());
        value.setProfileHash(resolved.profile.getProfileHash()); value.setResolvedProfileSnapshot(resolved.profile.getResolvedSnapshot());
        value.setRuleSetId(resolved.behaviorRules.getRuleSetId()); value.setRuleSetVersion(resolved.behaviorRules.getRuleSetVersion());
        value.setRuleSetHash(resolved.behaviorRules.getRuleSetHash()); value.setBehaviorRuleSetHash(resolved.behaviorRules.getRuleSetHash());
        value.setSceneRuleSetHash(resolved.sceneRules.getRuleSetHash()); value.setResolvedRulesSnapshot(rulesSnapshot(resolved));
        value.setSceneMode(resolved.sceneMode.name()); value.setSceneSnapshotVersion(resolved.scene.getSnapshotVersion());
        value.setSceneFingerprint(resolved.scene.getSnapshotFingerprint()); value.setRuntimePolicyVersion(resolved.scene.getRuntimePolicyVersion());
        value.setSnapshotPolicyVersion(resolved.scene.getSnapshotPolicyVersion());
        value.setWriteModeRequested(resolved.profile.getWriteMode().name()); value.setWriteModeEffective(resolved.writeMode.name());
        value.setGitCommit(gitCommit); value.setJarSha256(jarSha256); value.setSourceType(input.getSourceType().name());
        value.setSourceSnapshot(input.getSnapshot()); value.setSourceFingerprint(input.getFingerprint());
        value.setListBatchNo(resolved.envelope.getListBatchNo()); value.setStartSeq(resolved.envelope.getStartSeq());
        value.setPageSize(resolved.envelope.getPageSize()); value.setParallelism(resolved.envelope.getParallelism());
        value.setResume(resolved.envelope.isResume()); value.setInlineShipCount(resolved.envelope.getShips().size());
        value.setListEnabledCount(scope.getIntValue("listEnabledCount"));
        if (region != null) {
            value.setRegionId(region.getRegionId()); value.setRegionVersion(region.getVersion());
            value.setRegionFingerprint(region.getFingerprint()); value.setRegionAreaSqKm(region.getAreaSqKm());
            value.setQuerySliceCount(scope.getIntValue("querySlices"));
        }
        value.setStartTime(resolved.envelope.getStartTime()); value.setEndTime(resolved.envelope.getEndTime());
        value.setShipLimit(resolved.envelope.getShipLimit()); value.setStartedAt(Instant.now().toString());
        value.setParameterHash(BehaviorHashes.sha256(input.getFingerprint() + "|" + value.getProfileHash()
                + "|" + value.getBehaviorRuleSetHash() + "|" + value.getSceneRuleSetHash() + "|"
                + value.getStartTime() + "|" + value.getEndTime() + "|" + value.getBatchNo()));
        return value;
    }

    private String rulesSnapshot(Resolved resolved) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("behaviorRuleSet", resolved.behaviorRules); value.put("sceneRuleSet", resolved.sceneRules);
        return BehaviorHashes.canonicalJson(value);
    }

    private void applyScopeMetrics(BehaviorRunManifest manifest, JSONObject metrics) {
        if (BehaviorTargetSourceType.REGION.name().equals(manifest.getSourceType())) {
            manifest.setCandidateVesselCount(metrics.getIntValue("rawVessels"));
            manifest.setQuerySliceCount(metrics.getIntValue("querySlices"));
            manifest.setQueryCompleteness(metrics.getBooleanValue("queryComplete") ? "COMPLETE" : "PARTIAL");
        }
    }

    private BehaviorResultWriter writer(WriteMode mode) {
        if (mode == WriteMode.SHADOW_DB) return new ShadowDatabaseBehaviorResultWriter(shadowSink, value -> {
            throw new IllegalStateException("shadow writer cannot use formal sink");
        });
        throw new IllegalStateException("database writer is unavailable for " + mode);
    }

    private static final class Resolved {
        private final BehaviorJobEnvelope envelope;
        private final BehaviorRunProfile profile;
        private final RuleSetDefinition behaviorRules;
        private final RuleSetDefinition sceneRules;
        private final SceneMode sceneMode;
        private final WriteMode writeMode;
        private final ScenePreflightResult scene;
        private final BehaviorScopeExecutor executor;
        private Resolved(BehaviorJobEnvelope envelope, BehaviorRunProfile profile,
                         RuleSetDefinition behaviorRules, RuleSetDefinition sceneRules,
                         SceneMode sceneMode, WriteMode writeMode, ScenePreflightResult scene,
                         BehaviorScopeExecutor executor) {
            this.envelope = envelope; this.profile = profile;
            this.behaviorRules = behaviorRules; this.sceneRules = sceneRules; this.sceneMode = sceneMode;
            this.writeMode = writeMode; this.scene = scene; this.executor = executor;
        }
        private BehaviorScopeContext context(BehaviorRunCheckpointStore checkpoints) {
            return new BehaviorScopeContext(envelope, profile, behaviorRules, sceneRules, sceneMode,
                    checkpoints == null ? new BehaviorRunCheckpointStore() : checkpoints);
        }
    }
}
