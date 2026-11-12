package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.sts.domain.StsIdentityRecord;
import com.hifleet.sts.domain.StsRegionSetJobParams;
import com.hifleet.sts.domain.StsRiskRegionRow;
import com.hifleet.sts.domain.StsSeedIdentityRow;
import com.hifleet.sts.domain.StsShadowEvent;
import com.hifleet.sts.repository.StsRiskRegionRepository;
import com.hifleet.sts.repository.StsSeedIdentityRepository;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StsRegionSetShadowService {

    private static final Logger log = LoggerFactory.getLogger(StsRegionSetShadowService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_SPAN_DAYS = 7;
    private static final int MAX_SHIP_LIMIT = 100;
    private static final String ALGORITHM_VERSION = "STS_PHASE1_3_REGION_SET_SHADOW_V1";
    private static final String BEHAVIOR_TYPE = "BASELINE_ALONGSIDE_CANDIDATE";
    private static final String BASELINE_RULE = "ALONGSIDE_V3_BASELINE";

    @Autowired
    private StsSeedIdentityRepository seedIdentityRepository;

    @Autowired
    private StsRiskRegionRepository riskRegionRepository;

    @Autowired
    private StsBaselineService stsBaselineService;

    @Autowired
    private StsShadowEventRepository shadowEventRepository;

    public RegionSetShadowRunResult runRegionSetShadow(StsRegionSetJobParams params) {
        validateParams(params);

        int actualCount = seedIdentityRepository.countDistinctImos(params.getSeedBatchNo());
        if (actualCount != params.getExpectedShipCount()) {
            throw new IllegalArgumentException(
                    "expectedShipCount mismatch: expected=" + params.getExpectedShipCount()
                            + ", actual=" + actualCount
                            + " (seedBatchNo=" + params.getSeedBatchNo() + ")");
        }

        List<String> imos = seedIdentityRepository.selectImos(
                params.getSeedBatchNo(), params.getShipOffset(), params.getShipLimit());
        int selectedShipCount = imos.size();

        List<StsSeedIdentityRow> identityRows = seedIdentityRepository.selectIdentityRows(
                params.getSeedBatchNo(), imos);
        DbBackedSeedCatalog catalog = new DbBackedSeedCatalog(identityRows);

        List<StsRiskRegionRow> regionRows = riskRegionRepository.loadRegionSet(params.getRegionSetCode());
        if (regionRows == null || regionRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "no enabled regions found for regionSetCode=" + params.getRegionSetCode());
        }
        StsRegionSetGeometry regionSet = new StsRegionSetGeometry(
                params.getRegionSetCode(), regionRows);

        RegionSetShadowRunResult runResult = new RegionSetShadowRunResult();
        runResult.batchNo = params.getBatchNo();
        runResult.seedBatchNo = params.getSeedBatchNo();
        runResult.expectedShipCount = params.getExpectedShipCount();
        runResult.actualShipCount = actualCount;
        runResult.shipOffset = params.getShipOffset();
        runResult.shipLimit = params.getShipLimit();
        runResult.selectedShipCount = selectedShipCount;
        runResult.regionSetCode = params.getRegionSetCode();
        runResult.resolvedRegionCount = regionSet.size();

        log.info("STS_REGION_SET_SHADOW start: seedBatchNo={}, expected={}, actual={}, " +
                        "offset={}, limit={}, selected={}, regionSet={}, regions={}, batch={}",
                params.getSeedBatchNo(), params.getExpectedShipCount(), actualCount,
                params.getShipOffset(), params.getShipLimit(), selectedShipCount,
                params.getRegionSetCode(), regionSet.size(), params.getBatchNo());

        long startMs = System.currentTimeMillis();
        int success = 0;
        int failed = 0;

        for (String imo : imos) {
            try {
                RegionSetImoResult r = runOneImo(imo, params, catalog, regionSet);
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
                runResult.multiRegionMatches += r.multiRegionMatches;
                runResult.shadowInserted += r.shadowInserted;
                runResult.shadowDuplicateSkipped += r.shadowDuplicateSkipped;
                runResult.selfPairEvents += r.selfPairEvents;
                success++;
                logImoSummary(r);
            } catch (Exception e) {
                failed++;
                runResult.failedImos.add(imo);
                log.error("STS_REGION_SET_SHADOW IMO={} failed: {}", imo, e.getMessage(), e);
            }
        }

        runResult.durationMs = System.currentTimeMillis() - startMs;
        runResult.success = success;
        runResult.failed = failed;

        logRunSummary(runResult);
        return runResult;
    }

    private RegionSetImoResult runOneImo(String imo, StsRegionSetJobParams params,
                                         DbBackedSeedCatalog catalog,
                                         StsRegionSetGeometry regionSet) {
        RegionSetImoResult r = new RegionSetImoResult();
        r.imo = imo;
        r.identityMode = catalog.getIdentityMode(imo);

        com.hifleet.sts.service.StsBaselineService.StsPipelineHooks hooks =
                buildHooks(regionSet, r, imo, params, catalog);

        StsBaselineService.StsImoResult baseline = stsBaselineService.runOneImoWithHooks(
                imo, adaptToLegacyParams(params), params.getBatchNo(), hooks, catalog);

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

    private StsBaselineService.StsPipelineHooks buildHooks(
            final StsRegionSetGeometry regionSet,
            final RegionSetImoResult r,
            final String ownImo,
            final StsRegionSetJobParams params,
            final DbBackedSeedCatalog catalog) {

        final String identityMode = r.identityMode;
        final String baselineRule = BASELINE_RULE;

        return new StsBaselineService.StsPipelineHooks() {
            @Override
            public boolean acceptWindow(String mmsi, ShipAlongsideDetector.LowSpeedWindow window,
                                        List<TrajectoryPoint> windowPoints) {
                boolean accepted = false;
                for (TrajectoryPoint p : windowPoints) {
                    double lon = p.getLon();
                    double lat = p.getLat();
                    if (Double.isNaN(lon) || Double.isNaN(lat)) continue;
                    if (regionSet.analysisContainsAny(new Coordinate(lon, lat))) {
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
                if (clon == null || clat == null) {
                    r.regionRejectedEvents++;
                    return;
                }

                StsRegionSetGeometry.AttributionResult attr =
                        regionSet.attributePrimary(new Coordinate(clon, clat));

                if (!attr.hasMatch()) {
                    r.regionRejectedEvents++;
                    return;
                }
                r.regionAcceptedEvents++;
                if (attr.matchCount() > 1) {
                    r.multiRegionMatches++;
                }

                StsRegionSetGeometry.ResolvedRegion primary = attr.primary;
                String targetImo = catalog.findImoByMmsi(targetMmsi);

                String eventKey = computeEventKey(primary.regionCode, ownImo, ownMmsi,
                        targetMmsi, event.getStartTime(), event.getEndTime(), baselineRule);

                List<String> matchedCodes = new ArrayList<String>();
                for (StsRegionSetGeometry.ResolvedRegion m : attr.allMatches) {
                    matchedCodes.add(m.regionCode);
                }

                StsShadowEvent shadow = new StsShadowEvent();
                shadow.setBatchNo(params.getBatchNo());
                shadow.setEventKey(eventKey);
                shadow.setAlgorithmVersion(ALGORITHM_VERSION);
                shadow.setSeedBatchNo(params.getSeedBatchNo());
                shadow.setRegionSetCode(params.getRegionSetCode());
                shadow.setRegionId(primary.regionCode);
                shadow.setRegionVersion(primary.regionVersion);
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

                String existingDetail = event.getScoreDetailJson();
                shadow.setScoreDetailJson(enrichWithMatchedRegions(existingDetail, matchedCodes));

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

    private String enrichWithMatchedRegions(String existingDetail, List<String> matchedCodes) {
        if (matchedCodes.size() <= 1) {
            return existingDetail;
        }
        try {
            JSONObject detail = existingDetail != null && !existingDetail.isEmpty()
                    ? JSONObject.parseObject(existingDetail)
                    : new JSONObject();
            JSONArray arr = new JSONArray();
            for (String code : matchedCodes) arr.add(code);
            detail.put("matchedRegionCodes", arr);
            return detail.toJSONString();
        } catch (Exception e) {
            return existingDetail;
        }
    }

    static String computeEventKey(String regionCode, String ownImo, String ownMmsi,
                                   String targetMmsi, String startTime,
                                   String endTime, String baselineRule) {
        String raw = regionCode + "|" + ownImo + "|" + ownMmsi + "|" + targetMmsi + "|"
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

    private void validateParams(StsRegionSetJobParams params) {
        if (StringUtils.isBlank(params.getSeedBatchNo())) {
            throw new IllegalArgumentException("seedBatchNo is required");
        }
        if (params.getExpectedShipCount() <= 0) {
            throw new IllegalArgumentException("expectedShipCount must be > 0, got: "
                    + params.getExpectedShipCount());
        }
        if (params.getShipOffset() < 0) {
            throw new IllegalArgumentException("shipOffset must be >= 0, got: "
                    + params.getShipOffset());
        }
        if (params.getShipLimit() <= 0 || params.getShipLimit() > MAX_SHIP_LIMIT) {
            throw new IllegalArgumentException("shipLimit must be 1–" + MAX_SHIP_LIMIT
                    + ", got: " + params.getShipLimit());
        }
        if (StringUtils.isBlank(params.getRegionSetCode())) {
            throw new IllegalArgumentException("regionSetCode is required");
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
        if (StringUtils.isBlank(params.getBatchNo())) {
            throw new IllegalArgumentException("batchNo is required");
        }
    }

    private com.hifleet.sts.domain.StsJobParams adaptToLegacyParams(StsRegionSetJobParams params) {
        com.hifleet.sts.domain.StsJobParams p = new com.hifleet.sts.domain.StsJobParams();
        p.setMode("STS_PHASE1_REGION_SHADOW");
        p.setFrom(params.getFrom());
        p.setTo(params.getTo());
        p.setWindowHours(24);
        p.setActivityMinSpeedKn(0.0);
        p.setActivityMaxSpeedKn(4.0);
        p.setParallelism(1);
        p.setWriteMode("SHADOW_ONLY");
        return p;
    }

    public static class DbBackedSeedCatalog implements StsSeedLookup {
        private final Map<String, List<StsIdentityRecord>> byImo =
                new LinkedHashMap<String, List<StsIdentityRecord>>();
        private final Map<String, String> mmsiToImo = new HashMap<String, String>();

        public DbBackedSeedCatalog(List<StsSeedIdentityRow> rows) {
            for (StsSeedIdentityRow row : rows) {
                StsIdentityRecord rec = row.toIdentityRecord();
                if (!byImo.containsKey(row.getImo())) {
                    byImo.put(row.getImo(), new ArrayList<StsIdentityRecord>());
                }
                byImo.get(row.getImo()).add(rec);
                mmsiToImo.put(row.getMmsi(), row.getImo());
            }
        }

        public List<StsIdentityRecord> getByIdentity(String imo) {
            List<StsIdentityRecord> list = byImo.get(imo);
            return list != null ? list : Collections.<StsIdentityRecord>emptyList();
        }

        public Set<String> getIdentityMmsis(String imo) {
            List<StsIdentityRecord> records = byImo.get(imo);
            if (records == null) return Collections.emptySet();
            Set<String> s = new HashSet<String>();
            for (StsIdentityRecord r : records) s.add(r.getMmsi());
            return s;
        }

        public String getIdentityMode(String imo) {
            List<StsIdentityRecord> records = byImo.get(imo);
            if (records == null || records.isEmpty()) return "UNKNOWN";
            return records.get(0).getIdentityMode();
        }

        public String findImoByMmsi(String mmsi) {
            return mmsiToImo.get(mmsi);
        }

        public int getTotalImos() { return byImo.size(); }
    }

    private void logImoSummary(RegionSetImoResult r) {
        JSONObject s = new JSONObject();
        s.put("event", "STS_REGION_SET_SHADOW_IMO_SUMMARY");
        s.put("imo", r.imo);
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
        s.put("multiRegionMatches", r.multiRegionMatches);
        s.put("shadowInserted", r.shadowInserted);
        s.put("shadowDuplicateSkipped", r.shadowDuplicateSkipped);
        s.put("selfPairEvents", r.selfPairEvents);
        s.put("durationMs", r.durationMs);
        log.info(s.toJSONString());
    }

    private void logRunSummary(RegionSetShadowRunResult r) {
        JSONObject s = new JSONObject();
        s.put("event", "STS_REGION_SET_SHADOW_SUMMARY");
        s.put("batchNo", r.batchNo);
        s.put("seedBatchNo", r.seedBatchNo);
        s.put("expectedShipCount", r.expectedShipCount);
        s.put("actualShipCount", r.actualShipCount);
        s.put("shipOffset", r.shipOffset);
        s.put("shipLimit", r.shipLimit);
        s.put("selectedShipCount", r.selectedShipCount);
        s.put("regionSetCode", r.regionSetCode);
        s.put("resolvedRegionCount", r.resolvedRegionCount);
        s.put("requestedImos", r.selectedShipCount);
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
        s.put("multiRegionMatches", r.multiRegionMatches);
        s.put("shadowInserted", r.shadowInserted);
        s.put("shadowDuplicateSkipped", r.shadowDuplicateSkipped);
        s.put("selfPairEvents", r.selfPairEvents);
        s.put("durationMs", r.durationMs);
        log.info(s.toJSONString());
    }

    public static class RegionSetShadowRunResult {
        public String batchNo;
        public String seedBatchNo;
        public int expectedShipCount;
        public int actualShipCount;
        public int shipOffset;
        public int shipLimit;
        public int selectedShipCount;
        public String regionSetCode;
        public int resolvedRegionCount;
        public int success;
        public int failed;
        public List<String> failedImos = new ArrayList<String>();
        public long durationMs;
        public List<RegionSetImoResult> results = new ArrayList<RegionSetImoResult>();

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
        public int multiRegionMatches;
        public int shadowInserted;
        public int shadowDuplicateSkipped;
        public int selfPairEvents;

        public boolean isSuccess() { return failed == 0; }

        public String getSummary() {
            return String.format(
                    "STS region set shadow: %d/%d IMOs, regions=%d, %d windows in region, " +
                            "%d shadow inserted, %d multi-region, %dms",
                    success, selectedShipCount, resolvedRegionCount,
                    regionActivityWindows, shadowInserted, multiRegionMatches, durationMs);
        }
    }

    public static class RegionSetImoResult {
        public String imo;
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
        public int multiRegionMatches;
        public int shadowInserted;
        public int shadowDuplicateSkipped;
        public int selfPairEvents;
        public long durationMs;
    }
}
