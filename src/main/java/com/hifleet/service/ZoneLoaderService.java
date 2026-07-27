package com.hifleet.service;

import com.hifleet.domain.ZoneDefinition;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区域维表加载。
 * <p>
 * 海峡、海区范围数据来源参考 event 项目文档：
 * ais_event_history_architecture_codex_task_v2.md 第 2.6 / 2.8 节
 */
@Service
public class ZoneLoaderService {

    private static final Logger log = LoggerFactory.getLogger(ZoneLoaderService.class);
    private static final WKTReader WKT_READER = new WKTReader();

    /**
     * event 项目 DimensionLoader#loadStraits 使用的海峡 oid 白名单（共约 30 条）。
     */
    private static final String STRAIT_OID_IN =
            "24087,24313,24471,24475,24480,24482,24483,24484,24485,33302," +
            "41141,41142,199651,2508564,2508567";

    /**
     * 桥区 / 风电区在 alertzones 表中的 parent 标识，同一 parent 下按 name 前缀区分类型。
     */
    private static final String BRIDGE_WIND_FARM_PARENT = "594504766@qq.com";

    /** alertarea 账号警示/特殊区域 parent 标识。 */
    private static final String ALERT_AREA_PARENT = "alertarea";

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate newDataanalysisJdbcTemplate;

    @Autowired
    @Qualifier("aid41JdbcTemplate")
    private JdbcTemplate ais41JdbcTemplate;

    public List<ZoneDefinition> loadPortZones(List<Integer> zoneIds) {
        String sql = "SELECT id, port_id, name, zones_type, AsText(`bound`) AS geom_wkt " +
                "FROM dataanalysis.zones_with_port_id " +
                "WHERE is_delete = 0 AND bound IS NOT NULL";
        if (zoneIds != null && !zoneIds.isEmpty()) {
            sql += " AND id IN (" + buildPlaceholders(zoneIds.size()) + ")";
        }
        return queryZones(newDataanalysisJdbcTemplate, sql, zoneIds, "zones_with_port_id");
    }

    public List<ZoneDefinition> loadAlertZones(List<Integer> zoneIds) {
        String sql = "SELECT oid AS id, name, AsText(`bound`) AS geom_wkt " +
                "FROM ais41.alertzones WHERE bound IS NOT NULL";
        if (zoneIds != null && !zoneIds.isEmpty()) {
            sql += " AND oid IN (" + buildPlaceholders(zoneIds.size()) + ")";
        }
        return queryZones(ais41JdbcTemplate, sql, zoneIds, "alertzones");
    }

    public List<ZoneDefinition> loadAllZones(List<Integer> zoneIds) {
        List<ZoneDefinition> zones = new ArrayList<ZoneDefinition>();
        zones.addAll(loadPortZones(zoneIds));
        zones.addAll(loadAlertZones(zoneIds));
        return zones;
    }

    public List<ZoneDefinition> loadAnchorZones() {
        String sql = "SELECT oid AS id, name, AsText(`bound`) AS geom_wkt "
                + "FROM ais41.alertzones WHERE bound IS NOT NULL "
                + "AND (LOWER(name) LIKE '%anchor%' OR name LIKE '%锚地%' OR name LIKE '%锚泊%')";
        return queryZones(ais41JdbcTemplate, sql, new Object[0], "anchorzones");
    }

    /**
     * 步骤2：加载海峡范围。
     * 数据表：ais41.alertzones（文档 2.6 节）
     */
    public List<ZoneDefinition> loadStraits() {
        String sql = "SELECT oid AS id, name, AsText(`bound`) AS geom_wkt " +
                "FROM ais41.alertzones " +
                "WHERE bound IS NOT NULL AND oid IN (" + STRAIT_OID_IN + ")";
        return queryZones(ais41JdbcTemplate, sql, new Object[0], "ais41.alertzones");
    }

