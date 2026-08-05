package com.hifleet.behaviorplatform;

import java.time.Instant;

public final class BehaviorProgressRecord {
    private final String key;
    private final String status;
    private final int attempt;
    private final String timestamp;
    public BehaviorProgressRecord(String key, String status, int attempt) {
        this.key = key; this.status = status; this.attempt = attempt; this.timestamp = Instant.now().toString();
    }
    public String getKey() { return key; }
    public String getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public String getTimestamp() { return timestamp; }
}
