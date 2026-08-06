package com.hifleet.worldsea;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.domain.AlongsideEvidencePair;
import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Service
public class WorldSeaEventGate {
    public static final String MASK_TYPE = "WORLD_SEA_NAMED_AREA";
    public static final String PASS = "PASS";
    private final WorldSeaMaskService maskService;
    private final WorldSeaMaskProperties properties;
    private final ThreadLocal<GateStats> lastStats = new ThreadLocal<GateStats>();

    @Autowired public WorldSeaEventGate(WorldSeaMaskService maskService, WorldSeaMaskProperties properties) {
        this.maskService = maskService; this.properties = properties;
    }

    public List<ShipBehaviorTrace> filterAndAnnotate(List<ShipBehaviorTrace> traces) {
        WorldSeaMaskSnapshot snapshot = maskService.requireSnapshot();
        if (traces == null || traces.isEmpty()) {
            lastStats.set(new GateStats(0, 0, 0, 0));
            return Collections.emptyList();
        }
        List<ShipBehaviorTrace> passed = new ArrayList<ShipBehaviorTrace>();
        int evidence = 0;
        for (ShipBehaviorTrace trace : traces) {
            if (trace != null && trace.getEvent() != null) evidence += ShipBehaviorEvent.TYPE_ALONGSIDE.equals(trace.getEvent().getBehaviorType())
                    ? size(trace.getAlongsideEvidencePairs()) : size(trace.getOwnPoints());
            if (evaluate(trace, snapshot)) passed.add(trace);
        }
        lastStats.set(new GateStats(traces.size(), passed.size(), traces.size() - passed.size(), evidence));
        return passed;
    }

    public GateStats getLastStats() {
        GateStats value = lastStats.get();
        return value == null ? new GateStats(0, 0, 0, 0) : value;
    }

    private int size(List<?> values) { return values == null ? 0 : values.size(); }

    public void requirePassed(List<ShipBehaviorTrace> traces) {
        WorldSeaMaskSnapshot snapshot = maskService.requireSnapshot();
        if (traces == null) return;
        for (ShipBehaviorTrace trace : traces) {
            if (!hasPass(trace == null ? null : trace.getEvent(), snapshot)) {
                throw new IllegalStateException("WORLD_SEA_MASK formal write rejected: missing valid PASS evidence");
            }
        }
    }

    public void requireEventsPassed(List<ShipBehaviorEvent> events) {
        if (events == null) return;
        boolean guarded = false;
        for (ShipBehaviorEvent event : events) if (event != null
                && (ShipBehaviorEvent.TYPE_LOITERING.equals(event.getBehaviorType())
                || ShipBehaviorEvent.TYPE_ALONGSIDE.equals(event.getBehaviorType()))) guarded = true;
        if (!guarded) return;
        WorldSeaMaskSnapshot snapshot = maskService.requireSnapshot();
        for (ShipBehaviorEvent event : events) {
            if (!hasPass(event, snapshot)) {
                throw new IllegalStateException("WORLD_SEA_MASK formal write rejected: missing valid PASS marker");
            }
        }
    }

    private boolean evaluate(ShipBehaviorTrace trace, WorldSeaMaskSnapshot snapshot) {
        ShipBehaviorEvent event = trace == null ? null : trace.getEvent();
        if (event == null) return false;
        List<TrajectoryPoint> own = trace.getOwnPoints();
        List<TrajectoryPoint> target = trace.getTargetPoints();
        int total = 0, eligible = 0;
        PairValidation pairValidation = null;
        if (ShipBehaviorEvent.TYPE_LOITERING.equals(event.getBehaviorType())) {
            total = own == null ? 0 : own.size();
            if (own != null) for (TrajectoryPoint point : own) if (inside(point)) eligible++;
        } else if (ShipBehaviorEvent.TYPE_ALONGSIDE.equals(event.getBehaviorType())) {
            pairValidation = validateDetectorPairs(trace);
            total = pairValidation.pairs.size();
            for (AlongsideEvidencePair pair : pairValidation.pairs) {
                if (inside(pair.getOwnPoint()) && inside(pair.getTargetPoint())
                        && midpointInside(pair.getOwnPoint(), pair.getTargetPoint())) eligible++;
            }
        } else return true;
        double coverage = total == 0 ? 0.0 : (double) eligible / total;
        WorldSeaMatch center = event.getCenterLon() == null || event.getCenterLat() == null
                ? null : snapshot.match(event.getCenterLon(), event.getCenterLat());
        boolean accepted = total > 0 && eligible == total
                && (pairValidation == null || pairValidation.valid)
                && coverage >= properties.getMinEvidenceCoverage()
                && (!properties.isRequireEventCenter() || center != null);
        annotate(event, snapshot, center, eligible, total, coverage, accepted, trace, pairValidation);
        return accepted;
    }

