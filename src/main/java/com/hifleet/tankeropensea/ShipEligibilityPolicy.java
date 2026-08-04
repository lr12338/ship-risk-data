package com.hifleet.tankeropensea;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class ShipEligibilityPolicy {

    public ShipEligibilityDecision evaluate(ShipMetadata metadata) {
        if (metadata == null) {
            return lookupFailed("metadata missing");
        }
        Set<StandardShipType> signals = new LinkedHashSet<StandardShipType>();
        addSignal(signals, classify(metadata.getRawType()));
        addSignal(signals, classify(metadata.getRawMinorType()));
        addSignal(signals, classifyAisCode(metadata.getAisShipTypeCode()));
        signals.remove(StandardShipType.UNKNOWN);

        if (signals.isEmpty()) {
            return ShipEligibilityDecision.rejected(StandardShipType.UNKNOWN,
                    "SHIP_TYPE_UNKNOWN", quality(metadata));
        }
        if (allLiquidCargo(signals)) {
            StandardShipType type = signals.size() == 1
                    ? signals.iterator().next() : StandardShipType.TANKER_UNSPECIFIED;
            return ShipEligibilityDecision.eligible(type,
                    signals.size() == 1 ? "EXPLICIT_LIQUID_CARGO"
                            : "EXPLICIT_LIQUID_CARGO_SUBTYPE_CONFLICT",
                    quality(metadata));
        }
        if (signals.size() > 1) {
            return ShipEligibilityDecision.rejected(StandardShipType.CONFLICT,
                    "SHIP_TYPE_CONFLICT:" + signals, quality(metadata));
        }
        StandardShipType type = signals.iterator().next();
        if (type == StandardShipType.CONTAINER) {
            return ShipEligibilityDecision.eligible(type,
                    "EXPLICIT_CONTAINER", quality(metadata));
        }
        return ShipEligibilityDecision.rejected(type,
                "NOT_LIQUID_CARGO_OR_CONTAINER", quality(metadata));
    }

    public ShipEligibilityDecision lookupFailed(String reason) {
        return ShipEligibilityDecision.rejected(StandardShipType.UNKNOWN,
                "SHIP_METADATA_LOOKUP_FAILED:" + StringUtils.defaultString(reason),
                "UNAVAILABLE");
    }

    private void addSignal(Set<StandardShipType> signals, StandardShipType type) {
        if (type != null) {
            signals.add(type);
        }
    }

    private StandardShipType classifyAisCode(Integer code) {
        // AIS 80-89 只能说明广义 tanker，不能区分油/化学品/气体，
        // 因而既不能单独放行，也不能与明确静态油轮资料制造伪冲突。
        return StandardShipType.UNKNOWN;
    }

    private StandardShipType classify(String raw) {
        if (StringUtils.isBlank(raw) || "-".equals(raw.trim())) {
            return StandardShipType.UNKNOWN;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (contains(value, "化学", "chemical")) return StandardShipType.TANKER_CHEMICAL;
        if (contains(value, "液化气", "lng", "lpg", "gas tanker")) return StandardShipType.TANKER_GAS;
        if (contains(value, "油轮", "油船", "原油", "成品油",
                "oil tanker", "crude tanker", "crude oil", "product tanker")) {
            return StandardShipType.TANKER_OIL;
        }
        if (contains(value, "液货船", "tanker")) return StandardShipType.TANKER_UNSPECIFIED;
        if (contains(value, "拖轮", "tug")) return StandardShipType.TUG;
        if (contains(value, "拖带", "towing")) return StandardShipType.TOWING;
        if (contains(value, "散货", "bulk")) return StandardShipType.BULK_CARRIER;
        if (contains(value, "集装箱", "container")) return StandardShipType.CONTAINER;
        if (contains(value, "货船", "杂货", "cargo")) return StandardShipType.GENERAL_CARGO;
        if (contains(value, "客船", "游轮", "passenger", "cruise")) return StandardShipType.PASSENGER;
        if (contains(value, "渔", "鱼", "fishing")) return StandardShipType.FISHING;
        if (contains(value, "引航", "pilot")) return StandardShipType.PILOT;
        if (contains(value, "游艇", "小艇", "快艇", "yacht", "pleasure")) return StandardShipType.YACHT;
        if (contains(value, "工程", "engineering")) return StandardShipType.ENGINEERING;
        if (contains(value, "近海作业", "服务船", "offshore", "service")) {
            return StandardShipType.OFFSHORE_SERVICE;
        }
        if (contains(value, "其他", "other")) return StandardShipType.OTHER;
        return StandardShipType.UNKNOWN;
    }

    private String quality(ShipMetadata metadata) {
        int present = 0;
        if (StringUtils.isNotBlank(metadata.getRawType())) present++;
        if (StringUtils.isNotBlank(metadata.getRawMinorType())) present++;
        if (metadata.getMetadataUpdatedAt() != null) present++;
        if (metadata.getLengthMeters() != null && metadata.getLengthMeters() > 0) present++;
        if (metadata.getDwt() != null && metadata.getDwt() > 0) present++;
        return present >= 4 ? "HIGH" : present >= 2 ? "MEDIUM" : "LOW";
    }

    private boolean contains(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    public static boolean isEligibleType(StandardShipType type) {
        return type == StandardShipType.TANKER_OIL
                || type == StandardShipType.TANKER_CHEMICAL
                || type == StandardShipType.TANKER_GAS
                || type == StandardShipType.TANKER_UNSPECIFIED
                || type == StandardShipType.CONTAINER;
    }

    private boolean allLiquidCargo(Set<StandardShipType> signals) {
        if (signals.isEmpty()) return false;
        for (StandardShipType signal : signals) {
            if (signal != StandardShipType.TANKER_OIL
                    && signal != StandardShipType.TANKER_CHEMICAL
                    && signal != StandardShipType.TANKER_GAS
                    && signal != StandardShipType.TANKER_UNSPECIFIED) {
                return false;
            }
        }
        return true;
    }
}
