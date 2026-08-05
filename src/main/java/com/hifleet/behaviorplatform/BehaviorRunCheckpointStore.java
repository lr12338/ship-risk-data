package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BehaviorRunCheckpointStore {
    private final Map<String, BehaviorProgressRecord> values = new LinkedHashMap<String, BehaviorProgressRecord>();
    public void load(Path directory, String parameterHash, boolean resume) {
        if (!Files.exists(directory)) return;
        Path manifest = directory.resolve("manifest.json");
        if (Files.exists(manifest)) try {
            JSONObject value = JSON.parseObject(new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8));
            if (!parameterHash.equals(value.getString("parameterHash")))
                throw new IllegalArgumentException("resume parameter hash mismatch");
        } catch (IOException ex) { throw new IllegalStateException("cannot read checkpoint manifest", ex); }
        if (!resume) throw new IllegalArgumentException("run directory already exists; use resume=true");
        Path progress = directory.resolve("progress.ndjson");
        if (!Files.exists(progress)) return;
        try {
            for (String line : Files.readAllLines(progress, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) continue;
                JSONObject json = JSON.parseObject(line);
                BehaviorProgressRecord record = new BehaviorProgressRecord(json.getString("key"),
                        json.getString("status"), json.getIntValue("attempt"));
                values.put(record.getKey(), record);
            }
        } catch (IOException ex) { throw new IllegalStateException("cannot read progress checkpoint", ex); }
    }
    public boolean skipSuccess(String key) {
        BehaviorProgressRecord value = values.get(key);
        return value != null && "SUCCESS".equals(value.getStatus());
    }
    public int nextAttempt(String key) {
        BehaviorProgressRecord value = values.get(key);
        return value == null ? 1 : value.getAttempt() + 1;
    }
    public void record(String key, String status, int attempt) { values.put(key, new BehaviorProgressRecord(key, status, attempt)); }
    public java.util.List<BehaviorProgressRecord> records() { return new java.util.ArrayList<BehaviorProgressRecord>(values.values()); }
}
