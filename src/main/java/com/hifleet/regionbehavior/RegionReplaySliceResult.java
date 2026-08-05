package com.hifleet.regionbehavior;

public final class RegionReplaySliceResult {
    private final String sliceId;
    private final RegionBehaviorStatus status;
    private final int rawPointCount;
    private final int validPointCount;
    private final int parseErrorCount;
    private final long elapsedMs;
    private final String errorMessage;

    public RegionReplaySliceResult(String sliceId, RegionBehaviorStatus status,
                                   int rawPointCount, int validPointCount, int parseErrorCount,
                                   long elapsedMs, String errorMessage) {
        this.sliceId = sliceId;
        this.status = status;
        this.rawPointCount = rawPointCount;
        this.validPointCount = validPointCount;
        this.parseErrorCount = parseErrorCount;
        this.elapsedMs = elapsedMs;
        this.errorMessage = errorMessage;
    }

    public String getSliceId() { return sliceId; }
    public RegionBehaviorStatus getStatus() { return status; }
    public int getRawPointCount() { return rawPointCount; }
    public int getValidPointCount() { return validPointCount; }
    public int getParseErrorCount() { return parseErrorCount; }
    public long getElapsedMs() { return elapsedMs; }
    public String getErrorMessage() { return errorMessage; }
}
