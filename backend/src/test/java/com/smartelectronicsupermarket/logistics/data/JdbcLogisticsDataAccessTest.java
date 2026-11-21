package com.smartelectronicsupermarket.logistics.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcLogisticsDataAccessTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JdbcLogisticsDataAccess data = new JdbcLogisticsDataAccess(jdbc);
    private final UUID order = UUID.randomUUID(), technician = UUID.randomUUID(), actor = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-12-01T02:00:00Z");
    private final Instant end = Instant.parse("2026-12-01T03:00:00Z");

    @Test
    void callsV006ScheduleFunctionWithExactOrder() {
        UUID delivery = UUID.randomUUID();
        when(jdbc.queryForObject(JdbcLogisticsDataAccess.SCHEDULE_SQL, UUID.class, order, technician,
                Timestamp.from(start), Timestamp.from(end), "address", actor)).thenReturn(delivery);
        assertEquals(delivery, data.scheduleDelivery(order, technician, start, end, "address", actor));
        verify(jdbc).queryForObject(JdbcLogisticsDataAccess.SCHEDULE_SQL, UUID.class, order, technician,
                Timestamp.from(start), Timestamp.from(end), "address", actor);
    }

    @Test
    void callsV006ResultFunctionAndPropagatesFailureWithoutRetry() {
        UUID delivery = UUID.randomUUID();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db down");
        when(jdbc.queryForObject(JdbcLogisticsDataAccess.RESULT_SQL, UUID.class, delivery,
                "FAILED", "E-1", actor)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> data.recordDeliveryResult(delivery, "FAILED", "E-1", actor)));
        verify(jdbc, times(1)).queryForObject(JdbcLogisticsDataAccess.RESULT_SQL, UUID.class,
                delivery, "FAILED", "E-1", actor);
    }

    @Test
    void readQueryKeepsAuthorizationInSqlAndReturnsEmptyWhenConcealed() {
        assertTrue(JdbcLogisticsDataAccess.FIND_SQL.contains("logistics.manage"));
        assertTrue(JdbcLogisticsDataAccess.FIND_SQL.contains("logistics.execute"));
        assertTrue(JdbcLogisticsDataAccess.FIND_SQL.contains("a.technician_id"));
        assertTrue(data.findById(UUID.randomUUID(), actor).isEmpty());
    }
}
