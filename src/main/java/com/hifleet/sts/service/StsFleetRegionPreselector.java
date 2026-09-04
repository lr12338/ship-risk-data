package com.hifleet.sts.service;

import com.hifleet.regionbehavior.RegionAisDiscoveryResult;
import com.hifleet.regionbehavior.RegionAisDiscoveryService;
import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import com.hifleet.sts.domain.StsRiskRegionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StsFleetRegionPreselector {
    private static final Logger log = LoggerFactory.getLogger(StsFleetRegionPreselector.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DISCOVERY_CANDIDATES = 20000;

    @Autowired private RegionAisDiscoveryService discoveryService;
    @Autowired private RegionBehaviorConfig regionBehaviorConfig;
    @Autowired private StsFleetDiscoveryPlanner discoveryPlanner;

    public static class PreselectResult {
        public Set<String> discoveredTargetMmsis = new HashSet<String>();
        public Set<String> allRegionMmsis = new HashSet<String>();
        public boolean complete = true;
        public boolean resumeEnabled;
        public String errorMessage;
        public int initialPlanSlices;
        public int queryCount;
        public int rawVessels;
        public int rawPoints;
        public int truncatedQueries;
        public int failedQueries;
        public int timeoutQueries;
        public long elapsedMs;
    }

    public PreselectResult preselect(List<StsRiskRegionRow> regions,
                                     LocalDateTime from, LocalDateTime to,
                                     Set<String> targetMmsis, String batchNo,
                                     String regionSetFingerprint) {
        long totalStart = System.currentTimeMillis();
        PreselectResult result = new PreselectResult();
        result.resumeEnabled = resumeAvailable();
        String targetFingerprint = fingerprint(targetMmsis);

        for (StsRiskRegionRow regionRow : regions) {
            long regionStart = System.currentTimeMillis();
            RegionGeometry regionGeom;
            try {
                regionGeom = RegionGeometry.parse(regionRow.getGeometryWkt(),
                        regionRow.getAnalysisBufferNm(), regionBehaviorConfig);
            } catch (Exception ex) {
                result.complete = false;
                result.errorMessage = "region geometry parse failed: " + regionRow.getRegionCode()
                        + ": " + ex.getMessage();
                logRegion(batchNo, regionRow, from, to, 0, null, 0,
                        System.currentTimeMillis() - regionStart, false, "GEOMETRY_FAILED");
                break;
            }

            StsFleetDiscoveryPlanner.Plan plan = discoveryPlanner.plan(regionGeom, from, to);
            result.initialPlanSlices += plan.getInitialSlices();
            log.info("STS_FLEET_REGION_DISCOVERY batchNo={} regionCode={} regionVersion={} "
                            + "sliceFrom={} sliceTo={} spatialCells={} timeBuckets={} "
                            + "initialPlanSlices={} querySlices=0 truncatedSlices=0 failedSlices=0 "
                            + "timeoutSlices=0 rawVessels=0 rawPoints=0 uniqueVessels=0 "
                            + "targetMatchedMmsis=0 elapsedMs=0 complete=false status=STARTED",
                    batchNo, regionRow.getRegionCode(), regionRow.getRegionVersion(),
                    from.format(TIME_FMT), to.format(TIME_FMT), plan.getSpatialCells(),
                    plan.getTimeBuckets(), plan.getInitialSlices());

            RegionAisDiscoveryResult discovery;
            try {
                String discoveryBatchNo = discoveryBatchNo(regionSetFingerprint, from, to,
                        regionRow, targetFingerprint);
                discovery = discoveryService.discover(regionGeom, plan.getQueryPlan(),
                        discoveryBatchNo, MAX_DISCOVERY_CANDIDATES,
                        result.resumeEnabled, result.resumeEnabled, targetMmsis);
            } catch (Exception ex) {
                result.complete = false;
                result.errorMessage = "region discovery failed: " + regionRow.getRegionCode()
                        + ": " + ex.getMessage();
                logRegion(batchNo, regionRow, from, to, plan.getInitialSlices(), null, 0,
                        System.currentTimeMillis() - regionStart, false, "FAILED");
                break;
            }

            result.queryCount += discovery.getQuerySlices();
            result.rawVessels += discovery.getRawVessels();
            result.rawPoints += discovery.getRawPoints();
            result.truncatedQueries += discovery.getTruncatedSlices();
            result.failedQueries += discovery.getFailedSlices();
            result.timeoutQueries += discovery.getTimeoutSlices();
            result.allRegionMmsis.addAll(discovery.getObservedMmsis());
            result.discoveredTargetMmsis.addAll(discovery.getCandidates().keySet());

            logRegion(batchNo, regionRow, from, to, plan.getInitialSlices(), discovery,
                    discovery.getCandidates().size(), System.currentTimeMillis() - regionStart,
                    discovery.isQueryComplete(), discovery.getStatus().name());
            if (!discovery.isQueryComplete()) {
                result.complete = false;
                result.errorMessage = "region discovery incomplete: " + regionRow.getRegionCode()
                        + " status=" + discovery.getStatus()
                        + " (queries=" + discovery.getQuerySlices()
                        + ", truncated=" + discovery.getTruncatedSlices()
                        + ", failed=" + discovery.getFailedSlices()
                        + ", timeout=" + discovery.getTimeoutSlices() + ")";
                break;
            }
        }

        result.elapsedMs = System.currentTimeMillis() - totalStart;
        log.info("STS_FLEET_PRESELECT_SUMMARY batchNo={} regionCount={} totalInitialSlices={} "
                        + "totalQuerySlices={} totalRawVessels={} totalRawPoints={} allRegionMmsis={} "
                        + "targetFleetMmsis={} matchedTargetMmsis={} totalElapsedMs={} "
                        + "complete={} resumeEnabled={}",
                batchNo, regions.size(), result.initialPlanSlices, result.queryCount,
                result.rawVessels, result.rawPoints, result.allRegionMmsis.size(),
                targetMmsis.size(), result.discoveredTargetMmsis.size(), result.elapsedMs,
                result.complete, result.resumeEnabled);
        return result;
    }

    private boolean resumeAvailable() {
        try {
            return discoveryService.isResumeAvailable();
        } catch (Exception ex) {
            log.warn("STS_FLEET_PRESELECT discovery resume disabled: {}", ex.getMessage());
            return false;
        }
    }

    private void logRegion(String batchNo, StsRiskRegionRow region, LocalDateTime from,
                           LocalDateTime to, int initialSlices,
                           RegionAisDiscoveryResult discovery, int matched, long elapsedMs,
                           boolean complete, String status) {
        log.info("STS_FLEET_REGION_DISCOVERY batchNo={} regionCode={} regionVersion={} "
                        + "sliceFrom={} sliceTo={} initialPlanSlices={} querySlices={} "
                        + "truncatedSlices={} failedSlices={} timeoutSlices={} rawVessels={} "
                        + "rawPoints={} uniqueVessels={} targetMatchedMmsis={} elapsedMs={} "
                        + "complete={} status={}",
                batchNo, region.getRegionCode(), region.getRegionVersion(),
                from.format(TIME_FMT), to.format(TIME_FMT), initialSlices,
                discovery == null ? 0 : discovery.getQuerySlices(),
                discovery == null ? 0 : discovery.getTruncatedSlices(),
                discovery == null ? 0 : discovery.getFailedSlices(),
                discovery == null ? 0 : discovery.getTimeoutSlices(),
                discovery == null ? 0 : discovery.getRawVessels(),
                discovery == null ? 0 : discovery.getRawPoints(),
                discovery == null ? 0 : discovery.getObservedUniqueVessels(),
                matched, elapsedMs, complete, status);
    }

    static String discoveryBatchNo(String regionSetFingerprint, LocalDateTime from,
                                   LocalDateTime to, StsRiskRegionRow region,
                                   String targetFingerprint) {
        String identity = "STS_PRESELECT|" + regionSetFingerprint + "|"
                + from.format(TIME_FMT) + "|" + to.format(TIME_FMT) + "|"
                + region.getRegionCode() + "|" + region.getRegionVersion() + "|"
                + targetFingerprint;
        return "STS_PRESELECT_" + sha256(identity).substring(0, 48);
    }

    private static String fingerprint(Set<String> values) {
        List<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        StringBuilder text = new StringBuilder();
        for (String value : sorted) text.append(value).append('\n');
        return sha256(text.toString());
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
