package com.smartelectronicsupermarket.logistics.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;

import com.smartelectronicsupermarket.logistics.data.LogisticsDataAccess;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class LogisticsServiceTest {
    private final LogisticsDataAccess data = mock(LogisticsDataAccess.class);
    private final LogisticsService service = new LogisticsService(data);
    private final UUID order = UUID.randomUUID(), technician = UUID.randomUUID(), actor = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-12-01T02:00:00Z");
    private final Instant end = Instant.parse("2026-12-01T03:00:00Z");

    @Test
    void schedulesWithTrustedActorAndV006Inputs() {
        UUID result = UUID.randomUUID();
        when(data.scheduleDelivery(order, technician, start, end, "A", actor)).thenReturn(result);
        assertEquals(result, service.schedule(order, technician, start, end, " A ", actor));
        verify(data).scheduleDelivery(order, technician, start, end, "A", actor);
    }

    @Test
    void rejectsInvalidWindowAndAddressBeforeDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.schedule(order, technician, end, start, "A", actor));
        assertThrows(IllegalArgumentException.class,
                () -> service.schedule(order, technician, start, end, " ", actor));
        verifyNoInteractions(data);
    }

    @Test
    void validatesResultAndDoesNotRetryDatabaseFailure() {
        UUID delivery = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.recordResult(delivery, "DELIVERED", "E", actor));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db down");
        when(data.recordDeliveryResult(delivery, "COMPLETED", "E", actor)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.recordResult(delivery, "COMPLETED", " E ", actor)));
        verify(data, times(1)).recordDeliveryResult(delivery, "COMPLETED", "E", actor);
    }

    @Test
    void requiresActorAndNeverCreatesOne() {
        assertThrows(IllegalArgumentException.class,
                () -> service.schedule(order, technician, start, end, "A", null));
        verifyNoInteractions(data);
    }
}
