package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.job.services.ShipBehaviorAnalysisService;
import com.hifleet.service.ShipAlongsideDetector;
import com.hifleet.sts.domain.StsIdentityRecord;
import com.hifleet.sts.domain.StsJobParams;
import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * STS Phase 1 Baseline Pipeline.
 *
 * Normalized Seed CSV
 *   → IMO/MMSI Identity
 *   → Historical Trajectory
 *   → Production Alongside history compute
 *   → Own Identity Exclusion
 *   → LOG_ONLY summary
 *
 * Does NOT write any production events. Does NOT modify any production components.
 */
@Service
public class StsBaselineService {

    private static final Logger log = LoggerFactory.getLogger(StsBaselineService.class);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_SPAN_DAYS = 7;

    @Autowired
    private StsSeedCatalog seedCatalog;

    @Autowired
    private ShipBehaviorAnalysisService analysisService;

    public StsRunResult runBaseline(StsJobParams params) {
        validateParams(params);
        seedCatalog.loadFromFile(params.getSeedFile());

        List<String> imos = selectImos(params);
        String batchNo = "sts-baseline-" + System.currentTimeMillis();
        StsRunResult runResult = new StsRunResult();
        runResult.batchNo = batchNo;
        runResult.totalImos = imos.size();

        log.info("STS_PHASE1_BASELINE start: {} IMOs (explicit={}), {} → {}, batch={}",
                imos.size(), params.hasExplicitImos(), params.getFrom(), params.getTo(), batchNo);

        long startMs = System.currentTimeMillis();
        int success = 0;
        int failed = 0;

        for (String imo : imos) {
            try {
                StsImoResult r = runOneImo(imo, params, batchNo);
                runResult.results.add(r);
                success++;
                logSummary(r);
            } catch (Exception e) {
                failed++;
                log.error("STS_PHASE1_BASELINE IMO={} failed: {}", imo, e.getMessage(), e);
            }
        }

        runResult.durationMs = System.currentTimeMillis() - startMs;
        runResult.success = success;
        runResult.failed = failed;

        log.info("STS_PHASE1_BASELINE complete: success={} failed={} durationMs={}",
                success, failed, runResult.durationMs);

        return runResult;
    }

    public StsImoResult runOneImo(String imo, StsJobParams params, String batchNo) {
        return runOneImoWithHooks(imo, params, batchNo, null, seedCatalog);
    }

    public StsImoResult runOneImoWithHooks(String imo, StsJobParams params, String batchNo,
                                            StsPipelineHooks hooks) {
        return runOneImoWithHooks(imo, params, batchNo, hooks, seedCatalog);
    }

    public StsImoResult runOneImoWithHooks(String imo, StsJobParams params, String batchNo,
                                            StsPipelineHooks hooks, StsSeedLookup seedLookup) {
        long startMs = System.currentTimeMillis();
        StsImoResult r = new StsImoResult();
        r.imo = imo;
        r.from = params.getFrom();
        r.to = params.getTo();
        r.identityMode = seedLookup.getIdentityMode(imo);

        List<StsIdentityRecord> records = seedLookup.getByIdentity(imo);
        Set<String> ownIdentityMmsis = new HashSet<String>();
        for (StsIdentityRecord rec : records) {
            ownIdentityMmsis.add(rec.getMmsi());
            r.queriedMmsis.add(rec.getMmsi());
        }
        r.ownIdentityMmsis = ownIdentityMmsis;

        final Set<String> rejectedSelfMmsis = new LinkedHashSet<String>();
        for (final StsIdentityRecord rec : records) {
            final String mmsi = rec.getMmsi();
            ShipBehaviorAnalysisService.AlongsideWindowResult computed =
                    analysisService.computeAlongsideHistory(mmsi, params.getFrom(), params.getTo(), batchNo,
                            new ShipBehaviorAnalysisService.AlongsideComputePolicy() {
                                @Override
                                public boolean acceptWindow(ShipAlongsideDetector.LowSpeedWindow window,
                                                            List<TrajectoryPoint> points) {
                                    return hooks == null || hooks.acceptWindow(mmsi, window, points);
                                }

                                @Override
                                public boolean acceptCandidate(String targetMmsi) {
                                    String targetImo = seedLookup.findImoByMmsi(targetMmsi);
                                    boolean self = ownIdentityMmsis.contains(targetMmsi)
                                            || (targetImo != null && targetImo.equals(imo));
                                    if (self) rejectedSelfMmsis.add(targetMmsi);
                                    return !self;
                                }
                            });
            r.trajectoryPoints += computed.getPoints();
            r.activityWindows += computed.getAlongsideLowSpeedWindows();
            r.replayQueries += computed.getAttemptedReplayQueries();
            r.replayFailures += computed.getFailedReplayQueries();
            r.replayTruncated += computed.getTruncatedReplayQueryCount();
            r.rawCandidateTargets += computed.getCandidateTargets()
                    + computed.getPolicyRejectedCandidates();
            r.selfIdentityRejected += computed.getPolicyRejectedCandidates();
            r.finalCandidateTargets += computed.getCandidateTargets();
            r.baselineEvents += computed.getEvents();
            for (ShipBehaviorTrace trace : computed.getTraces()) {
                ShipBehaviorEvent event = trace.getEvent();
                String targetMmsi = event.getTargetMmsi();
                String targetImo = seedLookup.findImoByMmsi(targetMmsi);
                if (ownIdentityMmsis.contains(targetMmsi)
                        || (targetImo != null && targetImo.equals(imo))) {
                    r.selfPairEvents++;
                    continue;
                }
                if (hooks != null) hooks.onEvent(event, mmsi, targetMmsi);
            }
        }
        r.rejectedSelfMmsis = rejectedSelfMmsis;

        r.durationMs = System.currentTimeMillis() - startMs;
        return r;
    }

