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
    private boolean formalEventsChanged;
    private int databaseWrites;

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
        json.put("formalEventsChanged", formalEventsChanged);
        json.put("databaseWrites", databaseWrites);
        if (outputDirectory != null) {
            json.put("outputDirectory", outputDirectory);
        }
        return json;
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
    void setFormalWriteResult(int writes) {
        databaseWrites = writes;
        formalEventsChanged = true;
    }

    public RegionBehaviorStatus getStatus() { return status; }
    public boolean isSuccess() { return status == RegionBehaviorStatus.REGION_RUN_COMPLETED; }
    public boolean isQueryComplete() { return queryComplete; }
    public int getAnalysisFailures() { return analysisFailures; }
    public int getDuplicatePairEvents() { return duplicatePairEvents; }
    public List<RegionMatchedEvent> getRegionEvents() { return Collections.unmodifiableList(regionEvents); }
    public List<RegionMatchedEvent> getBoundaryEvents() { return Collections.unmodifiableList(boundaryEvents); }
    public List<JSONObject> getFailures() { return Collections.unmodifiableList(failures); }
}
