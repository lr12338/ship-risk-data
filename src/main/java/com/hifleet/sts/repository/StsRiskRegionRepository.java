package com.hifleet.sts.repository;

import com.hifleet.sts.domain.StsRiskRegionRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class StsRiskRegionRepository {

    @Autowired
    @Qualifier("newDataanalysisJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public List<StsRiskRegionRow> loadRegionSet(String regionSetCode) {
        String sql = "SELECT r.id, r.region_code, r.region_version, r.region_name, " +
                "r.geometry_wkt, r.analysis_buffer_nm, " +
                "COALESCE(si.priority, r.priority) AS priority, " +
                "r.status, r.enabled, r.geometry_fingerprint, " +
                "r.source_type, r.source_note " +
                "FROM dataanalysis.ship_sts_region_set_item si " +
                "INNER JOIN dataanalysis.ship_sts_risk_region r " +
                "  ON si.region_code = r.region_code AND si.region_version = r.region_version " +
                "WHERE si.region_set_code = ? " +
                "  AND si.enabled = 1 " +
                "  AND r.enabled = 1 " +
                "  AND r.status IN ('TEST', 'APPROVED') " +
                "ORDER BY COALESCE(si.priority, r.priority) ASC, r.region_code ASC";
        return jdbcTemplate.query(sql, new Object[]{regionSetCode}, new RiskRegionRowMapper());
    }

    private static class RiskRegionRowMapper implements RowMapper<StsRiskRegionRow> {
        @Override
        public StsRiskRegionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            StsRiskRegionRow r = new StsRiskRegionRow();
            r.setId(rs.getLong("id"));
            r.setRegionCode(rs.getString("region_code"));
            r.setRegionVersion(rs.getString("region_version"));
            r.setRegionName(rs.getString("region_name"));
            r.setGeometryWkt(rs.getString("geometry_wkt"));
            r.setAnalysisBufferNm(rs.getDouble("analysis_buffer_nm"));
            r.setPriority(rs.getInt("priority"));
            r.setStatus(rs.getString("status"));
            r.setEnabled(rs.getInt("enabled"));
            r.setGeometryFingerprint(rs.getString("geometry_fingerprint"));
            r.setSourceType(rs.getString("source_type"));
            r.setSourceNote(rs.getString("source_note"));
            return r;
        }
    }
}
