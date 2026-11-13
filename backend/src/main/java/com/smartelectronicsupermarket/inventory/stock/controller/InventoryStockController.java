package com.smartelectronicsupermarket.inventory.stock.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.inventory.stock.dto.InventoryStockItemResponse;
import com.smartelectronicsupermarket.inventory.stock.dto.InventoryStockListResponse;
import com.smartelectronicsupermarket.inventory.stock.service.InventoryStockService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/stock")
public class InventoryStockController {

    private final InventoryStockService service;

    public InventoryStockController(InventoryStockService service) {
        this.service = service;
    }

    @GetMapping
    public InventoryStockListResponse list(
            @RequestParam(name = "warehouse_id", required = false) UUID warehouseId,
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            Authentication authentication) {
        List<InventoryStockItemResponse> items = service.listStock(
                        TrustedActorAccess.actorId(authentication),
                        warehouseId,
                        sku,
                        limit,
                        offset)
                .stream()
                .map(row -> new InventoryStockItemResponse(
                        row.productId(),
                        row.warehouseId(),
                        row.accountingStock(),
                        row.sellableCount(),
                        row.reservedCount()))
                .toList();
        return new InventoryStockListResponse(items, OffsetDateTime.now(), null);
    }
}
