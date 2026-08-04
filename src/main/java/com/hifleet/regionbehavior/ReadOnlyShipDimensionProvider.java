package com.hifleet.regionbehavior;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class ReadOnlyShipDimensionProvider implements ShipDimensionProvider {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ReadOnlyShipDimensionProvider(@Qualifier("aid41JdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Double> lengths(String ownMmsi, String targetMmsi) {
        if (ownMmsi == null || targetMmsi == null) return Collections.emptyMap();
        try {
            Map<String, Double> result = new HashMap<String, Double>();
            jdbcTemplate.query("SELECT CAST(mmsi AS CHAR) mmsi, length FROM ais41.recentships "
                            + "WHERE mmsi IN (?,?)", new Object[]{ownMmsi, targetMmsi}, rs -> {
                        double length = rs.getDouble("length");
                        if (!rs.wasNull() && length > 0.0) result.put(rs.getString("mmsi"), length);
                    });
            return result;
        } catch (RuntimeException ex) {
            return Collections.emptyMap();
        }
    }
}
