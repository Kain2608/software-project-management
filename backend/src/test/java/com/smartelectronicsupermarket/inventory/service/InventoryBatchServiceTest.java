package com.smartelectronicsupermarket.inventory.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.smartelectronicsupermarket.inventory.data.InventoryBatchDataAccess;

class InventoryBatchServiceTest {

    private static final UUID RECEIPT_ITEM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID REQUEST_KEY =
            UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000023");
    private static final String VALID_DEVICES_JSON = """
            [ { "serial" : " serial-3 ", "imei" : null },
              { "serial" : "SERIAL-2", "imei" : "123456789012345" },
              { "serial" : "SERIAL-1" } ]
            """.strip();

    private InventoryBatchDataAccess dataAccess;
    private InventoryBatchService service;

    @BeforeEach
    void setUp() {
        dataAccess = mock(InventoryBatchDataAccess.class);
        service = new InventoryBatchService(dataAccess);
    }

    @Test
    void validBatchPreservesInputsAndReturnsIdsInDaoOrder() {
        UUID[] databaseOrder = {
                UUID.fromString("00000000-0000-0000-0000-000000000203"),
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                UUID.fromString("00000000-0000-0000-0000-000000000201")
        };
        when(dataAccess.receiveDeviceBatch(
                RECEIPT_ITEM_ID,
                REQUEST_KEY,
                VALID_DEVICES_JSON,
                ACTOR_ID)).thenReturn(databaseOrder);

        UUID[] result = service.receiveDeviceBatch(
                RECEIPT_ITEM_ID,
                REQUEST_KEY,
                VALID_DEVICES_JSON,
                ACTOR_ID);

        assertArrayEquals(databaseOrder, result);
        verify(dataAccess, times(1)).receiveDeviceBatch(
                RECEIPT_ITEM_ID,
                REQUEST_KEY,
                VALID_DEVICES_JSON,
                ACTOR_ID);
    }

    @Test
    void rejectsEmptyArrayWithoutCallingDao() {
        assertValidationFailure("[]");
    }

    @Test
    void rejectsBatchLargerThanFiveHundredWithoutCallingDao() {
        String devicesJson = "[" + String.join(",",
                Collections.nCopies(501, "{\"serial\":\"SERIAL\"}")) + "]";

        assertValidationFailure(devicesJson);
    }

    @Test
    void rejectsMalformedJsonWithoutCallingDao() {
        assertValidationFailure("[{\"serial\":]");
    }

    @Test
    void rejectsNullJsonWithoutCallingDao() {
        assertValidationFailure(null);
    }

    @Test
    void rejectsObjectInsteadOfArrayWithoutCallingDao() {
        assertValidationFailure("{\"serial\":\"SERIAL-1\"}");
    }

    @Test
    void rejectsNonObjectArrayElementWithoutCallingDao() {
        assertValidationFailure("[\"SERIAL-1\"]");
    }

    @Test
    void rejectsMissingSerialWithoutCallingDao() {
        assertValidationFailure("[{\"imei\":null}]");
    }

    @Test
    void rejectsNonStringSerialWithoutCallingDao() {
        assertValidationFailure("[{\"serial\":123}]");
    }

    @Test
    void rejectsInvalidImeiTypeWithoutCallingDao() {
        assertValidationFailure("[{\"serial\":\"SERIAL-1\",\"imei\":123}]");
    }

    @Test
    void rejectsUnsupportedFieldWithoutCallingDao() {
        assertValidationFailure(
                "[{\"serial\":\"SERIAL-1\",\"productId\":\"unexpected\"}]");
    }

    @Test
    void rejectsMissingRequiredUuidsWithoutCallingDao() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.receiveDeviceBatch(
                        null, REQUEST_KEY, VALID_DEVICES_JSON, ACTOR_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.receiveDeviceBatch(
                        RECEIPT_ITEM_ID, null, VALID_DEVICES_JSON, ACTOR_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.receiveDeviceBatch(
                        RECEIPT_ITEM_ID, REQUEST_KEY, VALID_DEVICES_JSON, null));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void propagatesDaoFailureWithoutRetryOrFallback() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(dataAccess.receiveDeviceBatch(
                RECEIPT_ITEM_ID,
                REQUEST_KEY,
                VALID_DEVICES_JSON,
                ACTOR_ID)).thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.receiveDeviceBatch(
                        RECEIPT_ITEM_ID,
                        REQUEST_KEY,
                        VALID_DEVICES_JSON,
                        ACTOR_ID));

        assertSame(failure, thrown);
        verify(dataAccess, times(1)).receiveDeviceBatch(
                RECEIPT_ITEM_ID,
                REQUEST_KEY,
                VALID_DEVICES_JSON,
                ACTOR_ID);
    }

    private void assertValidationFailure(String devicesJson) {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.receiveDeviceBatch(
                        RECEIPT_ITEM_ID, REQUEST_KEY, devicesJson, ACTOR_ID));
        verifyNoInteractions(dataAccess);
    }
}
