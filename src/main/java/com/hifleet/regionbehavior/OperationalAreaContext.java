package com.hifleet.regionbehavior;

public final class OperationalAreaContext {
    private final boolean insidePort;
    private final boolean insideAnchorage;
    private final boolean nearBerth;
    private final boolean insideKnownStsArea;
    private final boolean operationalAreaDataAvailable;

    public OperationalAreaContext(boolean insidePort, boolean insideAnchorage,
                                  boolean nearBerth, boolean insideKnownStsArea,
                                  boolean operationalAreaDataAvailable) {
        this.insidePort = insidePort;
        this.insideAnchorage = insideAnchorage;
        this.nearBerth = nearBerth;
        this.insideKnownStsArea = insideKnownStsArea;
        this.operationalAreaDataAvailable = operationalAreaDataAvailable;
    }

    public static OperationalAreaContext unavailable() {
        return new OperationalAreaContext(false, false, false, false, false);
    }

    public boolean isInsidePort() { return insidePort; }
    public boolean isInsideAnchorage() { return insideAnchorage; }
    public boolean isNearBerth() { return nearBerth; }
    public boolean isInsideKnownStsArea() { return insideKnownStsArea; }
    public boolean isOperationalAreaDataAvailable() { return operationalAreaDataAvailable; }
    public boolean requiresReview() {
        return !operationalAreaDataAvailable || insidePort || insideAnchorage || nearBerth || insideKnownStsArea;
    }
}
