package com.smartelectronicsupermarket.ai.controller;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.smartelectronicsupermarket.ai.service.AiScopeDeniedException;
import com.smartelectronicsupermarket.ai.service.AiSkuNotFoundException;
import com.smartelectronicsupermarket.common.web.ApiErrorResponse;

/** D52 v2 error codes for the AI endpoints; messages are generic to avoid leaking scope details. */
@RestControllerAdvice(assignableTypes = AiCrossSellController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiErrorResponse> invalidArgument(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Request is invalid", false);
    }

    @ExceptionHandler(AiScopeDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbiddenScope(AiScopeDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN_SCOPE", "Access to the requested store is denied", false);
    }

    @ExceptionHandler(AiSkuNotFoundException.class)
    ResponseEntity<ApiErrorResponse> skuNotFound(AiSkuNotFoundException exception) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, "SKU_NOT_FOUND",
                "A cart SKU does not exist in the catalog", false);
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String code, String message, boolean retryable) {
        return ResponseEntity.status(status).body(
                new ApiErrorResponse(code, message, null, UUID.randomUUID().toString(), retryable));
    }
}
