package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

public final class AlongsideEventIdGenerator {

    private AlongsideEventIdGenerator() {
    }

    public static String generate(AlongsideEvent event) {
        String source = text(event.getRuleVersion()) + "|"
            + text(event.getPairId()) + "|"
            + text(event.getEventSubType()) + "|"
            + instant(event.getStartTime()) + "|"
            + instant(event.getEndTime());
        return md5(source);
    }

    public static String ensureEventId(AlongsideEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            event.setEventId(generate(event));
        }
        return event.getEventId();
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate alongside event id", ex);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }
}
