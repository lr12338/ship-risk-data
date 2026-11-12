package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.repository.ShipBehaviorEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class RegionFormalEventStore {
    @Autowired @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;
    @Autowired @Qualifier("newDataanalysisDataSource")
    private DataSource dataSource;
    @Autowired
    private ShipBehaviorEventRepository eventRepository;
    @Autowired
    private RegionBehaviorStateRepository stateRepository;

    public int replace(final RegionBehaviorJobParams params, final String fingerprint,
                        final String mmsi, final List<RegionMatchedEvent> matched) {
        Integer writes = new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
            jdbcTemplate.update("DELETE FROM dataanalysis.ship_behavior_event WHERE batch_no=? "
                            + "AND event_source='REGION' AND region_id=? AND behavior_type=? AND mmsi=? "
                            + "AND start_time>=? AND start_time<?", params.getBatchNo(), params.getRegionId(),
                    params.getBehaviorTypes().get(0), mmsi,
                    Timestamp.valueOf(RegionBehaviorTimes.parse(params.getStartTime())),
                    Timestamp.valueOf(RegionBehaviorTimes.parse(params.getEndTime())));
            List<ShipBehaviorEvent> events = new ArrayList<ShipBehaviorEvent>();
            for (RegionMatchedEvent value : matched) {
                if (!mmsi.equals(value.getMmsi())) continue;
                ShipBehaviorEvent event = JSON.parseObject(value.getSourceEvent().toJSONString(),
                        ShipBehaviorEvent.class);
                event.setBatchNo(params.getBatchNo());
                event.setEventSource("REGION");
                event.setRegionId(params.getRegionId());
                event.setRegionFingerprint(fingerprint);
                event.setEventKey(value.getEventKey());
                JSONObject detail = event.getScoreDetailJson() == null
                        ? new JSONObject(true) : JSON.parseObject(event.getScoreDetailJson());
                detail.put("regionMatchStatus", value.getRegionMatchStatus().name());
                detail.put("regionFingerprint", fingerprint);
                detail.put("ownPointInsideRatio", value.getOwnPointInsideRatio());
                detail.put("targetPointInsideRatio", value.getTargetPointInsideRatio());
                detail.put("pairMidpointInsideRatio", value.getPairMidpointInsideRatio());
                detail.put("queryCompleteness", value.getQueryCompleteness().name());
                event.setScoreDetailJson(detail.toJSONString());
                events.add(event);
            }
            eventRepository.saveEvents(events);
            stateRepository.mark(params.getBatchNo(), "ANALYSIS", mmsi, true, null);
            return events.size();
        });
        return writes == null ? 0 : writes;
    }
}
