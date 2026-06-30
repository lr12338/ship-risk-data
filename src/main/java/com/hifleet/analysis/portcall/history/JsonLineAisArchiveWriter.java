package com.hifleet.analysis.portcall.history;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Writes archive records as bucketed JSONL files.
 */
public class JsonLineAisArchiveWriter implements Closeable {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR_FORMATTER =
        DateTimeFormatter.ofPattern("HH").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FILE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);

    private final Path baseDir;
    private final String rollover;
    private final String partitionTimeField;
    private final String writerId;

    private BufferedWriter writer;
    private Path currentFile;

    public JsonLineAisArchiveWriter(String baseDir, String rollover, String partitionTimeField) {
        this.baseDir = Paths.get(baseDir);
        this.rollover = rollover == null ? "hourly" : rollover;
        this.partitionTimeField = partitionTimeField == null ? "updatetime" : partitionTimeField;
        this.writerId = buildWriterId();
    }

    public void write(AisArchiveRecord record) throws IOException {
        Path targetFile = resolveFile(record);
        if (!targetFile.equals(currentFile)) {
            rotate(targetFile);
        }
        writer.write(record.toJsonLine());
        writer.newLine();
    }

    public void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }

    private Path resolveFile(AisArchiveRecord record) throws IOException {
        Instant time = record.resolvePartitionTime(partitionTimeField);
        Path dir = baseDir.resolve("date=" + DATE_FORMATTER.format(time));
        if (!"daily".equalsIgnoreCase(rollover)) {
            dir = dir.resolve("hour=" + HOUR_FORMATTER.format(time));
        }
        Files.createDirectories(dir);
        String suffix = "daily".equalsIgnoreCase(rollover)
            ? DATE_FORMATTER.format(time).replace("-", "")
            : FILE_FORMATTER.format(time);
        return dir.resolve("part-" + suffix + "-" + writerId + ".jsonl");
    }

    private void rotate(Path targetFile) throws IOException {
        close();
        currentFile = targetFile;
        writer = Files.newBufferedWriter(
            currentFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    private static String buildWriterId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return runtimeName.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
