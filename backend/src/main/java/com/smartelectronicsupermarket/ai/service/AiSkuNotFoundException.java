package com.smartelectronicsupermarket.ai.service;

/** A cart SKU is not in the catalog (HTTP 422 SKU_NOT_FOUND). */
public class AiSkuNotFoundException extends RuntimeException {

    public AiSkuNotFoundException(String message) {
        super(message);
    }
}
