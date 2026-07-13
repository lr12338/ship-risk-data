package com.hifleet.analysis.alongside.flink;

import com.hifleet.analysis.alongside.core.model.AlongsideEvent;
import com.hifleet.analysis.alongside.core.model.PairPriority;
import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import com.hifleet.analysis.alongside.core.model.VesselClass;
import com.hifleet.analysis.alongside.core.model.VesselProfile;
import com.hifleet.analysis.alongside.core.model.VesselType;
import com.hifleet.analysis.alongside.core.rule.AlongsideRuleEngineImpl;
import com.hifleet.analysis.portcall.config.AlongsideConfig;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Properties;

public class AlongsidePairDetectorProcessFunctionTest {

    @Test
    public void shouldEmitUpdateAfterConfirmedEventAndConfiguredInterval() {
        AlongsideConfig config = config();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();

        for (int i = 0; i < 6; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence(i), config);
        }
        AlongsideEvent confirmed = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);
        AlongsidePairDetectorProcessFunction.markEmitted(state, confirmed);
        AlongsidePairDetectorProcessFunction.addEvidence(state, evidence(7), config);

        AlongsideEvent update = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);

        Assert.assertEquals(AlongsideRuleEngineImpl.SUB_TYPE_UPDATE, update.getEventSubType());
        Assert.assertEquals(32, update.getEventId().length());
        Assert.assertTrue(update.getEvidencePoints().isEmpty());
        Assert.assertNotNull(update.getStartLon());
        Assert.assertNotNull(update.getCenterLon());
    }

    @Test
    public void shouldBuildEndEventAfterAnyCandidateWasEmitted() {
        AlongsideConfig config = config();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();

        for (int i = 0; i < 3; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence(i * 2), config);
        }
        AlongsideEvent weak = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);
        AlongsidePairDetectorProcessFunction.markEmitted(state, weak);

        AlongsideEvent end = AlongsidePairDetectorProcessFunction.buildEndEventIfNeeded(state, config);

        Assert.assertEquals(AlongsideRuleEngineImpl.SUB_TYPE_END, end.getEventSubType());
        Assert.assertEquals("EVENT_ENDED", end.getExclusionFlag());
    }

    @Test
    public void shouldKeepParsedBeijingTimeOnEventWindow() {
        AlongsideConfig config = config();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant parsedBeijingStart = Instant.parse("2026-07-07T11:10:51Z");

        for (int i = 0; i < 6; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidenceAt(parsedBeijingStart.plusSeconds(i * 360L)), config);
        }

        AlongsideEvent event = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);

        Assert.assertEquals(parsedBeijingStart, event.getStartTime());
        Assert.assertEquals(parsedBeijingStart.plusSeconds(5 * 360L), event.getEndTime());
    }

    @Test
    public void shouldEmitHighMatchCommercialConfirmedEvent() {
        AlongsideConfig config = highMatchConfig();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant start = Instant.parse("2026-06-29T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state,
                evidenceAt(start.plusSeconds(i * 420L), VesselType.TANKER, VesselClass.HIGH_PRIORITY,
                    VesselType.BUNKER, VesselClass.HIGH_PRIORITY, PairPriority.HIGH), config);
        }

        AlongsideEvent event = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);

        Assert.assertNotNull(event);
        Assert.assertEquals(AlongsideRuleEngineImpl.SUB_TYPE_CONFIRMED, event.getEventSubType());
        Assert.assertEquals("HIGH", event.getConfidenceLevel());
        Assert.assertTrue(event.getTrackCoverageRatio() >= 0.85d);
        Assert.assertEquals(Integer.valueOf(0), event.getAisGapCount());
    }

    @Test
    public void shouldSuppressNormalServicePairInHighMatchMode() {
        AlongsideConfig config = highMatchConfig();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant start = Instant.parse("2026-06-29T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state,
                evidenceAt(start.plusSeconds(i * 420L), VesselType.CARGO, VesselClass.HIGH_PRIORITY,
                    VesselType.TUG, VesselClass.NORMAL_SERVICE, PairPriority.DOWNGRADED), config);
        }

        Assert.assertNull(AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config));
    }

    @Test
    public void shouldSuppressLowQualityTrackInHighMatchMode() {
        AlongsideConfig config = highMatchConfig();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant start = Instant.parse("2026-06-29T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state,
                evidenceAt(start.plusSeconds(i * 1200L), VesselType.TANKER, VesselClass.HIGH_PRIORITY,
                    VesselType.TANKER, VesselClass.HIGH_PRIORITY, PairPriority.HIGH), config);
        }

        Assert.assertNull(AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config));
    }

    @Test
    public void shouldKeepStationaryCommercialPairWithoutMooringOrCluster() {
        AlongsideConfig config = highMatchStationaryConfig();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant start = Instant.parse("2026-06-29T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            PairProximityEvidence evidence = evidenceAt(start.plusSeconds(i * 420L), VesselType.TANKER,
                VesselClass.HIGH_PRIORITY, VesselType.BUNKER, VesselClass.HIGH_PRIORITY, PairPriority.HIGH);
            evidence.setSpeedA(0.0d);
            evidence.setSpeedB(0.0d);
            evidence.setRelativeSpeedKnots(0.0d);
            evidence.setNavigationStatusA(0);
            evidence.setNavigationStatusB(0);
            evidence.setNearbyStationaryVesselCount(2);
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence, config);
        }

        AlongsideEvent event = AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config);

        Assert.assertNotNull(event);
        Assert.assertEquals(Double.valueOf(0.0d), event.getAvgSpeedKnots());
        Assert.assertTrue(event.getScoreDetailJson().contains("\"both_stationary_ratio\":1.0"));
        Assert.assertTrue(event.getScoreDetailJson().contains("\"max_nearby_stationary_vessel_count\":2"));
    }

    @Test
    public void shouldSuppressAnchoredOrMooredStationaryHighMatchEvent() {
        AlongsideConfig config = highMatchStationaryConfig();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant start = Instant.parse("2026-06-29T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            PairProximityEvidence evidence = evidenceAt(start.plusSeconds(i * 420L), VesselType.TANKER,
                VesselClass.HIGH_PRIORITY, VesselType.TANKER, VesselClass.HIGH_PRIORITY, PairPriority.HIGH);
            evidence.setSpeedA(0.0d);
            evidence.setSpeedB(0.0d);
            evidence.setRelativeSpeedKnots(0.0d);
            evidence.setNavigationStatusA(5);
            evidence.setNavigationStatusB(0);
            evidence.setNearbyStationaryVesselCount(2);
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence, config);
        }

        Assert.assertNull(AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config));
    }

    @Test
    public void shouldSuppressStationaryMultiShipClusterHighMatchEvent() {
        AlongsideConfig config = highMatchStationaryConfig();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant start = Instant.parse("2026-06-29T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            PairProximityEvidence evidence = evidenceAt(start.plusSeconds(i * 420L), VesselType.CARGO,
                VesselClass.HIGH_PRIORITY, VesselType.TANKER, VesselClass.HIGH_PRIORITY, PairPriority.HIGH);
            evidence.setSpeedA(0.0d);
            evidence.setSpeedB(0.0d);
            evidence.setRelativeSpeedKnots(0.0d);
            evidence.setNavigationStatusA(0);
            evidence.setNavigationStatusB(0);
            evidence.setNearbyStationaryVesselCount(3);
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence, config);
        }

        Assert.assertNull(AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config));
    }

    @Test
    public void shouldKeepAnchoredHighMatchEventWhenStationaryFilterDisabled() {
        AlongsideConfig config = highMatchConfig();
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();
        Instant start = Instant.parse("2026-06-29T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            PairProximityEvidence evidence = evidenceAt(start.plusSeconds(i * 420L), VesselType.TANKER,
                VesselClass.HIGH_PRIORITY, VesselType.TANKER, VesselClass.HIGH_PRIORITY, PairPriority.HIGH);
            evidence.setSpeedA(0.0d);
            evidence.setSpeedB(0.0d);
            evidence.setRelativeSpeedKnots(0.0d);
            evidence.setNavigationStatusA(1);
            evidence.setNavigationStatusB(0);
            evidence.setNearbyStationaryVesselCount(3);
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence, config);
        }

        Assert.assertNotNull(AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config));
    }

    @Test
    public void shouldSuppressWeakCandidateWhenEmitWeakDisabled() {
        Properties props = new Properties();
        AlongsideConfig.addDefaults(props);
        props.setProperty("alongside.output.emit-weak", "false");
        AlongsideConfig config = AlongsideConfig.fromProperties(props);
        AlongsidePairDetectorProcessFunction.PairState state =
            new AlongsidePairDetectorProcessFunction.PairState();

        for (int i = 0; i < 3; i++) {
            AlongsidePairDetectorProcessFunction.addEvidence(state, evidence(i * 2), config);
        }

        Assert.assertNull(AlongsidePairDetectorProcessFunction.buildEventIfReady(state, config));
    }

    private AlongsideConfig config() {
        Properties props = new Properties();
        AlongsideConfig.addDefaults(props);
        props.setProperty("alongside.output.update-interval-minutes", "5");
        props.setProperty("alongside.db-sink.enabled", "true");
        return AlongsideConfig.fromProperties(props);
    }

    private AlongsideConfig highMatchConfig() {
        Properties props = new Properties();
        AlongsideConfig.addDefaults(props);
        props.setProperty("alongside.high-match.enabled", "true");
        props.setProperty("alongside.output.emit-weak", "false");
        props.setProperty("alongside.candidate.max-distance-nm", "0.25");
        props.setProperty("alongside.candidate.max-speed-knots", "3.0");
        props.setProperty("alongside.candidate.max-time-gap-minutes", "5");
        props.setProperty("alongside.candidate.min-duration-minutes", "30");
        props.setProperty("alongside.candidate.min-points", "5");
        props.setProperty("alongside.confirm.max-distance-nm", "0.08");
        props.setProperty("alongside.confirm.max-speed-knots", "1.5");
        props.setProperty("alongside.confirm.max-relative-speed-knots", "0.5");
        props.setProperty("alongside.confirm.high-confidence-time-gap-minutes", "2");
        props.setProperty("alongside.confirm.min-duration-minutes", "45");
        props.setProperty("alongside.confirm.min-points", "8");
        props.setProperty("alongside.heading.parallel-threshold-degrees", "20");
        props.setProperty("alongside.ais-gap-minutes", "15");
        return AlongsideConfig.fromProperties(props);
    }

    private AlongsideConfig highMatchStationaryConfig() {
        Properties props = new Properties();
        AlongsideConfig.addDefaults(props);
        props.setProperty("alongside.high-match.enabled", "true");
        props.setProperty("alongside.high-match.stationary-filter.enabled", "true");
        props.setProperty("alongside.output.emit-weak", "false");
        props.setProperty("alongside.candidate.max-distance-nm", "0.25");
        props.setProperty("alongside.candidate.max-speed-knots", "3.0");
        props.setProperty("alongside.candidate.max-time-gap-minutes", "5");
        props.setProperty("alongside.candidate.min-duration-minutes", "30");
        props.setProperty("alongside.candidate.min-points", "5");
        props.setProperty("alongside.confirm.max-distance-nm", "0.08");
        props.setProperty("alongside.confirm.max-speed-knots", "1.5");
        props.setProperty("alongside.confirm.max-relative-speed-knots", "0.5");
        props.setProperty("alongside.confirm.high-confidence-time-gap-minutes", "2");
        props.setProperty("alongside.confirm.min-duration-minutes", "45");
        props.setProperty("alongside.confirm.min-points", "8");
        props.setProperty("alongside.heading.parallel-threshold-degrees", "20");
        props.setProperty("alongside.ais-gap-minutes", "15");
        return AlongsideConfig.fromProperties(props);
    }

    private PairProximityEvidence evidence(int index) {
        Instant time = Instant.parse("2026-06-29T00:00:00Z").plusSeconds(index * 360L);
        return evidenceAt(time);
    }

    private PairProximityEvidence evidenceAt(Instant time) {
        return evidenceAt(time, VesselType.TANKER, VesselClass.HIGH_PRIORITY,
            VesselType.TANKER, VesselClass.HIGH_PRIORITY, PairPriority.HIGH);
    }

    private PairProximityEvidence evidenceAt(Instant time, VesselType typeA, VesselClass classA,
                                             VesselType typeB, VesselClass classB, PairPriority priority) {
        PairProximityEvidence evidence = new PairProximityEvidence();
        evidence.setPairId("100-200");
        evidence.setMmsiA(100L);
        evidence.setMmsiB(200L);
        evidence.setEvidenceTime(time);
        evidence.setPointATime(time);
        evidence.setPointBTime(time.plusSeconds(30L));
        evidence.setLonA(121.0d);
        evidence.setLatA(31.0d);
        evidence.setLonB(121.001d);
        evidence.setLatB(31.0d);
        evidence.setSpeedA(1.0d);
        evidence.setSpeedB(1.1d);
        evidence.setCourseA(90.0d);
        evidence.setCourseB(91.0d);
        evidence.setDistanceNm(0.05d);
        evidence.setTimeGapSeconds(30L);
        evidence.setRelativeSpeedKnots(0.1d);
        evidence.setHeadingParallelDiff(1.0d);
        evidence.setPairPriority(priority);
        evidence.setVesselA(new VesselProfile(100L, typeA, classA, null));
        evidence.setVesselB(new VesselProfile(200L, typeB, classB, null));
        return evidence;
    }
}
