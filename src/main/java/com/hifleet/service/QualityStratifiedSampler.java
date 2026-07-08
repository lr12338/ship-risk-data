package com.hifleet.service;

import com.hifleet.repository.ZonePassageQualityRepository.ShipProfile;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 质量评估分层抽样：按航行范围（国际/国内）、船型、船长档位选取样本。
 * 每个分层默认抽 2 艘，总量不超过上限。
 */
public final class QualityStratifiedSampler {

    public static final String VOYAGE_DOMESTIC = "国内";
    public static final String VOYAGE_INTERNATIONAL = "国际";

    /** 船长 ≤120m */
    public static final String LENGTH_SHORT = "短";
    /** 120m < 船长 ≤225m */
    public static final String LENGTH_MEDIUM = "中";
    /** 船长 >225m */
    public static final String LENGTH_LONG = "长";
    public static final String LENGTH_UNKNOWN = "未知";

    /** 每个分层（国际/国内 × 船型 × 船长档）默认抽取条数 */
    public static final int DEFAULT_SAMPLES_PER_STRATUM = 2;
    /** 轨迹回放抽样船舶上限 */
    public static final int DEFAULT_MAX_REPLAY_SHIPS = 60;
    /** 空间抽检船舶上限 */
    public static final int DEFAULT_MAX_SPATIAL_SHIPS = 30;
    /** 空间抽检事件条数上限 */
    public static final int DEFAULT_MAX_SPATIAL_EVENTS = 60;

    private static final double LENGTH_SHORT_MAX = 120.0;
    private static final double LENGTH_MEDIUM_MAX = 225.0;

    private QualityStratifiedSampler() {
    }

    public static String classifyVoyageScope(String mmsi) {
        if (StringUtils.isBlank(mmsi)) {
            return VOYAGE_INTERNATIONAL;
        }
        String trimmed = mmsi.trim();
        if (trimmed.startsWith("412") || trimmed.startsWith("413")
                || trimmed.startsWith("414") || trimmed.startsWith("416")) {
            return VOYAGE_DOMESTIC;
        }
        return VOYAGE_INTERNATIONAL;
    }

