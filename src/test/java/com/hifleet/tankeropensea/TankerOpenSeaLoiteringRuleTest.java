package com.hifleet.tankeropensea;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TankerOpenSeaLoiteringRuleTest {

    private final TankerOpenSeaLoiteringRule rule =
            new TankerOpenSeaLoiteringRule(TankerOpenSeaLoiteringRule.Config.defaults());

    @Test
    public void allowedShipOutsideExclusionsLoopAndBacktrackPass() {
        assertTrue(rule.evaluate(loop(), eligible(), outsideExclusions()).isAccepted());
        assertTrue(rule.evaluate(backtrack(), eligible(), outsideExclusions()).isAccepted());
    }

    @Test
    public void straightSingleTurnNonTankerPortAndUnknownAreRejected() {
        assertFalse(rule.evaluate(straight(), eligible(), outsideExclusions()).isAccepted());
        assertFalse(rule.evaluate(singleTurn(), eligible(), outsideExclusions()).isAccepted());
        assertFalse(rule.evaluate(loop(), rejected(), outsideExclusions()).isAccepted());
        assertFalse(rule.evaluate(loop(), eligible(),
                ExclusionDecision.rejected("ANCHORAGE", "anchors", "2",
                        fingerprint(), java.util.Collections.<String, Integer>emptyMap()))
                .isAccepted());
    }

    private List<TrajectoryPoint> loop() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        int count = 37;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / (count - 1);
            points.add(point(start, i, 120.0 + 0.012 * Math.cos(angle),
                    20.0 + 0.012 * Math.sin(angle),
                    (90.0 - Math.toDegrees(angle) + 360.0) % 360.0));
        }
        return points;
    }

    private List<TrajectoryPoint> backtrack() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 19; i++) {
            points.add(point(start, i, 120.0 + i * 0.0012, 20.0, 90.0));
        }
        for (int i = 19; i < 37; i++) {
            points.add(point(start, i, 120.0 + (36 - i) * 0.0012, 20.0, 270.0));
        }
        return points;
    }

    private List<TrajectoryPoint> straight() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 37; i++) {
            points.add(point(start, i, 120.0 + i * 0.0012, 20.0, 90.0));
        }
        return points;
    }

    private List<TrajectoryPoint> singleTurn() {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 37; i++) {
            if (i < 18) {
                points.add(point(start, i, 120.0 + i * 0.0012, 20.0, 90.0));
            } else {
                points.add(point(start, i, 120.0 + 18 * 0.0012,
                        20.0 + (i - 18) * 0.0012, 0.0));
            }
        }
        return points;
    }

    private TrajectoryPoint point(LocalDateTime start, int index,
                                  double lon, double lat, double course) {
        return new TrajectoryPoint("123456789", start.plusMinutes(index * 5L),
                lon, lat, 2.0, course, "under way");
    }

    private ShipEligibilityDecision eligible() {
        return ShipEligibilityDecision.eligible(
                StandardShipType.CONTAINER, "EXPLICIT_CONTAINER", "HIGH");
    }

    private ShipEligibilityDecision rejected() {
        return ShipEligibilityDecision.rejected(StandardShipType.TUG, "NOT_OIL_TANKER", "HIGH");
    }

    private ExclusionDecision outsideExclusions() {
        return ExclusionDecision.accepted(fingerprint(),
                java.util.Collections.<String, Integer>emptyMap());
    }

    private String fingerprint() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
}
