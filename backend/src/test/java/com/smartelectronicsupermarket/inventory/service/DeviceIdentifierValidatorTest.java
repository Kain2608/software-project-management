package com.smartelectronicsupermarket.inventory.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceIdentifierValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "860123456789014",
            "490154203237518",
            "352099001761481",
            "867456030123455"
    })
    @DisplayName("Should accept valid 15-digit IMEIs with correct Luhn check digit")
    void shouldAcceptValidLuhnImeis(String validImei) {
        assertTrue(DeviceIdentifierValidator.isValidImeiLuhn(validImei));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123456789012345", // sum modulo 10 = 8 != 0
            "860123456789015", // wrong check digit (expected 4)
            "490154203237510", // wrong check digit
            "000000000000001"  // sum modulo 10 != 0
    })
    @DisplayName("Should reject 15-digit IMEIs with invalid Luhn checksum")
    void shouldRejectInvalidLuhnChecksum(String invalidChecksumImei) {
        assertFalse(DeviceIdentifierValidator.isValidImeiLuhn(invalidChecksumImei));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "12345678901234",   // 14 digits
            "1234567890123456", // 16 digits
            "12345678901234a", // non-numeric
            " 860123456789014", // leading whitespace
            "860123456789014 ", // trailing whitespace
            "860123-45678901"  // hyphenated
    })
    @DisplayName("Should reject malformed or invalid length IMEI strings")
    void shouldRejectMalformedImeis(String malformedImei) {
        assertFalse(DeviceIdentifierValidator.isValidImeiLuhn(malformedImei));
    }

    @Test
    @DisplayName("Should reject null IMEI safely")
    void shouldRejectNullImei() {
        assertFalse(DeviceIdentifierValidator.isValidImeiLuhn(null));
    }
}
