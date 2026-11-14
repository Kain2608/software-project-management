package com.smartelectronicsupermarket.inventory.stock.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.smartelectronicsupermarket.inventory.stock.data.InventoryStockDataAccess;
import com.smartelectronicsupermarket.inventory.stock.data.InventoryStockRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class InventoryStockServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");

    private InventoryStockDataAccess dataAccess;
    private InventoryStockService service;

    @BeforeEach
    void setUp() {
        dataAccess = mock(InventoryStockDataAccess.class);
        service = new InventoryStockService(dataAccess);
    }

    @Test
    void delegatesTrustedActorScopeAndReturnsFreshQueryResult() {
        List<InventoryStockRecord> expected = List.of(new InventoryStockRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000803"),
                WAREHOUSE_ID,
                3,
                2,
                1));
        when(dataAccess.listStock(ACTOR_ID, WAREHOUSE_ID, "TV-55", 20, 0)).thenReturn(expected);

        assertSame(expected, service.listStock(ACTOR_ID, WAREHOUSE_ID, " TV-55 ", 20, 0));
        verify(dataAccess).listStock(ACTOR_ID, WAREHOUSE_ID, "TV-55", 20, 0);
    }

    @Test
    void validatesPaginationAndTrustedActorBeforeDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.listStock(null, null, null, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> service.listStock(ACTOR_ID, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.listStock(ACTOR_ID, null, null, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> service.listStock(ACTOR_ID, null, null, 20, -1));

        verify(dataAccess, never()).listStock(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void propagatesDatabaseFailureWithoutRetryOrStaleFallback() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("db unavailable");
        when(dataAccess.listStock(ACTOR_ID, null, null, 20, 0)).thenThrow(failure);

        assertSame(failure, assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.listStock(ACTOR_ID, null, " ", 20, 0)));
        verify(dataAccess, times(1)).listStock(ACTOR_ID, null, null, 20, 0);
    }
}