    private List<String> selectImos(StsJobParams params) {
        if (params.hasExplicitImos()) {
            List<String> explicit = new ArrayList<String>();
            for (String imo : params.getImos()) {
                if (seedCatalog.getByIdentity(imo).isEmpty()) {
                    log.warn("StsBaselineService: explicit IMO {} not found in seed catalog, skipping", imo);
                    continue;
                }
                explicit.add(imo);
            }
            return explicit;
        }
        List<String> all = seedCatalog.getAllImos();
        if (params.getShipLimit() > 0 && params.getShipLimit() < all.size()) {
            return all.subList(0, params.getShipLimit());
        }
        return all;
    }

    private void validateParams(StsJobParams params) {
        if (!"STS_PHASE1_BASELINE".equals(params.getMode())) {
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
            throw new IllegalArgumentException("Phase 1 parallelism must be 1, got: " + params.getParallelism());
        }
        if (!"LOG_ONLY".equals(params.getWriteMode())) {
            throw new IllegalArgumentException("Phase 1 writeMode must be LOG_ONLY, got: " + params.getWriteMode());
        }
    }

    private void logSummary(StsImoResult r) {
        JSONObject summary = new JSONObject();
        summary.put("event", "STS_PHASE1_SUMMARY");
        summary.put("imo", r.imo);
        summary.put("queriedMmsis", StringUtils.join(r.queriedMmsis, ","));
        summary.put("identityMode", r.identityMode);
        summary.put("from", r.from);
        summary.put("to", r.to);
        summary.put("trajectoryPoints", r.trajectoryPoints);
        summary.put("activityWindows", r.activityWindows);
        summary.put("replayQueries", r.replayQueries);
        summary.put("replayFailures", r.replayFailures);
        summary.put("replayTruncated", r.replayTruncated);
        summary.put("rawCandidateTargets", r.rawCandidateTargets);
        summary.put("selfIdentityRejected", r.selfIdentityRejected);
        summary.put("finalCandidateTargets", r.finalCandidateTargets);
        summary.put("selfPairEvents", r.selfPairEvents);
        summary.put("baselineEvents", r.baselineEvents);
        summary.put("durationMs", r.durationMs);
        log.info(summary.toJSONString());
    }

    public static class StsRunResult {
        public String batchNo;
        public int totalImos;
        public int success;
        public int failed;
        public long durationMs;
        public List<StsImoResult> results = new ArrayList<StsImoResult>();

        public boolean isSuccess() { return failed == 0; }

        public String getSummary() {
            return String.format("STS baseline: %d/%d IMOs succeeded in %dms",
                    success, totalImos, durationMs);
        }
    }

    public static class StsImoResult {
        public String imo;
        public Set<String> queriedMmsis = new LinkedHashSet<String>();
        public Set<String> ownIdentityMmsis = Collections.emptySet();
        public String identityMode;
        public String from;
        public String to;
        public int trajectoryPoints;
        public int activityWindows;
        public int replayQueries;
        public int replayFailures;
        public int replayTruncated;
        public int rawCandidateTargets;
        public int selfIdentityRejected;
        public Set<String> rejectedSelfMmsis = Collections.emptySet();
        public int finalCandidateTargets;
        public int selfPairEvents;
        public int baselineEvents;
        public long durationMs;
    }

    /**
     * Extension hooks for plugging into the baseline pipeline without
     * duplicating it. Used by STS Phase 1.2 region shadow pipeline.
     *
     * - acceptWindow: called before replay to gate windows spatially
     * - onEvent: called after detection for each baseline event
     */
    public interface StsPipelineHooks {
        boolean acceptWindow(String mmsi, ShipAlongsideDetector.LowSpeedWindow window,
                             List<TrajectoryPoint> windowPoints);

        void onEvent(ShipBehaviorEvent event, String ownMmsi, String targetMmsi);
    }
}
