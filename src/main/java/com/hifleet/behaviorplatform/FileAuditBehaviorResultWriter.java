package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FileAuditBehaviorResultWriter implements BehaviorResultWriter {
    private final Path root;
    public FileAuditBehaviorResultWriter(Path root) { this.root = root; }
    public WriteMode mode() { return WriteMode.FILE_AUDIT; }

    public synchronized int write(BehaviorRunManifest manifest, List<BehaviorAuditResult> results) {
        return write(manifest, results, java.util.Collections.<BehaviorFailureRecord>emptyList(),
                java.util.Collections.<BehaviorProgressRecord>emptyList());
    }

    public synchronized int write(BehaviorRunManifest manifest, List<BehaviorAuditResult> results,
                                  List<BehaviorFailureRecord> failures,
                                  List<BehaviorProgressRecord> progress) {
        Path directory = outputDirectory(manifest);
        try {
            Files.createDirectories(directory);
            Map<String, BehaviorAuditResult> merged = new LinkedHashMap<String, BehaviorAuditResult>();
            for (BehaviorAuditResult value : readResults(directory)) merged.put(value.getComparisonKey(), value);
            for (BehaviorAuditResult value : results) merged.put(value.getComparisonKey(), value);
            writeAtomic(directory.resolve("results.ndjson"), ndjson(merged.values()));
            writeAtomic(directory.resolve("manifest.json"),
                    JSON.toJSONString(manifest, true).getBytes(StandardCharsets.UTF_8));
            writeAtomic(directory.resolve("resolved-profile.json"),
                    manifest.getResolvedProfileSnapshot().getBytes(StandardCharsets.UTF_8));
            writeAtomic(directory.resolve("resolved-rules.json"),
                    (manifest.getResolvedRulesSnapshot() == null ? "{}" : manifest.getResolvedRulesSnapshot())
                            .getBytes(StandardCharsets.UTF_8));
            writeAtomic(directory.resolve("progress.ndjson"), ndjsonObjects(progress));
            writeAtomic(directory.resolve("failures.ndjson"), ndjsonObjects(failures));
            writeChecksums(directory);
            return results.size();
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write behavior file audit", ex);
        }
    }

    public synchronized void writeManifestOnly(BehaviorRunManifest manifest) {
        Path directory = outputDirectory(manifest);
        try {
            Files.createDirectories(directory);
            writeAtomic(directory.resolve("manifest.json"),
                    JSON.toJSONString(manifest, true).getBytes(StandardCharsets.UTF_8));
            writeAtomic(directory.resolve("resolved-profile.json"),
                    manifest.getResolvedProfileSnapshot().getBytes(StandardCharsets.UTF_8));
            if (manifest.getResolvedRulesSnapshot() != null) writeAtomic(directory.resolve("resolved-rules.json"),
                    manifest.getResolvedRulesSnapshot().getBytes(StandardCharsets.UTF_8));
            writeChecksums(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write behavior run manifest", ex);
        }
    }

    public Path outputDirectory(BehaviorRunManifest manifest) {
        return root.resolve(safe(manifest.getBatchNo())).resolve(safe(manifest.getProfileId()))
                .resolve(safe(manifest.getProfileHash()));
    }

    public List<BehaviorAuditResult> readResults(Path directory) {
        List<BehaviorAuditResult> result = new ArrayList<BehaviorAuditResult>();
        Path file = directory.resolve("results.ndjson");
        if (!Files.exists(file)) return result;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) continue;
                JSONObject value = JSON.parseObject(line);
                result.add(new BehaviorAuditResult(value.getString("comparisonKey"), value.getString("mmsi"),
                        value.getString("targetMmsi"), value.getString("startTime"), value.getString("endTime"),
                        value.getBooleanValue("controlAccepted"), value.getString("sceneDecision"),
                        value.getString("filteredDisposition"), null));
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read behavior audit results", ex);
        }
    }

    private byte[] ndjson(Iterable<BehaviorAuditResult> values) {
        StringBuilder result = new StringBuilder();
        for (BehaviorAuditResult value : values) result.append(JSON.toJSONString(value)).append('\n');
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] ndjsonObjects(Iterable<?> values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) result.append(JSON.toJSONString(value)).append('\n');
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeChecksums(Path directory) throws IOException {
        StringBuilder value = new StringBuilder();
        for (String name : new String[]{"manifest.json", "resolved-profile.json", "resolved-rules.json",
                "progress.ndjson", "results.ndjson", "failures.ndjson"}) {
            if (!Files.exists(directory.resolve(name))) continue;
            byte[] bytes = Files.readAllBytes(directory.resolve(name));
            value.append(BehaviorHashes.sha256(new String(bytes, StandardCharsets.UTF_8)))
                    .append("  ").append(name).append('\n');
        }
        writeAtomic(directory.resolve("checksums.sha256"), value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void writeAtomic(Path target, byte[] content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.write(temporary, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("unsafe audit path segment");
        return value;
    }
}
