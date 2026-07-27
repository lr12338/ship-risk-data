package com.hifleet.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 搭靠 V3 船型范围判定。只依赖 recentships.type，未知船型不在这里被误判为非商船。
 */
@Component
public class ShipTypeClassifier {

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

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
