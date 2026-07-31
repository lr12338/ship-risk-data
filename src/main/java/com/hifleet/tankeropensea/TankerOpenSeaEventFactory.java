package com.hifleet.tankeropensea;

import com.alibaba.fastjson.JSONObject;
import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.trajectory.TrajectoryPoint;
import com.hifleet.trajectory.TrajectoryUtils;

import java.util.Collections;
import java.util.List;

final class TankerOpenSeaEventFactory {
    private TankerOpenSeaEventFactory() {
    }

    static ShipBehaviorTrace alongside(String batchNo, ListedTanker own,
                                       ShipMetadata target,
                                       ShipEligibilityDecision targetEligibility,
                                       List<TrajectoryPoint> ownPoints,
                                       List<TrajectoryPoint> targetPoints, RuleResult result) {
        ShipBehaviorEvent event = common(ShipBehaviorEvent.TYPE_ALONGSIDE,
                batchNo, own, ownPoints, result);
        event.setTargetMmsi(target.getMmsi());
        event.setTargetShipType(
                targetEligibility.getStandardShipType().name());
        event.setEventSubType("LIQUID_CONTAINER_SIDE_BY_SIDE");
        event.setMinDistanceNm(number(result, "distanceP10Nm"));
        event.setAvgDistanceNm(number(result, "distanceP50Nm"));
        event.setMaxDistanceNm(number(result, "distanceP90Nm"));
        event.setSpeedDifferenceKnots(number(result, "relativeSpeedP90"));
        event.setCourseDifferenceDegree(number(result, "headingDifferenceP90"));
        event.setParallelRatio(number(result, "sideBySideRatio"));
        event.setRuleVersion(TankerOpenSeaAlongsideRule.RULE_VERSION);
        JSONObject detail = detail(result, own, target, targetEligibility);
        detail.put("conclusion", "两艘允许船型船舶持续低速并行，舷侧关系、间距和相对速度稳定，且未进入港区、锚地或泊位。");
        event.setScoreDetailJson(detail.toJSONString());
        return new ShipBehaviorTrace(event, ownPoints, targetPoints);
    }

    static ShipBehaviorTrace loitering(String batchNo, ListedTanker own,
                                       List<TrajectoryPoint> points, RuleResult result) {
        ShipBehaviorEvent event = common(ShipBehaviorEvent.TYPE_LOITERING,
                batchNo, own, points, result);
        event.setEventSubType("LIQUID_CONTAINER_LOOP_BACKTRACK");
        event.setMaxDistanceNm(number(result, "radiusNm"));
        event.setRadiusNm(number(result, "radiusNm"));
        event.setTortuosity(number(result, "pathToDisplacementRatio"));
        event.setTurnCountPerHour(number(result, "turnCountPerHour"));
        event.setCogChangePerHour(number(result, "cumulativeTurnDegree"));
        event.setRuleVersion(TankerOpenSeaLoiteringRule.RULE_VERSION);
        JSONObject detail = detail(result, own, null, null);
        detail.put("conclusion", "该允许船型船舶持续低速小范围闭环或往返，实际航程明显大于净位移，且未进入港区、锚地或泊位。");
        event.setScoreDetailJson(detail.toJSONString());
        return new ShipBehaviorTrace(event, points,
                Collections.<TrajectoryPoint>emptyList());
    }

    private static ShipBehaviorEvent common(String behaviorType, String batchNo,
                                            ListedTanker own, List<TrajectoryPoint> points,
                                            RuleResult result) {
        TrajectoryPoint first = points.get(0);
        TrajectoryPoint last = points.get(points.size() - 1);
        double lon = 0.0;
        double lat = 0.0;
        for (TrajectoryPoint point : points) {
            lon += point.getLon();
            lat += point.getLat();
        }
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(behaviorType);
        event.setMmsi(own.getMetadata().getMmsi());
        event.setStartTime(TrajectoryUtils.formatTime(first));
        event.setEndTime(TrajectoryUtils.formatTime(last));
        event.setDurationMinutes(longNumber(result, "durationMinutes"));
        event.setStartLon(first.getLon());
        event.setStartLat(first.getLat());
        event.setEndLon(last.getLon());
        event.setEndLat(last.getLat());
        event.setCenterLon(lon / points.size());
        event.setCenterLat(lat / points.size());
        event.setNearPort(false);
        event.setNearAnchor(false);
        event.setOpenSea(null);
        event.setOwnShipType(
                own.getEligibility().getStandardShipType().name());
        event.setAvgSpeed(averageSpeed(points));
        event.setPointCount(points.size());
        event.setBatchNo(batchNo);
        event.setSeverityLevel("L3");
        event.setEventScore(85.0);
        event.setConfidenceLevel("HIGH");
        event.setExclusionFlag("VALID_RISK_EVENT");
        event.setExclusionReason("OUTSIDE_PORT_ANCHORAGE_BERTH");
        event.setTrackCoverageRatio(number(result, "trackCoverageRatio") == null
                ? number(result, "synchronizedPointRatio")
                : number(result, "trackCoverageRatio"));
        return event;
    }

    private static JSONObject detail(RuleResult result, ListedTanker listedOwn,
                                     ShipMetadata target,
                                     ShipEligibilityDecision targetEligibility) {
        ShipMetadata own = listedOwn.getMetadata();
        JSONObject json = new JSONObject(true);
        json.put("ruleFeatures", result.getFeatures());
        json.put("ownShipName", own.getShipName());
        json.put("ownMmsi", own.getMmsi());
        json.put("ownRawType", own.getRawType());
        json.put("ownRawMinorType", own.getRawMinorType());
        json.put("ownStandardShipType",
                listedOwn.getEligibility().getStandardShipType().name());
        json.put("ownEligibilityReason",
                listedOwn.getEligibility().getDecisionReason());
        json.put("ownMetadataQuality",
                listedOwn.getEligibility().getMetadataQuality());
        if (target != null) {
            json.put("targetShipName", target.getShipName());
            json.put("targetMmsi", target.getMmsi());
            json.put("targetRawType", target.getRawType());
            json.put("targetRawMinorType", target.getRawMinorType());
            json.put("targetStandardShipType",
                    targetEligibility.getStandardShipType().name());
            json.put("targetEligibilityReason",
                    targetEligibility.getDecisionReason());
            json.put("targetMetadataQuality",
                    targetEligibility.getMetadataQuality());
        }
        return json;
    }

    private static Double number(RuleResult result, String key) {
        Object value = result.getFeatures().get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static Long longNumber(RuleResult result, String key) {
        Object value = result.getFeatures().get(key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static double averageSpeed(List<TrajectoryPoint> points) {
        double total = 0.0;
        for (TrajectoryPoint point : points) total += point.getSpeed();
        return total / Math.max(1, points.size());
    }
}