    private boolean inside(TrajectoryPoint point) {
        return point != null && maskService.match(point.getLon(), point.getLat()) != null;
    }

    private boolean midpointInside(TrajectoryPoint a, TrajectoryPoint b) {
        if (a == null || b == null) return false;
        double left = a.getLon(), right = b.getLon();
        if (Math.abs(left - right) > 180.0) {
            if (left < right) left += 360.0; else right += 360.0;
        }
        double midpointLon = (left + right) / 2.0;
        if (midpointLon > 180.0) midpointLon -= 360.0;
        return maskService.match(midpointLon,
                (a.getLat() + b.getLat()) / 2.0) != null;
    }

    private PairValidation validateDetectorPairs(ShipBehaviorTrace trace) {
        List<AlongsideEvidencePair> pairs = trace.getAlongsideEvidencePairs();
        if (pairs == null || pairs.isEmpty()
                || !"DETECTOR_CONFIRMED_PAIRS".equals(trace.getAlongsidePairEvidenceSource())
                || trace.getAlongsideMaxTimeGapSeconds() <= 0L) {
            return new PairValidation(Collections.<AlongsideEvidencePair>emptyList(), false,
                    "WORLD_SEA_PAIR_EVIDENCE_MISSING");
        }
        int lastOwn = -1, lastTarget = -1;
        Set<Integer> ownIndexes = new HashSet<Integer>();
        Set<Integer> targetIndexes = new HashSet<Integer>();
        for (AlongsideEvidencePair pair : pairs) {
            if (pair == null || pair.getOwnPoint() == null || pair.getTargetPoint() == null
                    || pair.getOwnPoint().getTime() == null || pair.getTargetPoint().getTime() == null
                    || pair.getOwnPointIndex() <= lastOwn || pair.getTargetPointIndex() <= lastTarget
                    || !ownIndexes.add(pair.getOwnPointIndex()) || !targetIndexes.add(pair.getTargetPointIndex())) {
                return new PairValidation(pairs, false, "WORLD_SEA_PAIR_EVIDENCE_INVALID");
            }
            long actualGap = Math.abs(Duration.between(pair.getOwnPoint().getTime(),
                    pair.getTargetPoint().getTime()).getSeconds());
            if (pair.getTimeGapSeconds() != actualGap
                    || actualGap > trace.getAlongsideMaxTimeGapSeconds()
                    || !Double.isFinite(pair.getDistanceNm()) || pair.getDistanceNm() < 0.0) {
                return new PairValidation(pairs, false, "WORLD_SEA_PAIR_EVIDENCE_INVALID");
            }
            lastOwn = pair.getOwnPointIndex();
            lastTarget = pair.getTargetPointIndex();
        }
        return new PairValidation(pairs, true, null);
    }

    private void annotate(ShipBehaviorEvent event, WorldSeaMaskSnapshot snapshot, WorldSeaMatch center,
                          int eligible, int total, double coverage, boolean accepted,
                          ShipBehaviorTrace trace, PairValidation pairValidation) {
        JSONObject detail;
        try { detail = StringUtils.isBlank(event.getScoreDetailJson())
                ? new JSONObject(true) : JSON.parseObject(event.getScoreDetailJson()); }
        catch (Exception ex) { detail = new JSONObject(true); }
        detail.put("waterMaskType", MASK_TYPE);
        detail.put("worldSeaMaskEnabled", true);
        detail.put("worldSeaMaskVersion", snapshot.getDatasetVersion());
        detail.put("worldSeaMaskFingerprint", snapshot.getGeometryFingerprint());
        detail.put("worldSeaDecision", accepted ? PASS : "REJECTED");
        detail.put("worldSeaEvidenceCoverage", coverage);
        detail.put("worldSeaEligibleEvidenceCount", eligible);
        detail.put("worldSeaTotalEvidenceCount", total);
        detail.put("eventCenterInsideWorldSea", center != null);
        detail.put("worldSeaShadowThreshold", properties.getShadowMinEvidenceCoverage());
        detail.put("worldSeaShadowPass", total > 0 && coverage >= properties.getShadowMinEvidenceCoverage()
                && (pairValidation == null || pairValidation.valid)
                && (!properties.isRequireEventCenter() || center != null));
        if (ShipBehaviorEvent.TYPE_ALONGSIDE.equals(event.getBehaviorType())) {
            detail.put("worldSeaTotalPairEvidenceCount", total);
            detail.put("worldSeaEligiblePairEvidenceCount", eligible);
            detail.put("worldSeaPairCoverage", coverage);
            detail.put("alongsideUnmatchedOwnPointCount",
                    Math.max(0, trace.getAlongsideSourceOwnPointCount() - total));
            detail.put("alongsideUnmatchedTargetPointCount",
                    Math.max(0, trace.getAlongsideSourceTargetPointCount() - total));
            detail.put("alongsidePairEvidenceSource", trace.getAlongsidePairEvidenceSource());
            putPairTimeGapMetrics(detail, pairValidation == null
                    ? Collections.<AlongsideEvidencePair>emptyList() : pairValidation.pairs);
        }
        if (center != null) {
            detail.put("worldSeaAreaId", center.getAreaId());
            detail.put("worldSeaAreaName", center.getAreaName());
            detail.put("worldSeaAreaCnName", center.getAreaCnName());
        }
        event.setInsideWorldSea(center != null);
        event.setWorldSeaMaskDecision(accepted ? PASS : "REJECTED");
        event.setWaterMaskType(MASK_TYPE);
        if (center != null) {
            event.setWorldSeaAreaId(center.getAreaId());
            event.setWorldSeaAreaName(center.getAreaName());
            event.setWorldSeaAreaCnName(center.getAreaCnName());
        }
        List<String> reasons = new ArrayList<String>();
        reasons.add(accepted ? "WORLD_SEA_MASK_PASS"
                : rejectionReason(total, eligible, center, pairValidation));
        if (accepted) reasons.add("ALL_EVIDENCE_INSIDE_WORLD_SEA");
        detail.put("worldSeaReasonCodes", reasons);
        event.setScoreDetailJson(detail.toJSONString());
    }

