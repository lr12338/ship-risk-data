package com.hifleet.behaviorplatform;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BehaviorResultWriterTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void noneWriterPerformsZeroWrites() {
        assertEquals(0, new NoopBehaviorResultWriter().write(manifest(), Arrays.asList(result())));
    }

    @Test
    public void shadowWriterCannotReachFormalSink() {
        final AtomicInteger shadow = new AtomicInteger();
        final AtomicInteger formal = new AtomicInteger();
        ShadowDatabaseBehaviorResultWriter writer = new ShadowDatabaseBehaviorResultWriter(
                (manifest, value) -> { shadow.incrementAndGet(); return true; },
                value -> { formal.incrementAndGet(); return true; });
        assertEquals(1, writer.write(manifest(), Arrays.asList(result())));
        assertEquals(1, shadow.get());
        assertEquals(0, formal.get());
    }

    @Test
    public void fileWriterIsIdempotentByComparisonKey() {
        FileAuditBehaviorResultWriter writer = new FileAuditBehaviorResultWriter(folder.getRoot().toPath());
        assertEquals(1, writer.write(manifest(), Arrays.asList(result())));
        assertEquals(1, writer.write(manifest(), Arrays.asList(result())));
        assertTrue(writer.outputDirectory(manifest()).toString().contains("profile-v1"));
        assertEquals(1, writer.readResults(writer.outputDirectory(manifest())).size());
        assertTrue(Files.exists(writer.outputDirectory(manifest()).resolve("resolved-rules.json")));
        assertTrue(Files.exists(writer.outputDirectory(manifest()).resolve("progress.ndjson")));
        assertTrue(Files.exists(writer.outputDirectory(manifest()).resolve("failures.ndjson")));
    }

    private BehaviorRunManifest manifest() {
        BehaviorRunManifest value = new BehaviorRunManifest();
        value.setRunId("run-1");
        value.setBatchNo("batch-1");
        value.setProfileId("profile-v1");
        value.setProfileHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        value.setResolvedProfileSnapshot("{\"profileId\":\"profile-v1\"}");
        return value;
    }

    private BehaviorAuditResult result() {
        return new BehaviorAuditResult("key-1", "413000001", null,
                "2026-01-01 00:00:00", "2026-01-01 01:00:00",
                true, "OPEN_WATER_CONFIRMED", "ACCEPTED", null);
    }
}
