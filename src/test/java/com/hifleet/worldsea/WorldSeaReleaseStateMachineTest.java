package com.hifleet.worldsea;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldSeaReleaseStateMachineTest {
    @Test
    public void recoversEveryPersistedPreActivationStepIdempotently() {
        assertAction(null, false, false, false, false, false, false, "IMPORT_STAGE");
        assertAction(WorldSeaReleaseStateMachine.Status.STAGE_IMPORTED, true, false, false, false, false, false,
                "VALIDATE_STAGE");
        assertAction(WorldSeaReleaseStateMachine.Status.FAILED, true, false, false, false, false, false,
                "VALIDATE_STAGE");
        assertAction(WorldSeaReleaseStateMachine.Status.STAGE_VALIDATED, true, false, false, false, false, false,
                "REGISTER_STAGE");
        assertAction(WorldSeaReleaseStateMachine.Status.STAGED, true, false, false, false, false, false,
                "PUBLISH_STAGE");
        assertAction(WorldSeaReleaseStateMachine.Status.PUBLISHING, false, false, true, true, false, false,
                "MARK_PUBLISHED_INACTIVE");
        assertAction(WorldSeaReleaseStateMachine.Status.PUBLISHED_INACTIVE, false, false, true, true, false, false,
                "ACTIVATE_DATASET");
    }

    @Test
    public void detectsRenameAmbiguityAndRequiresRollback() {
        assertAction(WorldSeaReleaseStateMachine.Status.PUBLISHING, false, true, true, false, true, false,
                "ROLLBACK_RELEASE");
        assertAction(WorldSeaReleaseStateMachine.Status.ROLLBACK_REQUIRED, false, true, true, false, true, false,
                "ROLLBACK_RELEASE");
    }

    @Test
    public void missingStageOrFormalCannotAdvanceOnMetadataAlone() {
        assertAction(WorldSeaReleaseStateMachine.Status.STAGED, false, false,
                false, false, false, false, "INSPECT_STAGE_MISSING");
        assertAction(WorldSeaReleaseStateMachine.Status.PUBLISHED_INACTIVE, false, false,
                false, false, false, false, "ROLLBACK_REQUIRED");
        assertAction(WorldSeaReleaseStateMachine.Status.ACTIVE, false, false,
                false, false, false, true, "ROLLBACK_REQUIRED");
    }

    @Test
    public void identicalAndActiveReleasesAreIdempotent() {
        WorldSeaReleaseStateMachine.RecoveryDecision noop = decision(
                WorldSeaReleaseStateMachine.Status.NOOP_IDENTICAL, true, false, true, false, true);
        assertEquals("NONE", noop.getNextAllowedAction()); assertFalse(noop.isRecoveryNeeded());
        WorldSeaReleaseStateMachine.RecoveryDecision active = decision(
                WorldSeaReleaseStateMachine.Status.ACTIVE, false, false, true, false, true);
        assertEquals("NONE", active.getNextAllowedAction()); assertFalse(active.isRecoveryNeeded());
        assertAction(WorldSeaReleaseStateMachine.Status.NOOP_IDENTICAL, true, false, true, true, false, false,
                "ACTIVATE_DATASET");
    }

    @Test(expected = IllegalStateException.class)
    public void illegalTransitionIsRejected() {
        WorldSeaReleaseStateMachine.require(WorldSeaReleaseStateMachine.Status.STAGE_IMPORTED,
                WorldSeaReleaseStateMachine.Status.STAGED);
    }

    @Test
    public void rolledBackReleaseIsSafeToRetryWithoutDataLoss() {
        WorldSeaReleaseStateMachine.RecoveryDecision value = decision(
                WorldSeaReleaseStateMachine.Status.ROLLED_BACK, false, false, false, false, false);
        assertEquals("NONE", value.getNextAllowedAction());
        assertFalse(value.isRecoveryNeeded());
    }

    @Test(expected = IllegalStateException.class)
    public void deprecatedOneStepApplyIsDisabled() throws Exception {
        WorldSeaImportTool.main(new String[]{"APPLY", "ship_behavior_world_sea_stage_20260806_155556"});
    }

    private void assertAction(WorldSeaReleaseStateMachine.Status status, boolean stage,
                              boolean backup, boolean formalExists, boolean formalMatch, boolean formalMismatch,
                              boolean activeMatch, String expected) {
        WorldSeaReleaseStateMachine.RecoveryDecision value = decision(
                status, stage, backup, formalExists, formalMatch, formalMismatch, activeMatch);
        assertEquals(expected, value.getNextAllowedAction());
        assertTrue(value.isRecoveryNeeded());
    }

    private WorldSeaReleaseStateMachine.RecoveryDecision decision(
            WorldSeaReleaseStateMachine.Status status, boolean stage, boolean backup,
            boolean formalMatch, boolean formalMismatch, boolean activeMatch) {
        return decision(status, stage, backup, formalMatch || formalMismatch,
                formalMatch, formalMismatch, activeMatch);
    }

    private WorldSeaReleaseStateMachine.RecoveryDecision decision(
            WorldSeaReleaseStateMachine.Status status, boolean stage, boolean backup,
            boolean formalExists, boolean formalMatch, boolean formalMismatch, boolean activeMatch) {
        return WorldSeaReleaseStateMachine.recover(new WorldSeaReleaseStateMachine.ReleaseReality(
                status, stage, backup, formalExists, formalMatch, formalMismatch, activeMatch));
    }
}
