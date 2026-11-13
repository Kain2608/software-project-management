package com.smartelectronicsupermarket.pos.selection.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.smartelectronicsupermarket.pos.selection.data.PosDeviceSelectionDataAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;

class PosDeviceSelectionServiceTest {

    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000912");
    private static final UUID ORDER_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000913");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000914");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000915");

    private PosDeviceSelectionDataAccess dataAccess;
    private PosDeviceSelectionService service;

    @BeforeEach
    void setUp() {
        dataAccess = mock(PosDeviceSelectionDataAccess.class);
        service = new PosDeviceSelectionService(dataAccess);
    }

    @Test
    void locksDeviceOnceWithoutChangingSerialOrTrustedActor() {
        String serial = "  Serial-Abc  ";
        when(dataAccess.lockDevice(STORE_ID, ORDER_ID, ORDER_ITEM_ID, serial, ACTOR_ID))
                .thenReturn(DEVICE_ID);

        assertEquals(
                DEVICE_ID,
                service.lockDevice(STORE_ID, ORDER_ID, ORDER_ITEM_ID, serial, ACTOR_ID));
        verify(dataAccess, times(1))
                .lockDevice(STORE_ID, ORDER_ID, ORDER_ITEM_ID, serial, ACTOR_ID);
    }

    @Test
    void rejectsMissingRequiredInputWithoutCallingDatabase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lockDevice(null, ORDER_ID, ORDER_ITEM_ID, "SERIAL-1", ACTOR_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lockDevice(STORE_ID, ORDER_ID, ORDER_ITEM_ID, " ", ACTOR_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.releaseDevice(ORDER_ID, DEVICE_ID, null));

        verify(dataAccess, never()).lockDevice(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(dataAccess, never()).releaseDevice(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void releasesDeviceOnceWithOriginalIdentifiers() {
        when(dataAccess.releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID)).thenReturn(true);

        assertTrue(service.releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID));
        verify(dataAccess, times(1)).releaseDevice(ORDER_ID, DEVICE_ID, ACTOR_ID);
    }

    @Test
    void propagatesConcurrencyFailureWithoutRetry() {
        ConcurrencyFailureException failure = new ConcurrencyFailureException("device locked");
        when(dataAccess.lockDevice(STORE_ID, ORDER_ID, ORDER_ITEM_ID, "SERIAL-1", ACTOR_ID))
                .thenThrow(failure);

        ConcurrencyFailureException thrown = assertThrows(
                ConcurrencyFailureException.class,
                () -> service.lockDevice(
                        STORE_ID, ORDER_ID, ORDER_ITEM_ID, "SERIAL-1", ACTOR_ID));

        assertSame(failure, thrown);
        verify(dataAccess, times(1))
                .lockDevice(STORE_ID, ORDER_ID, ORDER_ITEM_ID, "SERIAL-1", ACTOR_ID);
    }
}