    /**
     * 步骤3：加载海区范围。
     * 数据表：ais41.world_sea（文档 2.8 节，约 102 条）
     */
    public List<ZoneDefinition> loadSeaAreas() {
        String sql = "SELECT ID AS id, NAME AS name, AsText(`the_geom`) AS geom_wkt " +
                "FROM ais41.world_sea WHERE the_geom IS NOT NULL";
        return queryZones(ais41JdbcTemplate, sql, new Object[0], "ais41.world_sea");
    }

    /**
     * 步骤4：加载桥区 / 风电区范围。
     * <p>
     * 数据表：ais41.alertzones，筛选 parent = {@value #BRIDGE_WIND_FARM_PARENT} 的围栏数据。
     * 桥区示例：围栏清水浦大桥、围栏金塘大桥；风电区示例：风电东村、风电威海。
     * 具体类型由 {@link com.hifleet.job.services.ZonePassageAnalysisService} 按 name 前缀打标。
     */
    public List<ZoneDefinition> loadBridgeAndWindFarmZones() {
        String sql = "SELECT oid AS id, name, AsText(`bound`) AS geom_wkt " +
                "FROM ais41.alertzones " +
                "WHERE bound IS NOT NULL AND parent = ?";
        return queryZones(ais41JdbcTemplate, sql, new Object[]{BRIDGE_WIND_FARM_PARENT}, "ais41.alertzones");
    }

    /**
     * 步骤5：加载 alertarea 账号区域（高风险警示区、冰区、海洋保护区、养殖区、排放控制区、MARPOL 等）。
     * 数据表：ais41.alertzones，parent = alertarea。
     * 海盗区、桥区等由 {@link com.hifleet.job.services.ZonePassageAnalysisService} 按 name 规则排除或分类。
     */
    public List<ZoneDefinition> loadAlertAreaZones() {
        String sql = "SELECT oid AS id, name, AsText(`bound`) AS geom_wkt " +
                "FROM ais41.alertzones " +
                "WHERE bound IS NOT NULL AND parent = ?";
        return queryZones(ais41JdbcTemplate, sql, new Object[]{ALERT_AREA_PARENT}, "ais41.alertzones");
    }

    private String buildPlaceholders(int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('?');
        }
        return builder.toString();
    }

    private List<ZoneDefinition> queryZones(JdbcTemplate jdbcTemplate, String sql, List<Integer> zoneIds, String source) {
        Object[] args = zoneIds == null || zoneIds.isEmpty() ? new Object[0] : zoneIds.toArray();
        return queryZones(jdbcTemplate, sql, args, source);
    }

    private List<ZoneDefinition> queryZones(JdbcTemplate jdbcTemplate, String sql, Object[] args, String source) {
        try {
            Object[] queryArgs = args == null ? new Object[0] : args;
            List<ZoneDefinition> zones = jdbcTemplate.query(sql, queryArgs, (rs, rowNum) -> mapZone(rs, source));
            List<ZoneDefinition> result = new ArrayList<ZoneDefinition>();
            for (ZoneDefinition zone : zones) {
                if (zone != null) {
                    result.add(zone);
                }
            }
            log.info("loaded {} zones from {}", result.size(), source);
            return result;
        } catch (Exception ex) {
            log.error("load zones failed, source={}", source, ex);
            return Collections.emptyList();
        }
    }

    private Geometry parseGeometry(String wkt) {
        if (StringUtils.isBlank(wkt)) {
            return null;
        }
        try {
            return WKT_READER.read(wkt);
        } catch (Exception ex) {
            log.warn("invalid zone wkt: {}", wkt, ex);
            return null;
        }
    }

    private ZoneDefinition mapZone(java.sql.ResultSet rs, String source) throws java.sql.SQLException {
        Geometry geometry = parseGeometry(rs.getString("geom_wkt"));
        if (geometry == null) {
            return null;
        }
        ZoneDefinition zone = new ZoneDefinition();
        zone.setId(rs.getInt("id"));
        zone.setName(rs.getString("name"));
        zone.setGeometry(geometry);
        zone.setSource(source);
        if ("zones_with_port_id".equals(source)) {
            zone.setPortId((Integer) rs.getObject("port_id"));
            zone.setZoneType((Integer) rs.getObject("zones_type"));
        }
        return zone;
    }
}
