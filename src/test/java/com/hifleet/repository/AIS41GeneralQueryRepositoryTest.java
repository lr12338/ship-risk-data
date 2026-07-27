package com.hifleet.repository;

import com.hifleet.beans.ShipForm;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AIS41GeneralQueryRepositoryTest {

    @Test
    public void recentShipsPageUsesMmsiCursorAndLimit() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        AIS41GeneralQueryRepository repository = new AIS41GeneralQueryRepository();
        ReflectionTestUtils.setField(repository, "ais41JdbcTemplate", jdbcTemplate);

        List<ShipForm> ships = repository.selectRecentShipsPage("413000001", 100);

        assertEquals(1, ships.size());
        assertEquals("413000002", ships.get(0).getMmsi());
        assertTrue(jdbcTemplate.sql.contains("mmsi > ?"));
        assertTrue(jdbcTemplate.sql.contains("ORDER BY mmsi"));
        assertTrue(jdbcTemplate.sql.contains("LIMIT ?"));
        assertArrayEquals(new Object[]{413000001L, 100}, jdbcTemplate.params);
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] params;

        @Override
        public <T> List<T> query(String sql, Object[] args, RowMapper<T> rowMapper) {
            this.sql = sql;
            this.params = args;
            List<T> result = new ArrayList<T>();
            ShipForm ship = new ShipForm();
            ship.setMmsi("413000002");
            result.add((T) ship);
            return result;
        }
    }
}
