package com.hifleet.sts.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.Locale;

/** Classification of existing Alongside evidence; does not run physical detection. */
final class StsHistoricalClassification {
    static void classify(JSONObject e) {
        String type = role(e.getString("targetShipTypeRaw"));
        String minotype = role(e.getString("targetShipMinTypeRaw"));
        String role = "UNKNOWN".equals(type) ? minotype : type;
        boolean service = "SERVICE".equals(type) || "SERVICE".equals(minotype);
        if (service) role = "SERVICE";
        else if (!"UNKNOWN".equals(type) && !"UNKNOWN".equals(minotype) && !type.equals(minotype)) role = "CONFLICTING";
        if (e.getBooleanValue("identityConflict") && !service) role = "CONFLICTING";
        boolean known = e.getBooleanValue("sceneDataAvailable") && !"UNKNOWN".equals(e.getString("sceneType"));
        String scene = known ? e.getString("sceneType") : "UNKNOWN";
        e.put("sceneType", scene);
        if (!known) {
            e.put("sceneDataAvailable", false);
            e.put("sceneContextComplete", false);
            for (String key : new String[]{"nearPort", "nearAnchor", "nearAnchorage", "offshore", "openSea", "outsideKnownPortAndAnchor"}) e.remove(key);
        }
        JSONArray context = new JSONArray();
        boolean port = known && ("PORT_OPERATIONAL".equals(scene) || "PORT".equals(scene) || "BERTH".equals(scene) || "TERMINAL".equals(scene));
        boolean stationary = "STATIONARY_SIDE_BY_SIDE_REVIEW".equals(e.getString("behaviorMode"));
        boolean weak = e.getBooleanValue("weakCandidate");
        if (service) context.add("SERVICE_INTERACTION");
        if (port && stationary) context.add("PORT_BERTH_STATIONARY");
        String reason = "TARGET_" + role + "_REQUIRES_REVIEW";
        String classification = "STS_REVIEW_REQUIRED";
        if (weak) {
            classification = "PHYSICAL_ONLY";
            reason = "WEAK_PHYSICAL_NOT_PROMOTED";
            if (service) reason += ";EXPLICIT_SERVICE_ALTERNATIVE";
            if (port && stationary) reason += ";KNOWN_PORT_BERTH_STATIONARY";
        } else if (service) {
            classification = "PHYSICAL_ONLY";
                        reason = "EXPLICIT_SERVICE_ALTERNATIVE";
        } else if (port && stationary) {
            classification = "PHYSICAL_ONLY";
            reason = "KNOWN_PORT_BERTH_STATIONARY";
        } else if ("TANKER_LIKE".equals(role)) {
            classification = "STS_CANDIDATE";
            reason = "TANKER_LIKE_SUPPORTS_CARGO_STS_NOT_CONFIRMED_TRANSFER";
        }
        if (known && "ANCHORAGE".equals(scene)) context.add("ANCHORAGE_PROXIMITY");
        boolean promoted = !"PHYSICAL_ONLY".equals(classification);
        e.put("physicalEventType", "OBSERVED_ALONGSIDE");
        e.put("physicalSupportLevel", weak ? "WEAK" : "FORMAL");
        e.put("stsClassification", classification);
        e.put("stsPromotionStatus", promoted ? "PROMOTED" : "NOT_PROMOTED");
        e.put("reviewQueueEligible", promoted);
        e.put("classificationReason", reason);
        e.put("targetRole", role);
        e.put("targetRoleConfidence", "UNKNOWN".equals(role) || "CONFLICTING".equals(role) ? "UNKNOWN" : "METADATA_SUPPORTED");
        e.put("contextEvidence", context);
        e.put("riskScoreStatus", "DISABLED");
    }

    private static String role(String value) {
        if (value == null) return "UNKNOWN";
        String s = value.toLowerCase(Locale.ROOT).trim();
        if (contains(s, "tug", "pilot", "service", "work boat", "workboat", "bunker", "supply", "拖轮", "拖带", "引航", "服务", "工程", "补给", "加油", "供应")) return "SERVICE";
        if (contains(s, "barge", "lighter", "驳船", "驳运")) return "BARGE_LIGHTER";
        if (contains(s, "tanker", "petroleum", "原油船", "油船", "油轮", "液货船", "化学品船")) return "TANKER_LIKE";
        if (contains(s, "bulk", "cargo", "container", "merchant", "散货", "货船", "集装箱")) return "GENERIC_MERCHANT";
        if (contains(s, "渔船", "游艇", "fishing", "yacht", "passenger", "客船")) return "OTHER";
        return "UNKNOWN";
    }
    private static boolean contains(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
