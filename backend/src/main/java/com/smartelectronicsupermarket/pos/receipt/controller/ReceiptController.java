package com.smartelectronicsupermarket.pos.receipt.controller;

import java.math.BigDecimal;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.pos.receipt.data.ReceiptRecord;
import com.smartelectronicsupermarket.pos.receipt.dto.ReceiptItemResponse;
import com.smartelectronicsupermarket.pos.receipt.dto.ReceiptResponse;
import com.smartelectronicsupermarket.pos.receipt.service.ReceiptService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/orders")
public class ReceiptController {

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}/receipt")
    public ReceiptResponse getReceipt(
            @PathVariable UUID orderId,
            Authentication authentication) {
        ReceiptRecord receipt = service
                .findPaidReceipt(TrustedActorAccess.actorId(authentication), orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt was not found"));
        return new ReceiptResponse(
                receipt.orderId(),
                receipt.items().stream()
                        .map(item -> new ReceiptItemResponse(
                                item.productId(),
                                item.deviceId(),
                                maskSerial(item.serial()),
                                toMinor(item.unitPrice())))
                        .toList(),
                "VND",
                toMinor(receipt.totalAmount()),
                receipt.paidAt(),
                null);
    }

    private static long toMinor(BigDecimal amount) {
        return amount.longValueExact();
    }

    private static String maskSerial(String serial) {
        if (serial == null || serial.isEmpty()) {
            return "****";
        }
        int visibleCharacters = Math.min(4, serial.length());
        return "****" + serial.substring(serial.length() - visibleCharacters);
    }
}
