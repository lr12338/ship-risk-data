package com.hifleet.regionbehavior;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RegionBehaviorReportWriterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesCompleteDryRunArtifactSetAndChecksums() throws Exception {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setOutputRoot(temporaryFolder.getRoot().getAbsolutePath());
        RegionBehaviorJobParams params = params();
        RegionGeometry region = RegionGeometry.parse(params.getPolygonWkt(), 0, config);
        RegionQueryPlan plan = new RegionQueryPlanner(config).plan(params, region);
        RegionAisDiscoveryResult discovery = new RegionAisDiscoveryResult();
        RegionShipCandidate candidate = new RegionShipCandidate("413000001");
        candidate.addPoint(new TrajectoryPoint("413000001",
                LocalDateTime.of(2026, 6, 1, 0, 0), 121.05, 30.05, 1.0, 0.0, "0"));
        discovery.candidates.put("413000001", candidate);
        RegionShipMetadataSummary metadata = new RegionShipMetadataSummary();
        metadata.discovered();
        RegionBehaviorRunResult result = RegionBehaviorRunResult.fromDiscovery(discovery);

        Path output = new RegionBehaviorReportWriter(config).write(
                params, region, plan, discovery, metadata, result);

        for (String name : Arrays.asList("manifest.json", "query-plan.json", "discovery-summary.json",
                "candidates.ndjson.gz", "region-events.ndjson.gz", "boundary-events.ndjson.gz",
                "failures.ndjson.gz", "checksums.sha256")) {
            assertTrue(Files.isRegularFile(output.resolve(name)));
        }
        assertEquals(7, Files.readAllLines(output.resolve("checksums.sha256"),
                StandardCharsets.UTF_8).size());
        String manifest = new String(Files.readAllBytes(output.resolve("manifest.json")),
                StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"databaseWrites\":0"));
        assertTrue(manifest.contains("\"formalEventsChanged\":false"));
    }

    @Test(expected = IllegalStateException.class)
    public void refusesToOverwriteExistingBatchReport() throws Exception {
        RegionBehaviorConfig config = RegionBehaviorConfig.defaults();
        config.setOutputRoot(temporaryFolder.getRoot().getAbsolutePath());
        Files.createDirectory(temporaryFolder.getRoot().toPath().resolve("batch-1"));
        RegionBehaviorJobParams params = params();
        RegionGeometry region = RegionGeometry.parse(params.getPolygonWkt(), 0, config);
        RegionQueryPlan plan = new RegionQueryPlanner(config).plan(params, region);
        new RegionBehaviorReportWriter(config).write(params, region, plan,
                new RegionAisDiscoveryResult(), new RegionShipMetadataSummary(),
                RegionBehaviorRunResult.fromDiscovery(new RegionAisDiscoveryResult()));
    }

    private RegionBehaviorJobParams params() {
        RegionBehaviorJobParams params = new RegionBehaviorJobParams();
        params.setMode("region_behavior_v1");
        params.setRegionId("region-1");
        params.setRegionName("test");
        params.setPolygonWkt("POLYGON((121 30,121.1 30,121.1 30.1,121 30.1,121 30))");
        params.setStartTime("2026-06-01 00:00:00");
        params.setEndTime("2026-06-01 01:00:00");
        params.getBehaviorTypes().add("LOITERING");
        params.setBatchNo("batch-1");
        params.setDryRun(true);
        return params;
    }
}
