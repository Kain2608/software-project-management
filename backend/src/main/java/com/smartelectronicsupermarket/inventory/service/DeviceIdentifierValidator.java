package com.smartelectronicsupermarket.inventory.service;

/**
 * Validates device identifiers per D34 BR-01 and D35 SER-01..09 specifications.
 * Implements authoritative Luhn (MOD-10) checksum verification for 15-digit IMEIs,
 * directly matching PostgreSQL function sasms.valid_imei(text).
 */
public final class DeviceIdentifierValidator {

    private DeviceIdentifierValidator() {
    }

    /**
     * Verifies that the given IMEI string is exactly 15 numeric digits
     * and satisfies the standard Luhn MOD-10 checksum algorithm.
     *
     * @param imei the candidate IMEI string
     * @return true if valid 15-digit numeric Luhn checksum passes, false otherwise
     */
    public static boolean isValidImeiLuhn(String imei) {
        if (imei == null || imei.length() != 15) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            char c = imei.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int digit = c - '0';
            if ((i + 1) % 2 == 0) {
                int doubled = digit * 2;
                sum += (doubled > 9) ? (doubled - 9) : doubled;
            } else {
                sum += digit;
            }
        }
        return sum % 10 == 0;
    }
}
