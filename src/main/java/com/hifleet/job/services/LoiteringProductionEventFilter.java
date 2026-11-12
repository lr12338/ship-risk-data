package com.hifleet.job.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.service.ShipLoiteringDetector;
import com.hifleet.service.TraceRecipeService;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LoiteringProductionEventFilter {

    private static final String V3_RULE_VERSION = "LOITERING_V3_PROD_TEST_2026H1";
    private static final String V3_STATUS_FINAL = "FINAL";

    private LoiteringProductionEventFilter() {
    }

    static FilterResult validNonWeak(List<ShipBehaviorTrace> traces) {
        List<ShipBehaviorTrace> stored = new ArrayList<ShipBehaviorTrace>();
        int candidates = 0;
        int weak = 0;
        int downgraded = 0;
        int valid = 0;
        if (traces != null) {
            for (ShipBehaviorTrace trace : traces) {
                if (trace == null || trace.getEvent() == null) {
                    continue;
                }
                ShipBehaviorEvent event = trace.getEvent();
                candidates++;
                boolean weakCandidate =
                        ShipLoiteringDetector.SUB_TYPE_WEAK_LOITERING_CANDIDATE
                                .equals(event.getEventSubType());
                if (weakCandidate) {
                    weak++;
                }
                if (ShipLoiteringDetector.DOWNGRADED_NORMAL_LIKELY
                        .equals(event.getExclusionFlag())) {
                    downgraded++;
                }
                boolean validRisk = ShipLoiteringDetector.VALID_RISK_EVENT
                        .equals(event.getExclusionFlag());
                if (validRisk) {
                    valid++;
                }
                TraceRecipeService.attachPersistPolicy(trace,
                        TraceRecipeService.PERSIST_POLICY_VALID_NON_WEAK);
                if (validRisk && !weakCandidate && isFinalIfV3(event)) {
                    stored.add(trace);
                }
            }
        }
        return new FilterResult(candidates, weak, downgraded, valid, stored);
    }

    private static boolean isFinalIfV3(ShipBehaviorEvent event) {
        if (!V3_RULE_VERSION.equals(event.getRuleVersion())) {
            return true;
        }
        if (StringUtils.isBlank(event.getScoreDetailJson())) {
            return false;
        }
        try {
            JSONObject root = JSON.parseObject(event.getScoreDetailJson());
            return root != null
                    && V3_STATUS_FINAL.equals(root.getString("decisionStatus"));
        } catch (Exception ex) {
            return false;
        }
    }

    static final class FilterResult {
        private final int candidateCount;
        private final int weakCount;
        private final int downgradedCount;
        private final int validCount;
        private final List<ShipBehaviorTrace> storedTraces;

        private FilterResult(int candidateCount, int weakCount,
                             int downgradedCount, int validCount,
                             List<ShipBehaviorTrace> storedTraces) {
            this.candidateCount = candidateCount;
            this.weakCount = weakCount;
            this.downgradedCount = downgradedCount;
            this.validCount = validCount;
            this.storedTraces = Collections.unmodifiableList(
                    new ArrayList<ShipBehaviorTrace>(storedTraces));
        }

        int getCandidateCount() {
            return candidateCount;
        }

        int getWeakCount() {
            return weakCount;
        }

        int getDowngradedCount() {
            return downgradedCount;
        }

        int getValidCount() {
            return validCount;
        }

        List<ShipBehaviorTrace> getStoredTraces() {
            return storedTraces;
        }
    }
}
