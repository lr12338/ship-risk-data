package com.hifleet.behaviorplatform;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class BehaviorCheckpointStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void resumeSkipsSuccessAndRetriesFailed() throws Exception {
        Path directory = folder.newFolder("run").toPath();
        Files.write(directory.resolve("manifest.json"), "{\"parameterHash\":\"same\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("progress.ndjson"), ("{\"key\":\"a\",\"status\":\"SUCCESS\",\"attempt\":1}\n"
                + "{\"key\":\"b\",\"status\":\"FAILED\",\"attempt\":2}\n").getBytes(StandardCharsets.UTF_8));
        BehaviorRunCheckpointStore value = new BehaviorRunCheckpointStore();
        value.load(directory, "same", true);
        assertTrue(value.skipSuccess("a"));
        assertFalse(value.skipSuccess("b"));
        assertEquals(3, value.nextAttempt("b"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void changedParameterHashRejectsResume() throws Exception {
        Path directory = folder.newFolder("changed").toPath();
        Files.write(directory.resolve("manifest.json"), "{\"parameterHash\":\"old\"}".getBytes(StandardCharsets.UTF_8));
        new BehaviorRunCheckpointStore().load(directory, "new", true);
    }
}
