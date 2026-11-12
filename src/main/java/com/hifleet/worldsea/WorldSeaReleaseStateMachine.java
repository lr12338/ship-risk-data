package com.hifleet.worldsea;

/** Pure release-state rules shared by the importer and fault-injection tests. */
public final class WorldSeaReleaseStateMachine {
    public enum Status {
        NEW, STAGE_IMPORTED, STAGE_VALIDATED, STAGED, PUBLISHING,
        PUBLISHED_INACTIVE, ACTIVE, NOOP_IDENTICAL, ROLLBACK_REQUIRED,
        ROLLED_BACK, FAILED
    }

    public static void require(Status actual, Status... allowed) {
        for (Status value : allowed) if (value == actual) return;
        throw new IllegalStateException("illegal WORLD_SEA release state=" + actual);
    }

    public static RecoveryDecision recover(ReleaseReality value) {
        if (value.status == null) {
            return new RecoveryDecision("IMPORT_STAGE", true);
        }
        switch (value.status) {
            case NEW: return new RecoveryDecision("IMPORT_STAGE", true);
            case STAGE_IMPORTED: return new RecoveryDecision("VALIDATE_STAGE", true);
            case STAGE_VALIDATED: return new RecoveryDecision(value.stageExists
                    ? "REGISTER_STAGE" : "INSPECT_STAGE_MISSING", true);
            case STAGED: return new RecoveryDecision(value.stageExists
                    ? "PUBLISH_STAGE" : "INSPECT_STAGE_MISSING", true);
            case PUBLISHING:
                if (!value.stageExists && value.formalFingerprintMatchesRelease) {
                    return new RecoveryDecision("MARK_PUBLISHED_INACTIVE", true);
                }
                if ((!value.formalExists || value.formalFingerprintMismatch) && value.backupExists) {
                    return new RecoveryDecision("ROLLBACK_RELEASE", true);
                }
                return new RecoveryDecision("PUBLISH_STAGE", true);
            case PUBLISHED_INACTIVE: return new RecoveryDecision(value.formalFingerprintMatchesRelease
                    ? "ACTIVATE_DATASET" : "ROLLBACK_REQUIRED", true);
            case NOOP_IDENTICAL:
                return value.formalFingerprintMatchesRelease && value.activeFingerprintMatchesRelease
                        ? new RecoveryDecision("NONE", false)
                        : new RecoveryDecision(value.formalFingerprintMatchesRelease
                        ? "ACTIVATE_DATASET" : "ROLLBACK_REQUIRED", true);
            case ACTIVE:
                return value.formalFingerprintMatchesRelease && value.activeFingerprintMatchesRelease
                        ? new RecoveryDecision("NONE", false)
                        : new RecoveryDecision("ROLLBACK_REQUIRED", true);
            case ROLLBACK_REQUIRED: return new RecoveryDecision("ROLLBACK_RELEASE", true);
            case ROLLED_BACK: return new RecoveryDecision("NONE", false);
            case FAILED: return new RecoveryDecision(value.stageExists
                    ? "VALIDATE_STAGE" : "INSPECT_AND_RESUME_RELEASE", true);
            default: throw new IllegalStateException("unsupported WORLD_SEA release state=" + value.status);
        }
    }

    public static final class ReleaseReality {
        private final Status status;
        private final boolean stageExists;
        private final boolean backupExists;
        private final boolean formalExists;
        private final boolean formalFingerprintMatchesRelease;
        private final boolean formalFingerprintMismatch;
        private final boolean activeFingerprintMatchesRelease;

        public ReleaseReality(Status status, boolean stageExists, boolean backupExists,
                              boolean formalExists,
                              boolean formalFingerprintMatchesRelease,
                              boolean formalFingerprintMismatch,
                              boolean activeFingerprintMatchesRelease) {
            this.status = status;
            this.stageExists = stageExists;
            this.backupExists = backupExists;
            this.formalExists = formalExists;
            this.formalFingerprintMatchesRelease = formalFingerprintMatchesRelease;
            this.formalFingerprintMismatch = formalFingerprintMismatch;
            this.activeFingerprintMatchesRelease = activeFingerprintMatchesRelease;
        }
    }

    public static final class RecoveryDecision {
        private final String nextAllowedAction;
        private final boolean recoveryNeeded;
        private RecoveryDecision(String nextAllowedAction, boolean recoveryNeeded) {
            this.nextAllowedAction = nextAllowedAction;
            this.recoveryNeeded = recoveryNeeded;
        }
        public String getNextAllowedAction() { return nextAllowedAction; }
        public boolean isRecoveryNeeded() { return recoveryNeeded; }
    }

    private WorldSeaReleaseStateMachine() {}
}
