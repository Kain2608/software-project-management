package com.smartelectronicsupermarket.catalog.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.smartelectronicsupermarket.catalog.data.ProductCatalogDataAccess;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProductCatalogService {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MIN_WARRANTY_MONTHS = 0;
    private static final int MAX_WARRANTY_MONTHS = 120;
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999999999.99");

    private final ProductCatalogDataAccess dataAccess;
    private final ObjectMapper objectMapper;

    public ProductCatalogService(
            ProductCatalogDataAccess dataAccess,
            ObjectMapper objectMapper) {
        this.dataAccess = dataAccess;
        this.objectMapper = objectMapper;
    }

    public UUID createProduct(
            UUID actorId,
            String sku,
            UUID brandId,
            UUID categoryId,
            String name,
            String specificationsJson,
            BigDecimal price,
            int warrantyMonths) {
        requireValue(actorId, "actorId");
        requireText(sku, "sku");
        requireValue(brandId, "brandId");
        requireValue(categoryId, "categoryId");
        validateName(name);
        validateSpecifications(specificationsJson, true);
        validatePrice(price);
        validateWarrantyMonths(warrantyMonths);

        return dataAccess.createProduct(
                actorId,
                sku,
                brandId,
                categoryId,
                name,
                specificationsJson,
                price,
                warrantyMonths);
    }

    public int updateProduct(
            UUID actorId,
            UUID productId,
            int expectedRevision,
            String name,
            String specificationsJson,
            BigDecimal price,
            int warrantyMonths,
            boolean active) {
        requireValue(actorId, "actorId");
        requireValue(productId, "productId");
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        validateName(name);
        validateSpecifications(specificationsJson, false);
        validatePrice(price);
        validateWarrantyMonths(warrantyMonths);

        return dataAccess.updateProduct(
                actorId,
                productId,
                expectedRevision,
                name,
                specificationsJson,
                price,
                warrantyMonths,
                active);
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static void validateName(String name) {
        requireText(name, "name");
        String databaseNormalizedName = trimSqlSpaces(name);
        if (databaseNormalizedName.codePointCount(0, databaseNormalizedName.length())
                > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must not exceed 200 characters");
        }
    }

    private static String trimSqlSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(start, end);
    }

    private void validateSpecifications(String specificationsJson, boolean nullable) {
        if (specificationsJson == null) {
            if (nullable) {
                return;
            }
            throw new IllegalArgumentException("specificationsJson is required for update");
        }

        try {
            JsonNode specifications = objectMapper.readTree(specificationsJson);
            if (specifications == null || !specifications.isObject()) {
                throw new IllegalArgumentException("specificationsJson must be a JSON object");
            }
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "specificationsJson must contain valid JSON", exception);
        }
    }

    private static void validatePrice(BigDecimal price) {
        requireValue(price, "price");
        if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException(
                    "price must be between 0 and 999999999999.99");
        }
    }

    private static void validateWarrantyMonths(int warrantyMonths) {
        if (warrantyMonths < MIN_WARRANTY_MONTHS
                || warrantyMonths > MAX_WARRANTY_MONTHS) {
            throw new IllegalArgumentException("warrantyMonths must be between 0 and 120");
        }
    }
}
