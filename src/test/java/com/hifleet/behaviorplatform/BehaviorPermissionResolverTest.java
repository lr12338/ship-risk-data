package com.hifleet.behaviorplatform;

import org.junit.Test;

import static org.junit.Assert.*;

public class BehaviorPermissionResolverTest {
    @Test
    public void taskOverridesCanOnlyDowngrade() {
        assertEquals(SceneMode.SHADOW,
                BehaviorPermissionResolver.resolveScene(SceneMode.FILTER, SceneMode.SHADOW));
        assertEquals(WriteMode.NONE,
                BehaviorPermissionResolver.resolveWrite(WriteMode.SHADOW_DB, WriteMode.NONE, false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sceneOverrideCannotUpgrade() {
        BehaviorPermissionResolver.resolveScene(SceneMode.OFF, SceneMode.SHADOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void writeOverrideCannotUpgrade() {
        BehaviorPermissionResolver.resolveWrite(WriteMode.NONE, WriteMode.SHADOW_DB, false);
    }

    @Test
    public void dryRunAlwaysResolvesToNone() {
        assertEquals(WriteMode.NONE,
                BehaviorPermissionResolver.resolveWrite(WriteMode.FORMAL_DB, null, true));
    }

    @Test(expected = IllegalStateException.class)
    public void formalWriteRequiresEveryGate() {
        BehaviorWriteGate.requireAllowed(WriteMode.FORMAL_DB,
                new BehaviorWriteGate(false, true, true, true, true));
    }

    @Test
    public void formalWriteRejectsEachMissingGate() {
        BehaviorWriteGate[] values = new BehaviorWriteGate[]{
                new BehaviorWriteGate(false, true, true, true, true),
                new BehaviorWriteGate(true, false, true, true, true),
                new BehaviorWriteGate(true, true, false, true, true),
                new BehaviorWriteGate(true, true, true, false, true),
                new BehaviorWriteGate(true, true, true, true, false)};
        for (BehaviorWriteGate gate : values) {
            try {
                BehaviorWriteGate.requireAllowed(WriteMode.FORMAL_DB, gate);
                fail("missing formal gate must reject");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("FORMAL_DB"));
            }
        }
    }
}
