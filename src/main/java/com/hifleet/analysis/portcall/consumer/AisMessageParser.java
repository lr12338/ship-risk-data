package com.hifleet.analysis.portcall.consumer;

import com.hifleet.analysis.portcall.model.AisPoint;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses one raw message into {@link AisPoint} with tolerant fallback.
 *
 * Primary Kafka payload format from flink_aisdata_topic:
 * one JSON object compatible with hifleet FlinkedAisMessageStruct.
 *
 * Compatibility payload format:
 * JSON array of AISMsgStruct/FlinkedAisMessageStruct objects.
 *
 * Compatibility row format, produced by hifleet.filter.south.china.sea after filtering:
 * mmsi;updatetime;lon;lat;course;speed;sourceid;heading;rot;messageid;status;
 * staticInfoUpdatetime;eta;destination;draught;name;imonumber;callsign;type;minotype;
 * length;width;offseta;offsetb;offsetc;offsetd;flag;ais_shiptype
 *
 * Preferred payload format:
 * mmsi,updatetime,server_receive_time,lon,lat,speed,course,heading,navigation_status,
 * data_source_id,message_id,draught,eta,destination,ship_name,callsign,imonumber,ship_length,ship_width,data_state
 *
 * The legacy ten-column format is still accepted:
 * mmsi,updatetime,server_receive_time,lon,lat,speed,course,heading,data_source_id,message_id
 */
public class AisMessageParser {

    private static final DateTimeFormatter SQL_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * Parses one payload line into AisPoint.
     *
     * @param payload raw payload line
     * @return parsed point, or null when payload is invalid
     */
    public AisPoint parse(String payload) {
        List<AisPoint> points = parseMany(payload);
        return points.isEmpty() ? null : points.get(0);
    }

