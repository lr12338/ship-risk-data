package com.hifleet.behaviorplatform;

import com.hifleet.domain.TargetShip;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public final class InlineShipTargetResolver {
    private final JdbcTemplate jdbcTemplate;
    public InlineShipTargetResolver(@Qualifier("aid41JdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    public List<TargetShip> resolve(List<String> mmsis) {
        if (mmsis.isEmpty()) return java.util.Collections.emptyList();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < mmsis.size(); i++) placeholders.append(i == 0 ? "?" : ",?");
        Map<String, TargetShip> found = new LinkedHashMap<String, TargetShip>();
        for (TargetShip ship : jdbcTemplate.query("SELECT CAST(mmsi AS CHAR) mmsi,imonumber,name,`type` "
                + "FROM ais41.recentships WHERE mmsi IN (" + placeholders + ")", mmsis.toArray(),
                (rs, rowNum) -> new TargetShip(rs.getString("mmsi"), rs.getString("imonumber"),
                        rs.getString("name"), rs.getString("type"), null))) found.put(ship.getMmsi(), ship);
        List<TargetShip> ordered = new ArrayList<TargetShip>();
        int seq = 0;
        for (String mmsi : mmsis) {
            TargetShip value = found.get(mmsi);
            if (value == null) value = new TargetShip(mmsi, null, null, null, null);
            value.setSeqNo(++seq);
            ordered.add(value);
        }
        return ordered;
    }
}
