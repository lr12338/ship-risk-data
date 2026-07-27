package com.hifleet.service;

import com.hifleet.service.LoiteringV3QualityService.DailyStats;
import com.hifleet.service.LoiteringV3QualityService.EventSample;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LoiteringV3QualityServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
    private final LoiteringV3QualityService service = new LoiteringV3QualityService();

    @Test
    public void dailyStatsUseDistinctShipsAndFinalEventDurations() {
        EventSample shanghai = sample("event-a", LoiteringV3RuleEngine.STATUS_FINAL,
                120, 90.0, "上海港", LoiteringV3RuleEngine.PATTERN_CIRCULAR,
                LoiteringV3RuleEngine.REASON_LOITERING_PATTERN_CONFIRMED);
        EventSample ningbo = sample("event-b", LoiteringV3RuleEngine.STATUS_FINAL,
                240, 80.0, "宁波舟山港", LoiteringV3RuleEngine.PATTERN_SERPENTINE,
                LoiteringV3RuleEngine.REASON_LOITERING_PATTERN_CONFIRMED);
        EventSample review = sample("event-c", LoiteringV3RuleEngine.STATUS_REVIEW,
                60, 0.0, "上海港", LoiteringV3RuleEngine.PATTERN_NONE,
                LoiteringV3RuleEngine.REASON_FORMAL_GATE_NOT_MET);

        DailyStats stats = service.summarize(
                new HashSet<String>(Arrays.asList("1", "1", "2", "3")),
                new HashSet<String>(Arrays.asList("1", "2")),
                Arrays.asList(shanghai, ningbo, review));

        assertEquals(3, stats.getAnalyzedShipCount());
        assertEquals(2, stats.getEligibleShipCount());
        assertEquals(3, stats.getCandidateEventCount());
        assertEquals(2, stats.getFinalEventCount());
        assertEquals(1, stats.getReviewEventCount());
        assertEquals(180.0, stats.getAverageDurationMinutes(), 0.0001);
        assertEquals(180.0, stats.getMedianDurationMinutes(), 0.0001);
        assertEquals(240.0, stats.getP90DurationMinutes(), 0.0001);
        assertEquals(Integer.valueOf(1), stats.getRegionDistribution().get("上海港"));
        assertEquals(Integer.valueOf(1), stats.getRegionDistribution().get("宁波舟山港"));
    }

    @Test
    public void topRiskCasesContainOnlyUniqueFinalEventsInRiskOrder() {
        EventSample high = sample("event-high", LoiteringV3RuleEngine.STATUS_FINAL,
                180, 95.0, "上海港", LoiteringV3RuleEngine.PATTERN_CIRCULAR, "CIRCLE");
        EventSample medium = sample("event-medium", LoiteringV3RuleEngine.STATUS_FINAL,
                180, 75.0, "上海港", LoiteringV3RuleEngine.PATTERN_SERPENTINE, "SERPENTINE");
        EventSample review = sample("event-review", LoiteringV3RuleEngine.STATUS_REVIEW,
                180, 99.0, "上海港", LoiteringV3RuleEngine.PATTERN_CIRCULAR, "REVIEW");

        List<EventSample> top = service.selectTopRiskCases(
                Arrays.asList(medium, high, high, review), 10);

        assertEquals(2, top.size());
        assertEquals("event-high", top.get(0).getEventKey());
        assertEquals("CIRCLE", top.get(0).getReasonCodes());
        assertEquals("event-medium", top.get(1).getEventKey());
    }

    @Test
    public void selectedCaseRetainsTrajectoryForMapAndSpeedSeries() {
        EventSample event = sample("event-a", LoiteringV3RuleEngine.STATUS_FINAL,
                120, 90.0, "上海港", LoiteringV3RuleEngine.PATTERN_CIRCULAR, "CIRCLE");

        EventSample selected = service.selectTopRiskCases(
                Collections.singletonList(event), 1).get(0);

        assertSame(event, selected);
        assertEquals(2, selected.getTrajectory().size());
        assertEquals(2.0, selected.getTrajectory().get(0).getSpeed(), 0.0001);
    }

    @Test
    public void exclusionReasonsAreCountedInsideReasonCodeJson() {
        EventSample normalBerthing = sample("event-a", LoiteringV3RuleEngine.STATUS_EXCLUDED,
                60, 0.0, "上海港", LoiteringV3RuleEngine.PATTERN_NONE,
                "[\"NORMAL_BERTHING\",\"INSIDE_PORT\"]");
        EventSample straightTransit = sample("event-b", LoiteringV3RuleEngine.STATUS_EXCLUDED,
                60, 0.0, "上海港", LoiteringV3RuleEngine.PATTERN_NONE,
                "[\"STRAIGHT_LOW_SPEED_TRANSIT\"]");

        DailyStats stats = service.summarize(Collections.<String>emptySet(),
                Collections.<String>emptySet(), Arrays.asList(normalBerthing, straightTransit));

        assertEquals(1, stats.getExcludedNormalBerthingCount());
        assertEquals(1, stats.getExcludedStraightTransitCount());
    }

    @Test
    public void emptyInputProducesZeroStatsAndNoCases() {
        DailyStats stats = service.summarize(Collections.<String>emptySet(),
                Collections.<String>emptySet(), Collections.<EventSample>emptyList());

        assertEquals(0, stats.getCandidateEventCount());
        assertEquals(0.0, stats.getAverageDurationMinutes(), 0.0001);
        assertEquals(0, service.selectTopRiskCases(
                Collections.<EventSample>emptyList(), 20).size());
    }

    private EventSample sample(String eventKey, String status, int durationMinutes,
                               double riskScore, String region, String pattern,
                               String reasonCodes) {
        List<TrajectoryPoint> trajectory = Arrays.asList(
                new TrajectoryPoint("413193000", START, 121.0, 31.0, 2.0, 90.0, null),
                new TrajectoryPoint("413193000", START.plusMinutes(durationMinutes),
                        121.01, 31.0, 2.5, 180.0, null));
        return new EventSample(eventKey, "413193000", "IMO1234567", "TEST SHIP",
                "CONTAINER", status, START, START.plusMinutes(durationMinutes),
                riskScore, region, pattern, reasonCodes, trajectory);
    }
}
