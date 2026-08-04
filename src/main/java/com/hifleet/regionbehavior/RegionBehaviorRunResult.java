package com.hifleet.regionbehavior;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RegionBehaviorRunResult {
    private RegionBehaviorStatus status;
    private int querySlices;
    private int completedSlices;
    private int timeoutSlices;
    private int truncatedSlices;
    private int failedSlices;
    private int rawVessels;
    private int eligibleVessels;
    private int analyzedVessels;
    private int loiteringCandidates;
    private int loiteringRegionEvents;
    private int alongsideCandidates;
    private int alongsideRegionEvents;
    private int boundaryPartialEvents;
    private int duplicatePairEvents;
    private int analysisFailures;
    private boolean queryComplete;
    private final List<RegionMatchedEvent> regionEvents = new ArrayList<RegionMatchedEvent>();
    private final List<RegionMatchedEvent> boundaryEvents = new ArrayList<RegionMatchedEvent>();
    private final List<JSONObject> failures = new ArrayList<JSONObject>();
    private String outputDirectory;

    public static RegionBehaviorRunResult fromDiscovery(RegionAisDiscoveryResult discovery) {
        RegionBehaviorRunResult result = new RegionBehaviorRunResult();
        result.querySlices = discovery.getQuerySlices();
        result.completedSlices = discovery.getCompletedSlices();
        result.timeoutSlices = discovery.getTimeoutSlices();
        result.truncatedSlices = discovery.getTruncatedSlices();
        result.failedSlices = discovery.getFailedSlices() + discovery.getParseErrorSlices();
        result.rawVessels = discovery.getRawVessels();
        result.queryComplete = discovery.isQueryComplete();
        result.status = discovery.isQueryComplete()
                ? RegionBehaviorStatus.REGION_RUN_COMPLETED : RegionBehaviorStatus.REGION_RUN_PARTIAL;
        for (RegionReplaySliceResult slice : discovery.getSliceResults()) {
            if (slice.getStatus() != RegionBehaviorStatus.COMPLETE) {
                JSONObject failure = new JSONObject(true);
                failure.put("kind", "DISCOVERY_SLICE");
                failure.put("sliceId", slice.getSliceId());
                failure.put("status", slice.getStatus().name());
                failure.put("error", slice.getErrorMessage());
                result.failures.add(failure);
            }
        }
        return result;
    }

    public JSONObject summaryJson() {
        JSONObject json = new JSONObject(true);
        json.put("status", status.name());
        json.put("querySlices", querySlices);
        json.put("completedSlices", completedSlices);
        json.put("timeoutSlices", timeoutSlices);
        json.put("truncatedSlices", truncatedSlices);
        json.put("failedSlices", failedSlices);
        json.put("rawVessels", rawVessels);
        json.put("eligibleVessels", eligibleVessels);
        json.put("analyzedVessels", analyzedVessels);
        json.put("loiteringCandidates", loiteringCandidates);
        json.put("loiteringRegionEvents", loiteringRegionEvents);
        json.put("alongsideCandidates", alongsideCandidates);
        json.put("alongsideRegionEvents", alongsideRegionEvents);
        json.put("boundaryPartialEvents", boundaryPartialEvents);
        json.put("duplicatePairEvents", duplicatePairEvents);
        json.put("analysisFailures", analysisFailures);
        json.put("queryComplete", queryComplete);
        json.put("formalEventsChanged", false);
        json.put("databaseWrites", 0);
        json.put("formalEventMutation", false);
        json.put("enforceEnabled", false);
        addShadowStatistics(json);
        if (outputDirectory != null) {
            json.put("outputDirectory", outputDirectory);
        }
        return json;
    }

    private void addShadowStatistics(JSONObject json) {
        List<RegionMatchedEvent> all = new ArrayList<RegionMatchedEvent>(regionEvents);
        all.addAll(boundaryEvents);
        json.put("baseCandidateCount", loiteringCandidates + alongsideCandidates);
        json.put("strictCoreCount", regionEvents.size());
        json.put("boundaryPartialCount", boundaryEvents.size());
        json.put("sceneOpenWaterCount", count(all, "scene", "OPEN_WATER_CONFIRMED"));
        json.put("sceneInlandRejectedCount", count(all, "scene", "WOULD_REJECT_INLAND"));
        json.put("sceneCoastalReviewCount", count(all, "scene", "COASTAL_OPERATION_REVIEW"));
        json.put("sceneUnknownCount", count(all, "scene", "SCENE_REVIEW_REQUIRED"));
        json.put("sceneConflictCount", count(all, "scene", "SCENE_CONFLICT"));
        json.put("alongsideSideBySideConfirmedCount", count(all, "behavior", "SIDE_BY_SIDE_CONFIRMED"));
        json.put("alongsideFollowingRejectedCount", count(all, "behavior", "WOULD_REJECT_FOLLOWING"));
        json.put("alongsideOvertakingRejectedCount", count(all, "behavior", "WOULD_REJECT_OVERTAKING"));
        json.put("alongsideCrossingRejectedCount", count(all, "behavior", "WOULD_REJECT_CROSSING"));
        json.put("alongsideInsufficientGeometryCount", countPrefix(all, "behavior", "INSUFFICIENT_"));
        json.put("loiteringShapeConfirmedCount", count(all, "behavior", "SHAPE_CONFIRMED"));
        json.put("loiteringStraightTransitRejectedCount", count(all, "behavior", "WOULD_REJECT_STRAIGHT_TRANSIT"));
        json.put("loiteringStationaryWaitingCount", count(all, "behavior", "WOULD_REJECT_STATIONARY_WAITING"));
        json.put("loiteringInsufficientShapeCount", count(all, "behavior", "INSUFFICIENT_SHAPE_EVIDENCE"));
        json.put("combinedWouldAcceptCount", count(all, "combined", "WOULD_ACCEPT_SHADOW"));
        json.put("combinedWouldRejectCount", countPrefix(all, "combined", "WOULD_REJECT_"));
        json.put("combinedReviewCount", countContains(all, "combined", "REVIEW"));
        json.put("partialFailClosedCount", count(all, "combined", "DATA_PARTIAL_FAIL_CLOSED"));
        JSONObject reasons = new JSONObject(true);
        for (RegionMatchedEvent event : all) {
            for (String reason : event.getShadowRejectReasons()) {
                reasons.put(reason, reasons.getIntValue(reason) + 1);
            }
        }
        json.put("rejectReasonCounts", reasons);
    }

    private int count(List<RegionMatchedEvent> events, String field, String expected) {
        int result = 0;
        for (RegionMatchedEvent event : events) if (expected.equals(value(event, field))) result++;
        return result;
    }

    private int countPrefix(List<RegionMatchedEvent> events, String field, String prefix) {
        int result = 0;
        for (RegionMatchedEvent event : events) {
            String value = value(event, field);
            if (value != null && value.startsWith(prefix)) result++;
        }
        return result;
    }

    private int countContains(List<RegionMatchedEvent> events, String field, String token) {
        int result = 0;
        for (RegionMatchedEvent event : events) {
            String value = value(event, field);
            if (value != null && value.contains(token)) result++;
        }
        return result;
    }

    private String value(RegionMatchedEvent event, String field) {
        if ("scene".equals(field)) return event.getSceneShadowDecision();
        if ("behavior".equals(field)) return event.getBehaviorShadowDecision();
        return event.getCombinedShadowDecision();
    }

    void analyzedVessel() { analyzedVessels++; }
    void addLoiteringCandidates(int value) { loiteringCandidates += value; }
    void addAlongsideCandidates(int value) { alongsideCandidates += value; }
    void setEligibleVessels(int value) { eligibleVessels = value; }
    void addRegionEvent(RegionMatchedEvent event) {
        regionEvents.add(event);
        if ("LOITERING".equals(event.getBehaviorType())) {
            loiteringRegionEvents++;
        } else if ("ALONGSIDE".equals(event.getBehaviorType())) {
            alongsideRegionEvents++;
        }
    }
    void addBoundaryEvent(RegionMatchedEvent event) {
        boundaryEvents.add(event);
        boundaryPartialEvents++;
    }
    void setDuplicatePairEvents(int value) { duplicatePairEvents = value; }
    void analysisFailure(String behaviorType, String mmsi, String error) {
        analysisFailures++;
        status = RegionBehaviorStatus.REGION_RUN_PARTIAL;
        JSONObject failure = new JSONObject(true);
        failure.put("kind", "ANALYSIS");
        failure.put("behaviorType", behaviorType);
        failure.put("mmsi", mmsi);
        failure.put("error", error);
        failures.add(failure);
    }
    void dependencyFailure(String dependency, String error) {
        analysisFailures++;
        status = RegionBehaviorStatus.REGION_RUN_PARTIAL;
        JSONObject failure = new JSONObject(true);
        failure.put("kind", "DEPENDENCY");
        failure.put("dependency", dependency);
        failure.put("error", error);
        failures.add(failure);
    }
    public void markPartial() { status = RegionBehaviorStatus.REGION_RUN_PARTIAL; }
    void setOutputDirectory(String value) { outputDirectory = value; }

    public RegionBehaviorStatus getStatus() { return status; }
    public boolean isSuccess() { return status == RegionBehaviorStatus.REGION_RUN_COMPLETED; }
    public boolean isQueryComplete() { return queryComplete; }
    public int getAnalysisFailures() { return analysisFailures; }
    public int getDuplicatePairEvents() { return duplicatePairEvents; }
    public List<RegionMatchedEvent> getRegionEvents() { return Collections.unmodifiableList(regionEvents); }
    public List<RegionMatchedEvent> getBoundaryEvents() { return Collections.unmodifiableList(boundaryEvents); }
    public List<JSONObject> getFailures() { return Collections.unmodifiableList(failures); }
}
