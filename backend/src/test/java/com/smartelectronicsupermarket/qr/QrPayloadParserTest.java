package com.smartelectronicsupermarket.qr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QrPayloadParserTest {

    private static final UUID QR_TOKEN =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private QrPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new QrPayloadParser();
    }

    @Test
    void validInternalPayloadReturnsEncodedQrToken() {
        UUID result = parser.parse("SASMS:DEVICE:" + QR_TOKEN);

        assertEquals(QR_TOKEN, result);
    }

    @Test
    void rejectsNullPayload() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void rejectsEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }

    @Test
    void rejectsUnsupportedPrefix() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("SASMS:PRODUCT:" + QR_TOKEN));
    }

    @Test
    void rejectsMissingToken() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("SASMS:DEVICE:"));
    }

    @Test
    void rejectsUuidVariantAcceptedAndPaddedByJavaParser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("SASMS:DEVICE:1-1-1-1-1"));
    }

    @Test
    void rejectsTrailingPayloadContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("SASMS:DEVICE:" + QR_TOKEN + ":EXTRA"));
    }

    @Test
    void doesNotNormalizePendingCaseOrWhitespaceVariants() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("sasms:device:" + QR_TOKEN));
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(" SASMS:DEVICE:" + QR_TOKEN));
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("SASMS:DEVICE:"
                        + QR_TOKEN.toString().toUpperCase()));
    }

    @Test
    void rejectsExternalUrlInsteadOfOpeningOrInterpretingIt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("https://example.test/device/" + QR_TOKEN));
    }

    @Test
    void parserHasNoDatabaseOrNetworkCollaborator() {
        assertEquals(0, QrPayloadParser.class.getConstructors()[0].getParameterCount());
        assertTrue(Arrays.stream(QrPayloadParser.class.getDeclaredFields())
                .allMatch(field -> Modifier.isStatic(field.getModifiers())));
    }

    @Test
    void returnsQrTokenWithoutSubstitutingAnUnrelatedDeviceId() {
        UUID deviceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        UUID result = parser.parse("SASMS:DEVICE:" + QR_TOKEN);

        assertEquals(QR_TOKEN, result);
        assertNotEquals(deviceId, result);
    }
}
