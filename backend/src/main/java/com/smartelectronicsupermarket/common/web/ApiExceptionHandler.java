package com.smartelectronicsupermarket.common.web;

import java.sql.SQLException;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid", null, false);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", null, false);
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(ResourceConflictException exception) {
        return response(HttpStatus.CONFLICT, "CONFLICT", "Resource state does not allow this operation", null, false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied", null, false);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrorResponse> databaseError(DataAccessException exception) {
        return switch (findSqlState(exception)) {
            case "23505" -> response(
                    HttpStatus.CONFLICT, "DUPLICATE", "A unique value already exists", null, false);
            case "40001" -> response(
                    HttpStatus.CONFLICT, "REVISION_CONFLICT", "The resource changed; reload before retrying", null, false);
            case "55P03" -> response(
                    HttpStatus.CONFLICT, "DEVICE_LOCKED", "The device is held by another active order", null, true);
            case "42501" -> response(
                    HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied", null, false);
            case "23503", "P0002" -> response(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", null, false);
            case "23502", "23514" -> response(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "VALIDATION_FAILED",
                    "Data violates a business constraint",
                    null,
                    false);
            default -> response(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE",
                    "A required dependency is unavailable",
                    null,
                    true);
        };
    }

    private static String findSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return "";
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            String field,
            boolean retryable) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                field,
                UUID.randomUUID().toString(),
                retryable));
    }
}
