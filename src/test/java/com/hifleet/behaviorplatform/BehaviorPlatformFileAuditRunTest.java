package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.maritime.MaritimeSceneService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BehaviorPlatformFileAuditRunTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void fileAuditWritesSixFilesAndZeroDatabaseRows() {
        BehaviorPlatformRegistries registries = new BehaviorPlatformRegistries();
        ReflectionTestUtils.setField(registries, "externalProfileDirectory", ""); registries.load();
        BehaviorScopeExecutor executor = new BehaviorScopeExecutor() {
            public boolean supports(BehaviorTargetSourceType type) { return type == BehaviorTargetSourceType.SHIPS; }
            public JSONObject preflight(BehaviorScopeContext context) {
                JSONObject value = new JSONObject(); value.put("sourceResolvedCount", 1); return value;
            }
            public BehaviorScopeExecutionResult execute(BehaviorScopeContext context) {
                BehaviorScopeExecutionResult value = new BehaviorScopeExecutionResult();
                value.processed(); value.successful(); context.getCheckpoints().record("413000001", "SUCCESS", 1);
                return value;
            }
        };
        BehaviorPlatformFleetAuditService service = new BehaviorPlatformFleetAuditService(registries,
                new MaritimeSceneService(), (manifest, value) -> { throw new AssertionError("no DB write"); },
                Collections.singletonList(executor), null);
        set(service, "outputDirectory", folder.getRoot().getAbsolutePath());
        set(service, "shadowWriteEnabled", false); set(service, "formalWriteEnabled", false);
        set(service, "maxInlineShips", 10); set(service, "maxListShips", 5000);
        set(service, "maxParamBytes", 420); set(service, "maxParallelism", 1);
        BehaviorRunManifest manifest = service.run("{\"profile\":\"loitering-control-off-v2\","
                + "\"source\":\"SHIPS\",\"ships\":\"413000001\",\"from\":\"2026-01-01\","
                + "\"to\":\"2026-01-02\",\"batch\":\"audit-test\"}", false);
        assertEquals(0, manifest.getDatabaseWriteCount()); assertEquals(0, manifest.getFormalWriteCount());
        Path run = new FileAuditBehaviorResultWriter(folder.getRoot().toPath()).outputDirectory(manifest);
        for (String name : new String[]{"manifest.json", "resolved-profile.json", "resolved-rules.json",
                "progress.ndjson", "results.ndjson", "failures.ndjson", "checksums.sha256"})
            assertTrue(name, Files.exists(run.resolve(name)));
    }
    private void set(Object target, String name, Object value) { ReflectionTestUtils.setField(target, name, value); }
}
