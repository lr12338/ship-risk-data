package com.hifleet.sts.service;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.domain.ReplayQueryResult;
import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import com.hifleet.service.SpatialAisReplayClient;
import com.hifleet.sts.domain.StsRiskRegionRow;
import com.hifleet.trajectory.TrajectoryPoint;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Region-first fleet preselector for STS backfill.
 *
 * Given a region set and a time window, returns the set of MMSIs from the target
 * fleet that have been observed inside any of the analysis regions.
 *
 * Fail-closed: if any region's discovery is incomplete (truncated, timeout, error),
 * the entire preselection is marked incomplete and the task should fail.
 *
 * Reuses: SpatialAisReplayClient, RegionGeometry
 */
@Service
public class StsFleetRegionPreselector {

    private static final Logger log = LoggerFactory.getLogger(StsFleetRegionPreselector.class);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int DEFAULT_DEV_SIZE = 50000;
    private static final int MAX_TIME_SPLIT_DEPTH = 4;
    private static final int MIN_CHUNK_HOURS = 6;
    private static final long DAILY_CHUNK_HOURS = 24;

    @Autowired
    private SpatialAisReplayClient replayClient;

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

            discoverRegion(regionRow.getRegionCode(), regionGeom, from, to, 0,
                    batchNo, result);

            if (!result.complete) {
                return result;
            }
        }

        for (String mmsi : result.allRegionMmsis) {
            if (targetMmsis.contains(mmsi)) {
                result.discoveredTargetMmsis.add(mmsi);
            }
        }

        log.info("STS_FLEET_PRESELECT regionSet={} regions={} queries={} " +
                        "allMmsis={} targetMmsis={} matched={} complete={}",
                regions.size(), result.queryCount,
                result.allRegionMmsis.size(), targetMmsis.size(),
                result.discoveredTargetMmsis.size(), result.complete);

        return result;
    }

    private void discoverRegion(String regionCode, RegionGeometry region,
                                LocalDateTime start, LocalDateTime end, int depth,
                                String batchNo, PreselectResult result) {
        if (!result.complete) return;

        long hours = Duration.between(start, end).toHours();
        if (hours <= 0) return;

        boolean needsSplit = hours > DAILY_CHUNK_HOURS;

        if (needsSplit) {
            long halfHours = hours / 2;
            LocalDateTime mid = start.plusHours(halfHours);
            discoverRegion(regionCode, region, start, mid, depth + 1, batchNo, result);
            if (!result.complete) return;
            discoverRegion(regionCode, region, mid, end, depth + 1, batchNo, result);
            return;
        }

        result.queryCount++;

        ReplayQueryRequest req = buildRequest(region, start, end, batchNo, regionCode);
        ReplayQueryResult resp = replayClient.query(req);

        if (resp == null) {
            result.complete = false;
            result.failedQueries++;
            result.errorMessage = "null replay response for region=" + regionCode;
            return;
        }

        result.rawVessels += resp.getRawVesselCount();
        result.rawPoints += resp.getRawPointCount();

        if (resp.isTimeout()) {
            result.complete = false;
            result.timeoutQueries++;
            result.errorMessage = "replay timeout for region=" + regionCode;
            return;
        }
        if (!resp.isSuccess()) {
            result.complete = false;
            result.failedQueries++;
            result.errorMessage = "replay failed for region=" + regionCode
                    + ": " + resp.getErrorMessage();
            return;
        }

        if (resp.isTruncated()) {
            if (depth < MAX_TIME_SPLIT_DEPTH && hours > MIN_CHUNK_HOURS) {
                result.truncatedQueries++;
                long halfHours = hours / 2;
                if (halfHours < MIN_CHUNK_HOURS) halfHours = MIN_CHUNK_HOURS;
                LocalDateTime mid = start.plusHours(halfHours);
                if (mid.isBefore(end) && mid.isAfter(start)) {
                    discoverRegion(regionCode, region, start, mid, depth + 1, batchNo, result);
                    if (!result.complete) return;
                    discoverRegion(regionCode, region, mid, end, depth + 1, batchNo, result);
                    return;
                }
            }
            result.complete = false;
            result.truncatedQueries++;
            result.errorMessage = "replay truncated at max depth for region=" + regionCode
                    + " (depth=" + depth + ", hours=" + hours + ")";
            return;
        }

        Map<String, List<TrajectoryPoint>> tracks = resp.getTracksByMmsi();
        if (tracks != null) {
            for (Map.Entry<String, List<TrajectoryPoint>> entry : tracks.entrySet()) {
                String mmsi = entry.getKey();
                if (mmsi == null || mmsi.isEmpty()) continue;
                List<TrajectoryPoint> points = entry.getValue();
                if (points == null || points.isEmpty()) continue;
                boolean inside = false;
                for (TrajectoryPoint p : points) {
                    if (p == null || p.getTime() == null) continue;
                    double lon = p.getLon();
                    double lat = p.getLat();
                    if (Double.isNaN(lon) || Double.isNaN(lat)) continue;
                    if (region.containsAnalysis(new Coordinate(lon, lat))) {
                        inside = true;
                        break;
                    }
                }
                if (inside) {
                    result.allRegionMmsis.add(mmsi);
                }
            }
        }
    }

    private ReplayQueryRequest buildRequest(RegionGeometry region,
                                            LocalDateTime start, LocalDateTime end,
                                            String batchNo, String regionCode) {
        ReplayQueryRequest req = new ReplayQueryRequest();
        req.setStartTime(start.format(TIME_FMT));
        req.setEndTime(end.format(TIME_FMT));
        org.locationtech.jts.geom.Envelope env = region.getAnalysisEnvelope();
        req.setMinLon(env.getMinX());
        req.setMaxLon(env.getMaxX());
        req.setMinLat(env.getMinY());
        req.setMaxLat(env.getMaxY());
        req.setDevSize(DEFAULT_DEV_SIZE);
        req.setBatchNo(batchNo + ":preselect:" + regionCode);
        return req;
    }
}
