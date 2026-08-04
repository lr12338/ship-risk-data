package com.hifleet.regionbehavior;

import com.hifleet.tankeropensea.ExclusionDecision;
import com.hifleet.tankeropensea.PortAnchorBerthExclusionService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PortAnchorBerthOperationalAreaContextProvider implements OperationalAreaContextProvider {
    private final PortAnchorBerthExclusionService exclusionService;

    @Autowired
    public PortAnchorBerthOperationalAreaContextProvider(PortAnchorBerthExclusionService exclusionService) {
        this.exclusionService = exclusionService;
    }

    @Override
    public OperationalAreaContext classify(List<TrajectoryPoint> points) {
        try {
            ExclusionDecision decision = exclusionService.classify(points, exclusionService.requireReady(null));
            Map<String, Object> evidence = decision.toFeatureMap();
            return new OperationalAreaContext(Boolean.TRUE.equals(evidence.get("portHit")),
                    Boolean.TRUE.equals(evidence.get("anchorageHit")),
                    Boolean.TRUE.equals(evidence.get("berthHit")), false, true);
        } catch (RuntimeException ex) {
            return OperationalAreaContext.unavailable();
        }
    }
}
