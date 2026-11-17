package com.smartelectronicsupermarket.pos.order.service;

public class PosOrderValidationException extends RuntimeException {

    private final String field;

    public PosOrderValidationException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
