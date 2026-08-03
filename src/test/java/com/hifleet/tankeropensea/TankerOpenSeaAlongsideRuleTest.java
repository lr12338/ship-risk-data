package com.hifleet.tankeropensea;

import com.hifleet.trajectory.TrajectoryPoint;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TankerOpenSeaAlongsideRuleTest {

    private final TankerOpenSeaAlongsideRule rule =
            new TankerOpenSeaAlongsideRule(TankerOpenSeaAlongsideRule.Config.defaults());

    @Test
    public void stableSideBySideAllowedShipsOutsideExclusionsPass() {
        RuleResult result = rule.evaluate(
                track(0.0, 0.0, 0.0010, 0.0, 90.0),
                track(0.0, 0.0010, 0.0010, 0.0, 90.0),
                eligible(StandardShipType.CONTAINER),
                eligible(StandardShipType.TANKER_GAS),
                outsideExclusions(), ReplayCompleteness.COMPLETE);

        assertTrue(result.getReason(), result.isAccepted());
    }

    @Test
    public void tugFollowingInlandUnknownAndTruncatedAreRejected() {
        List<TrajectoryPoint> own = track(0.0, 0.0, 0.0010, 0.0, 90.0);
        List<TrajectoryPoint> following = track(0.0030, 0.0, 0.0010, 0.0, 90.0);

        assertFalse(rule.evaluate(own, following,
                eligible(StandardShipType.CONTAINER),
                eligible(StandardShipType.TANKER_OIL),
                outsideExclusions(), ReplayCompleteness.COMPLETE).isAccepted());
        assertFalse(rule.evaluate(own, own,
                eligible(StandardShipType.TANKER_OIL),
                rejected(StandardShipType.TUG),
                outsideExclusions(), ReplayCompleteness.COMPLETE).isAccepted());
        assertFalse(rule.evaluate(own, own,
                eligible(StandardShipType.TANKER_OIL),
                eligible(StandardShipType.CONTAINER),
                ExclusionDecision.rejected("PORT", "ports", "1",
                        fingerprint(), java.util.Collections.<String, Integer>emptyMap()),
                ReplayCompleteness.COMPLETE).isAccepted());
        assertFalse(rule.evaluate(own, own,
                eligible(StandardShipType.TANKER_OIL),
                eligible(StandardShipType.CONTAINER),
                outsideExclusions(), ReplayCompleteness.TRUNCATED_UNRESOLVED)
                .isAccepted());
        assertFalse(rule.evaluate(own, own,
                eligible(StandardShipType.TANKER_OIL),
                eligible(StandardShipType.CONTAINER),
                outsideExclusions(), ReplayCompleteness.PARTIAL_TIMEOUT)
                .isAccepted());
    }

    private List<TrajectoryPoint> track(double startLon, double startLat,
                                        double lonStep, double latStep, double course) {
        List<TrajectoryPoint> points = new ArrayList<TrajectoryPoint>();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < 13; i++) {
            points.add(new TrajectoryPoint("123456789", start.plusMinutes(i * 5L),
                    startLon + lonStep * i, startLat + latStep * i,
                    2.0, course, "under way"));
        }
        return points;
    }

    private ShipEligibilityDecision eligible(StandardShipType type) {
        return ShipEligibilityDecision.eligible(type, "EXPLICIT_ALLOWED", "HIGH");
    }

    private ShipEligibilityDecision rejected(StandardShipType type) {
        return ShipEligibilityDecision.rejected(type, "NOT_OIL_TANKER", "HIGH");
    }

    private ExclusionDecision outsideExclusions() {
        return ExclusionDecision.accepted(fingerprint(),
                java.util.Collections.<String, Integer>emptyMap());
    }

    private String fingerprint() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
}
