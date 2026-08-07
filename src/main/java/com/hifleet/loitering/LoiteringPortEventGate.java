package com.hifleet.loitering;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class LoiteringPortEventGate {
    private final LoiteringPortSceneService sceneService;
    private final LoiteringPortSceneProperties properties;
    private final ThreadLocal<GateStats> stats = new ThreadLocal<GateStats>();
    @Autowired public LoiteringPortEventGate(LoiteringPortSceneService sceneService,
                                              LoiteringPortSceneProperties properties) {
        this.sceneService = sceneService; this.properties = properties;
    }
    public List<ShipBehaviorTrace> filterAndAnnotate(List<ShipBehaviorTrace> traces) {
        LoiteringPortSceneSnapshot snapshot = sceneService.requireSnapshot();
        if (traces == null || traces.isEmpty()) { stats.set(new GateStats()); return Collections.emptyList(); }
        List<ShipBehaviorTrace> passed = new ArrayList<ShipBehaviorTrace>(); GateStats value = new GateStats();
        for (ShipBehaviorTrace trace : traces) { value.rawCandidateCount++; if (evaluate(trace, snapshot)) { passed.add(trace); value.scenePassCount++; } else value.sceneRejectedCount++; }
        stats.set(value); return passed;
    }
    public void requirePassed(List<ShipBehaviorTrace> traces) {
        LoiteringPortSceneSnapshot snapshot = sceneService.requireSnapshot();
        if (traces == null) return;
        for (ShipBehaviorTrace trace : traces) if (trace != null && trace.getEvent() != null
                && ShipBehaviorEvent.TYPE_LOITERING.equals(trace.getEvent().getBehaviorType())
                && trace.getEvent().getRuleVersion() != null
                && trace.getEvent().getRuleVersion().startsWith("LOITERING_V2_PORT_APPROACH_")
                && !isPassed(trace.getEvent(), snapshot))
            throw new IllegalStateException("LOITERING_PORT_SCENE formal write rejected: missing valid PASS evidence");
    }
    public GateStats getLastStats() { GateStats value=stats.get(); return value == null ? new GateStats() : value; }
    private boolean evaluate(ShipBehaviorTrace trace, LoiteringPortSceneSnapshot snapshot) {
        ShipBehaviorEvent event = trace == null ? null : trace.getEvent();
        if (event == null || !ShipBehaviorEvent.TYPE_LOITERING.equals(event.getBehaviorType())) return false;
        List<TrajectoryPoint> points = trace.getOwnPoints(); int total=points == null ? 0 : points.size(), approach=0, operational=0, anchor=0, below=0;
        double min=Double.POSITIVE_INFINITY, sum=0.0; List<Double> speeds=new ArrayList<Double>();
        for (TrajectoryPoint point : points == null ? Collections.<TrajectoryPoint>emptyList() : points) {
            boolean low=point == null || !point.isSpeedValid() || point.getSpeed() < properties.getMinSpeed();
            if (low) { below++; continue; }
            min=Math.min(min, point.getSpeed()); sum+=point.getSpeed(); speeds.add(point.getSpeed());
            LoiteringPortSceneSnapshot.Match match=snapshot.match(point.getLon(), point.getLat(), properties.getMaxDistanceNm());
            if (match.isOperational()) operational++; if (match.isAnchor()) anchor++; if (match.isEligible()) approach++;
        }
        LoiteringPortSceneSnapshot.Match center=event.getCenterLon()==null || event.getCenterLat()==null ? null
                : snapshot.match(event.getCenterLon(), event.getCenterLat(), properties.getMaxDistanceNm());
        double coverage=total == 0 ? 0.0 : (double) approach / total;
        Collections.sort(speeds); double median=speeds.isEmpty() ? Double.NaN : speeds.get((speeds.size()-1)/2);
        boolean accepted=total>0 && coverage >= properties.getMinEvidenceCoverage() && operational==0 && anchor==0 && below==0
                && center != null && center.isEligible();
        annotate(event, snapshot, center, total, approach, operational, anchor, below, min, sum, speeds.size(), median, coverage, accepted);
        return accepted;
    }
    private boolean isPassed(ShipBehaviorEvent event, LoiteringPortSceneSnapshot snapshot) {
        try { JSONObject root=JSON.parseObject(event.getScoreDetailJson()); return root != null && "PASS".equals(root.getString("loiteringSceneDecision"))
                && snapshot.getFingerprint().equals(root.getString("loiteringSceneFingerprint")); }
        catch (Exception ex) { return false; }
    }
    private void annotate(ShipBehaviorEvent event, LoiteringPortSceneSnapshot snapshot, LoiteringPortSceneSnapshot.Match center,
                          int total, int approach, int operational, int anchor, int below, double min, double sum, int speedCount,
                          double median, double coverage, boolean accepted) {
        JSONObject detail; try { detail=StringUtils.isBlank(event.getScoreDetailJson()) ? new JSONObject(true) : JSON.parseObject(event.getScoreDetailJson()); }
        catch (Exception ex) { detail=new JSONObject(true); }
        detail.put("loiteringSceneDecision", accepted ? "PASS" : "REJECTED"); detail.put("loiteringSceneVersion", event.getRuleVersion());
        detail.put("loiteringSceneFingerprint", snapshot.getFingerprint()); detail.put("portApproachMaxDistanceNm", properties.getMaxDistanceNm());
        detail.put("portApproachEvidenceCount", approach); detail.put("totalEvidenceCount", total); detail.put("portApproachEvidenceCoverage", coverage);
        detail.put("portOperationalEvidenceCount", operational); detail.put("anchorEvidenceCount", anchor); detail.put("belowMinSpeedEvidenceCount", below);
        detail.put("minSog", speedCount == 0 ? null : min); detail.put("avgSog", speedCount == 0 ? null : sum / speedCount); detail.put("medianSog", speedCount == 0 ? null : median);
        detail.put("eventCenterInsideEligiblePortApproach", center != null && center.isEligible());
        if (center != null) { detail.put("nearestPortId", center.getNearestPortId()); detail.put("nearestPortName", center.getNearestPortName()); detail.put("distanceToNearestPortNm", center.getDistanceNm()); }
        List<String> reasons=new ArrayList<String>();
        if (accepted) reasons.add("LOITERING_PORT_APPROACH_PASS"); else {
            if (below > 0) reasons.add("LOITERING_BELOW_MIN_SPEED_REJECTED"); if (operational > 0) reasons.add("LOITERING_INSIDE_PORT_OPERATIONAL_REJECTED");
            if (anchor > 0) reasons.add("LOITERING_INSIDE_ANCHORAGE_REJECTED"); if (coverage < properties.getMinEvidenceCoverage() || (center != null && !center.isWithinPortApproach())) reasons.add("LOITERING_TOO_FAR_FROM_PORT_REJECTED");
            if (coverage < properties.getMinEvidenceCoverage()) reasons.add("LOITERING_PORT_APPROACH_INSUFFICIENT_COVERAGE");
        }
        detail.put("reasonCodes", reasons); event.setScoreDetailJson(detail.toJSONString());
    }
    public static final class GateStats { private int rawCandidateCount, scenePassCount, sceneRejectedCount;
        public int getRawCandidateCount() { return rawCandidateCount; } public int getScenePassCount() { return scenePassCount; } public int getSceneRejectedCount() { return sceneRejectedCount; } }
}
