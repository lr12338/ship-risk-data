package com.hifleet.job.services;

import com.hifleet.job.services.ShipLoiteringV3BatchService.BatchResult;
import com.hifleet.job.services.ShipLoiteringV3BatchService.EventStore;
import com.hifleet.job.services.ShipLoiteringV3BatchService.JobLog;
import com.hifleet.job.services.ShipLoiteringV3BatchService.JobRequest;
import com.hifleet.job.services.ShipLoiteringV3BatchService.JdbcShipPageSource;
import com.hifleet.job.services.ShipLoiteringV3BatchService.ProgressStore;
import com.hifleet.job.services.ShipLoiteringV3BatchService.ReplacingEventStore;
import com.hifleet.job.services.ShipLoiteringV3BatchService.ShipPageSource;
import com.hifleet.job.services.ShipLoiteringV3BatchService.ShipWindowAnalyzer;
import com.hifleet.job.services.ShipLoiteringV3BatchService.WindowResult;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import com.hifleet.service.LoiteringV3AnalyzerService.AnalyzedEvent;
import com.hifleet.service.LoiteringV3AnalyzerService.Ship;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ShipLoiteringV3BatchServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-27T04:00:00Z"), ZONE);

    @Test
    public void testModeUsesSafeDefaults() {
        JobRequest request = ShipLoiteringV3BatchService.parseRequest(
                "{\"mode\":\"test\"}", CLOCK, 100, 500, 500);

        assertEquals("test", request.getMode());
        assertEquals(LocalDateTime.of(2026, 6, 27, 12, 0), request.getStartTime());
        assertEquals(LocalDateTime.of(2026, 7, 27, 12, 0), request.getEndTime());
        assertEquals(100, request.getLimit());
        assertEquals(0, request.getOffset());
        assertNull(request.getUpdatedAfter());
    }

    @Test
    public void historyModeKeepsExplicitPageAndRange() {
        JobRequest request = ShipLoiteringV3BatchService.parseRequest(
                "{\"mode\":\"history\",\"startTime\":\"2025-01-01 00:00:00\","
                        + "\"endTime\":\"2025-01-03 00:00:00\",\"limit\":500,\"offset\":1000}",
                CLOCK, 100, 500, 500);

        assertEquals("history", request.getMode());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), request.getStartTime());
        assertEquals(LocalDateTime.of(2025, 1, 3, 0, 0), request.getEndTime());
        assertEquals(500, request.getLimit());
        assertEquals(1000, request.getOffset());
    }

    @Test(expected = IllegalArgumentException.class)
    public void historyModeRequiresTimeRange() {
        ShipLoiteringV3BatchService.parseRequest(
                "{\"mode\":\"history\",\"limit\":500}", CLOCK, 100, 500, 500);
    }

    @Test
    public void batchUsesPaginationDailyWindowsAndIsolatesShipFailure() {
        RecordingShipPageSource ships = new RecordingShipPageSource(Arrays.asList(
                ship("412000001"), ship("412000002"), ship("412000003")));
        RecordingAnalyzer analyzer = new RecordingAnalyzer();
        analyzer.failedMmsi = "412000002";
        RecordingEventStore events = new RecordingEventStore();
        RecordingProgressStore progress = new RecordingProgressStore();
        RecordingLog log = new RecordingLog();
        ShipLoiteringV3BatchService service = service(
                ships, analyzer, events, progress, log);

        BatchResult result = service.execute(
                "{\"mode\":\"history\",\"startTime\":\"2025-01-01 00:00:00\","
                        + "\"endTime\":\"2025-01-03 00:00:00\",\"limit\":3,\"offset\":500}");

        assertEquals(500, ships.offset);
        assertEquals(3, ships.limit);
        assertNull(ships.updatedAfter);
        assertEquals(5, analyzer.calls.size());
        assertEquals("412000001|2025-01-01T00:00|2025-01-02T00:00", analyzer.calls.get(0));
        assertEquals("412000001|2025-01-02T00:00|2025-01-03T00:00", analyzer.calls.get(1));
        assertEquals(4, events.replacedWindows.size());
        assertEquals(4, progress.doneWindows.size());
        assertEquals(1, progress.failedWindows.size());
        assertEquals(2, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertEquals(3, result.getShipCount());
        assertTrue(log.joined().contains("Loitering V3 Batch Start"));
        assertTrue(log.joined().contains("success=2 failed=1"));
    }

    @Test
    public void incrementModeStartsAtEachShipsLastSuccessfulCalculation() {
        RecordingShipPageSource ships = new RecordingShipPageSource(
                Collections.singletonList(ship("412000001")));
        RecordingAnalyzer analyzer = new RecordingAnalyzer();
        RecordingProgressStore progress = new RecordingProgressStore();
        progress.lastCalculated.put("412000001",
                LocalDateTime.of(2026, 7, 27, 9, 0));
        ShipLoiteringV3BatchService service = service(
                ships, analyzer, new RecordingEventStore(), progress, new RecordingLog());

        BatchResult result = service.execute("{\"mode\":\"increment\",\"hours\":24}");

        assertEquals(LocalDateTime.of(2026, 7, 26, 12, 0), ships.updatedAfter);
        assertEquals(Collections.singletonList(
                "412000001|2026-07-27T09:00|2026-07-27T12:00"), analyzer.calls);
        assertEquals(1, result.getSuccess());
        assertEquals(0, result.getFailed());
    }

    @Test
    public void repeatedWindowReplacesOldDetailsAndEventsBeforeWriting() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        ShipBehaviorEventRepository eventRepository =
                mock(ShipBehaviorEventRepository.class);
        ShipBehaviorEventDetailRepository detailRepository =
                mock(ShipBehaviorEventDetailRepository.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        ReplacingEventStore store = new ReplacingEventStore(
                jdbcTemplate, dataSource, eventRepository, detailRepository);
        Ship ship = ship("412000001");
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 2, 0, 0);

        store.replace(ship, start, end, "loitering-v3-test",
                Collections.<AnalyzedEvent>emptyList());
        store.replace(ship, start, end, "loitering-v3-test",
                Collections.<AnalyzedEvent>emptyList());

        InOrder order = inOrder(jdbcTemplate, eventRepository, detailRepository);
        order.verify(jdbcTemplate).update(
                contains("ship_behavior_event_detail"),
                any(), any(), any(), any(), any());
        order.verify(jdbcTemplate).update(
                contains("ship_behavior_event "),
                any(), any(), any(), any(), any());
        order.verify(eventRepository).saveEvents(any(List.class));
        order.verify(detailRepository).saveDetails(any(List.class));
        verify(jdbcTemplate, times(2)).update(
                contains("ship_behavior_event_detail"),
                any(), any(), any(), any(), any());
        verify(jdbcTemplate, times(2)).update(
                contains("ship_behavior_event "),
                any(), any(), any(), any(), any());
    }

    @Test
    public void shipPageSqlKeepsExistingFiltersAndUsesLimitOffset() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        JdbcShipPageSource source = new JdbcShipPageSource(jdbcTemplate);
        LocalDateTime updatedAfter = LocalDateTime.of(2026, 7, 26, 12, 0);

        source.select(500, 500, updatedAfter);

        assertTrue(jdbcTemplate.sql.contains("`type`!='渔船'"));
        assertTrue(jdbcTemplate.sql.contains("length>70"));
        assertTrue(jdbcTemplate.sql.contains(
                "mmsi BETWEEN 100000000 AND 999999999"));
        assertTrue(jdbcTemplate.sql.contains("updatetime>=?"));
        assertTrue(jdbcTemplate.sql.contains("ORDER BY mmsi LIMIT ? OFFSET ?"));
        assertArrayEquals(new Object[]{
                Timestamp.valueOf(updatedAfter), 500, 500}, jdbcTemplate.arguments);
    }

    private ShipLoiteringV3BatchService service(ShipPageSource ships,
                                                ShipWindowAnalyzer analyzer,
                                                EventStore events,
                                                ProgressStore progress,
                                                JobLog log) {
        return new ShipLoiteringV3BatchService(
                ships, analyzer, events, progress, log, CLOCK,
                100, 500, 500, 24);
    }

    private static Ship ship(String mmsi) {
        return new Ship(mmsi, "IMO" + mmsi, "SHIP-" + mmsi, "集装箱船");
    }

    private static class RecordingShipPageSource implements ShipPageSource {
        private final List<Ship> ships;
        private int offset;
        private int limit;
        private LocalDateTime updatedAfter;

        private RecordingShipPageSource(List<Ship> ships) {
            this.ships = ships;
        }

        @Override
        public List<Ship> select(int offset, int limit, LocalDateTime updatedAfter) {
            this.offset = offset;
            this.limit = limit;
            this.updatedAfter = updatedAfter;
            return ships;
        }
    }

    private static class RecordingAnalyzer implements ShipWindowAnalyzer {
        private final List<String> calls = new ArrayList<String>();
        private String failedMmsi;

        @Override
        public WindowResult analyze(Ship ship, LocalDateTime startTime,
                                    LocalDateTime endTime) {
            calls.add(ship.getMmsi() + "|" + startTime + "|" + endTime);
            if (ship.getMmsi().equals(failedMmsi)) {
                throw new IllegalStateException("synthetic failure");
            }
            return new WindowResult(20, Collections.<AnalyzedEvent>emptyList());
        }
    }

    private static class RecordingEventStore implements EventStore {
        private final List<String> replacedWindows = new ArrayList<String>();

        @Override
        public void replace(Ship ship, LocalDateTime startTime, LocalDateTime endTime,
                            String batchNo, List<AnalyzedEvent> analyzedEvents) {
            replacedWindows.add(ship.getMmsi() + "|" + startTime + "|" + endTime);
        }
    }

    private static class RecordingProgressStore implements ProgressStore {
        private final Map<String, LocalDateTime> lastCalculated =
                new HashMap<String, LocalDateTime>();
        private final List<String> doneWindows = new ArrayList<String>();
        private final List<String> failedWindows = new ArrayList<String>();

        @Override
        public LocalDateTime lastCalculatedAt(String mmsi) {
            return lastCalculated.get(mmsi);
        }

        @Override
        public void markDone(String batchNo, String mmsi, LocalDateTime startTime,
                             LocalDateTime endTime, int pointCount, int eventCount) {
            doneWindows.add(mmsi + "|" + startTime + "|" + endTime);
        }

        @Override
        public void markFailed(String batchNo, String mmsi, LocalDateTime startTime,
                               LocalDateTime endTime, String errorMessage) {
            failedWindows.add(mmsi + "|" + startTime + "|" + endTime);
        }
    }

    private static class RecordingLog implements JobLog {
        private final List<String> messages = new ArrayList<String>();

        @Override
        public void log(String message) {
            messages.add(message);
        }

        private String joined() {
            return messages.toString();
        }
    }

    private static class CapturingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] arguments;

        @Override
        public <T> List<T> query(String sql, Object[] args, RowMapper<T> rowMapper) {
            this.sql = sql;
            this.arguments = args;
            return Collections.emptyList();
        }
    }
}
