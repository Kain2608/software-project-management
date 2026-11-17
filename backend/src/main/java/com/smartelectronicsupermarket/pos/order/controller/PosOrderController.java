package com.smartelectronicsupermarket.pos.order.controller;

import java.util.UUID;

import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.pos.order.dto.CreatePosOrderRequest;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderResponse;
import com.smartelectronicsupermarket.pos.order.dto.ReservePosOrderResponse;
import com.smartelectronicsupermarket.pos.order.service.PosOrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/orders")
@ConditionalOnProperty(
        prefix = "sasms.features.pos-order-api",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PosOrderController {

    private final PosOrderService service;

    public PosOrderController(PosOrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PosOrderResponse createDraftOrder(
            @RequestBody CreatePosOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader,
            Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        // Precedence: Idempotency-Key (RFC standard) > X-Idempotency-Key (legacy) > body idempotency_key
        Object effectiveKey = request.rawIdempotencyKey();
        if (xIdempotencyKeyHeader != null) {
            effectiveKey = xIdempotencyKeyHeader;
        }
        if (idempotencyKeyHeader != null) {
            effectiveKey = idempotencyKeyHeader;
        }

        CreatePosOrderRequest effectiveRequest = new CreatePosOrderRequest(
                request.storeId(),
                request.warehouseId(),
                request.customerId(),
                effectiveKey,
                request.items());

        UUID actorId = TrustedActorAccess.actorId(authentication);
        return service.createDraftOrder(effectiveRequest, actorId);
    }

    @PostMapping("/{orderId}/reserve")
    public ReservePosOrderResponse reserveOrder(
            @PathVariable UUID orderId,
            Authentication authentication) {
        UUID actorId = TrustedActorAccess.actorId(authentication);
        return service.reserveOrder(orderId, actorId);
    }
}
