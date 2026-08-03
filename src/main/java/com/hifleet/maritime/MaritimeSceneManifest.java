package com.hifleet.maritime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class MaritimeSceneManifest {
    private static final String SHADOW_READY = "SHADOW_READY";
    private static final List<String> RELEASE_STATUSES;

    static {
        List<String> statuses = new ArrayList<String>();
        statuses.add("RAW_VERIFIED");
        statuses.add("GEOMETRY_VALIDATED");
        statuses.add("AUTO_CROSS_VALIDATED");
        statuses.add(SHADOW_READY);
        statuses.add("ENFORCE_CANDIDATE");
        statuses.add("ENFORCE_READY");
        RELEASE_STATUSES = Collections.unmodifiableList(statuses);
    }

    private final Path releaseRoot;
    private final JSONObject json;
    private final String releaseVersion;
    private final String snapshotFingerprint;
    private final String releaseStatus;
    private final String policyVersion;

    private MaritimeSceneManifest(
            Path releaseRoot,
            JSONObject json,
            String releaseVersion,
            String snapshotFingerprint,
            String releaseStatus,
            String policyVersion) {
        this.releaseRoot = releaseRoot;
        this.json = json;
        this.releaseVersion = releaseVersion;
        this.snapshotFingerprint = snapshotFingerprint;
        this.releaseStatus = releaseStatus;
        this.policyVersion = policyVersion;
    }

    public static MaritimeSceneManifest read(
            Path releaseRoot, String expectedFingerprint, String expectedPolicyVersion) {
        Path root = releaseRoot.toAbsolutePath().normalize();
        Path manifestPath = root.resolve("manifest.json");
        if (!Files.exists(manifestPath)) throw new IllegalStateException("manifest.json is missing");
        try {
            JSONObject json = JSON.parseObject(
                    new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8));
            String releaseVersion = trim(json.getString("releaseVersion"));
            String fingerprint = trim(json.getString("snapshotFingerprint"));
            String status = trim(json.getString("releaseStatus"));
            JSONObject validation = json.getJSONObject("validationSummary");
            if (status == null && validation != null) status = trim(validation.getString("releaseStatus"));
            if (status == null && validation != null) status = trim(validation.getString("status"));
            String policyVersion = trim(json.getString("policyVersion"));
            if (policyVersion == null && validation != null) policyVersion = trim(validation.getString("policyVersion"));
            if (releaseVersion == null || fingerprint == null || fingerprint.length() != 64) {
                throw new IllegalStateException("manifest release version or fingerprint is missing");
            }
            if (status == null || rank(status) < rank(SHADOW_READY)) {
                throw new IllegalStateException("release status must be at least SHADOW_READY");
            }
            if (expectedFingerprint != null && !expectedFingerprint.trim().isEmpty()
                    && !expectedFingerprint.equalsIgnoreCase(fingerprint)) {
                throw new IllegalStateException("snapshot fingerprint does not match expected fingerprint");
            }
            if (expectedPolicyVersion != null && !expectedPolicyVersion.trim().isEmpty()
                    && !expectedPolicyVersion.equals(policyVersion)) {
                throw new IllegalStateException("scene policy version does not match expected policy");
            }
            if (policyVersion == null) throw new IllegalStateException("scene policy version is missing");
            JSONObject fingerprintInputs = json.getJSONObject("fingerprintInputs");
            if (fingerprintInputs == null || fingerprintInputs.getJSONArray("tiles") == null) {
                throw new IllegalStateException("manifest fingerprint inputs are incomplete");
            }
            verifyChecksums(root);
            verifyTileFiles(root);
            return new MaritimeSceneManifest(root, json, releaseVersion, fingerprint, status, policyVersion);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read maritime scene manifest", ex);
        }
    }

    private static void verifyTileFiles(Path root) throws IOException {
        boolean scene = false;
        boolean coast = false;
        try (Stream<Path> paths = Files.list(root.resolve("tiles"))) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String name = path.getFileName().toString();
                if (name.endsWith(".scene.ndjson.gz")) scene = true;
                if (name.endsWith(".coast.ndjson.gz")) coast = true;
                if (!(name.endsWith(".scene.ndjson.gz") || name.endsWith(".coast.ndjson.gz"))) {
                    throw new IllegalStateException("unsupported tile format: " + name);
                }
            }
        }
        if (!scene || !coast) throw new IllegalStateException("required scene/coast tiles are missing");
    }

    private static void verifyChecksums(Path root) throws IOException {
        Path checksumPath = root.resolve("checksums.sha256");
        if (!Files.exists(checksumPath)) throw new IllegalStateException("checksums.sha256 is missing");
        try (BufferedReader reader = Files.newBufferedReader(checksumPath, StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int separator = line.indexOf("  ");
                if (separator <= 0) throw new IllegalStateException("invalid checksum line");
                String expected = line.substring(0, separator).trim();
                String relative = line.substring(separator + 2).trim();
                Path path = root.resolve(relative).normalize();
                if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                    throw new IllegalStateException("checksum target is missing: " + relative);
                }
                if (!expected.equalsIgnoreCase(sha256(path))) {
                    throw new IllegalStateException("checksum mismatch: " + relative);
                }
                count++;
            }
            if (count == 0) throw new IllegalStateException("checksums.sha256 is empty");
        }
    }

    private static int rank(String status) {
        int index = RELEASE_STATUSES.indexOf(status);
        return index < 0 ? -1 : index;
    }

    private static String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            try (java.io.InputStream input = Files.newInputStream(path)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public Map<String, Object> preflight() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("releaseVersion", releaseVersion);
        result.put("snapshotFingerprint", snapshotFingerprint);
        result.put("releaseStatus", releaseStatus);
        result.put("policyVersion", policyVersion);
        result.put("checksumsVerified", true);
        result.put("tileFormat", "ndjson.gz");
        return result;
    }

    public Path getReleaseRoot() { return releaseRoot; }
    public JSONObject getJson() { return json; }
    public String getReleaseVersion() { return releaseVersion; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public String getReleaseStatus() { return releaseStatus; }
    public String getPolicyVersion() { return policyVersion; }
}
