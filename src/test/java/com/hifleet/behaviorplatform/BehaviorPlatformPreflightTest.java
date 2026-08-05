package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.maritime.MaritimeSceneService;
import com.hifleet.repository.TargetShipRepository;
import com.hifleet.service.ShipTypeClassifier;
import com.hifleet.trajectory.TrajectoryLoaderService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BehaviorPlatformPreflightTest {
    @Test
    public void preflightReadsNoTrajectoryAndDryRunForcesZeroWriteMode() {
        BehaviorPlatformRegistries registries = new BehaviorPlatformRegistries();
        ReflectionTestUtils.setField(registries, "externalProfileDirectory", "");
        registries.load();
        TargetShipRepository targets = mock(TargetShipRepository.class);
        when(targets.countEnabled("fleet-v1")).thenReturn(100);
        ShipBehaviorAnalysisService analysis = mock(ShipBehaviorAnalysisService.class);
        TrajectoryLoaderService trajectories = mock(TrajectoryLoaderService.class);
        BehaviorPlatformFleetAuditService service = new BehaviorPlatformFleetAuditService(
                registries, targets, analysis, trajectories, new MaritimeSceneService(),
                new ShipTypeClassifier(), (manifest, value) -> true);
        ReflectionTestUtils.setField(service, "outputDirectory", "var/behavior-runs");
        ReflectionTestUtils.setField(service, "shadowWriteEnabled", false);
        ReflectionTestUtils.setField(service, "formalWriteEnabled", false);
        String raw = "{\"profile\":\"alongside-scene-shadow-v1\",\"list\":\"fleet-v1\","
                + "\"from\":\"2026-01-01\",\"to\":\"2026-01-02\",\"batch\":\"b1\","
                + "\"dryRun\":true,\"sceneModeOverride\":\"OFF\"}";

        JSONObject result = service.preflight(raw, false);

        assertEquals("PASS", result.getString("status"));
        assertEquals("NONE", result.getString("writeModeEffective"));
        assertEquals(0, result.getIntValue("databaseWriteCount"));
        verifyZeroInteractions(analysis, trajectories);
    }
}
