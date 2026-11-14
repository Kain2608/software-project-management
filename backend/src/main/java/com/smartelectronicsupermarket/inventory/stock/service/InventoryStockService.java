package com.smartelectronicsupermarket.inventory.stock.service;

import java.util.List;
import java.util.UUID;

import com.smartelectronicsupermarket.inventory.stock.data.InventoryStockDataAccess;
import com.smartelectronicsupermarket.inventory.stock.data.InventoryStockRecord;
import org.springframework.stereotype.Service;

@Service
public class InventoryStockService {

    private static final int MAX_LIMIT = 100;

    private final InventoryStockDataAccess dataAccess;

    public InventoryStockService(InventoryStockDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public List<InventoryStockRecord> listStock(
            UUID actorId,
            UUID warehouseId,
            String sku,
            int limit,
            int offset) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        String normalizedSku = normalizeOptionalSku(sku);
        return dataAccess.listStock(actorId, warehouseId, normalizedSku, limit, offset);
    }

    private static String normalizeOptionalSku(String sku) {
        if (sku == null) {
            return null;
        }
        String normalized = sku.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("sku filter is too long");
        }
        return normalized;
    }
}
