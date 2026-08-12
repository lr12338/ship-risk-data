package com.hifleet.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Collections;

/**
 * 搭靠 V3 船型范围判定。只依赖 recentships.type，未知船型不在这里被误判为非商船。
 */
@Component
public class ShipTypeClassifier {

    public OwnShipEligibility classifyOwnShip(String shipType) {
        Boolean merchant = isMerchant(shipType);
        if (Boolean.TRUE.equals(merchant)) {
            return OwnShipEligibility.ELIGIBLE_MERCHANT;
        }
        return Boolean.FALSE.equals(merchant) ? OwnShipEligibility.INELIGIBLE : OwnShipEligibility.UNKNOWN;
    }

    public TargetVesselRole classifyTargetShip(String shipType) {
        return classifyTargetShipWithReason(shipType, null).getRole();
    }

    public TargetRoleClassification classifyTargetShipWithReason(String shipType, String minType) {
        String combined = normalize(shipType) + " " + normalize(minType);
        if (StringUtils.isBlank(combined.trim())) {
            return classification(TargetVesselRole.UNKNOWN, ClassificationConfidence.LOW,
                    "TARGET_TYPE_MISSING", shipType, minType);
        }
        if (containsAny(combined, "加油", "供油", "补给", "供应", "bunker", "supply")) {
            return classification(TargetVesselRole.BUNKER_OR_SUPPLY, ClassificationConfidence.HIGH,
                    "TARGET_ROLE_TYPE_DICTIONARY", shipType, minType);
        }
        if (containsAny(combined, "驳船", "油驳", "货驳", "过驳", "lighter", "barge")) {
            return classification(TargetVesselRole.BARGE_OR_LIGHTER, ClassificationConfidence.HIGH,
                    "TARGET_ROLE_TYPE_DICTIONARY", shipType, minType);
        }
        if (containsAny(combined, "拖轮", "拖带", "工作艇", "服务船", "tug", "work boat", "service")) {
            return classification(TargetVesselRole.TUG_OR_SERVICE, ClassificationConfidence.HIGH,
                    "TARGET_ROLE_TYPE_DICTIONARY", shipType, minType);
        }
        if (containsAny(combined, "渔", "鱼", "小艇", "快艇", "游艇", "fishing", "small craft", "yacht")) {
            return classification(TargetVesselRole.FISHING_OR_SMALL_CRAFT, ClassificationConfidence.HIGH,
                    "TARGET_ROLE_TYPE_DICTIONARY", shipType, minType);
        }
        if (containsAny(combined, "客船", "引航", "领航", "passenger", "pilot")) {
            return classification(TargetVesselRole.PASSENGER_OR_PILOT, ClassificationConfidence.HIGH,
                    "TARGET_ROLE_TYPE_DICTIONARY", shipType, minType);
        }
        if (Boolean.TRUE.equals(isMerchant(combined))) {
            return classification(TargetVesselRole.LARGE_MERCHANT, ClassificationConfidence.HIGH,
                    "TARGET_ROLE_MERCHANT_TYPE", shipType, minType);
        }
        return classification(TargetVesselRole.UNKNOWN, ClassificationConfidence.LOW,
                "TARGET_TYPE_UNRECOGNIZED", shipType, minType);
    }

    public Boolean isMerchant(String shipType) {
        if (StringUtils.isBlank(shipType)) {
            return null;
        }
        String type = shipType.trim().toLowerCase(Locale.ROOT);
        if (containsAny(type, "渔", "鱼", "游艇", "小艇", "快艇", "拖轮", "拖带",
                "工程", "服务船", "近海作业", "客船", "fishing", "yacht", "tug",
                "pleasure", "passenger", "pilot", "service")) {
            return false;
        }
        if (containsAny(type, "油轮", "油船", "散货", "集装箱", "货船", "杂货",
                "液化气", "化学品", "滚装", "矿砂", "tanker", "bulk", "container",
                "cargo", "lng", "lpg", "chemical", "ro-ro", "general cargo")) {
            return true;
        }
        return null;
    }

    public Boolean merchantPair(String ownShipType, String targetShipType) {
        Boolean ownMerchant = isMerchant(ownShipType);
        Boolean targetMerchant = isMerchant(targetShipType);
        if (Boolean.FALSE.equals(ownMerchant) || Boolean.FALSE.equals(targetMerchant)) {
            return false;
        }
        if (Boolean.TRUE.equals(ownMerchant) && Boolean.TRUE.equals(targetMerchant)) {
            return true;
        }
        return null;
    }

    private TargetRoleClassification classification(TargetVesselRole role, ClassificationConfidence confidence,
                                                      String reason, String rawType, String rawMinType) {
        return new TargetRoleClassification(role, confidence, Collections.singletonList(reason), rawType, rawMinType);
    }

    private String normalize(String value) {
        return StringUtils.isBlank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
