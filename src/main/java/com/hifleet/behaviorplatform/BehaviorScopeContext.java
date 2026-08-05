package com.hifleet.behaviorplatform;

public final class BehaviorScopeContext {
    private final BehaviorJobEnvelope envelope;
    private final BehaviorRunProfile profile;
    private final RuleSetDefinition behaviorRules;
    private final RuleSetDefinition sceneRules;
    private final SceneMode sceneMode;
    private final BehaviorRunCheckpointStore checkpoints;
    public BehaviorScopeContext(BehaviorJobEnvelope envelope, BehaviorRunProfile profile,
                                RuleSetDefinition behaviorRules, RuleSetDefinition sceneRules,
                                SceneMode sceneMode, BehaviorRunCheckpointStore checkpoints) {
        this.envelope = envelope; this.profile = profile; this.behaviorRules = behaviorRules;
        this.sceneRules = sceneRules; this.sceneMode = sceneMode; this.checkpoints = checkpoints;
    }
    public BehaviorJobEnvelope getEnvelope() { return envelope; }
    public BehaviorRunProfile getProfile() { return profile; }
    public RuleSetDefinition getBehaviorRules() { return behaviorRules; }
    public RuleSetDefinition getSceneRules() { return sceneRules; }
    public SceneMode getSceneMode() { return sceneMode; }
    public BehaviorRunCheckpointStore getCheckpoints() { return checkpoints; }
}