    private String rejectionReason(int total, int eligible, WorldSeaMatch center,
                                   PairValidation pairValidation) {
        if (pairValidation != null && !pairValidation.valid) return pairValidation.reason;
        if (total == 0) return "WORLD_SEA_EVIDENCE_INSUFFICIENT";
        if (center == null) return "WORLD_SEA_EVENT_CENTER_OUTSIDE";
        return eligible < total ? "WORLD_SEA_MASK_REJECTED" : "WORLD_SEA_EVIDENCE_INSUFFICIENT";
    }

    private void putPairTimeGapMetrics(JSONObject detail, List<AlongsideEvidencePair> pairs) {
        List<Long> gaps = new ArrayList<Long>();
        long max = 0L;
        for (AlongsideEvidencePair pair : pairs) {
            gaps.add(pair.getTimeGapSeconds());
            max = Math.max(max, pair.getTimeGapSeconds());
        }
        Collections.sort(gaps);
        detail.put("alongsidePairTimeGapMaxSeconds", max);
        detail.put("alongsidePairTimeGapMedianSeconds", percentile(gaps, 0.5));
        detail.put("alongsidePairTimeGapP90Seconds", percentile(gaps, 0.9));
    }

    private long percentile(List<Long> values, double value) {
        if (values.isEmpty()) return 0L;
        return values.get(Math.max(0, (int) Math.ceil(values.size() * value) - 1));
    }

    private static final class PairValidation {
        private final List<AlongsideEvidencePair> pairs;
        private final boolean valid;
        private final String reason;
        private PairValidation(List<AlongsideEvidencePair> pairs, boolean valid, String reason) {
            this.pairs = pairs; this.valid = valid; this.reason = reason;
        }
    }

    private boolean hasPass(ShipBehaviorEvent event, WorldSeaMaskSnapshot snapshot) {
        if (event == null || (!ShipBehaviorEvent.TYPE_LOITERING.equals(event.getBehaviorType())
                && !ShipBehaviorEvent.TYPE_ALONGSIDE.equals(event.getBehaviorType()))) return true;
        try {
            JSONObject detail = JSON.parseObject(event.getScoreDetailJson());
            return detail != null && PASS.equals(detail.getString("worldSeaDecision"))
                    && snapshot.getDatasetVersion().equals(detail.getString("worldSeaMaskVersion"))
                    && snapshot.getGeometryFingerprint().equals(detail.getString("worldSeaMaskFingerprint"))
                    && detail.getDoubleValue("worldSeaEvidenceCoverage") >= properties.getMinEvidenceCoverage();
        } catch (Exception ex) { return false; }
    }

    public static final class GateStats {
        private final int rawEventCount, passEventCount, rejectedEventCount, checkedPointCount;
        GateStats(int raw, int pass, int rejected, int checked) {
            rawEventCount = raw; passEventCount = pass; rejectedEventCount = rejected; checkedPointCount = checked;
        }
        public int getRawEventCount() { return rawEventCount; }
        public int getPassEventCount() { return passEventCount; }
        public int getRejectedEventCount() { return rejectedEventCount; }
        public int getCheckedPointCount() { return checkedPointCount; }
    }
}
