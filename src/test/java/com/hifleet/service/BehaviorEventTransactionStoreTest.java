package com.hifleet.service;

import com.hifleet.domain.ShipBehaviorEvent;
import com.hifleet.domain.ShipBehaviorEventDetail;
import com.hifleet.domain.ShipBehaviorTrace;
import com.hifleet.repository.ShipBehaviorEventDetailRepository;
import com.hifleet.repository.ShipBehaviorEventRepository;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BehaviorEventTransactionStoreTest {

    @Test
    public void eventOnlyDeletesAndWritesEventsWithoutTouchingDetails() throws Exception {
        Fixture fixture = fixture();

        fixture.store.replace(
                ShipBehaviorEvent.TYPE_LOITERING, "batch-a", "413000001",
                "2026-01-01 00:00:00", "2026-07-01 00:00:00",
                Collections.singletonList("LOITERING_V2_INSURANCE"),
                Collections.singletonList(trace()), false);

        verify(fixture.jdbc).update(
                contains("ship_behavior_event "),
                any(), any(), any(), any(), any(), any());
        verify(fixture.jdbc, never()).update(
                contains("ship_behavior_event_detail"),
                any(), any(), any(), any(), any(), any());
        verify(fixture.events).saveEvents(anyList());
        verify(fixture.details, never()).saveDetails(anyList());
        verify(fixture.connection).commit();
    }

    @Test
    public void repositoryFailureRollsBackDeleteAndInsertTransaction() throws Exception {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("insert failed"))
                .when(fixture.events).saveEvents(anyList());
        try {
            fixture.store.replace(
                    ShipBehaviorEvent.TYPE_LOITERING, "batch-a", "413000001",
                    "2026-01-01 00:00:00", "2026-07-01 00:00:00",
                    Collections.singletonList("LOITERING_V2_INSURANCE"),
                    Collections.singletonList(trace()), true);
        } catch (IllegalStateException expected) {
            // expected
        }

        verify(fixture.connection).rollback();
        verify(fixture.details, never()).saveDetails(any(List.class));
    }

    private Fixture fixture() throws Exception {
        Fixture fixture = new Fixture();
        fixture.store = new BehaviorEventTransactionStore();
        fixture.jdbc = mock(JdbcTemplate.class);
        fixture.dataSource = mock(DataSource.class);
        fixture.connection = mock(Connection.class);
        fixture.events = mock(ShipBehaviorEventRepository.class);
        fixture.details = mock(ShipBehaviorEventDetailRepository.class);
        when(fixture.dataSource.getConnection()).thenReturn(fixture.connection);
        when(fixture.connection.getAutoCommit()).thenReturn(true);
        ReflectionTestUtils.setField(fixture.store, "jdbcTemplate", fixture.jdbc);
        ReflectionTestUtils.setField(fixture.store, "dataSource", fixture.dataSource);
        ReflectionTestUtils.setField(fixture.store, "eventRepository", fixture.events);
        ReflectionTestUtils.setField(fixture.store, "detailRepository", fixture.details);
        return fixture;
    }

    private ShipBehaviorTrace trace() {
        ShipBehaviorEvent event = new ShipBehaviorEvent();
        event.setBehaviorType(ShipBehaviorEvent.TYPE_LOITERING);
        event.setMmsi("413000001");
        event.setStartTime("2026-01-01 01:00:00");
        event.setEndTime("2026-01-01 02:00:00");
        event.setRuleVersion("LOITERING_V2_INSURANCE");
        event.setBatchNo("batch-a");
        return new ShipBehaviorTrace(
                event, Collections.emptyList(), Collections.emptyList());
    }

    private static class Fixture {
        private BehaviorEventTransactionStore store;
        private JdbcTemplate jdbc;
        private DataSource dataSource;
        private Connection connection;
        private ShipBehaviorEventRepository events;
        private ShipBehaviorEventDetailRepository details;
    }
}
