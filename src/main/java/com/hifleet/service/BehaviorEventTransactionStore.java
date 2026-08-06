package com.hifleet.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorEventDetail;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectoryUtils;
import com.hifleet.worldsea.WorldSeaEventGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BehaviorEventTransactionStore {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("newDataanalysisDataSource")
    private DataSource dataSource;

    @Autowired
    private ShipBehaviorEventRepository eventRepository;

    @Autowired
    private ShipBehaviorEventDetailRepository detailRepository;

    @Autowired
    private WorldSeaEventGate worldSeaEventGate;

    public void replace(final String behaviorType, final String batchNo,
                        final String mmsi, final String startTime, final String endTime,
                        final List<String> ruleVersions,
                        final List<ShipBehaviorTrace> traces,
                        final boolean storeDetails) {
        replace(behaviorType, batchNo, mmsi, startTime, endTime, ruleVersions,
                traces, storeDetails, null);
    }

    public void replace(final String behaviorType, final String batchNo,
                        final String mmsi, final String startTime, final String endTime,
                        final List<String> ruleVersions,
                        final List<ShipBehaviorTrace> traces,
                        final boolean storeDetails, final Runnable afterSave) {
        final List<ShipBehaviorTrace> safeTraces = traces == null
                ? new ArrayList<ShipBehaviorTrace>() : traces;
        if (worldSeaEventGate != null) worldSeaEventGate.requirePassed(safeTraces);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
            Set<String> versions = new HashSet<String>();
            if (ruleVersions != null) {
                versions.addAll(ruleVersions);
            }
            for (ShipBehaviorTrace trace : safeTraces) {
                if (trace != null && trace.getEvent() != null
                        && trace.getEvent().getRuleVersion() != null) {
                    versions.add(trace.getEvent().getRuleVersion());
                }
            }
            for (String version : versions) {
                deleteEvents(behaviorType, batchNo, mmsi, version, startTime, endTime);
                if (storeDetails) {
                    deleteDetails(behaviorType, batchNo, mmsi, version, startTime, endTime);
                }
            }
            eventRepository.saveEvents(events(safeTraces));
            if (storeDetails) {
                detailRepository.saveDetails(details(safeTraces, batchNo));
            }
            if (afterSave != null) afterSave.run();
            return null;
        });
    }

    private void deleteEvents(String behaviorType, String batchNo, String mmsi,
                              String ruleVersion, String startTime, String endTime) {
        jdbcTemplate.update("DELETE FROM dataanalysis.ship_behavior_event "
                        + "WHERE behavior_type=? AND batch_no=? AND mmsi=? AND rule_version=? "
                        + "AND start_time>=? AND start_time<?",
                behaviorType, batchNo, mmsi, ruleVersion,
                timestamp(startTime), timestamp(endTime));
    }

    private void deleteDetails(String behaviorType, String batchNo, String mmsi,
                               String ruleVersion, String startTime, String endTime) {
        jdbcTemplate.update("DELETE FROM dataanalysis.ship_behavior_event_detail "
                        + "WHERE behavior_type=? AND batch_no=? AND mmsi=? AND rule_version=? "
                        + "AND event_start_time>=? AND event_start_time<?",
                behaviorType, batchNo, mmsi, ruleVersion,
                timestamp(startTime), timestamp(endTime));
    }

    private List<ShipBehaviorEvent> events(List<ShipBehaviorTrace> traces) {
        List<ShipBehaviorEvent> result = new ArrayList<ShipBehaviorEvent>();
        for (ShipBehaviorTrace trace : traces) {
            if (trace != null && trace.getEvent() != null) {
                result.add(trace.getEvent());
            }
        }
        return result;
    }

    private List<ShipBehaviorEventDetail> details(List<ShipBehaviorTrace> traces,
                                                   String batchNo) {
        List<ShipBehaviorEventDetail> result = new ArrayList<ShipBehaviorEventDetail>();
        for (ShipBehaviorTrace trace : traces) {
            if (trace == null || trace.getEvent() == null) {
                continue;
            }
            append(result, trace.getEvent(), trace.getOwnPoints(),
                    ShipBehaviorEventDetail.ROLE_OWN, batchNo);
            append(result, trace.getEvent(), trace.getTargetPoints(),
                    ShipBehaviorEventDetail.ROLE_TARGET, batchNo);
        }
        return result;
    }

    private void append(List<ShipBehaviorEventDetail> target, ShipBehaviorEvent event,
                        List<TrajectoryPoint> points, String role, String batchNo) {
        if (points == null) {
            return;
        }
        for (int i = 0; i < points.size(); i++) {
            TrajectoryPoint point = points.get(i);
            ShipBehaviorEventDetail detail = new ShipBehaviorEventDetail();
            detail.setBehaviorType(event.getBehaviorType());
            detail.setMmsi(event.getMmsi());
            detail.setTargetMmsi(event.getTargetMmsi());
            detail.setPointRole(role);
            detail.setEventStartTime(event.getStartTime());
            detail.setEventEndTime(event.getEndTime());
            detail.setPointTime(TrajectoryUtils.formatTime(point));
            detail.setPointIndex(i);
            detail.setLon(point.getLon());
            detail.setLat(point.getLat());
            detail.setSpeed(point.getSpeed());
            detail.setCourse(point.getCourse());
            detail.setNavStatus(point.getNavStatus());
            detail.setBatchNo(batchNo);
            detail.setRuleVersion(event.getRuleVersion());
            target.add(detail);
        }
    }

    private Timestamp timestamp(String value) {
        return Timestamp.valueOf(LocalDateTime.parse(value, TIME_FORMATTER));
    }
}
