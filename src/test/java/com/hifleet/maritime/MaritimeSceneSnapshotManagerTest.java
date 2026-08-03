package com.hifleet.maritime;

import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.junit.Assume;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class MaritimeSceneSnapshotManagerTest {
    @Test
    public void lruEvictsLeastRecentlyUsedTile() {
        MaritimeSceneTileCache cache = new MaritimeSceneTileCache(2);
        final MaritimeSceneTileLoader loader = new MaritimeSceneTileLoader(
                java.nio.file.Paths.get("target"), 2);
        cache.getOrLoad("one", new MaritimeSceneTileCache.Loader<MaritimeSceneTileLoader.TileData>() {
            @Override public MaritimeSceneTileLoader.TileData load(String tileId) { return loader.load(tileId); }
        });
        cache.getOrLoad("two", new MaritimeSceneTileCache.Loader<MaritimeSceneTileLoader.TileData>() {
            @Override public MaritimeSceneTileLoader.TileData load(String tileId) { return loader.load(tileId); }
        });
        cache.get("one");
        cache.getOrLoad("three", new MaritimeSceneTileCache.Loader<MaritimeSceneTileLoader.TileData>() {
            @Override public MaritimeSceneTileLoader.TileData load(String tileId) { return loader.load(tileId); }
        });
        assertEquals(2, cache.size());
        if (cache.get("two") != null) throw new AssertionError("least recently used tile was not evicted");
    }

    @Test
    public void realShanghaiReleaseCanBeLoadedWhenPresent() throws Exception {
        Path root = java.nio.file.Paths.get("var/maritime-scene-data/releases/phase1-smoke-shanghai-autocross-v1");
        Assume.assumeTrue(Files.exists(root));
        JSONObject manifest = JSONObject.parseObject(new String(
                Files.readAllBytes(root.resolve("manifest.json")), StandardCharsets.UTF_8));
        MaritimeSceneSnapshotManager manager = new MaritimeSceneSnapshotManager(
                root, manifest.getString("snapshotFingerprint"), MaritimeScenePolicy.VERSION, 4);
        assertTrue(manager.load());
        MaritimeSceneService service = new MaritimeSceneService(manager,
                MaritimeScenePolicy.defaults("SHADOW"));
        MaritimeSceneDecision decision = service.classify(121.0, 31.0);
        if (decision.getSceneClass() == MaritimeSceneClass.SCENE_UNKNOWN) {
            throw new AssertionError("real Release returned SCENE_UNKNOWN: "
                    + decision.getEvidence().getAttributes());
        }
    }

    @Test
    public void shadowReadyManifestAndChecksumsAreAccepted() throws Exception {
        Path root = fixture("SHADOW_READY");
        MaritimeSceneSnapshotManager manager = new MaritimeSceneSnapshotManager(
                root, fingerprint(), MaritimeScenePolicy.VERSION, 2);
        assertTrue(manager.load());
        assertTrue(manager.isAvailable());
        assertEquals("fixture-release", manager.getManifest().getReleaseVersion());
    }

    @Test
    public void statusFingerprintAndChecksumFailuresFailClosed() throws Exception {
        Path statusRoot = fixture("AUTO_CROSS_VALIDATED");
        assertFalse(new MaritimeSceneSnapshotManager(
                statusRoot, fingerprint(), MaritimeScenePolicy.VERSION, 2).load());

        Path fingerprintRoot = fixture("SHADOW_READY");
        assertFalse(new MaritimeSceneSnapshotManager(
                fingerprintRoot, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                MaritimeScenePolicy.VERSION, 2).load());

        Path checksumRoot = fixture("SHADOW_READY");
        Files.write(checksumRoot.resolve("tiles/N31_E121.scene.ndjson.gz"),
                new byte[] {1, 2, 3});
        assertFalse(new MaritimeSceneSnapshotManager(
                checksumRoot, fingerprint(), MaritimeScenePolicy.VERSION, 2).load());
    }

    private Path fixture(String status) throws Exception {
        Path root = Files.createTempDirectory("maritime-scene-fixture");
        Files.createDirectories(root.resolve("tiles"));
        gzip(root.resolve("tiles/N31_E121.scene.ndjson.gz"), "");
        gzip(root.resolve("tiles/N31_E121.coast.ndjson.gz"), "");
        JSONObject validation = new JSONObject();
        validation.put("releaseStatus", status);
        validation.put("policyVersion", MaritimeScenePolicy.VERSION);
        JSONObject fingerprintInputs = new JSONObject();
        fingerprintInputs.put("tiles", Arrays.asList("N31_E121.scene.ndjson.gz"));
        JSONObject manifest = new JSONObject();
        manifest.put("schemaVersion", 1);
        manifest.put("releaseVersion", "fixture-release");
        manifest.put("snapshotFingerprint", fingerprint());
        manifest.put("fingerprintInputs", fingerprintInputs);
        manifest.put("validationSummary", validation);
        Files.write(root.resolve("manifest.json"),
                (manifest.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
        String checksums = sha256(root.resolve("manifest.json")) + "  manifest.json\n"
                + sha256(root.resolve("tiles/N31_E121.scene.ndjson.gz"))
                + "  tiles/N31_E121.scene.ndjson.gz\n"
                + sha256(root.resolve("tiles/N31_E121.coast.ndjson.gz"))
                + "  tiles/N31_E121.coast.ndjson.gz\n";
        Files.write(root.resolve("checksums.sha256"), checksums.getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static void gzip(Path path, String value) throws IOException {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sha256(Path path) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static String fingerprint() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
}
