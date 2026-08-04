package com.hifleet.regionbehavior;

import java.util.Locale;

public enum ShipTypePolicy {
    OIL_TANKER_ONLY,
    LIQUID_CARGO_ONLY,
    LIQUID_AND_CONTAINER,
    ALL_MERCHANT;

    public boolean accepts(String shipType) {
        if (shipType == null || shipType.trim().isEmpty()) return false;
        String value = shipType.trim().toLowerCase(Locale.ROOT);
        boolean oil = contains(value, "原油", "成品油", "油轮", "油船", "oil tanker", "crude tanker", "product tanker");
        boolean liquid = oil || contains(value, "化学品", "液化气", "chemical", "lng", "lpg", "gas carrier");
        boolean container = contains(value, "集装箱", "container");
        boolean merchant = liquid || container || contains(value, "散货", "货船", "杂货", "滚装", "矿砂",
                "bulk", "cargo", "ro-ro", "general cargo");
        if (this == OIL_TANKER_ONLY) return oil;
        if (this == LIQUID_CARGO_ONLY) return liquid;
        if (this == LIQUID_AND_CONTAINER) return liquid || container;
        return merchant;
    }

    private static boolean contains(String value, String... tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }
}
