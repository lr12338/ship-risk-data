package com.hifleet.service;

import com.hifleet.service.LoiteringV3AnalyzerService.AnalyzedEvent;
import com.hifleet.service.LoiteringV3AnalyzerService.MerchantShipProvider;
import com.hifleet.service.LoiteringV3AnalyzerService.PortContextProvider;
import com.hifleet.service.LoiteringV3AnalyzerService.RunResult;
import com.hifleet.service.LoiteringV3AnalyzerService.Ship;
import com.hifleet.service.LoiteringV3AnalyzerService.TrajectorySource;
import com.hifleet.service.LoiteringV3EpisodeBuilder.Episode;
import com.hifleet.service.LoiteringV3EpisodeBuilder.Observation;
import com.hifleet.service.LoiteringV3FeatureExtractor.Features;
import com.hifleet.service.LoiteringV3RuleEngine.Context;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoiteringV3AnalyzerServiceTest {

    private static final LocalDateTime RUN_TIME = LocalDateTime.of(2026, 7, 2, 12, 0);

    @Test
    public void analyzerScansPreviousTwentyFourHoursAndBuildsStandardEvent() {
        CapturingTrajectorySource trajectorySource = new CapturingTrajectorySource(circlePoints());
        LoiteringV3AnalyzerService service = new LoiteringV3AnalyzerService(
                singleShipProvider("413193000"), trajectorySource, nearPortContextProvider());

        RunResult result = service.analyze(RUN_TIME);

        assertEquals(RUN_TIME.minusHours(24), trajectorySource.windowStart);
        assertEquals(RUN_TIME, trajectorySource.windowEnd);
        assertEquals(1, result.getAnalyzedShipCount());
        assertEquals(0, result.getFailedShipCount());
        assertEquals(1, result.getEvents().size());

        AnalyzedEvent event = result.getEvents().get(0);
        assertEquals("413193000", event.getMmsi());
        assertEquals("IMO1234567", event.getImo());
        assertEquals("LOITERING", event.getEventType());
        assertEquals(180L, event.getDurationMinutes());
        assertEquals(2.0, event.getAverageSpeed(), 0.0001);
        assertEquals(1.0, event.getRadiusNm(), 0.08);
        assertEquals(LoiteringV3RuleEngine.PATTERN_CIRCULAR,
                event.getTrajectoryPattern());
        assertTrue(event.isNearPort());
        assertFalse(event.isInsidePort());
        assertTrue(event.getConfidenceScore() >= 0.8);
        assertTrue(event.getRiskScore() > 0.0);
        assertFalse(event.getEventKey().isEmpty());
    }

    @Test
    public void oneShipFailureDoesNotAbortRemainingShips() {
        MerchantShipProvider ships = new MerchantShipProvider() {
            @Override
            public List<Ship> listEligibleShips(LocalDateTime windowStart,
                                                LocalDateTime windowEnd) {
                return Arrays.asList(ship("bad"), ship("413193000"));
            }
        };
        TrajectorySource trajectorySource = new TrajectorySource() {
            @Override
            public List<TrajectoryPoint> load(String mmsi, LocalDateTime windowStart,
                                              LocalDateTime windowEnd) {
                if ("bad".equals(mmsi)) {
                    throw new IllegalStateException("synthetic load failure");
                }
                return circlePoints();
            }
        };
        LoiteringV3AnalyzerService service = new LoiteringV3AnalyzerService(
                ships, trajectorySource, nearPortContextProvider());

        RunResult result = service.analyze(RUN_TIME);

        assertEquals(2, result.getAnalyzedShipCount());
        assertEquals(1, result.getFailedShipCount());
        assertEquals(Collections.singletonList("bad"), result.getFailedMmsi());
        assertEquals(1, result.getEvents().size());
    }

    private MerchantShipProvider singleShipProvider(final String mmsi) {
        return new MerchantShipProvider() {
            @Override
            public List<Ship> listEligibleShips(LocalDateTime windowStart,
                                                LocalDateTime windowEnd) {
                return Collections.singletonList(ship(mmsi));
            }
        };
    }

    private Ship ship(String mmsi) {
        return new Ship(mmsi, "IMO1234567", "TEST SHIP", "CONTAINER");
    }

    private PortContextProvider nearPortContextProvider() {
        return new PortContextProvider() {
            @Override
            public List<Observation> annotate(Ship ship, List<TrajectoryPoint> points) {
                List<Observation> observations = new ArrayList<Observation>();
                for (TrajectoryPoint point : points) {
                    observations.add(new Observation(point, true, false, false));
                }
                return observations;
            }

            @Override
            public Context contextFor(Ship ship, Episode episode, Features features,
                                      List<Observation> windowObservations) {
                assertEquals(37, windowObservations.size());
                return Context.builder()
                        .merchantShip(true)
                        .portDataAvailable(true)
                        .berthDataAvailable(true)
                        .nearPort(true)
                        .insidePort(false)
                        .insideBerth(false)
                        .nearBerthPoint(false)
                        .build();
            }
        };
    }

    private List<TrajectoryPoint> circlePoints() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = RUN_TIME.minusHours(3);
        for (int i = 0; i < 37; i++) {
            double angle = 2.0 * Math.PI * i / 36.0;
            double xNm = Math.cos(angle);
            double yNm = Math.sin(angle);
            double lon = 121.0 + xNm / (60.0 * Math.cos(Math.toRadians(31.0)));
            double lat = 31.0 + yNm / 60.0;
            points.add(new TrajectoryPoint("413193000", start.plusMinutes(i * 5),
                    lon, lat, 2.0, -1.0, null));
        }
        return points;
    }

    private static class CapturingTrajectorySource implements TrajectorySource {
        private final List<TrajectoryPoint> points;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;

        private CapturingTrajectorySource(List<TrajectoryPoint> points) {
            this.points = points;
        }

        @Override
        public List<TrajectoryPoint> load(String mmsi, LocalDateTime windowStart,
                                          LocalDateTime windowEnd) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            return points;
        }
    }
}