    /**
     * Parses one Kafka record value into zero or more AIS points.
     *
     * @param payload raw Kafka record value
     * @return parsed points
     */
    public List<AisPoint> parseMany(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String trimmed = payload.trim();
        if (trimmed.startsWith("[")) {
            return parseJsonArray(trimmed);
        }
        if (trimmed.startsWith("{")) {
            AisPoint point = parseJsonObject(trimmed);
            return point == null ? Collections.<AisPoint>emptyList() : Collections.singletonList(point);
        }

        AisPoint single = parseLine(trimmed);
        if (single == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(single);
    }

    private List<AisPoint> parseJsonArray(String payload) {
        List<AisPoint> points = new ArrayList<AisPoint>();
        try {
            JSONArray array = JSON.parseArray(payload);
            if (array == null || array.isEmpty()) {
                return points;
            }

            for (Object item : array) {
                AisPoint point;
                if (item instanceof JSONObject) {
                    point = parseLiveRecentShipsObject((JSONObject) item);
                } else {
                    point = parseSouthChinaSeaOutputRow(String.valueOf(item));
                }
                if (point != null) {
                    points.add(point);
                }
            }
            return points;
        } catch (RuntimeException ex) {
            List<String> rows = parseJsonStringArray(payload);
            for (String row : rows) {
                AisPoint point = parseSouthChinaSeaOutputRow(row);
                if (point != null) {
                    points.add(point);
                }
            }
            return points;
        }
    }

    private AisPoint parseJsonObject(String payload) {
        try {
            return parseLiveRecentShipsObject(JSON.parseObject(payload));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private AisPoint parseLiveRecentShipsObject(JSONObject object) {
        if (object == null) {
            return null;
        }
        try {
            AisPoint point = new AisPoint();
            point.setMmsi(toLong(object.get("mmsi")));
            point.setUpdateTime(parseInstant(object.getString("updatetime")));
            point.setServerReceiveTime(parseServerReceiveTime(object));
            point.setLon(toDouble(object.get("lon")));
            point.setLat(toDouble(object.get("lat")));
            point.setSpeed(toDouble(object.get("speed")));
            point.setCourse(toDouble(object.get("course")));
            point.setHeading(toDouble(object.get("heading")));
            point.setNavigationStatus(parseNavigationStatus(object.get("status")));
            point.setDataSourceId(toStringValue(object.get("sourceid")));
            point.setMessageId(toStringValue(object.get("messageid")));
            point.setDataState(firstStringValue(object, "data_state", "dataState"));
            point.setDraught(toDouble(object.get("draught")));
            point.setEta(parseInstantLenient(object.getString("eta")));
            point.setDestination(blankToNull(object.getString("destination")));
            point.setShipName(blankToNull(object.getString("name")));
            point.setCallSign(blankToNull(object.getString("callsign")));
            point.setImoNumber(imoString(toStringValue(object.get("imonumber"))));
            point.setShipType(toInteger(object.get("ais_shiptype")));
            point.setShipLength(toDouble(object.get("length")));
            point.setShipWidth(toDouble(object.get("width")));
            return point;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private AisPoint parseLine(String payload) {
        if (payload.indexOf(';') >= 0) {
            return parseSouthChinaSeaOutputRow(payload);
        }
        return parseCsvLine(payload);
    }

    private AisPoint parseSouthChinaSeaOutputRow(String row) {
        if (row == null || row.trim().isEmpty()) {
            return null;
        }

        String[] tokens = row.split(";", -1);
        if (tokens.length < 22) {
            return null;
        }

        try {
            AisPoint point = new AisPoint();
            point.setMmsi(parseLong(tokens[0]));
            point.setUpdateTime(parseInstant(tokens[1]));
            point.setLon(parseDouble(tokens[2]));
            point.setLat(parseDouble(tokens[3]));
            point.setCourse(parseDouble(tokens[4]));
            point.setSpeed(parseDouble(tokens[5]));
            point.setDataSourceId(blankToNull(tokens[6]));
            point.setHeading(parseDouble(tokens[7]));
            point.setMessageId(blankToNull(tokens[9]));
            point.setDataState(stringAt(tokens, 28));
            point.setEta(parseInstantLenient(tokens[12]));
            point.setDestination(stringAt(tokens, 13));
            point.setDraught(parseDoubleAt(tokens, 14));
            point.setShipName(stringAt(tokens, 15));
            point.setImoNumber(imoString(tokens[16]));
            point.setCallSign(stringAt(tokens, 17));
            point.setShipType(parseIntegerAt(tokens, 27));
            point.setShipLength(parseDoubleAt(tokens, 20));
            point.setShipWidth(parseDoubleAt(tokens, 21));
            return point;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private AisPoint parseCsvLine(String payload) {
        String[] tokens = payload.split(",", -1);
        if (tokens.length < 10) {
            return null;
        }

        try {
            AisPoint point = new AisPoint();
            point.setMmsi(parseLong(tokens[0]));
            point.setUpdateTime(parseInstant(tokens[1]));
            point.setServerReceiveTime(parseInstant(tokens[2]));
            point.setLon(parseDouble(tokens[3]));
            point.setLat(parseDouble(tokens[4]));
            point.setSpeed(parseDouble(tokens[5]));
            point.setCourse(parseDouble(tokens[6]));
            point.setHeading(parseDouble(tokens[7]));
            if (tokens.length >= 11) {
                point.setNavigationStatus(parseInteger(tokens[8]));
                point.setDataSourceId(blankToNull(tokens[9]));
                point.setMessageId(blankToNull(tokens[10]));
                point.setDraught(parseDoubleAt(tokens, 11));
                point.setEta(parseInstantAt(tokens, 12));
                point.setDestination(stringAt(tokens, 13));
                point.setShipName(stringAt(tokens, 14));
                point.setCallSign(stringAt(tokens, 15));
                point.setImoNumber(stringAt(tokens, 16));
                point.setShipLength(parseDoubleAt(tokens, 17));
                point.setShipWidth(parseDoubleAt(tokens, 18));
                point.setDataState(stringAt(tokens, 19));
            } else {
                point.setDataSourceId(blankToNull(tokens[8]));
                point.setMessageId(blankToNull(tokens[9]));
            }
            return point;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<String> parseJsonStringArray(String payload) {
        List<String> rows = new ArrayList<String>();
        boolean inString = false;
        boolean escaping = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < payload.length(); i++) {
            char ch = payload.charAt(i);
            if (!inString) {
                if (ch == '"') {
                    inString = true;
                    current.setLength(0);
                }
                continue;
            }

            if (escaping) {
                appendEscaped(current, ch);
                escaping = false;
                continue;
            }

            if (ch == '\\') {
                escaping = true;
                continue;
            }

            if (ch == '"') {
                inString = false;
                rows.add(current.toString());
                continue;
            }

            current.append(ch);
        }
        return rows;
    }

    private void appendEscaped(StringBuilder builder, char ch) {
        if (ch == 'n') {
            builder.append('\n');
        } else if (ch == 'r') {
            builder.append('\r');
        } else if (ch == 't') {
            builder.append('\t');
        } else {
            builder.append(ch);
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Long.valueOf(value.trim());
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Double.valueOf(value.trim());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return parseLong(String.valueOf(value));
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return parseDouble(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return parseInteger(String.valueOf(value));
    }

    private Double parseDoubleAt(String[] tokens, int index) {
        if (index >= tokens.length) {
            return null;
        }
        return parseDouble(tokens[index]);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }

    private Integer parseIntegerAt(String[] tokens, int index) {
        if (index >= tokens.length) {
            return null;
        }
        return parseInteger(tokens[index]);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            } catch (DateTimeParseException ignoredOffset) {
                // fall back to AIS SQL timestamps without an explicit zone
            }
            return LocalDateTime.parse(trimmed.substring(0, Math.min(19, trimmed.length())), SQL_TIMESTAMP_FORMATTER)
                .atZone(DEFAULT_TIME_ZONE)
                .toInstant();
        }
    }

    private Instant parseInstantLenient(String value) {
        try {
            return parseInstant(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Instant parseServerReceiveTime(JSONObject object) {
        String textValue = firstStringValue(object, "server_receive_time", "serverReceiveTime");
        if (textValue != null) {
            Instant parsed = parseInstantLenient(textValue);
            if (parsed != null) {
                return parsed;
            }
        }

        Long numericValue = toLong(object.get("servertime"));
        if (numericValue == null) {
            return null;
        }
        if (numericValue > 100000000000L) {
            return Instant.ofEpochMilli(numericValue);
        }
        return Instant.ofEpochSecond(numericValue);
    }

    private Instant parseInstantAt(String[] tokens, int index) {
        if (index >= tokens.length) {
            return null;
        }
        return parseInstant(tokens[index]);
    }

    private String stringAt(String[] tokens, int index) {
        if (index >= tokens.length) {
            return null;
        }
        return blankToNull(tokens[index]);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        return blankToNull(String.valueOf(value));
    }

    private String firstStringValue(JSONObject object, String primaryKey, String secondaryKey) {
        String primary = toStringValue(object.get(primaryKey));
        if (primary != null) {
            return primary;
        }
        return toStringValue(object.get(secondaryKey));
    }

    private Integer parseNavigationStatus(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String imoString(String value) {
        String normalized = blankToNull(value);
        if (normalized == null || "0".equals(normalized)) {
            return null;
        }
        return normalized;
    }
}
