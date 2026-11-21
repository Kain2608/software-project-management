package com.smartelectronicsupermarket.devicehistory.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcDeviceHistoryDataAccessTest {
    @Test void sqlScopesDeviceAndEveryEventSourceInsideDatabase(){
        assertTrue(JdbcDeviceHistoryDataAccess.DEVICE_SQL.contains("inventory.read"));
        assertTrue(JdbcDeviceHistoryDataAccess.EVENTS_SQL.contains("inventory.read"));
        assertTrue(JdbcDeviceHistoryDataAccess.EVENTS_SQL.contains("sales.read"));
        assertTrue(JdbcDeviceHistoryDataAccess.EVENTS_SQL.contains("warranty.manage"));
        assertFalse(JdbcDeviceHistoryDataAccess.EVENTS_SQL.toLowerCase().contains("customer"));
        assertTrue(JdbcDeviceHistoryDataAccess.EVENTS_SQL.contains("ORDER BY e.event_at DESC"));
    }
    @Test void databaseFailurePropagatesWithoutReturningEmptyHistory(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class); UUID device=UUID.randomUUID(),actor=UUID.randomUUID();
        var failure=new DataAccessResourceFailureException("down");
        when(jdbc.queryForObject(eq(JdbcDeviceHistoryDataAccess.DEVICE_SQL),any(org.springframework.jdbc.core.RowMapper.class),eq(device),eq(actor))).thenThrow(failure);
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class,
                ()->new JdbcDeviceHistoryDataAccess(jdbc).findDevice(device,actor)));
    }
}
