package com.smartelectronicsupermarket.ai.service;

/** Actor lacks ai.use for the store, or the warehouse is outside the store (HTTP 403 FORBIDDEN_SCOPE). */
public class AiScopeDeniedException extends RuntimeException {

    public AiScopeDeniedException(String message) {
        super(message);
    }
}
