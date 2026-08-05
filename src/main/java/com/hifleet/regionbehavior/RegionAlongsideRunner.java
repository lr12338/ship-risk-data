package com.hifleet.regionbehavior;

import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.job.services.RegionProductionEventFilterAdapter;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RegionAlongsideRunner {
    private final ShipBehaviorAnalysisService analysisService;
    private final RegionBehaviorConfig config;

    @Autowired
    public RegionAlongsideRunner(ShipBehaviorAnalysisService analysisService,
                                 RegionBehaviorConfig config) {
        this.analysisService = analysisService;
        this.config = config;
    }

    public RegionBehaviorRunnerResult run(String mmsi, RegionBehaviorJobParams params) {
        try {
            LocalDateTime analysisStart = RegionBehaviorTimes.parse(params.getStartTime())
                    .minusHours(params.effectiveContextBeforeHours(config));
            LocalDateTime analysisEnd = RegionBehaviorTimes.parse(params.getEndTime())
                    .plusHours(params.effectiveContextAfterHours(config));
            ShipBehaviorAnalysisService.AlongsideWindowResult computed =
                    analysisService.analysisAlongsideProductionWindow(mmsi,
                            RegionBehaviorTimes.format(analysisStart),
                            RegionBehaviorTimes.format(analysisEnd), params.getBatchNo());
            List<ShipBehaviorTrace> production = RegionProductionEventFilterAdapter
                    .validNonWeakAlongside(computed.getTraces());
            List<ShipBehaviorTrace> inWindow = new ArrayList<ShipBehaviorTrace>();
            LocalDateTime start = RegionBehaviorTimes.parse(params.getStartTime());
            LocalDateTime end = RegionBehaviorTimes.parse(params.getEndTime());
            for (ShipBehaviorTrace trace : production) {
                if (RegionBehaviorTraceWindows.overlaps(trace, start, end)) {
                    inWindow.add(trace);
                }
            }
            return RegionBehaviorRunnerResult.alongside(inWindow, computed.getEvents(),
                    computed.getPoints(), computed.getReplayQueries(), computed.getReplayFailures(),
                    computed.getTruncatedQueries(), computed.getFailed());
        } catch (RuntimeException ex) {
            return RegionBehaviorRunnerResult.failed(ex.getMessage());
        }
    }
}
