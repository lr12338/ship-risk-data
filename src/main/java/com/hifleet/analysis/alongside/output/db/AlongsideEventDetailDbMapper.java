package com.hifleet.analysis.alongside.output.db;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.io.Serializable;

public class AlongsideEventDetailDbMapper implements Serializable {

    private static final long serialVersionUID = 1L;

    public String insertSql(String tableName) {
        return "INSERT INTO " + tableName + " ("
            + "event_id, behavior_type, pair_id, mmsi, target_mmsi, point_role, "
            + "event_start_time, event_end_time, point_time, point_index, lon, lat, speed, course, heading, nav_status, "
            + "paired_point_time, time_gap_seconds, distance_nm, relative_speed, heading_diff, match_confidence"
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    public void bindOwn(PreparedStatement statement, AlongsideEvent event, PairProximityEvidence evidence, int pointIndex)
        throws SQLException {
        bind(statement, event, evidence, pointIndex, "OWN",
            evidence.getMmsiA(), evidence.getMmsiB(), evidence.getPointATime(), evidence.getPointBTime(),
            evidence.getLonA(), evidence.getLatA(), evidence.getSpeedA(), evidence.getCourseA(),
            evidence.getNavigationStatusA());
    }

    public void bindTarget(PreparedStatement statement, AlongsideEvent event, PairProximityEvidence evidence, int pointIndex)
        throws SQLException {
        bind(statement, event, evidence, pointIndex, "TARGET",
            evidence.getMmsiB(), evidence.getMmsiA(), evidence.getPointBTime(), evidence.getPointATime(),
            evidence.getLonB(), evidence.getLatB(), evidence.getSpeedB(), evidence.getCourseB(),
            evidence.getNavigationStatusB());
    }

    private void bind(PreparedStatement statement, AlongsideEvent event, PairProximityEvidence evidence, int pointIndex,
                      String role, Long mmsi, Long targetMmsi, Instant pointTime, Instant pairedPointTime,
                      Double lon, Double lat, Double speed, Double course, Integer navStatus) throws SQLException {
        int index = 1;
        statement.setString(index++, AlongsideEventIdGenerator.ensureEventId(event));
        statement.setString(index++, "ALONGSIDE");
        statement.setString(index++, event.getPairId());
        statement.setString(index++, text(mmsi));
        statement.setString(index++, text(targetMmsi));
        statement.setString(index++, role);
        statement.setTimestamp(index++, timestamp(event.getStartTime()));
        statement.setTimestamp(index++, timestamp(event.getEndTime()));
        statement.setTimestamp(index++, timestamp(pointTime));
        statement.setInt(index++, pointIndex);
        setDouble(statement, index++, lon);
        setDouble(statement, index++, lat);
        setDouble(statement, index++, speed);
        setDouble(statement, index++, course);
        statement.setObject(index++, null);
        statement.setString(index++, navStatus == null ? null : String.valueOf(navStatus));
        statement.setTimestamp(index++, timestamp(pairedPointTime));
        setDouble(statement, index++, evidence.getTimeGapSeconds() == null ? null : evidence.getTimeGapSeconds().doubleValue());
        setDouble(statement, index++, evidence.getDistanceNm());
        setDouble(statement, index++, evidence.getRelativeSpeedKnots());
        setDouble(statement, index++, evidence.getHeadingParallelDiff());
        statement.setString(index, "PAIR_NEAREST_TIME_DISTANCE");
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String text(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void setDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setDouble(index, value.doubleValue());
        }
    }
}
