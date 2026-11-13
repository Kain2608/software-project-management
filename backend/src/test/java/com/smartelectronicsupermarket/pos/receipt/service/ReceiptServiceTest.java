package com.smartelectronicsupermarket.pos.receipt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.pos.receipt.data.ReceiptDataAccess;
import com.smartelectronicsupermarket.pos.receipt.data.ReceiptRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ReceiptServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000821");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000822");

    private ReceiptDataAccess dataAccess;
    private ReceiptService service;

    @BeforeEach
    void setUp() {
        dataAccess = mock(ReceiptDataAccess.class);
        service = new ReceiptService(dataAccess);
    }

    @Test
    void returnsOnlyPaidReceiptAndKeepsMissingOrOutOfScopeConcealed() {
        ReceiptRecord paid = receipt("PAID");
        when(dataAccess.findByOrderId(ACTOR_ID, ORDER_ID)).thenReturn(Optional.of(paid));
        assertEquals(Optional.of(paid), service.findPaidReceipt(ACTOR_ID, ORDER_ID));

        when(dataAccess.findByOrderId(ACTOR_ID, ORDER_ID)).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), service.findPaidReceipt(ACTOR_ID, ORDER_ID));
    }

    @Test
    void rejectsNonPaidOrderWithoutRetryingOrPrinting() {
        when(dataAccess.findByOrderId(ACTOR_ID, ORDER_ID)).thenReturn(Optional.of(receipt("RESERVED")));

        assertThrows(ResourceConflictException.class, () -> service.findPaidReceipt(ACTOR_ID, ORDER_ID));
        verify(dataAccess, times(1)).findByOrderId(ACTOR_ID, ORDER_ID);
    }

    @Test
    void validatesIdentifiersAndPropagatesDatabaseFailure() {
        assertThrows(IllegalArgumentException.class, () -> service.findPaidReceipt(null, ORDER_ID));
        verify(dataAccess, never()).findByOrderId(null, ORDER_ID);

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db unavailable");
        when(dataAccess.findByOrderId(ACTOR_ID, ORDER_ID)).thenThrow(failure);
        assertSame(failure, assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.findPaidReceipt(ACTOR_ID, ORDER_ID)));
    }

    private static ReceiptRecord receipt(String status) {
        return new ReceiptRecord(
                ORDER_ID,
                status,
                new BigDecimal("100.00"),
                "PAID".equals(status) ? OffsetDateTime.parse("2026-11-10T10:00:00+07:00") : null,
                List.of());
    }
}
