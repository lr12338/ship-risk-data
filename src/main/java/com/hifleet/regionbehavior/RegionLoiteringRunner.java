package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.job.services.RegionProductionEventFilterAdapter;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.trajectory.TrajectoryLoaderService;
import com.hifleet.trajectory.TrajectoryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RegionLoiteringRunner {
    private final TrajectoryLoaderService trajectoryLoaderService;
    private final ShipBehaviorAnalysisService analysisService;
    private final RegionBehaviorConfig config;

    @Autowired
    public RegionLoiteringRunner(TrajectoryLoaderService trajectoryLoaderService,
                                 ShipBehaviorAnalysisService analysisService,
                                 RegionBehaviorConfig config) {
        this.trajectoryLoaderService = trajectoryLoaderService;
        this.analysisService = analysisService;
        this.config = config;
    }

    public RegionBehaviorRunnerResult run(String mmsi, RegionBehaviorJobParams params) {
        try {
            LocalDateTime analysisStart = RegionBehaviorTimes.parse(params.getStartTime())
                    .minusHours(params.effectiveContextBeforeHours(config));
            LocalDateTime analysisEnd = RegionBehaviorTimes.parse(params.getEndTime())
                    .plusHours(params.effectiveContextAfterHours(config));
            List<TrajectoryPoint> points = trajectoryLoaderService.loadSafely(mmsi,
                    RegionBehaviorTimes.format(analysisStart), RegionBehaviorTimes.format(analysisEnd),
                    config.getSimplifyZoom(), config.getTrajectorySliceHours(), config.getMaxPointsPerVessel());
            List<ShipBehaviorTrace> raw = points.isEmpty()
                    ? java.util.Collections.<ShipBehaviorTrace>emptyList()
                    : analysisService.analysisLoiteringV2Production(mmsi, points, params.getBatchNo());
            List<ShipBehaviorTrace> production = RegionProductionEventFilterAdapter
                    .validNonWeakLoitering(raw);
            return RegionBehaviorRunnerResult.success(inRunWindow(production, params), raw.size(), points.size(), 0);
        } catch (RuntimeException ex) {
            return RegionBehaviorRunnerResult.failed(ex.getMessage());
        }
    }

    private List<ShipBehaviorTrace> inRunWindow(List<ShipBehaviorTrace> traces,
                                                RegionBehaviorJobParams params) {
        List<ShipBehaviorTrace> result = new ArrayList<ShipBehaviorTrace>();
        LocalDateTime start = RegionBehaviorTimes.parse(params.getStartTime());
        LocalDateTime end = RegionBehaviorTimes.parse(params.getEndTime());
        for (ShipBehaviorTrace trace : traces) {
            if (RegionBehaviorTraceWindows.overlaps(trace, start, end)) {
                result.add(trace);
            }
        }
        return result;
    }
}
