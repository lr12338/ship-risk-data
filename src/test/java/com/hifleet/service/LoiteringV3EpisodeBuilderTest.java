package com.hifleet.service;

import com.hifleet.service.LoiteringV3EpisodeBuilder.Episode;
import com.hifleet.service.LoiteringV3EpisodeBuilder.Observation;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoiteringV3EpisodeBuilderTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
    private final LoiteringV3EpisodeBuilder builder = new LoiteringV3EpisodeBuilder();

    @Test
    public void fourCoreSamplesStartCandidateAtFirstRealPoint() {
        List<Episode> episodes = builder.build(coreRange(0, 15));

        assertEquals(1, episodes.size());
        assertEquals(START, episodes.get(0).getStartTime());
        assertEquals(START.plusMinutes(15), episodes.get(0).getEndTime());
        assertEquals(4, episodes.get(0).getPoints().size());
        assertTrue(episodes.get(0).isOpen());
    }

    @Test
    public void tenMinuteSpeedExcursionStaysInOneEpisode() {
        List<Observation> observations = coreRange(0, 15);
        observations.add(observation(20, 4.5, true, false));
        observations.add(observation(25, 4.5, true, false));
        observations.add(observation(30, 2.0, true, false));
        observations.add(observation(35, 2.0, true, false));

        List<Episode> episodes = builder.build(observations);

        assertEquals(1, episodes.size());
        assertEquals(START, episodes.get(0).getStartTime());
        assertEquals(START.plusMinutes(35), episodes.get(0).getEndTime());
        assertEquals(8, episodes.get(0).getPoints().size());
        assertTrue(episodes.get(0).isOpen());
    }

    @Test
    public void moreThanFifteenMinuteExcursionSplitsEpisodes() {
        List<Observation> observations = coreRange(0, 15);
        observations.add(observation(20, 4.5, true, false));
        observations.add(observation(25, 4.5, true, false));
        observations.add(observation(30, 4.5, true, false));
        observations.add(observation(35, 4.5, true, false));
        observations.addAll(coreRange(40, 55));

        List<Episode> episodes = builder.build(observations);

        assertEquals(2, episodes.size());
        assertEquals(START.plusMinutes(15), episodes.get(0).getEndTime());
        assertEquals(LoiteringV3EpisodeBuilder.END_BEHAVIOR_EXIT, episodes.get(0).getEndReason());
        assertFalse(episodes.get(0).isOpen());
        assertEquals(START.plusMinutes(40), episodes.get(1).getStartTime());
        assertTrue(episodes.get(1).isOpen());
    }

    @Test
    public void recoveryAfterTwentyMinuteExcursionStartsNewCandidate() {
        List<Observation> observations = coreRange(0, 15);
        observations.add(observation(20, 4.5, true, false));
        observations.add(observation(25, 4.5, true, false));
        observations.add(observation(30, 4.5, true, false));
        observations.addAll(coreRange(35, 50));

        List<Episode> episodes = builder.build(observations);

        assertEquals(2, episodes.size());
        assertEquals(START.plusMinutes(15), episodes.get(0).getEndTime());
        assertEquals(START.plusMinutes(35), episodes.get(1).getStartTime());
    }

    @Test
    public void gapOverThirtyMinutesSplitsEpisodes() {
        List<Observation> observations = coreRange(0, 15);
        observations.addAll(coreRange(50, 65));

        List<Episode> episodes = builder.build(observations);

        assertEquals(2, episodes.size());
        assertEquals(LoiteringV3EpisodeBuilder.END_AIS_GAP, episodes.get(0).getEndReason());
        assertEquals(START.plusMinutes(50), episodes.get(1).getStartTime());
    }

    @Test
    public void qualifiedNormalBerthingEndsAtPreviousCandidatePoint() {
        List<Observation> observations = coreRange(0, 60);
        observations.add(observation(65, 0.2, true, true));

        List<Episode> episodes = builder.build(observations);

        assertEquals(1, episodes.size());
        assertEquals(START.plusMinutes(60), episodes.get(0).getEndTime());
        assertEquals(LoiteringV3EpisodeBuilder.END_NORMAL_BERTHING, episodes.get(0).getEndReason());
        assertFalse(episodes.get(0).isOpen());
    }

    @Test
    public void resamplingKeepsNearestRealPointPerFiveMinuteSlot() {
        List<Observation> observations = new ArrayList<Observation>();
        observations.add(observation(0, 2.0, true, false));
        observations.add(observation(4, 2.0, true, false));
        observations.add(observation(6, 2.0, true, false));
        observations.add(observation(10, 2.0, true, false));
        observations.add(observation(15, 2.0, true, false));

        List<Episode> episodes = builder.build(observations);

        assertEquals(1, episodes.size());
        assertEquals(4, episodes.get(0).getPoints().size());
        assertEquals(START.plusMinutes(4), episodes.get(0).getPoints().get(1).getTime());
    }

    private List<Observation> coreRange(int startMinute, int endMinute) {
        List<Observation> observations = new ArrayList<Observation>();
        for (int minute = startMinute; minute <= endMinute; minute += 5) {
            observations.add(observation(minute, 2.0, true, false));
        }
        return observations;
    }

    private Observation observation(int minute, double speed, boolean nearTarget,
                                    boolean normalBerthing) {
        TrajectoryPoint point = new TrajectoryPoint("413193000", START.plusMinutes(minute),
                121.0 + minute * 0.00001, 31.0, speed, 90.0, null);
        return new Observation(point, nearTarget, false, normalBerthing);
    }
}
