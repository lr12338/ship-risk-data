package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Service
public class RegionBehaviorReportWriter {
    private static final String[] REPORT_FILES = {
            "manifest.json", "query-plan.json", "discovery-summary.json",
            "candidates.ndjson.gz", "region-events.ndjson.gz",
            "boundary-events.ndjson.gz", "failures.ndjson.gz"
    };
    private final RegionBehaviorConfig config;

    @Autowired
    public RegionBehaviorReportWriter(RegionBehaviorConfig config) {
        this.config = config;
    }

    public Path write(RegionBehaviorJobParams params, RegionGeometry region,
                      RegionQueryPlan plan, RegionAisDiscoveryResult discovery,
                      RegionShipMetadataSummary metadata, RegionBehaviorRunResult runResult) {
        Path root = Paths.get(config.getOutputRoot()).toAbsolutePath().normalize();
        Path batchDirectory = root.resolve(params.getBatchNo()).normalize();
        Path directory = params.isFormalWrite()
                ? batchDirectory.resolve("attempt-" + Instant.now().toEpochMilli()).normalize()
                : batchDirectory;
        if ((!params.isFormalWrite() && !directory.getParent().equals(root))
                || (params.isFormalWrite() && !directory.getParent().equals(batchDirectory))) {
            throw new IllegalArgumentException("unsafe batch output path");
        }
        try {
            Files.createDirectories(root);
            if (params.isFormalWrite()) Files.createDirectories(batchDirectory);
            if (Files.exists(directory)) {
                throw new IllegalStateException("region behavior output already exists: " + directory);
            }
            Files.createDirectory(directory);
            writeJson(directory.resolve("manifest.json"), manifest(params, region, runResult));
            writeJson(directory.resolve("query-plan.json"), queryPlan(plan));
            writeJson(directory.resolve("discovery-summary.json"), discovery(discovery, metadata));
            writeGzipLines(directory.resolve("candidates.ndjson.gz"),
                    new ArrayList<Object>(discovery.getCandidates().values()));
            writeGzipLines(directory.resolve("region-events.ndjson.gz"),
                    new ArrayList<Object>(runResult.getRegionEvents()));
            writeGzipLines(directory.resolve("boundary-events.ndjson.gz"),
                    new ArrayList<Object>(runResult.getBoundaryEvents()));
            writeGzipLines(directory.resolve("failures.ndjson.gz"),
                    new ArrayList<Object>(runResult.getFailures()));
            writeChecksums(directory);
            return directory;
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write region behavior report", ex);
        }
    }

    private JSONObject manifest(RegionBehaviorJobParams params, RegionGeometry region,
                                RegionBehaviorRunResult runResult) {
        JSONObject json = runResult.summaryJson();
        json.put("schemaVersion", "region_behavior_v1");
        json.put("generatedAt", Instant.now().toString());
        json.put("batchNo", params.getBatchNo());
        json.put("regionId", params.getRegionId());
        json.put("regionName", params.getRegionName());
        json.put("regionFingerprint", region.getFingerprint());
        json.put("dryRun", !params.isFormalWrite());
        json.put("storeCandidates", false);
        json.put("sourceMode", params.isFormalWrite() ? "REGION_FORMAL_EVENT" : "REGION_DRIVEN");
        json.put("eventContextEnricher", "NO_OP");
        return json;
    }

    private JSONObject queryPlan(RegionQueryPlan plan) {
        JSONObject root = new JSONObject(true);
        JSONArray slices = new JSONArray();
        for (RegionReplaySlice slice : plan.getSlices()) {
            JSONObject json = new JSONObject(true);
            json.put("sliceId", slice.getSliceId());
            json.put("cellId", slice.getCellId());
            json.put("minLon", slice.getMinLon());
            json.put("maxLon", slice.getMaxLon());
            json.put("minLat", slice.getMinLat());
            json.put("maxLat", slice.getMaxLat());
            json.put("sliceStart", RegionBehaviorTimes.format(slice.getStart()));
            json.put("sliceEnd", RegionBehaviorTimes.format(slice.getEnd()));
            slices.add(json);
        }
        root.put("querySlices", slices.size());
        root.put("slices", slices);
        return root;
    }

    private JSONObject discovery(RegionAisDiscoveryResult discovery,
                                 RegionShipMetadataSummary metadata) {
        JSONObject json = new JSONObject(true);
        json.put("status", discovery.getStatus().name());
        json.put("querySlices", discovery.getQuerySlices());
        json.put("completedSlices", discovery.getCompletedSlices());
        json.put("timeoutSlices", discovery.getTimeoutSlices());
        json.put("truncatedSlices", discovery.getTruncatedSlices());
        json.put("failedSlices", discovery.getFailedSlices());
        json.put("parseErrorSlices", discovery.getParseErrorSlices());
        json.put("rawVessels", discovery.getRawVessels());
        json.put("rawPoints", discovery.getRawPoints());
        json.put("validPoints", discovery.getValidPoints());
        json.put("uniqueVessels", discovery.getUniqueVessels());
        json.put("uniquePoints", discovery.getUniquePoints());
        json.put("duplicatePoints", discovery.getDuplicatePoints());
        json.put("discoveredVessels", metadata.getDiscoveredVessels());
        json.put("validMmsiVessels", metadata.getValidMmsiVessels());
        json.put("metadataFound", metadata.getMetadataFound());
        json.put("eligibleMainShips", metadata.getEligibleMainShips());
        json.put("excludedByType", metadata.getExcludedByType());
        json.put("unknownShipType", metadata.getUnknownShipType());
        json.put("metadataQueryFailures", metadata.getMetadataQueryFailures());
        json.put("cappedByMaxCandidates", discovery.isCappedByMaxCandidates());
        return json;
    }

    private void writeJson(Path file, JSONObject json) throws IOException {
        Files.write(file, JSON.toJSONString(json, true).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW);
    }

    private void writeGzipLines(Path file, List<Object> values) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(file,
                StandardOpenOption.CREATE_NEW));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {
            for (Object value : values) {
                writer.write(JSON.toJSONString(value));
                writer.newLine();
            }
        }
    }

    private void writeChecksums(Path directory) throws IOException {
        List<String> lines = new ArrayList<String>();
        for (String name : REPORT_FILES) {
            lines.add(sha256(directory.resolve(name)) + "  " + name);
        }
        Collections.sort(lines);
        Files.write(directory.resolve("checksums.sha256"), lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (java.io.InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder value = new StringBuilder();
            for (byte b : digest.digest()) {
                value.append(String.format("%02x", b & 0xff));
            }
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
