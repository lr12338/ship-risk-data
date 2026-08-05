package com.hifleet.behaviorplatform;

public final class BehaviorWriteGate {
    private final boolean serverFormalEnabled;
    private final boolean dedicatedFormalHandler;
    private final boolean productionApprovedRuleSet;
    private final boolean scenePreflightPassed;
    private final boolean dataPreflightPassed;

    public BehaviorWriteGate(boolean serverFormalEnabled, boolean dedicatedFormalHandler,
                             boolean productionApprovedRuleSet, boolean scenePreflightPassed,
                             boolean dataPreflightPassed) {
        this.serverFormalEnabled = serverFormalEnabled;
        this.dedicatedFormalHandler = dedicatedFormalHandler;
        this.productionApprovedRuleSet = productionApprovedRuleSet;
        this.scenePreflightPassed = scenePreflightPassed;
        this.dataPreflightPassed = dataPreflightPassed;
    }

    public static void requireAllowed(WriteMode mode, BehaviorWriteGate gate) {
        if (mode != WriteMode.FORMAL_DB) return;
        if (gate == null || !gate.serverFormalEnabled || !gate.dedicatedFormalHandler
                || !gate.productionApprovedRuleSet || !gate.scenePreflightPassed
                || !gate.dataPreflightPassed) {
            throw new IllegalStateException("FORMAL_DB requires every server, handler, rules and preflight gate");
        }
    }
}
