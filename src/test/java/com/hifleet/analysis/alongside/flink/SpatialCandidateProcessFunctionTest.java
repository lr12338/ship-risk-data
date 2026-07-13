package com.hifleet.analysis.alongside.flink;

import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import com.hifleet.analysis.portcall.config.AlongsideConfig;
import com.hifleet.analysis.portcall.model.CleanAisPoint;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class SpatialCandidateProcessFunctionTest {

    @Test
    public void shouldCountNearbyStationaryVesselsOnEvidence() {
        AlongsideConfig config = config();
        Instant time = Instant.parse("2026-06-29T00:00:00Z");
        CleanAisPoint a = point(100L, 121.000d, 31.000d, 0.0d, time);
        CleanAisPoint b = point(200L, 121.001d, 31.000d, 0.0d, time.plusSeconds(20L));
        CleanAisPoint c = point(300L, 121.002d, 31.000d, 0.0d, time.plusSeconds(30L));

        List<PairProximityEvidence> evidence = SpatialCandidateProcessFunction.findNearestEvidence(
            a, Arrays.asList(a, b, c), config);

        PairProximityEvidence pair = find(evidence, 100L, 200L);
        Assert.assertNotNull(pair);
        Assert.assertEquals(Integer.valueOf(3), pair.getNearbyStationaryVesselCount());
    }

    @Test
    public void shouldRejectCandidateWhenSpeedIsMissing() {
        AlongsideConfig config = config();
        Instant time = Instant.parse("2026-06-29T00:00:00Z");
        CleanAisPoint a = point(100L, 121.000d, 31.000d, null, time);
        CleanAisPoint b = point(200L, 121.001d, 31.000d, 0.0d, time.plusSeconds(20L));

        List<PairProximityEvidence> evidence = SpatialCandidateProcessFunction.findNearestEvidence(
            a, Arrays.asList(a, b), config);

        Assert.assertTrue(evidence.isEmpty());
    }

    private AlongsideConfig config() {
        Properties props = new Properties();
        AlongsideConfig.addDefaults(props);
        props.setProperty("alongside.candidate.max-distance-nm", "0.25");
        props.setProperty("alongside.candidate.max-time-gap-minutes", "5");
        props.setProperty("alongside.high-match.stationary-filter.max-speed-knots", "0.2");
        props.setProperty("alongside.high-match.stationary-filter.cluster-radius-nm", "0.15");
        return AlongsideConfig.fromProperties(props);
    }

    private CleanAisPoint point(Long mmsi, double lon, double lat, Double speed, Instant time) {
        CleanAisPoint point = new CleanAisPoint();
        point.setMmsi(mmsi);
        point.setLon(lon);
        point.setLat(lat);
        point.setSpeed(speed);
        point.setCourse(90.0d);
        point.setUpdateTime(time);
        point.setShipType(80);
        return point;
    }

    private PairProximityEvidence find(List<PairProximityEvidence> evidence, long mmsiA, long mmsiB) {
        for (PairProximityEvidence item : evidence) {
            if (item.getMmsiA() == mmsiA && item.getMmsiB() == mmsiB) {
                return item;
            }
        }
        return null;
    }
}
