package com.hifleet.behaviorplatform;

public final class BehaviorComparisonKey {
    private BehaviorComparisonKey() { }
    public static String of(BehaviorType type, String mmsi, String targetMmsi,
                            String startTime, String endTime, String ruleSetHash) {
        return BehaviorHashes.sha256(join(type, mmsi, targetMmsi, startTime, endTime, ruleSetHash));
    }
    private static String join(Object... values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) result.append(value == null ? "" : value).append('\u001f');
        return result.toString();
    }
}
