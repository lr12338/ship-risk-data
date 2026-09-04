package com.hifleet.sts.service;

import com.hifleet.regionbehavior.RegionAisDiscoveryResult;
import com.hifleet.regionbehavior.RegionAisDiscoveryService;
import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import com.hifleet.regionbehavior.RegionQueryPlan;
import com.hifleet.regionbehavior.RegionQueryPlanner;
import com.hifleet.regionbehavior.RegionReplaySlice;
import com.hifleet.sts.domain.StsRiskRegionRow;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Region-first fleet preselector for STS backfill.
 *
 * Reuses RegionAisDiscoveryService for adaptive time+space splitting.
 * Fail-closed: if any region's discovery is incomplete, task fails.
 */
@Service
public class StsFleetRegionPreselector {

    private static final Logger log = LoggerFactory.getLogger(StsFleetRegionPreselector.class);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DISCOVERY_CANDIDATES = 20000;

    @Autowired
    private RegionAisDiscoveryService discoveryService;

    @Autowired
    private RegionBehaviorConfig regionBehaviorConfig;

    public static class PreselectResult {
        public Set<String> discoveredTargetMmsis = new HashSet<String>();
        public Set<String> allRegionMmsis = new HashSet<String>();
        public boolean complete = true;
        public String errorMessage;
        public int queryCount;
        public int rawVessels;
        public int rawPoints;
        public int truncatedQueries;
        public int failedQueries;
        public int timeoutQueries;
    }

    public PreselectResult preselect(List<StsRiskRegionRow> regions,
                                     LocalDateTime from, LocalDateTime to,
                                     Set<String> targetMmsis, String batchNo) {
        PreselectResult result = new PreselectResult();

        for (StsRiskRegionRow regionRow : regions) {
            RegionGeometry regionGeom;
            try {
                regionGeom = RegionGeometry.parse(
                        regionRow.getGeometryWkt(),
                        regionRow.getAnalysisBufferNm(),
                        regionBehaviorConfig);
            } catch (Exception e) {
                result.complete = false;
                result.errorMessage = "region geometry parse failed: " + regionRow.getRegionCode()
                        + ": " + e.getMessage();
                return result;
            }

            // Build a simple query plan: one slice covering the whole region + time range
            // RegionAisDiscoveryService will handle time/space splitting on truncation
            RegionQueryPlan plan = buildSimplePlan(regionGeom, from, to, regionRow.getRegionCode());

            String regionBatchNo = batchNo + ":pre:" + regionRow.getRegionCode();
            RegionAisDiscoveryResult discovery = discoveryService.discover(
                    regionGeom, plan, regionBatchNo, MAX_DISCOVERY_CANDIDATES);

            result.queryCount += discovery.getQuerySlices();
            result.rawVessels += discovery.getRawVessels();
            result.rawPoints += discovery.getRawPoints();
            result.truncatedQueries += discovery.getTruncatedSlices();
            result.failedQueries += discovery.getFailedSlices();
            result.timeoutQueries += discovery.getTimeoutSlices();

            if (!discovery.isQueryComplete()) {
                result.complete = false;
                result.errorMessage = "region discovery incomplete: " + regionRow.getRegionCode()
                        + " status=" + discovery.getStatus()
                        + " (queries=" + discovery.getQuerySlices()
                        + ", truncated=" + discovery.getTruncatedSlices()
                        + ", failed=" + discovery.getFailedSlices()
                        + ", timeout=" + discovery.getTimeoutSlices() + ")";
                return result;
            }

            for (String mmsi : discovery.getCandidates().keySet()) {
                if (mmsi == null || mmsi.isEmpty()) continue;
                result.allRegionMmsis.add(mmsi);
            }

            log.info("STS_FLEET_PRESELECT region={} queries={} candidates={} complete=true",
                    regionRow.getRegionCode(), discovery.getQuerySlices(),
                    discovery.getUniqueVessels());
        }

        for (String mmsi : result.allRegionMmsis) {
            if (targetMmsis.contains(mmsi)) {
                result.discoveredTargetMmsis.add(mmsi);
            }
        }

        log.info("STS_FLEET_PRESELECT total: regions={} queries={} allMmsis={} targetMmsis={} matched={}",
                regions.size(), result.queryCount,
                result.allRegionMmsis.size(), targetMmsis.size(),
                result.discoveredTargetMmsis.size());

        return result;
    }

    private RegionQueryPlan buildSimplePlan(RegionGeometry region, LocalDateTime from,
                                            LocalDateTime to, String regionCode) {
        Envelope env = region.getAnalysisEnvelope();
        List<RegionReplaySlice> slices = new ArrayList<RegionReplaySlice>();

        // Start with one root slice covering the entire region envelope and time range.
        // RegionAisDiscoveryService will split recursively on truncation.
        String fingerprint = "sts-preselect-" + regionCode;
        slices.add(RegionReplaySlice.root(
                fingerprint, "full",
                env.getMinX(), env.getMaxX(),
                env.getMinY(), env.getMaxY(),
                from, to));

        return new RegionQueryPlan(slices);
    }
}
