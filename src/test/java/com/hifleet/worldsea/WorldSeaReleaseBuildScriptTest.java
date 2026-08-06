package com.hifleet.worldsea;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WorldSeaReleaseBuildScriptTest {
    @Test
    public void sourceCheckAcceptsCleanRemoteMatchedTreeAndRejectsDirtyOrDivergedTree() throws Exception {
        Path root = Files.createTempDirectory("world-sea-release-script");
        Path remote = root.resolve("remote.git");
        Path repo = root.resolve("repo");
        run(root, "git", "init", "--bare", remote.toString());
        run(root, "git", "init", repo.toString());
        run(repo, "git", "config", "user.email", "test@example.com");
        run(repo, "git", "config", "user.name", "Test");
        Files.write(repo.resolve("tracked.txt"), Arrays.asList("one"), StandardCharsets.UTF_8);
        run(repo, "git", "add", "tracked.txt"); run(repo, "git", "commit", "-m", "one");
        run(repo, "git", "branch", "-M", "codex/world-sea-mask-v1-hardening");
        run(repo, "git", "remote", "add", "github", remote.toString());
        run(repo, "git", "push", "-u", "github", "codex/world-sea-mask-v1-hardening");

        Path script = java.nio.file.Paths.get("scripts/build_world_sea_release.sh").toAbsolutePath();
        Result clean = execute(repo, script.toString(), "--verify-source-only", repo.toString(),
                "github", "codex/world-sea-mask-v1-hardening");
        assertEquals(clean.output, 0, clean.exitCode);
        assertTrue(clean.output.contains("gitDirty=false"));

        Files.write(repo.resolve("tracked.txt"), Arrays.asList("dirty"), StandardCharsets.UTF_8);
        assertNotEquals(0, execute(repo, script.toString(), "--verify-source-only", repo.toString(),
                "github", "codex/world-sea-mask-v1-hardening").exitCode);
        run(repo, "git", "checkout", "--", "tracked.txt");

        Files.write(repo.resolve("tracked.txt"), Arrays.asList("two"), StandardCharsets.UTF_8);
        run(repo, "git", "add", "tracked.txt"); run(repo, "git", "commit", "-m", "two");
        assertNotEquals(0, execute(repo, script.toString(), "--verify-source-only", repo.toString(),
                "github", "codex/world-sea-mask-v1-hardening").exitCode);
    }

    private void run(Path directory, String... command) throws Exception {
        Result result = execute(directory, command);
        if (result.exitCode != 0) throw new AssertionError(result.output);
    }

    private Result execute(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).start();
        byte[] output = read(process);
        return new Result(process.waitFor(), new String(output, StandardCharsets.UTF_8));
    }

    private byte[] read(Process process) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int count;
        while ((count = process.getInputStream().read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static final class Result {
        final int exitCode; final String output;
        Result(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
    }
}
