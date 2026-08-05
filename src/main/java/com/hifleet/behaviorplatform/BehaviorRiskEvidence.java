package com.hifleet.behaviorplatform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class BehaviorRiskEvidence {
    private final BehaviorType behaviorType;
    private final String eventSubType;
    private final String confidence;
    private final List<RuleDecision> ruleEvidence;
    private final Map<String, Object> sceneEvidence;
    private final CounterpartyTypeEvidence counterpartyEvidence;
    private final double normalOperationLikelihood;
    private final double dataCompleteness;
    private final List<String> reviewReasons;
    private final List<String> suggestedRiskTags;

    public BehaviorRiskEvidence(BehaviorType behaviorType, String eventSubType, String confidence,
                                List<RuleDecision> ruleEvidence, Map<String, Object> sceneEvidence,
                                CounterpartyTypeEvidence counterpartyEvidence,
                                double normalOperationLikelihood, double dataCompleteness,
                                List<String> reviewReasons, List<String> suggestedRiskTags) {
        this.behaviorType = behaviorType;
        this.eventSubType = eventSubType;
        this.confidence = confidence;
        this.ruleEvidence = immutable(ruleEvidence);
        this.sceneEvidence = sceneEvidence == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new java.util.LinkedHashMap<String, Object>(sceneEvidence));
        this.counterpartyEvidence = counterpartyEvidence;
        this.normalOperationLikelihood = normalOperationLikelihood;
        this.dataCompleteness = dataCompleteness;
        this.reviewReasons = immutable(reviewReasons);
        this.suggestedRiskTags = immutable(suggestedRiskTags);
    }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
    public BehaviorType getBehaviorType() { return behaviorType; }
    public String getEventSubType() { return eventSubType; }
    public String getConfidence() { return confidence; }
    public List<RuleDecision> getRuleEvidence() { return ruleEvidence; }
    public Map<String, Object> getSceneEvidence() { return sceneEvidence; }
    public CounterpartyTypeEvidence getCounterpartyEvidence() { return counterpartyEvidence; }
    public double getNormalOperationLikelihood() { return normalOperationLikelihood; }
    public double getDataCompleteness() { return dataCompleteness; }
    public List<String> getReviewReasons() { return reviewReasons; }
    public List<String> getSuggestedRiskTags() { return suggestedRiskTags; }
}
