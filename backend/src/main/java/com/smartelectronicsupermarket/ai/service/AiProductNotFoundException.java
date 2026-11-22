package com.smartelectronicsupermarket.ai.service;

public class AiProductNotFoundException extends RuntimeException {

    public AiProductNotFoundException(String message) {
        super(message);
    }
}
