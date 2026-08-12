package com.hifleet.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TargetRoleClassification {
    private final TargetVesselRole role;
    private final ClassificationConfidence confidence;
    private final List<String> reasonCodes;
    private final String rawType;
    private final String rawMinType;

    public TargetRoleClassification(TargetVesselRole role, ClassificationConfidence confidence,
                                    List<String> reasonCodes, String rawType, String rawMinType) {
        this.role = role;
        this.confidence = confidence;
        this.reasonCodes = Collections.unmodifiableList(new ArrayList<String>(reasonCodes));
        this.rawType = rawType;
        this.rawMinType = rawMinType;
    }

    public TargetVesselRole getRole() { return role; }
    public ClassificationConfidence getConfidence() { return confidence; }
    public List<String> getReasonCodes() { return reasonCodes; }
    public String getRawType() { return rawType; }
    public String getRawMinType() { return rawMinType; }
}
