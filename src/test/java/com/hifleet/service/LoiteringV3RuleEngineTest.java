package com.hifleet.service;

import com.hifleet.service.LoiteringV3FeatureExtractor.Features;
import com.hifleet.service.LoiteringV3RuleEngine.Context;
import com.hifleet.service.LoiteringV3RuleEngine.Decision;
import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoiteringV3RuleEngineTest {

    private static final double CENTER_LON = 121.0;
    private static final double CENTER_LAT = 31.0;

    private final LoiteringV3FeatureExtractor extractor = new LoiteringV3FeatureExtractor();
    private final LoiteringV3RuleEngine ruleEngine = new LoiteringV3RuleEngine();

    @Test
    public void circleFeaturesHaveSmallEndDistanceStableRadiusAndContinuousTurn() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 2.0));

        assertEquals(180L, features.getDurationMinutes());
        assertEquals(1.0, features.getRadiusNm(), 0.08);
        assertTrue(features.getTrajectoryAreaNm2() > 2.8);
        assertTrue(features.getClosureScore() > 0.95);
        assertTrue(features.getStartEndDistanceNm() < 0.05);
        assertTrue(Math.abs(features.getSignedTurnDegree()) >= 300.0);
        assertTrue(features.getRadialCv() < 0.1);
        assertEquals(1.0, features.getLowSpeedPointRatio(), 0.0001);
    }

    @Test
    public void circleNearPortOutsideBerthCreatesFinalCircularEvent() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 2.0));

        Decision decision = ruleEngine.evaluate(features, merchantNearPort());

        assertEquals(LoiteringV3RuleEngine.STATUS_FINAL, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.PATTERN_CIRCULAR, decision.getTrajectoryPattern());
        assertTrue(decision.getRiskScore() > 0.0);
        assertTrue(decision.getConfidenceScore() >= 0.8);
        assertTrue(decision.getMatchedPatterns().contains(LoiteringV3RuleEngine.PATTERN_CIRCULAR));
    }

    @Test
    public void wideningOrbitCreatesOrbitalEvent() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < 73; i++) {
            double progress = i / 72.0;
            double radiusNm = 0.1 + 2.4 * progress;
            double angle = 4.0 * Math.PI * progress;
            double xNm = radiusNm * Math.cos(angle);
            double yNm = radiusNm * Math.sin(angle);
            double lon = CENTER_LON + xNm / (60.0 * Math.cos(Math.toRadians(CENTER_LAT)));
            double lat = CENTER_LAT + yNm / 60.0;
            points.add(point(start.plusMinutes(i * 5), lon, lat, 2.0));
        }

        Decision decision = ruleEngine.evaluate(extractor.extract(points), merchantNearPort());

        assertEquals(LoiteringV3RuleEngine.STATUS_FINAL, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.PATTERN_ORBITAL, decision.getTrajectoryPattern());
    }

    @Test
    public void straightLowSpeedTrackIsExcludedAsTransit() {
        Features features = extractor.extract(straightPoints(6.0, 37, 5, 2.0));

        Decision decision = ruleEngine.evaluate(features, merchantInsidePort());

        assertEquals(LoiteringV3RuleEngine.STATUS_EXCLUDED, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.REASON_STRAIGHT_LOW_SPEED_TRANSIT, decision.getReasonCode());
        assertEquals(0.0, decision.getRiskScore(), 0.0001);
    }

    @Test
    public void berthStayIsExcludedBeforeMovementPatternScoring() {
        Features features = extractor.extract(circlePoints(0.3, 49, 5, 0.2));
        Context context = Context.builder()
                .merchantShip(true)
                .portDataAvailable(true)
                .berthDataAvailable(true)
                .nearPort(true)
                .insidePort(true)
                .insideBerth(true)
                .nearBerthPoint(true)
                .berthStationaryMinutes(40)
                .build();

        Decision decision = ruleEngine.evaluate(features, context);

        assertEquals(LoiteringV3RuleEngine.STATUS_EXCLUDED, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.REASON_NORMAL_BERTHING, decision.getReasonCode());
        assertEquals(0.0, decision.getRiskScore(), 0.0001);
    }

    @Test
    public void sceneDataUnavailableKeepsStrongCircleForReviewOnly() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 2.0));
        Context context = Context.builder()
                .merchantShip(true)
                .nearPort(true)
                .portDataAvailable(false)
                .berthDataAvailable(false)
                .build();

        Decision decision = ruleEngine.evaluate(features, context);

        assertEquals(LoiteringV3RuleEngine.STATUS_REVIEW, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.REASON_SCENE_DATA_UNAVAILABLE, decision.getReasonCode());
    }

    @Test
    public void normalBerthingApproachIsExcluded() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 2.0));
        Context context = Context.builder()
                .merchantShip(true)
                .portDataAvailable(true)
                .berthDataAvailable(true)
                .nearPort(true)
                .minutesToBerthEntry(60)
                .berthEntryFollowedByStay(true)
                .finalApproachDirectionEfficiency(0.8)
                .finalApproachDirectionChangePerHour(20.0)
                .build();

        Decision decision = ruleEngine.evaluate(features, context);

        assertEquals(LoiteringV3RuleEngine.STATUS_EXCLUDED, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.REASON_NORMAL_BERTHING_APPROACH, decision.getReasonCode());
    }

    @Test
    public void berthEntryWithoutQualifiedStayIsNotExcludedAsNormalApproach() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 2.0));
        Context context = Context.builder()
                .merchantShip(true)
                .portDataAvailable(true)
                .berthDataAvailable(true)
                .nearPort(true)
                .minutesToBerthEntry(60)
                .finalApproachDirectionEfficiency(0.8)
                .finalApproachDirectionChangePerHour(20.0)
                .build();

        Decision decision = ruleEngine.evaluate(features, context);

        assertEquals(LoiteringV3RuleEngine.STATUS_FINAL, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.PATTERN_CIRCULAR, decision.getTrajectoryPattern());
    }

    @Test
    public void trackOutsidePortAndChannelIsExcluded() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 2.0));
        Context context = Context.builder()
                .merchantShip(true)
                .portDataAvailable(true)
                .berthDataAvailable(true)
                .build();

        Decision decision = ruleEngine.evaluate(features, context);

        assertEquals(LoiteringV3RuleEngine.STATUS_EXCLUDED, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.REASON_OUTSIDE_TARGET_AREA, decision.getReasonCode());
    }

    @Test
    public void strongCircleOutsideOneToThreeKnotsRemainsReview() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 0.5));

        Decision decision = ruleEngine.evaluate(features, merchantNearPort());

        assertEquals(LoiteringV3RuleEngine.STATUS_REVIEW, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.PATTERN_CIRCULAR, decision.getTrajectoryPattern());
        assertEquals(LoiteringV3RuleEngine.REASON_FORMAL_GATE_NOT_MET, decision.getReasonCode());
    }

    @Test
    public void smallRangeFourHourDriftCreatesWaitingPattern() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < 49; i++) {
            points.add(point(start.plusMinutes(i * 5), CENTER_LON, CENTER_LAT, 2.0));
        }

        Decision decision = ruleEngine.evaluate(extractor.extract(points), merchantNearPort());

        assertEquals(LoiteringV3RuleEngine.STATUS_FINAL, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.PATTERN_WAITING_DRIFT, decision.getTrajectoryPattern());
    }

    @Test
    public void serpentineTrackCreatesSerpentineEvent() {
        Features features = extractor.extract(serpentinePoints());

        Decision decision = ruleEngine.evaluate(features, merchantNearPort());

        assertEquals(LoiteringV3RuleEngine.STATUS_FINAL, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.PATTERN_SERPENTINE, decision.getTrajectoryPattern());
        assertTrue(features.getTurnSignChangeCount() >= 4);
        assertTrue(features.getDirectionChangePerHour() >= 90.0);
    }

    @Test
    public void repeatedReversalsCreateBackAndForthInsteadOfOrbitalEvent() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < 49; i++) {
            double xNm = i % 2 == 0 ? -0.5 : 0.5;
            double lon = CENTER_LON + xNm / (60.0 * Math.cos(Math.toRadians(CENTER_LAT)));
            points.add(point(start.plusMinutes(i * 5), lon, CENTER_LAT, 2.0));
        }

        Features features = extractor.extract(points);
        Decision decision = ruleEngine.evaluate(features, merchantNearPort());

        assertTrue(features.getReversalCount() >= 3);
        assertEquals(LoiteringV3RuleEngine.STATUS_FINAL, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.PATTERN_BACK_AND_FORTH, decision.getTrajectoryPattern());
        assertFalse(decision.getMatchedPatterns().contains(LoiteringV3RuleEngine.PATTERN_ORBITAL));
    }

    @Test
    public void nonMerchantShipIsFilteredBeforeScoring() {
        Features features = extractor.extract(circlePoints(1.0, 37, 5, 2.0));
        Context context = Context.builder()
                .merchantShip(false)
                .portDataAvailable(true)
                .berthDataAvailable(true)
                .nearPort(true)
                .build();

        Decision decision = ruleEngine.evaluate(features, context);

        assertEquals(LoiteringV3RuleEngine.STATUS_EXCLUDED, decision.getStatus());
        assertEquals(LoiteringV3RuleEngine.REASON_NON_MERCHANT_SHIP, decision.getReasonCode());
    }

    @Test
    public void stationaryPointsDoNotProduceInvalidNumericFeatures() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < 13; i++) {
            points.add(point(start.plusMinutes(i * 5), CENTER_LON, CENTER_LAT, 0.0));
        }

        Features features = extractor.extract(points);

        assertFalse(Double.isNaN(features.getClosureScore()));
        assertFalse(Double.isInfinite(features.getClosureScore()));
        assertFalse(Double.isNaN(features.getTortuosity()));
        assertFalse(Double.isInfinite(features.getTortuosity()));
        assertEquals(0.0, features.getPathLengthNm(), 0.0001);
    }

    @Test
    public void duplicatePointsAreRemovedBeforeCoverageAndPatternCalculation() {
        List<TrajectoryPoint> original = circlePoints(1.0, 37, 5, 2.0);
        List<TrajectoryPoint> duplicated = new ArrayList<TrajectoryPoint>();
        duplicated.addAll(original);
        duplicated.addAll(original);

        Features features = extractor.extract(duplicated);

        assertEquals(original.size(), features.getPointCount());
        assertTrue(features.getClosureScore() > 0.95);
    }

    @Test
    public void conflictingCoordinatesAtSameTimeAreDiscarded() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        points.add(point(start, CENTER_LON, CENTER_LAT, 2.0));
        points.add(point(start.plusMinutes(5), CENTER_LON + 0.01, CENTER_LAT, 2.0));
        points.add(point(start.plusMinutes(5), CENTER_LON - 0.01, CENTER_LAT, 2.0));
        points.add(point(start.plusMinutes(10), CENTER_LON, CENTER_LAT, 2.0));

        Features features = extractor.extract(points);

        assertEquals(2, features.getPointCount());
        assertEquals(10L, features.getDurationMinutes());
        assertEquals(0.0, features.getPathLengthNm(), 0.0001);
    }

    @Test
    public void datelineCrossingTrackKeepsLocalCenterAndRadius() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < 37; i++) {
            double angle = 2.0 * Math.PI * i / 36.0;
            double xNm = Math.cos(angle);
            double yNm = Math.sin(angle);
            double lon = normalizeLongitude(179.99
                    + xNm / (60.0 * Math.cos(Math.toRadians(CENTER_LAT))));
            double lat = CENTER_LAT + yNm / 60.0;
            points.add(point(start.plusMinutes(i * 5), lon, lat, 2.0));
        }

        Features features = extractor.extract(points);

        assertTrue(Math.abs(features.getCenterLon()) > 179.0);
        assertEquals(1.0, features.getRadiusNm(), 0.08);
        assertTrue(features.getTrajectoryAreaNm2() > 2.8);
    }

    private Context merchantNearPort() {
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

    private Context merchantInsidePort() {
        return Context.builder()
                .merchantShip(true)
                .portDataAvailable(true)
                .berthDataAvailable(true)
                .nearPort(true)
                .insidePort(true)
                .insideBerth(false)
                .nearBerthPoint(false)
                .build();
    }

    private List<TrajectoryPoint> circlePoints(double radiusNm, int count, int stepMinutes, double speed) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / (count - 1);
            double xNm = radiusNm * Math.cos(angle);
            double yNm = radiusNm * Math.sin(angle);
            double lon = CENTER_LON + xNm / (60.0 * Math.cos(Math.toRadians(CENTER_LAT)));
            double lat = CENTER_LAT + yNm / 60.0;
            points.add(point(start.plusMinutes(i * stepMinutes), lon, lat, speed));
        }
        return points;
    }

    private List<TrajectoryPoint> straightPoints(double distanceNm, int count, int stepMinutes, double speed) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < count; i++) {
            double xNm = distanceNm * i / (count - 1);
            double lon = CENTER_LON + xNm / (60.0 * Math.cos(Math.toRadians(CENTER_LAT)));
            points.add(point(start.plusMinutes(i * stepMinutes), lon, CENTER_LAT, speed));
        }
        return points;
    }

    private List<TrajectoryPoint> serpentinePoints() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        for (int i = 0; i < 49; i++) {
            double xNm = i * 0.1;
            double yNm = i % 2 == 0 ? -0.3 : 0.3;
            double lon = CENTER_LON + xNm / (60.0 * Math.cos(Math.toRadians(CENTER_LAT)));
            double lat = CENTER_LAT + yNm / 60.0;
            points.add(point(start.plusMinutes(i * 5), lon, lat, 2.0));
        }
        return points;
    }

    private TrajectoryPoint point(LocalDateTime time, double lon, double lat, double speed) {
        return new TrajectoryPoint("413193000", time, lon, lat, speed, -1.0, null);
    }

    private double normalizeLongitude(double longitude) {
        if (longitude > 180.0) {
            return longitude - 360.0;
        }
        if (longitude < -180.0) {
            return longitude + 360.0;
        }
        return longitude;
    }
}
