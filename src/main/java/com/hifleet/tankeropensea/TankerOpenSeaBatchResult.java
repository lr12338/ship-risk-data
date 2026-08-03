package com.hifleet.tankeropensea;

public class TankerOpenSeaBatchResult {
    private final String behavior;
    private final String batchNo;
    private int shipsSeen;
    private int shipsFailed;
    private int windowsDone;
    private int windowsFailed;
    private int windowsSkipped;
    private int candidates;
    private int eventsDetected;
    private int eventsStored;
    private int replayTimeoutSkipped;
    private int degradedCandidatesStored;
    private int quarantineHits;
    private ExclusionHealth exclusionHealth;
    private int repairedGeometries;
    private int unparseableGeometries;

    public TankerOpenSeaBatchResult(String behavior, String batchNo) {
        this.behavior = behavior;
        this.batchNo = batchNo;
    }

    void shipSeen() { shipsSeen++; }
    void shipFailed() { shipsFailed++; }
    void windowDone() { windowsDone++; }
    void windowFailed() { windowsFailed++; }
    void windowSkipped() { windowsSkipped++; }
    void addCandidates(int value) { candidates += value; }
    void addDetected(int value) { eventsDetected += value; }
    void addStored(int value) { eventsStored += value; }
    void addReplayTimeoutSkipped(int value) { replayTimeoutSkipped += value; }
    void addDegradedStored(int value) { degradedCandidatesStored += value; }
    void addQuarantineHits(int value) { quarantineHits += value; }
    void setExclusionSnapshot(PortAnchorBerthExclusionService.Snapshot snapshot) {
        exclusionHealth = snapshot.getHealth();
        repairedGeometries = snapshot.getRepairedGeometries();
        unparseableGeometries = snapshot.getUnparseableGeometries();
    }

    public boolean isSuccess() { return shipsFailed == 0 && windowsFailed == 0; }

    public String summary() {
        return "LIQUID_CONTAINER " + behavior + " batch summary batchNo=" + batchNo
                + " shipsSeen=" + shipsSeen
                + " shipsFailed=" + shipsFailed
                + " windowsDone=" + windowsDone
                + " windowsFailed=" + windowsFailed
                + " windowsSkipped=" + windowsSkipped
                + " candidates=" + candidates
                + " eventsDetected=" + eventsDetected
                + " eventsStored=" + eventsStored
                + " replayTimeoutSkipped=" + replayTimeoutSkipped
                + " exclusionHealth=" + exclusionHealth
                + " formalEventsStored=" + eventsStored
                + " degradedCandidatesStored=" + degradedCandidatesStored
                + " quarantineHits=" + quarantineHits
                + " repairedGeometries=" + repairedGeometries
                + " unparseableGeometries=" + unparseableGeometries;
    }
}
