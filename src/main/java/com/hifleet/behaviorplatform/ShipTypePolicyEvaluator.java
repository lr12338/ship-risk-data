package com.hifleet.behaviorplatform;

import com.hifleet.service.ShipTypeClassifier;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShipTypePolicyEvaluator {
    private final ShipTypeClassifier classifier;
    public ShipTypePolicyEvaluator(ShipTypeClassifier classifier) { this.classifier = classifier; }

    public boolean primaryAllowed(String shipType, PrimaryShipTypePolicy policy) {
        return policy != null && Boolean.TRUE.equals(classifier.isMerchant(shipType));
    }

    public CounterpartyTypeEvidence counterparty(String shipType, CounterpartyShipTypePolicy policy,
                                                  UnknownShipTypeAction unknownAction) {
        Boolean merchant = classifier.isMerchant(shipType);
        if (merchant == null) return unknown(unknownAction);
        String normalized = normalize(shipType, merchant);
        List<String> tags = new ArrayList<String>();
        boolean normalRole = contains(shipType, "tug", "拖", "service", "服务", "pilot", "引航",
                "工程", "supply", "补给", "barge", "驳", "bunker", "加油");
        if (normalRole) tags.add("NORMAL_TOWING_POSSIBLE");
        boolean allowed;
        switch (policy) {
            case STRICT_SAME_AS_PRIMARY: allowed = Boolean.TRUE.equals(merchant); break;
            case COMMERCIAL_EXTENDED: allowed = Boolean.TRUE.equals(merchant) || normalRole; break;
            case ALL_KNOWN_SHIPS: allowed = true; break;
            case CUSTOM_ALLOWLIST: allowed = Boolean.TRUE.equals(merchant); break;
            default: allowed = false;
        }
        boolean review = normalRole || !allowed;
        return new CounterpartyTypeEvidence(normalized, 0.9, tags,
                normalRole ? -0.2 : 0.0, normalRole ? 0.8 : 0.3, allowed, review, false);
    }

    private CounterpartyTypeEvidence unknown(UnknownShipTypeAction action) {
        List<String> tags = new ArrayList<String>();
        tags.add("SHIP_TYPE_UNKNOWN");
        boolean shadow = action == UnknownShipTypeAction.ALLOW_LOW_CONFIDENCE_SHADOW_ONLY;
        boolean allowed = shadow || action == UnknownShipTypeAction.REVIEW_REQUIRED;
        return new CounterpartyTypeEvidence("UNKNOWN", 0.0, tags, -0.5, 0.5,
                allowed, true, shadow);
    }

    private String normalize(String type, Boolean merchant) {
        if (StringUtils.isBlank(type)) return "UNKNOWN";
        if (contains(type, "tug", "拖")) return "TUG";
        if (contains(type, "fishing", "渔", "鱼")) return "FISHING";
        return Boolean.TRUE.equals(merchant) ? "COMMERCIAL" : "OTHER_KNOWN";
    }

    private boolean contains(String value, String... tokens) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String token : tokens) if (lower.contains(token.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
