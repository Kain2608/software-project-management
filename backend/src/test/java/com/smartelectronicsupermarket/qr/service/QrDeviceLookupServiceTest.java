package com.smartelectronicsupermarket.qr.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import com.smartelectronicsupermarket.qr.QrPayloadParser;
import com.smartelectronicsupermarket.qr.data.QrDeviceLookupDataAccess;
import com.smartelectronicsupermarket.qr.data.QrDeviceRecord;

class QrDeviceLookupServiceTest {

    private static final String RAW_PAYLOAD =
            "SASMS:DEVICE:00000000-0000-0000-0000-000000000041";
    private static final UUID QR_TOKEN =
            UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final QrDeviceRecord DEVICE = new QrDeviceRecord(
            UUID.fromString("00000000-0000-0000-0000-000000000043"),
            UUID.fromString("00000000-0000-0000-0000-000000000044"),
            UUID.fromString("00000000-0000-0000-0000-000000000045"),
            "IN_STOCK");

    private QrPayloadParser payloadParser;
    private QrDeviceLookupDataAccess dataAccess;
    private QrDeviceLookupService service;

    @BeforeEach
    void setUp() {
        payloadParser = mock(QrPayloadParser.class);
        dataAccess = mock(QrDeviceLookupDataAccess.class);
        service = new QrDeviceLookupService(payloadParser, dataAccess);
    }

    @Test
    void validPayloadUsesParsedQrTokenAndInternalActorOnce() {
        Optional<QrDeviceRecord> expected = Optional.of(DEVICE);
        when(payloadParser.parse(RAW_PAYLOAD)).thenReturn(QR_TOKEN);
        when(dataAccess.findByQrToken(QR_TOKEN, ACTOR_ID)).thenReturn(expected);

        Optional<QrDeviceRecord> result = service.lookup(RAW_PAYLOAD, ACTOR_ID);

        assertSame(expected, result);
        InOrder order = inOrder(payloadParser, dataAccess);
        order.verify(payloadParser).parse(RAW_PAYLOAD);
        order.verify(dataAccess).findByQrToken(QR_TOKEN, ACTOR_ID);
        order.verifyNoMoreInteractions();
    }

    @Test
    void missingOrUnauthorizedResultRemainsIndistinguishableAndEmpty() {
        Optional<QrDeviceRecord> expected = Optional.empty();
        when(payloadParser.parse(RAW_PAYLOAD)).thenReturn(QR_TOKEN);
        when(dataAccess.findByQrToken(QR_TOKEN, ACTOR_ID)).thenReturn(expected);

        Optional<QrDeviceRecord> result = service.lookup(RAW_PAYLOAD, ACTOR_ID);

        assertSame(expected, result);
        verify(dataAccess, times(1)).findByQrToken(QR_TOKEN, ACTOR_ID);
    }

    @Test
    void rejectsNullActorWithoutCreatingOrUsingAnActor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lookup(RAW_PAYLOAD, null));

        verifyNoInteractions(payloadParser, dataAccess);
    }

    @Test
    void parserRejectsNullEmptyAndMalformedPayloadWithoutCallingDao() {
        String malformed = "https://example.test/device";
        when(payloadParser.parse(null))
                .thenThrow(new IllegalArgumentException("QR payload is required"));
        when(payloadParser.parse(""))
                .thenThrow(new IllegalArgumentException("QR payload must not be empty"));
        when(payloadParser.parse(malformed))
                .thenThrow(new IllegalArgumentException("unsupported QR payload"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.lookup(null, ACTOR_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lookup("", ACTOR_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lookup(malformed, ACTOR_ID));

        verify(payloadParser).parse(null);
        verify(payloadParser).parse("");
        verify(payloadParser).parse(malformed);
        verifyNoInteractions(dataAccess);
    }

    @Test
    void propagatesDaoFailureWithoutRetryOrFallback() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(payloadParser.parse(RAW_PAYLOAD)).thenReturn(QR_TOKEN);
        when(dataAccess.findByQrToken(QR_TOKEN, ACTOR_ID)).thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.lookup(RAW_PAYLOAD, ACTOR_ID));

        assertSame(failure, thrown);
        verify(payloadParser, times(1)).parse(RAW_PAYLOAD);
        verify(dataAccess, times(1)).findByQrToken(QR_TOKEN, ACTOR_ID);
    }
}
