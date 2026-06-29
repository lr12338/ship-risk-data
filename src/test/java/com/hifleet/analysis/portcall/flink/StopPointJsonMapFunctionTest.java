package com.hifleet.analysis.portcall.flink;

import com.hifleet.analysis.portcall.model.StopPointEvent;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class StopPointJsonMapFunctionTest {

    @Test
    public void shouldSerializePersistenceReadyStopUpdate() {
        StopPointEvent event = new StopPointEvent();
        event.setMessageId("413000001-stop-1-update-1");
        event.setEventType(StopPointEvent.EventType.STOP_UPDATE);
        event.setDbAction("UPSERT");
        event.setMmsi(413000001L);
        event.setVesselState(StopPointEvent.VesselState.STOPPED);
        event.setStopId("stop-413000001-1774051200000");
        event.setStopStartTime(Instant.parse("2026-03-21T00:00:00Z"));
        event.setStableStartTime(Instant.parse("2026-03-21T00:10:00Z"));
        event.setLastAisUpdateTime(Instant.parse("2026-03-21T00:15:00Z"));
        event.setStopLon(121.50012);
        event.setStopLat(31.20009);
        event.setPositionStatus(StopPointEvent.PositionStatus.STABLE);
        event.setPositionPointCount(8);
        event.setConfidence(0.91d);
        event.setDataState("normal");

        String json = StopPointJsonMapFunction.toJson(event);

        Assert.assertTrue(json.contains("\"message_id\":\"413000001-stop-1-update-1\""));
        Assert.assertTrue(json.contains("\"event_type\":\"STOP_UPDATE\""));
        Assert.assertTrue(json.contains("\"vessel_state\":\"STOPPED\""));
        Assert.assertTrue(json.contains("\"stop_lon\":121.50012"));
        Assert.assertTrue(json.contains("\"data_state\":\"normal\""));
    }
}