    public static double parseLengthMeters(String length) {
        if (StringUtils.isBlank(length)) {
            return 0;
        }
        try {
            return Double.parseDouble(length.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static String classifyLengthTier(double lengthMeters) {
        if (lengthMeters <= 0) {
            return LENGTH_UNKNOWN;
        }
        if (lengthMeters <= LENGTH_SHORT_MAX) {
            return LENGTH_SHORT;
        }
        if (lengthMeters <= LENGTH_MEDIUM_MAX) {
            return LENGTH_MEDIUM;
        }
        return LENGTH_LONG;
    }

    public static String normalizeShipType(String shipType) {
        if (StringUtils.isBlank(shipType) || "-".equals(shipType.trim())) {
            return "未知船型";
        }
        return shipType.trim();
    }

    /**
     * 分层键：航行范围 | 船型 | 船长档
     */
    public static String buildStratumKey(ShipProfile profile) {
        return classifyVoyageScope(profile.getMmsi()) + "|"
                + normalizeShipType(profile.getShipType()) + "|"
                + classifyLengthTier(profile.getLengthMeters());
    }

    public static String formatProfileTag(ShipProfile profile) {
        if (profile == null) {
            return "未知";
        }
        return classifyVoyageScope(profile.getMmsi()) + "/"
                + normalizeShipType(profile.getShipType()) + "/"
                + formatLength(profile.getLengthMeters());
    }

    public static String formatLength(double lengthMeters) {
        String tier = classifyLengthTier(lengthMeters);
        if (lengthMeters <= 0) {
            return tier;
        }
        return tier + "(" + Math.round(lengthMeters) + "m)";
    }

    /**
     * 分层选取船舶：每个分层最多 {@code perStratum} 艘，总量不超过 {@code maxTotal}。
     */
    public static List<String> selectStratifiedShips(List<ShipProfile> profiles,
                                                     int perStratum,
                                                     int maxTotal) {
        if (profiles == null || profiles.isEmpty() || maxTotal <= 0) {
            return Collections.emptyList();
        }
        int effectivePerStratum = Math.max(1, perStratum);

        Map<String, List<ShipProfile>> strata = groupByStratum(profiles);
        List<String> stratumKeys = new ArrayList<String>(strata.keySet());
        Collections.shuffle(stratumKeys, new Random(System.nanoTime()));

        List<String> selected = new ArrayList<String>();
        Set<String> picked = new LinkedHashSet<String>();

        // 第一轮：每个分层抽 perStratum 艘
        for (String key : stratumKeys) {
            if (selected.size() >= maxTotal) {
                break;
            }
            List<ShipProfile> bucket = new ArrayList<ShipProfile>(strata.get(key));
            Collections.shuffle(bucket);
            int count = 0;
            for (ShipProfile profile : bucket) {
                if (selected.size() >= maxTotal || count >= effectivePerStratum) {
                    break;
                }
                if (picked.add(profile.getMmsi())) {
                    selected.add(profile.getMmsi());
                    count++;
                }
            }
        }

        // 兜底：未达上限时从剩余船舶随机补（仍受 maxTotal 限制）
        if (selected.size() < maxTotal) {
            List<ShipProfile> shuffled = new ArrayList<ShipProfile>(profiles);
            Collections.shuffle(shuffled);
            for (ShipProfile profile : shuffled) {
                if (selected.size() >= maxTotal) {
                    break;
                }
                if (picked.add(profile.getMmsi())) {
                    selected.add(profile.getMmsi());
                }
            }
        }
        return selected;
    }

    public static int resolveReplaySampleCount(List<ShipProfile> profiles,
                                               int perStratum,
                                               int maxTotal) {
        if (profiles == null || profiles.isEmpty()) {
            return 0;
        }
        int strataCount = groupByStratum(profiles).size();
        int ideal = strataCount * Math.max(1, perStratum);
        return Math.min(maxTotal, ideal);
    }

    /**
     * 将事件配额分配到已选船舶，每艘至少 1 条。
     */
    public static Map<String, Integer> allocateEventQuota(List<String> mmsiList, int totalEvents) {
        Map<String, Integer> quota = new LinkedHashMap<String, Integer>();
        if (mmsiList == null || mmsiList.isEmpty() || totalEvents <= 0) {
            return quota;
        }
        int base = Math.max(1, totalEvents / mmsiList.size());
        int remainder = totalEvents - base * mmsiList.size();
        for (int i = 0; i < mmsiList.size(); i++) {
            int count = base + (i < remainder ? 1 : 0);
            quota.put(mmsiList.get(i), count);
        }
        return quota;
    }

    public static String describeSample(List<ShipProfile> profiles, List<String> selectedMmsi) {
        if (selectedMmsi == null || selectedMmsi.isEmpty()) {
            return "无样本";
        }
        Map<String, ShipProfile> byMmsi = new HashMap<String, ShipProfile>();
        for (ShipProfile p : profiles) {
            byMmsi.put(p.getMmsi(), p);
        }
        List<String> parts = new ArrayList<String>();
        for (String mmsi : selectedMmsi) {
            ShipProfile p = byMmsi.get(mmsi);
            if (p == null) {
                parts.add(mmsi + "[未知]");
            } else {
                parts.add(mmsi + "[" + formatProfileTag(p) + "]");
            }
        }
        return StringUtils.join(parts, "; ");
    }

    public static String describeStratumSummary(List<ShipProfile> profiles, List<String> selectedMmsi) {
        Map<String, Integer> stratumCounts = new LinkedHashMap<String, Integer>();
        Map<String, ShipProfile> byMmsi = new HashMap<String, ShipProfile>();
        for (ShipProfile p : profiles) {
            byMmsi.put(p.getMmsi(), p);
        }
        for (String mmsi : selectedMmsi) {
            ShipProfile p = byMmsi.get(mmsi);
            String key = p == null ? "未知" : buildStratumKey(p);
            stratumCounts.put(key, stratumCounts.containsKey(key) ? stratumCounts.get(key) + 1 : 1);
        }
        List<String> parts = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : stratumCounts.entrySet()) {
            parts.add(e.getKey() + "×" + e.getValue());
        }
        return "分层覆盖=" + stratumCounts.size() + "组 [" + StringUtils.join(parts, ", ") + "]";
    }

    private static Map<String, List<ShipProfile>> groupByStratum(List<ShipProfile> profiles) {
        Map<String, List<ShipProfile>> strata = new LinkedHashMap<String, List<ShipProfile>>();
        for (ShipProfile profile : profiles) {
            String key = buildStratumKey(profile);
            if (!strata.containsKey(key)) {
                strata.put(key, new ArrayList<ShipProfile>());
            }
            strata.get(key).add(profile);
        }
        return strata;
    }
}
