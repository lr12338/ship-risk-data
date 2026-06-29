package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.StopPointEvent;
import org.apache.flink.api.common.functions.MapFunction;

import java.time.Instant;

/**
 * Serializes stop point state events to stable JSON for Kafka output.
 */
public class StopPointJsonMapFunction implements MapFunction<StopPointEvent, String> {

    private static final long serialVersionUID = 1L;

    @Override
    public String map(StopPointEvent event) {
        return toJson(event);
    }

    public static String toJson(StopPointEvent event) {
        StringBuilder builder = new StringBuilder(512);
        builder.append('{');
        appendString(builder, "message_id", event.getMessageId()).append(',');
        appendString(builder, "event_type", name(event.getEventType())).append(',');
        appendString(builder, "db_action", event.getDbAction()).append(',');
        appendNumber(builder, "mmsi", event.getMmsi()).append(',');
        appendString(builder, "imo_number", event.getImoNumber()).append(',');
        appendString(builder, "ship_name", event.getShipName()).append(',');
        appendString(builder, "vessel_state", name(event.getVesselState())).append(',');
        appendString(builder, "stop_id", event.getStopId()).append(',');
        appendInstant(builder, "stop_start_time", event.getStopStartTime()).append(',');
        appendInstant(builder, "stable_start_time", event.getStableStartTime()).append(',');
        appendInstant(builder, "stop_end_time", event.getStopEndTime()).append(',');
        appendInstant(builder, "resume_sailing_time", event.getResumeSailingTime()).append(',');
        appendInstant(builder, "last_ais_update_time", event.getLastAisUpdateTime()).append(',');
        appendInstant(builder, "last_server_receive_time", event.getLastServerReceiveTime()).append(',');
        appendInstant(builder, "emit_time", event.getEmitTime()).append(',');
        appendNumber(builder, "stop_lon", event.getStopLon()).append(',');
        appendNumber(builder, "stop_lat", event.getStopLat()).append(',');
        appendString(builder, "position_status", name(event.getPositionStatus())).append(',');
        appendString(builder, "position_method", event.getPositionMethod()).append(',');
        appendNumber(builder, "position_point_count", event.getPositionPointCount()).append(',');
        appendNumber(builder, "swing_radius_meters", event.getSwingRadiusMeters()).append(',');
        appendNumber(builder, "duration_seconds", event.getDurationSeconds()).append(',');
        appendNumber(builder, "average_speed_knots", event.getAverageSpeedKnots()).append(',');
        appendNumber(builder, "max_speed_knots", event.getMaxSpeedKnots()).append(',');
        appendNumber(builder, "confidence", event.getConfidence()).append(',');
        appendString(builder, "data_state", event.getDataState()).append(',');
        appendString(builder, "source_mode", event.getSourceMode());
        builder.append('}');
        return builder.toString();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
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
