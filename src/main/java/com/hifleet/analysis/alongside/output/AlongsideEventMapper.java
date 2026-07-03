package com.hifleet.analysis.alongside.output;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;

import java.time.Instant;

public class AlongsideEventMapper implements AlongsideEventSinkMapper {

    @Override
    public String toJson(AlongsideEvent event) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append('{');
        appendString(builder, "event_id", event.getEventId()).append(',');
        appendString(builder, "event_type", event.getEventType()).append(',');
        appendString(builder, "event_sub_type", event.getEventSubType()).append(',');
        appendString(builder, "source_mode", event.getSourceMode()).append(',');
        appendString(builder, "rule_version", event.getRuleVersion()).append(',');
        appendString(builder, "pair_id", event.getPairId()).append(',');
        appendNumber(builder, "mmsi_a", event.getMmsiA()).append(',');
        appendNumber(builder, "mmsi_b", event.getMmsiB()).append(',');
        appendInstant(builder, "start_time", event.getStartTime()).append(',');
        appendInstant(builder, "end_time", event.getEndTime()).append(',');
        appendNumber(builder, "duration_minutes", event.getDurationMinutes()).append(',');
        appendNumber(builder, "start_lon", event.getStartLon()).append(',');
        appendNumber(builder, "start_lat", event.getStartLat()).append(',');
        appendNumber(builder, "end_lon", event.getEndLon()).append(',');
        appendNumber(builder, "end_lat", event.getEndLat()).append(',');
        appendNumber(builder, "center_lon", event.getCenterLon()).append(',');
        appendNumber(builder, "center_lat", event.getCenterLat()).append(',');
        appendNumber(builder, "min_distance_nm", event.getMinDistanceNm()).append(',');
        appendNumber(builder, "max_distance_nm", event.getMaxDistanceNm()).append(',');
        appendNumber(builder, "avg_distance_nm", event.getAvgDistanceNm()).append(',');
        appendNumber(builder, "avg_relative_speed", event.getAvgRelativeSpeedKnots()).append(',');
        appendNumber(builder, "heading_parallel_ratio", event.getHeadingParallelRatio()).append(',');
        appendString(builder, "pair_priority", event.getPairPriority()).append(',');
        appendString(builder, "ship_class_a", event.getShipClassA()).append(',');
        appendString(builder, "ship_class_b", event.getShipClassB()).append(',');
        appendString(builder, "severity_level", event.getSeverityLevel()).append(',');
        appendNumber(builder, "event_score", event.getEventScore()).append(',');
        appendString(builder, "confidence_level", event.getConfidenceLevel()).append(',');
        appendString(builder, "exclusion_flag", event.getExclusionFlag()).append(',');
        appendString(builder, "exclusion_reason", event.getExclusionReason()).append(',');
        appendRawJson(builder, "score_detail_json", event.getScoreDetailJson()).append(',');
        appendNumber(builder, "point_count", event.getPointCount()).append(',');
        appendNumber(builder, "ais_gap_count", event.getAisGapCount()).append(',');
        appendNumber(builder, "max_ais_gap_minutes", event.getMaxAisGapMinutes()).append(',');
        appendNumber(builder, "track_coverage_ratio", event.getTrackCoverageRatio()).append(',');
        appendInstant(builder, "emit_time", event.getEmitTime());
        builder.append('}');
        return builder.toString();
    }

    private static StringBuilder appendString(StringBuilder builder, String key, String value) {
        appendKey(builder, key);
        if (value == null) {
            builder.append("null");
        } else {
            builder.append('"').append(escape(value)).append('"');
        }
        return builder;
    }

    private static StringBuilder appendRawJson(StringBuilder builder, String key, String value) {
        appendKey(builder, key);
        if (value == null || value.trim().isEmpty()) {
            builder.append("null");
        } else {
            appendStringValue(builder, value);
        }
        return builder;
    }

    private static void appendStringValue(StringBuilder builder, String value) {
        builder.append('"').append(escape(value)).append('"');
    }

    private static StringBuilder appendInstant(StringBuilder builder, String key, Instant value) {
        return appendString(builder, key, value == null ? null : value.toString());
    }

    private static StringBuilder appendNumber(StringBuilder builder, String key, Number value) {
        appendKey(builder, key);
        if (value == null) {
            builder.append("null");
        } else {
            builder.append(value);
        }
        return builder;
    }

    private static void appendKey(StringBuilder builder, String key) {
        builder.append('"').append(key).append('"').append(':');
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }
}
