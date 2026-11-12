package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.regionbehavior.RegionBehaviorConfig;
import com.hifleet.regionbehavior.RegionGeometry;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.sts.domain.StsShadowEvent;
import com.hifleet.sts.repository.StsShadowEventRepository;
import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * STS Phase 1.2 — Region Shadow Pipeline.
 *
 * Seed → Historical Trajectory → STS Activity Window → Analysis Region Gate
 * → Spatial Replay → Self Identity Exclusion → ShipAlongsideDetector
 * → Core Region Attribution → Shadow Event → ship_sts_shadow_event
 *
 * Reuses StsBaselineService pipeline via hooks. Does NOT modify production
 * behavior tables. Writes ONLY to dataanalysis.ship_sts_shadow_event.
 *
 * Safety: writeMode must be SHADOW_ONLY.
 */
@Service
public class StsRegionShadowService {

    private static final Logger log = LoggerFactory.getLogger(StsRegionShadowService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_SPAN_DAYS = 7;
    private static final String ALGORITHM_VERSION = "STS_PHASE1_2_REGION_SHADOW_V1";
    private static final String BEHAVIOR_TYPE = "BASELINE_ALONGSIDE_CANDIDATE";

    @Autowired
    private StsSeedCatalog seedCatalog;

    @Autowired
    private StsBaselineService stsBaselineService;

    @Autowired
    private StsShadowEventRepository shadowEventRepository;

    public RegionShadowRunResult runRegionShadow(StsJobParams params) {
        validateParams(params);
        seedCatalog.loadFromFile(params.getSeedFile());

        List<String> imos = selectImos(params);
        String batchNo = params.getBatchNo();
        RegionGeometry region = RegionGeometry.parse(
                params.getRegionWkt(),
                params.getAnalysisBufferNm(),
                RegionBehaviorConfig.defaults());

        RegionShadowRunResult runResult = new RegionShadowRunResult();
        runResult.batchNo = batchNo;
        runResult.regionId = params.getRegionId();
        runResult.regionVersion = params.getRegionVersion();
        runResult.totalImos = imos.size();
        runResult.geometryFingerprint = region.getFingerprint();

        log.info("STS_REGION_SHADOW start: {} IMOs, region={}/{}, buffer={}nm, batch={}",
                imos.size(), params.getRegionId(), params.getRegionVersion(),
                params.getAnalysisBufferNm(), batchNo);

        long startMs = System.currentTimeMillis();
        int success = 0;
        int failed = 0;

        for (String imo : imos) {
            try {
                RegionShadowImoResult r = runOneImo(imo, params, batchNo, region);
                runResult.results.add(r);
                runResult.trajectoryLoadedImos++;
                runResult.trajectoryPoints += r.trajectoryPoints;
                runResult.activityWindows += r.activityWindows;
                runResult.regionActivityWindows += r.regionActivityWindows;
                runResult.regionWindowSkipped += r.regionWindowSkipped;
                runResult.replayQueries += r.replayQueries;
                runResult.replayFailures += r.replayFailures;
                runResult.replayTruncated += r.replayTruncated;
                runResult.rawCandidateTargets += r.rawCandidateTargets;
                runResult.selfIdentityRejected += r.selfIdentityRejected;
                runResult.finalCandidateTargets += r.finalCandidateTargets;
                runResult.baselineEvents += r.baselineEvents;
                runResult.regionAcceptedEvents += r.regionAcceptedEvents;
                runResult.regionRejectedEvents += r.regionRejectedEvents;
                runResult.shadowInserted += r.shadowInserted;
                runResult.shadowDuplicateSkipped += r.shadowDuplicateSkipped;
                runResult.selfPairEvents += r.selfPairEvents;
                success++;
                logImoSummary(r);
            } catch (Exception e) {
                failed++;
                runResult.failedImos.add(imo);
                log.error("STS_REGION_SHADOW IMO={} failed: {}", imo, e.getMessage(), e);
            }
        }

        runResult.durationMs = System.currentTimeMillis() - startMs;
        runResult.success = success;
        runResult.failed = failed;

        logRunSummary(runResult);
        return runResult;
    }

    private RegionShadowImoResult runOneImo(String imo, StsJobParams params, String batchNo,
                                            RegionGeometry region) {
        RegionShadowImoResult r = new RegionShadowImoResult();
        r.imo = imo;
        r.regionId = params.getRegionId();
        r.identityMode = seedCatalog.getIdentityMode(imo);

        StsBaselineService.StsPipelineHooks hooks = buildHooks(region, r, imo, params, batchNo);

        StsBaselineService.StsImoResult baseline = stsBaselineService.runOneImoWithHooks(
                imo, params, batchNo, hooks);

        r.trajectoryPoints = baseline.trajectoryPoints;
        r.activityWindows = baseline.activityWindows;
        r.replayQueries = baseline.replayQueries;
        r.replayFailures = baseline.replayFailures;
        r.replayTruncated = baseline.replayTruncated;
        r.rawCandidateTargets = baseline.rawCandidateTargets;
        r.selfIdentityRejected = baseline.selfIdentityRejected;
        r.finalCandidateTargets = baseline.finalCandidateTargets;
        r.baselineEvents = baseline.baselineEvents;
        r.selfPairEvents = baseline.selfPairEvents;
        r.durationMs = baseline.durationMs;

        return r;
    }

    private StsBaselineService.StsPipelineHooks buildHooks(RegionGeometry region,
                                                           RegionShadowImoResult r,
                                                           String ownImo, StsJobParams params,
                                                           String batchNo) {
        final String identityMode = r.identityMode;
        final String regionId = params.getRegionId();
        final String regionVersion = params.getRegionVersion();
        final String baselineRule = "ALONGSIDE_V3_BASELINE";

        return new StsBaselineService.StsPipelineHooks() {
            @Override
            public boolean acceptWindow(String mmsi, ShipAlongsideDetector.LowSpeedWindow window,
                                        List<TrajectoryPoint> windowPoints) {
                boolean accepted = false;
                for (TrajectoryPoint p : windowPoints) {
                    double lon = p.getLon();
                    double lat = p.getLat();
                    if (Double.isNaN(lon) || Double.isNaN(lat)) continue;
                    if (region.containsAnalysis(new Coordinate(lon, lat))) {
                        accepted = true;
                        break;
                    }
                }
                if (accepted) {
                    r.regionActivityWindows++;
                } else {
                    r.regionWindowSkipped++;
                }
                return accepted;
            }

            @Override
            public void onEvent(ShipBehaviorEvent event, String ownMmsi, String targetMmsi) {
                Double clon = event.getCenterLon();
                Double clat = event.getCenterLat();
                boolean inCore = clon != null && clat != null
                        && region.containsCore(new Coordinate(clon, clat));

                if (!inCore) {
                    r.regionRejectedEvents++;
                    return;
                }
                r.regionAcceptedEvents++;

                String targetImo = seedCatalog.findImoByMmsi(targetMmsi);

                String eventKey = computeEventKey(regionId, ownImo, ownMmsi, targetMmsi,
                        event.getStartTime(), event.getEndTime(), baselineRule);

                StsShadowEvent shadow = new StsShadowEvent();
                shadow.setBatchNo(batchNo);
                shadow.setEventKey(eventKey);
                shadow.setAlgorithmVersion(ALGORITHM_VERSION);
                shadow.setRegionId(regionId);
                shadow.setRegionVersion(regionVersion);
                shadow.setOwnImo(ownImo);
                shadow.setOwnMmsi(ownMmsi);
                shadow.setTargetImo(targetImo);
                shadow.setTargetMmsi(targetMmsi);
                shadow.setIdentityMode(identityMode);
                shadow.setStartTime(event.getStartTime());
                shadow.setEndTime(event.getEndTime());
                if (event.getDurationMinutes() != null) {
                    shadow.setDurationMinutes(event.getDurationMinutes().intValue());
                }
                shadow.setCenterLon(clon);
                shadow.setCenterLat(clat);
                shadow.setBehaviorType(BEHAVIOR_TYPE);
                shadow.setBaselineRule(baselineRule);
                shadow.setMinDistanceNm(event.getMinDistanceNm());
                shadow.setAvgDistanceNm(event.getAvgDistanceNm());
                shadow.setOwnAvgSpeedKn(event.getAvgSpeed());
                shadow.setBehaviorScore(event.getEventScore());
                shadow.setReviewStatus("PENDING");
                shadow.setScoreDetailJson(event.getScoreDetailJson());

                try {
                    int inserted = shadowEventRepository.insertIfAbsent(shadow);
                    if (inserted > 0) {
                        r.shadowInserted++;
                    } else {
                        r.shadowDuplicateSkipped++;
                    }
                } catch (Exception e) {
                    log.error("STS shadow insert failed for IMO={} targetMMSI={}: {}",
                            ownImo, targetMmsi, e.getMessage(), e);
                    throw new RuntimeException("shadow event insert failed", e);
                }
            }
        };
    }

    public static String computeEventKey(String regionId, String ownImo, String ownMmsi,
                                          String targetMmsi, String startTime,
                                          String endTime, String baselineRule) {
        String raw = regionId + "|" + ownImo + "|" + ownMmsi + "|" + targetMmsi + "|"
                + startTime + "|" + endTime + "|" + baselineRule;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<String> selectImos(StsJobParams params) {
        if (params.hasExplicitImos()) {
            List<String> explicit = new ArrayList<String>();
            for (String imo : params.getImos()) {
                if (seedCatalog.getByIdentity(imo).isEmpty()) {
                    log.warn("StsRegionShadowService: explicit IMO {} not found in seed catalog, skipping", imo);
                    continue;
                }
                explicit.add(imo);
            }
            if (explicit.isEmpty()) {
                throw new IllegalArgumentException("no explicit IMOs found in seed catalog");
            }
            return explicit;
        }
        throw new IllegalArgumentException("Phase 1.2 requires explicit imos list — no fleet-wide runs");
    }

    private void validateParams(StsJobParams params) {
        if (!"STS_PHASE1_REGION_SHADOW".equals(params.getMode())) {
            throw new IllegalArgumentException("unsupported mode: " + params.getMode());
        }
        if (StringUtils.isBlank(params.getSeedFile())) {
            throw new IllegalArgumentException("seedFile is required");
        }
        if (StringUtils.isBlank(params.getFrom()) || StringUtils.isBlank(params.getTo())) {
            throw new IllegalArgumentException("from and to are required");
        }
        LocalDateTime fromDt = LocalDateTime.parse(params.getFrom(), TIME_FMT);
        LocalDateTime toDt = LocalDateTime.parse(params.getTo(), TIME_FMT);
        long days = Duration.between(fromDt, toDt).toDays();
        if (days > MAX_SPAN_DAYS) {
            throw new IllegalArgumentException("time span exceeds " + MAX_SPAN_DAYS + " days: " + days);
        }
        if (params.getParallelism() != 1) {
            throw new IllegalArgumentException("parallelism must be 1, got: " + params.getParallelism());
        }
        if (!"SHADOW_ONLY".equals(params.getWriteMode())) {
            throw new IllegalArgumentException("writeMode must be SHADOW_ONLY, got: " + params.getWriteMode());
        }
        if (StringUtils.isBlank(params.getRegionId())) {
            throw new IllegalArgumentException("regionId is required");
        }
        if (StringUtils.isBlank(params.getRegionVersion())) {
            throw new IllegalArgumentException("regionVersion is required");
        }
        if (StringUtils.isBlank(params.getRegionWkt())) {
            throw new IllegalArgumentException("regionWkt is required");
        }
        if (StringUtils.isBlank(params.getBatchNo())) {
            throw new IllegalArgumentException("batchNo is required");
        }
        double buf = params.getAnalysisBufferNm();
        if (buf < 0.0 || buf > 30.0) {
            throw new IllegalArgumentException("analysisBufferNm must be 0–30 nm, got: " + buf);
        }
    }

    private void logImoSummary(RegionShadowImoResult r) {
        JSONObject s = new JSONObject();
        s.put("event", "STS_REGION_SHADOW_IMO_SUMMARY");
        s.put("imo", r.imo);
        s.put("regionId", r.regionId);
        s.put("identityMode", r.identityMode);
        s.put("trajectoryPoints", r.trajectoryPoints);
        s.put("activityWindows", r.activityWindows);
        s.put("regionActivityWindows", r.regionActivityWindows);
        s.put("regionWindowSkipped", r.regionWindowSkipped);
        s.put("replayQueries", r.replayQueries);
        s.put("replayFailures", r.replayFailures);
        s.put("replayTruncated", r.replayTruncated);
        s.put("rawCandidateTargets", r.rawCandidateTargets);
        s.put("selfIdentityRejected", r.selfIdentityRejected);
        s.put("finalCandidateTargets", r.finalCandidateTargets);
        s.put("baselineEvents", r.baselineEvents);
        s.put("regionAcceptedEvents", r.regionAcceptedEvents);
        s.put("regionRejectedEvents", r.regionRejectedEvents);
        s.put("shadowInserted", r.shadowInserted);
        s.put("shadowDuplicateSkipped", r.shadowDuplicateSkipped);
        s.put("selfPairEvents", r.selfPairEvents);
        s.put("durationMs", r.durationMs);
        log.info(s.toJSONString());
    }

    private void logRunSummary(RegionShadowRunResult r) {
        JSONObject s = new JSONObject();
        s.put("event", "STS_REGION_SHADOW_SUMMARY");
        s.put("batchNo", r.batchNo);
        s.put("regionId", r.regionId);
        s.put("regionVersion", r.regionVersion);
        s.put("geometryFingerprint", r.geometryFingerprint);
        s.put("requestedImos", r.totalImos);
        s.put("succeededImos", r.success);
        s.put("failedImos", r.failed);
        s.put("trajectoryLoadedImos", r.trajectoryLoadedImos);
        s.put("trajectoryPoints", r.trajectoryPoints);
        s.put("activityWindows", r.activityWindows);
        s.put("regionActivityWindows", r.regionActivityWindows);
        s.put("regionWindowSkipped", r.regionWindowSkipped);
        s.put("replayQueries", r.replayQueries);
        s.put("replayFailures", r.replayFailures);
        s.put("replayTruncated", r.replayTruncated);
        s.put("rawCandidateTargets", r.rawCandidateTargets);
        s.put("selfIdentityRejected", r.selfIdentityRejected);
        s.put("finalCandidateTargets", r.finalCandidateTargets);
        s.put("baselineEvents", r.baselineEvents);
        s.put("regionAcceptedEvents", r.regionAcceptedEvents);
        s.put("regionRejectedEvents", r.regionRejectedEvents);
        s.put("shadowInserted", r.shadowInserted);
        s.put("shadowDuplicateSkipped", r.shadowDuplicateSkipped);
        s.put("selfPairEvents", r.selfPairEvents);
        s.put("durationMs", r.durationMs);
        log.info(s.toJSONString());
    }

    public static class RegionShadowRunResult {
        public String batchNo;
        public String regionId;
        public String regionVersion;
        public String geometryFingerprint;
        public int totalImos;
        public int success;
        public int failed;
        public List<String> failedImos = new ArrayList<String>();
        public long durationMs;
        public List<RegionShadowImoResult> results = new ArrayList<RegionShadowImoResult>();

        public int trajectoryLoadedImos;
        public int trajectoryPoints;
        public int activityWindows;
        public int regionActivityWindows;
        public int regionWindowSkipped;
        public int replayQueries;
        public int replayFailures;
        public int replayTruncated;
        public int rawCandidateTargets;
        public int selfIdentityRejected;
        public int finalCandidateTargets;
        public int baselineEvents;
        public int regionAcceptedEvents;
        public int regionRejectedEvents;
        public int shadowInserted;
        public int shadowDuplicateSkipped;
        public int selfPairEvents;

        public boolean isSuccess() { return failed == 0; }

        public String getSummary() {
            return String.format(
                    "STS region shadow: %d/%d IMOs, %d windows in region, %d shadow inserted, %dms",
                    success, totalImos, regionActivityWindows, shadowInserted, durationMs);
        }
    }

    public static class RegionShadowImoResult {
        public String imo;
        public String regionId;
        public String identityMode;
        public int trajectoryPoints;
        public int activityWindows;
        public int regionActivityWindows;
        public int regionWindowSkipped;
        public int replayQueries;
        public int replayFailures;
        public int replayTruncated;
        public int rawCandidateTargets;
        public int selfIdentityRejected;
        public int finalCandidateTargets;
        public int baselineEvents;
        public int regionAcceptedEvents;
        public int regionRejectedEvents;
        public int shadowInserted;
        public int shadowDuplicateSkipped;
        public int selfPairEvents;
        public long durationMs;
    }
}
