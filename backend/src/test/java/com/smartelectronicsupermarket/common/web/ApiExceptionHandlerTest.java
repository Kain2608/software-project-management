package com.smartelectronicsupermarket.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsMissingForeignKeyTargetToSafeNotFoundResponse() {
        ResponseEntity<ApiErrorResponse> response = handler.databaseError(
                databaseException("23503", "Receipt item not found: internal SQL detail"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().code());
        assertEquals("Resource was not found", response.getBody().message());
        assertSafe(response.getBody());
    }

    @Test
    void mapsConstraintViolationToUnprocessableContentWithoutLeakingSql() {
        ResponseEntity<ApiErrorResponse> response = handler.databaseError(
                databaseException("23514", "Serial secret and SQL text"));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
        assertSafe(response.getBody());
    }

    @Test
    void mapsUnknownDatabaseFailureToRetryableServiceUnavailable() {
        ResponseEntity<ApiErrorResponse> response = handler.databaseError(
                databaseException("08006", "database host and credentials"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DEPENDENCY_UNAVAILABLE", response.getBody().code());
        assertEquals(true, response.getBody().retryable());
        assertSafe(response.getBody());
    }

    @Test
    void mapsConcurrentDeviceLockToRetryableConflict() {
        ResponseEntity<ApiErrorResponse> response = handler.databaseError(
                databaseException("55P03", "internal serial and lock detail"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DEVICE_LOCKED", response.getBody().code());
        assertEquals(true, response.getBody().retryable());
        assertSafe(response.getBody());
    }

    private static DataIntegrityViolationException databaseException(String sqlState, String detail) {
        return new DataIntegrityViolationException(
                "database operation failed",
                new SQLException(detail, sqlState));
    }

    private static void assertSafe(ApiErrorResponse body) {
        assertNotNull(body);
        assertNotNull(body.correlationId());
        assertFalse(body.message().toLowerCase().contains("sql"));
        assertFalse(body.message().toLowerCase().contains("secret"));
        assertFalse(body.message().toLowerCase().contains("credential"));
    }
}
