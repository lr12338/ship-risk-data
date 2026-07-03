package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class AlongsideJsonMapFunctionTest {

    @Test
    public void shouldSerializeIndependentAlongsideEvent() {
        AlongsideEvent event = new AlongsideEvent();
        event.setEventId("413193000-413433610-1-formal");
        event.setPairId("413193000-413433610");
        event.setEventType("ALONGSIDE_CANDIDATE");
        event.setMmsiA(413193000L);
        event.setMmsiB(413433610L);
        event.setStartTime(Instant.parse("2026-06-29T00:00:00Z"));
        event.setEndTime(Instant.parse("2026-06-29T00:30:00Z"));
        event.setDurationMinutes(30L);
        event.setMinDistanceNm(0.08d);
        event.setPointCount(6);
        event.setRuleVersion("ALONGSIDE_V2_INSURANCE");
        event.setEventSubType("ALONGSIDE_CONFIRMED_CANDIDATE");
        event.setSeverityLevel("L2");
        event.setConfidenceLevel("HIGH");
        event.setSourceMode("REALTIME_AUTO_BUCKET");
        event.setPairPriority("HIGH");
        event.setShipClassA("HIGH_PRIORITY");
        event.setShipClassB("UNKNOWN");

        String json = AlongsideJsonMapFunction.toJson(event);

        Assert.assertTrue(json.contains("\"pair_id\":\"413193000-413433610\""));
        Assert.assertTrue(json.contains("\"event_sub_type\":\"ALONGSIDE_CONFIRMED_CANDIDATE\""));
        Assert.assertTrue(json.contains("\"source_mode\":\"REALTIME_AUTO_BUCKET\""));
        Assert.assertTrue(json.contains("\"pair_priority\":\"HIGH\""));
        Assert.assertFalse(json.contains("stop_id"));
        Assert.assertFalse(json.contains("vessel_state"));
    }
}
