package com.hifleet.tankeropensea;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExclusionDecision {
    private enum Disposition { ACCEPTED, EXCLUDED, QUARANTINED }

    private final Disposition disposition;
    private final String reason;
    private final String hitType;
    private final String hitSource;
    private final String hitId;
    private final String fingerprint;
    private final Map<String, Integer> sourceCounts;

    private ExclusionDecision(Disposition disposition, String reason, String hitType,
                              String hitSource, String hitId, String fingerprint,
                              Map<String, Integer> sourceCounts) {
        this.disposition = disposition;
        this.reason = reason;
        this.hitType = hitType;
        this.hitSource = hitSource;
        this.hitId = hitId;
        this.fingerprint = fingerprint;
        this.sourceCounts = new LinkedHashMap<String, Integer>(sourceCounts);
    }

    public static ExclusionDecision accepted(String fingerprint,
                                             Map<String, Integer> sourceCounts) {
        return new ExclusionDecision(Disposition.ACCEPTED,
                "OUTSIDE_PORT_ANCHORAGE_BERTH",
                null, null, null, fingerprint, sourceCounts);
    }

    public static ExclusionDecision rejected(String hitType, String hitSource,
                                             String hitId, String fingerprint,
                                             Map<String, Integer> sourceCounts) {
        return new ExclusionDecision(Disposition.EXCLUDED,
                "EXCLUDED_BY_" + hitType,
                hitType, hitSource, hitId, fingerprint, sourceCounts);
    }

    public static ExclusionDecision quarantined(
            String hitType, String hitSource, String hitId, String fingerprint,
            Map<String, Integer> sourceCounts) {
        return new ExclusionDecision(Disposition.QUARANTINED,
                "GEOMETRY_QUARANTINE_HIT", hitType, hitSource, hitId,
                fingerprint, sourceCounts);
    }

    public boolean isAccepted() { return disposition == Disposition.ACCEPTED; }
    public boolean isExcluded() { return disposition == Disposition.EXCLUDED; }
    public boolean isQuarantined() {
        return disposition == Disposition.QUARANTINED;
    }
    public String getReason() { return reason; }
    public String getFingerprint() { return fingerprint; }

    public Map<String, Object> toFeatureMap() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("portHit", "PORT".equals(hitType));
        result.put("anchorageHit", "ANCHORAGE".equals(hitType));
        result.put("berthHit", "BERTH".equals(hitType));
        result.put("hitSource", hitSource);
        result.put("hitId", hitId);
        result.put("quarantineHit", isQuarantined());
        result.put("sourceCounts", sourceCounts);
        result.put("fingerprint", fingerprint);
        return result;
    }
}
