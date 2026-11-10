package com.smartelectronicsupermarket.inventory.controller;

import java.util.Arrays;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.inventory.dto.ReceiveDeviceBatchRequest;
import com.smartelectronicsupermarket.inventory.dto.ReceiveDeviceBatchResponse;
import com.smartelectronicsupermarket.inventory.service.InventoryBatchService;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/inventory/receipt-items")
public class InventoryBatchController {

    private final InventoryBatchService inventoryBatchService;
    private final ObjectMapper objectMapper;

    public InventoryBatchController(
            InventoryBatchService inventoryBatchService,
            ObjectMapper objectMapper) {
        this.inventoryBatchService = inventoryBatchService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{receiptItemId}/device-batches")
    public ReceiveDeviceBatchResponse receiveBatch(
            @PathVariable String receiptItemId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReceiveDeviceBatchRequest request,
            Authentication authentication) {
        UUID parsedReceiptItemId = parseCanonicalUuid(receiptItemId, "receiptItemId");
        UUID requestKey = parseCanonicalUuid(idempotencyKey, "Idempotency-Key");
        if (request == null || request.devices() == null) {
            throw new IllegalArgumentException("devices is required");
        }

        UUID[] deviceIds = inventoryBatchService.receiveDeviceBatch(
                parsedReceiptItemId,
                requestKey,
                serializeDevices(request),
                TrustedActorAccess.actorId(authentication));
        if (deviceIds == null || deviceIds.length == 0) {
            throw new DataRetrievalFailureException(
                    "Database returned no device identifiers for a successful batch");
        }

        return new ReceiveDeviceBatchResponse(
                requestKey,
                parsedReceiptItemId,
                Arrays.asList(deviceIds),
                deviceIds.length,
                "CONFIRMED");
    }

    private String serializeDevices(ReceiveDeviceBatchRequest request) {
        try {
            return objectMapper.writeValueAsString(request.devices());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("devices could not be serialized", exception);
        }
    }

    private static UUID parseCanonicalUuid(String rawValue, String fieldName) {
        if (rawValue == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            UUID parsed = UUID.fromString(rawValue);
            if (!parsed.toString().equalsIgnoreCase(rawValue)) {
                throw new IllegalArgumentException(fieldName + " must be a canonical UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " must be a canonical UUID", exception);
        }
    }
}
