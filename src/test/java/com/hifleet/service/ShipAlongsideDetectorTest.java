package com.hifleet.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShipAlongsideDetectorTest {

    private final ShipAlongsideDetector detector = new ShipAlongsideDetector();

    @Test
    public void detectsV2EventWithScoreAndNoSceneCap() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 7, 5, 0.6, 90.0, null),
                "413193000",
                points("413193000", 0.0005, 0.0005, 7, 5, 0.5, 92.0, null),
                rule());

        assertEquals(1, traces.size());
        ShipBehaviorEvent event = traces.get(0).getEvent();
        assertEquals("ALONGSIDE_V2_INSURANCE", event.getRuleVersion());
        assertEquals("HIGH", event.getConfidenceLevel());
        assertEquals("VALID_RISK_EVENT", event.getExclusionFlag());
        assertTrue(event.getEventScore() > 0.0);
        assertTrue(event.getScoreDetailJson().contains("avgRelativeSpeed"));
        assertTrue(severityRank(event.getSeverityLevel()) <= 2);
    }

    @Test
    public void downgradesAnchoredOrMooredScene() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 7, 5, 0.3, 90.0, "ANCHORED"),
                "413193000",
                points("413193000", 0.0005, 0.0005, 7, 5, 0.3, 90.0, null),
                rule());

        ShipBehaviorEvent event = traces.get(0).getEvent();
        assertEquals("DOWNGRADED_NORMAL_LIKELY", event.getExclusionFlag());
        assertEquals("NAV_STATUS_ANCHORED_OR_MOORED", event.getExclusionReason());
    }

    @Test
    public void rejectsLargeTimeGap() {
        List<TrajectoryPoint> own = points("413433610", 0.0, 0.0, 7, 5, 0.5, 90.0, null);
        List<TrajectoryPoint> target = pointsFromMinute("413193000", 11, 0.0005, 0.0005, 7, 30, 0.5, 90.0, null);

        assertTrue(detector.detectWithTrace("413433610", own, "413193000", target, rule()).isEmpty());
    }

    @Test
    public void rejectsShortPassingEncounter() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 2, 5, 0.5, 90.0, null),
                "413193000",
                points("413193000", 0.0005, 0.0005, 2, 5, 0.5, 90.0, null),
                rule());

        assertTrue(traces.isEmpty());
    }

    @Test
    public void mergesSamePairNearbySegments() {
        List<TrajectoryPoint> own = new ArrayList<TrajectoryPoint>();
        own.addAll(points("413433610", 0.0, 0.0, 4, 5, 0.5, 90.0, null));
        own.addAll(pointsFromMinute("413433610", 45, 0.0002, 0.0002, 4, 5, 0.5, 90.0, null));
        List<TrajectoryPoint> target = new ArrayList<TrajectoryPoint>();
        target.addAll(points("413193000", 0.0005, 0.0005, 4, 5, 0.5, 90.0, null));
        target.addAll(pointsFromMinute("413193000", 45, 0.0007, 0.0007, 4, 5, 0.5, 90.0, null));

        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610", own, "413193000", target, rule());

        assertEquals(1, traces.size());
        assertEquals(8, traces.get(0).getEvent().getPointCount().intValue());
        assertEquals(8, traces.get(0).getAlongsideEvidencePairs().size());
        for (int i = 0; i < 8; i++) {
            assertEquals(i, traces.get(0).getAlongsideEvidencePairs().get(i).getOwnPointIndex());
            assertEquals(i, traces.get(0).getAlongsideEvidencePairs().get(i).getTargetPointIndex());
        }
    }

    @Test
    public void extractsLowSpeedWindowsForAutoMode() {
        List<TrajectoryPoint> points = points("413433610", 0.0, 0.0, 5, 5, 3.5, 90.0, null);

        List<ShipAlongsideDetector.LowSpeedWindow> windows = detector.extractLowSpeedWindows(
                "413433610", points, 4.0, 10, 30);

        assertFalse(windows.isEmpty());
        assertEquals("413433610", windows.get(0).getMmsi());
    }

    @Test
    public void v3LowSpeedWindowExcludesStationaryTrack() {
        List<ShipAlongsideDetector.LowSpeedWindow> windows = detector.extractLowSpeedWindows(
                "413433610", points("413433610", 0.0, 0.0, 13, 5, 0.5, 90.0, null),
                1.0, 3.0, 30, 30);

        assertTrue(windows.isEmpty());
    }

    @Test
    public void v3OpenSeaMerchantPairProducesBehaviorFeaturesAndHighRisk() {
        ShipAlongsideDetector.AlongsideContext context = ShipAlongsideDetector.AlongsideContext.merchantPair(
                "油轮", "散货船", false, false, true, 2);

        ShipBehaviorEvent event = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, 90.0, null),
                "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 1.7, 94.0, null),
                v3Rule(), context).get(0).getEvent();

        assertEquals("ALONGSIDE_V3_INSURANCE", event.getRuleVersion());
        assertTrue(event.getEventScore() > 50.0);
        assertFalse(event.getOpenSea());
        assertFalse(event.getNearPort());
        assertFalse(event.getNearAnchor());
        assertTrue(event.getParallelRatio() >= 0.8);
        assertTrue(event.getDistanceVarianceNm2() >= 0.0);
        assertTrue(event.getScoreDetailJson().contains("\"outsideKnownPortAndAnchor\":true"));
    }

    @Test
    public void v3RejectsOppositeDirectionEncounter() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, 0.0, null),
                "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 1.8, 180.0, null),
                v3Rule(), ShipAlongsideDetector.AlongsideContext.merchantPair(
                        "油轮", "集装箱船", false, false, true, 2));

        assertTrue(traces.isEmpty());
    }

    @Test
    public void v3MissingCourseCannotBecomeHighRisk() {
        ShipBehaviorEvent event = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, -1.0, null),
                "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 1.7, -1.0, null),
                v3Rule(), ShipAlongsideDetector.AlongsideContext.merchantPair(
                        "油轮", "集装箱船", false, false, true, 2)).get(0).getEvent();

        assertEquals("MEDIUM", event.getConfidenceLevel());
        assertTrue(severityRank(event.getSeverityLevel()) <= 2);
        assertTrue(event.getScoreDetailJson().contains("\"courseDifferenceDegree\":-1"));
        assertTrue(event.getScoreDetailJson().contains("\"weakCandidate\":true"));
    }

    @Test
    public void v3RoutesSpeedBelowUnderwayBandToStationaryReview() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 0.5, 90.0, null),
                "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 0.5, 92.0, null),
                v3Rule(), ShipAlongsideDetector.AlongsideContext.merchantPair(
                        "油轮", "集装箱船", false, false, true, 2));

        assertEquals(1, traces.size());
        assertTrue(traces.get(0).getEvent().getScoreDetailJson().contains("STATIONARY_SIDE_BY_SIDE_REVIEW"));
    }

    @Test
    public void v3ExcludesPortOperationalAndAnchorageScenes() {
        List<TrajectoryPoint> own = points("413433610", 0.0, 0.0, 13, 5, 1.5, 90.0, null);
        List<TrajectoryPoint> target = points("413193000", 0.001, 0.001, 13, 5, 1.4, 92.0, null);

        ShipBehaviorEvent portEvent = detector.detectWithTrace("413433610", own, "413193000", target,
                v3Rule(), ShipAlongsideDetector.AlongsideContext.merchantPair(
                        "油轮", "集装箱船", true, false, false, 8)).get(0).getEvent();
        ShipBehaviorEvent anchorEvent = detector.detectWithTrace("413433610", own, "413193000", target,
                v3Rule(), ShipAlongsideDetector.AlongsideContext.merchantPair(
                        "油轮", "集装箱船", false, true, false, 8)).get(0).getEvent();

        assertEquals("EXCLUDED_NORMAL", portEvent.getExclusionFlag());
        assertEquals("PORT_OPERATION_LIKELY", portEvent.getExclusionReason());
        assertEquals("EXCLUDED_NORMAL", anchorEvent.getExclusionFlag());
        assertEquals("ANCHORAGE_WAITING_LIKELY", anchorEvent.getExclusionReason());
        assertTrue(portEvent.getEventScore() < anchorEvent.getEventScore());
    }

    @Test
    public void v3KeepsNonMerchantCounterpartyForReview() {
        List<ShipBehaviorTrace> traces = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, 90.0, null),
                "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 1.7, 92.0, null),
                v3Rule(), ShipAlongsideDetector.AlongsideContext.nonMerchantPair("油轮", "渔船"));

        assertEquals(1, traces.size());
        String detail = traces.get(0).getEvent().getScoreDetailJson();
        assertTrue(detail.contains("\"targetVesselRole\":\"FISHING_OR_SMALL_CRAFT\""));
        assertTrue(detail.contains("\"decisionStatus\":\"REVIEW\""));
    }

    @Test
    public void unknownTargetDoesNotLowerHighBehaviorConfidence() {
        TargetRoleClassification target = new ShipTypeClassifier().classifyTargetShipWithReason(null, null);
        ShipAlongsideDetector.AlongsideContext context = ShipAlongsideDetector.AlongsideContext.asymmetric(
                "油轮", null, OwnShipEligibility.ELIGIBLE_MERCHANT, target,
                false, false, true, true, 2);
        ShipBehaviorEvent event = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, 90.0, null), "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 1.7, 92.0, null), v3Rule(), context)
                .get(0).getEvent();
        assertEquals("HIGH", event.getConfidenceLevel());
        assertTrue(event.getScoreDetailJson().contains("\"targetTypeConfidence\":\"LOW\""));
        assertTrue(event.getScoreDetailJson().contains("\"decisionStatus\":\"REVIEW\""));
    }

    @Test
    public void doesNotReuseOneTargetPointAsSustainedEvidence() {
        List<TrajectoryPoint> oneTarget = points("413193000", 0.001, 0.001, 1, 5, 1.7, 92.0, null);
        assertTrue(detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, 90.0, null),
                "413193000", oneTarget, v3Rule()).isEmpty());
    }

    @Test
    public void detectorEvidenceIsStableForUnsortedInputAndHonorsTimeGap() {
        List<TrajectoryPoint> own = points("413433610", 0.0, 0.0, 7, 5, 1.8, 90.0, null);
        List<TrajectoryPoint> target = points("413193000", 0.001, 0.001, 7, 5, 1.7, 92.0, null);
        java.util.Collections.reverse(own);
        java.util.Collections.reverse(target);
        ShipBehaviorTrace trace = detector.detectWithTrace("413433610", own, "413193000", target,
                v3Rule()).get(0);
        assertEquals(7, trace.getAlongsideEvidencePairs().size());
        for (int i = 0; i < trace.getAlongsideEvidencePairs().size(); i++) {
            assertEquals(i, trace.getAlongsideEvidencePairs().get(i).getOwnPointIndex());
            assertEquals(i, trace.getAlongsideEvidencePairs().get(i).getTargetPointIndex());
            assertTrue(trace.getAlongsideEvidencePairs().get(i).getTimeGapSeconds()
                    <= trace.getAlongsideMaxTimeGapSeconds());
        }
    }

    @Test
    public void detectorEventCenterIsAntimeridianSafe() {
        ShipBehaviorTrace trace = detector.detectWithTrace("413433610",
                fixedLonPoints("413433610", 179.999, 7), "413193000",
                fixedLonPoints("413193000", -179.999, 7), v3Rule()).get(0);
        assertEquals(180.0, Math.abs(trace.getEvent().getCenterLon()), 0.000001);
    }

    @Test
    public void stationaryPairIsReviewRatherThanHighConfidenceParallelMotion() {
        ShipBehaviorEvent event = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 0.2, -1.0, null), "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 0.1, -1.0, null), v3Rule(),
                ShipAlongsideDetector.AlongsideContext.nonMerchantPair("油轮", "拖轮")).get(0).getEvent();
        assertTrue(event.getScoreDetailJson().contains("STATIONARY_SIDE_BY_SIDE_REVIEW"));
        assertTrue(event.getScoreDetailJson().contains("\"decisionStatus\":\"REVIEW\""));
    }

    @Test
    public void roleAndSceneProduceAuditableNonLegalInterpretations() {
        assertInterpretation("小型加油船", "PORT_APPROACH", "BUNKER_OR_SUPPLY", "OFF_PORT_BUNKERING_REVIEW", "REVIEW");
        assertInterpretation("驳船", "PORT_APPROACH", "BARGE_OR_LIGHTER", "POSSIBLE_LIGHTERING", "REVIEW");
        assertInterpretation("拖轮", "PORT_OPERATIONAL", "TUG_OR_SERVICE", "PORT_ASSISTANCE_LIKELY", "REVIEW");
        assertInterpretation("拖轮", "OFFSHORE", "TUG_OR_SERVICE", "OFF_PORT_SERVICE_CONTACT_REVIEW", "REVIEW");
        assertInterpretation(null, "OFFSHORE", "UNKNOWN", "UNKNOWN_OFFSHORE_COUNTERPARTY_CONTACT", "REVIEW");
    }

    @Test
    public void unavailableSceneIsIncompleteRatherThanOffshore() {
        TargetRoleClassification target = new ShipTypeClassifier()
                .classifyTargetShipWithReason(null, null);
        ShipAlongsideDetector.AlongsideContext context = ShipAlongsideDetector.AlongsideContext.asymmetricWithScene(
                "油轮", null, OwnShipEligibility.ELIGIBLE_MERCHANT, target,
                "UNKNOWN", null, null, Double.NaN, false, 0);

        assertFalse(context.isOutsideKnownPortAndAnchor());
        String detail = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, 90.0, null), "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 1.7, 92.0, null), v3Rule(), context)
                .get(0).getEvent().getScoreDetailJson();
        assertTrue(detail, detail.contains("SCENE_CONTEXT_INCOMPLETE"));
        assertFalse(detail, detail.contains("OUTSIDE_KNOWN_PORT_AND_ANCHOR"));
        assertTrue(detail, detail.contains("\"sceneDataAvailable\":false"));
        assertFalse(detail, detail.contains("\"nearPort\""));
        assertFalse(detail, detail.contains("\"nearAnchor\""));
        assertFalse(detail, detail.contains("\"outsideKnownPortAndAnchor\""));
    }

    private void assertInterpretation(String targetType, String sceneType, String role,
                                      String interpretation, String decision) {
        TargetRoleClassification target = new ShipTypeClassifier()
                .classifyTargetShipWithReason(targetType, null);
        ShipAlongsideDetector.AlongsideContext context = ShipAlongsideDetector.AlongsideContext.asymmetricWithScene(
                "油轮", targetType, OwnShipEligibility.ELIGIBLE_MERCHANT, target,
                sceneType, 1, "Test Port", 8.0, true, 2);
        String detail = detector.detectWithTrace("413433610",
                points("413433610", 0.0, 0.0, 13, 5, 1.8, 90.0, null), "413193000",
                points("413193000", 0.001, 0.001, 13, 5, 1.7, 92.0, null), v3Rule(), context)
                .get(0).getEvent().getScoreDetailJson();
        assertTrue(detail, detail.contains("\"targetVesselRole\":\"" + role + "\""));
        assertTrue(detail, detail.contains("\"riskInterpretation\":\"" + interpretation + "\""));
        assertTrue(detail, detail.contains("\"decisionStatus\":\"" + decision + "\""));
        assertTrue(detail, detail.contains("\"sceneType\":\"" + sceneType + "\""));
        assertFalse(detail.contains("ILLEGAL_STS"));
        assertFalse(detail.contains("SMUGGLING"));
    }

    private ShipAlongsideDetector.AlongsideRule rule() {
        ShipAlongsideDetector.AlongsideRule rule = ShipAlongsideDetector.AlongsideRule.defaultRule();
        rule.setRuleVersion("ALONGSIDE_V2_INSURANCE");
        rule.setConfirmMaxDistanceNm(0.15);
        rule.setConfirmMaxSpeed(2.0);
        rule.setMaxRelativeSpeed(1.0);
        rule.setMaxTimeGapMinutes(10);
        rule.setHighConfidenceTimeGapMinutes(5);
        rule.setWeakMinDurationMinutes(20);
        rule.setMinDurationMinutes(30);
        rule.setMinPoints(3);
        rule.setFormalMinPoints(6);
        rule.setMergeEnabled(true);
        rule.setMergeMaxGapMinutes(30);
        rule.setSceneDataAvailable(false);
        rule.setCounterpartyRiskAvailable(false);
        return rule;
    }

    private ShipAlongsideDetector.AlongsideRule v3Rule() {
        ShipAlongsideDetector.AlongsideRule rule = rule();
        rule.setRuleVersion("ALONGSIDE_V3_INSURANCE");
        rule.setConfirmMaxDistanceNm(0.27);
        rule.setConfirmMinSpeed(1.0);
        rule.setConfirmMaxSpeed(3.0);
        rule.setMinParallelRatio(0.8);
        rule.setSceneDataAvailable(true);
        return rule;
    }

    private List<TrajectoryPoint> points(String mmsi, double lon, double lat, int count, int stepMinutes,
                                         double speed, double course, String navStatus) {
        return pointsFromMinute(mmsi, 0, lon, lat, count, stepMinutes, speed, course, navStatus);
    }

    private List<TrajectoryPoint> pointsFromMinute(String mmsi, int startMinute, double lon, double lat,
                                                   int count, int stepMinutes, double speed, double course,
                                                   String navStatus) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 28, 18, 0).plusMinutes(startMinute);
        for (int i = 0; i < count; i++) {
            points.add(new TrajectoryPoint(mmsi, start.plusMinutes(i * stepMinutes),
                    lon + i * 0.00001, lat + i * 0.00001, speed, course, navStatus));
        }
        return points;
    }

    private List<TrajectoryPoint> fixedLonPoints(String mmsi, double lon, int count) {
        List<TrajectoryPoint> result = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 6, 28, 18, 0);
        for (int i = 0; i < count; i++) {
            result.add(new TrajectoryPoint(mmsi, start.plusMinutes(i * 5),
                    lon, 0.0, 1.8, 90.0, null));
        }
        return result;
    }

    private int severityRank(String severity) {
        if ("L4".equals(severity)) {
            return 4;
        }
        if ("L3".equals(severity)) {
            return 3;
        }
        if ("L2".equals(severity)) {
            return 2;
        }
        if ("L1".equals(severity)) {
            return 1;
        }
        return 0;
    }
}
