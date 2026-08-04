package com.hifleet.maritime;

public final class TimedSceneObservation {
    private final long epochMillis;
    private final MaritimeSceneDecision decision;

    public TimedSceneObservation(long epochMillis, MaritimeSceneDecision decision) {
        this.epochMillis = epochMillis;
        this.decision = decision;
    }

    public long getEpochMillis() { return epochMillis; }
    public MaritimeSceneDecision getDecision() { return decision; }
}
