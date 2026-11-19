package com.smartelectronicsupermarket.pos.payment.controller;

import java.util.UUID;

import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.pos.payment.dto.CashPaymentResponse;
import com.smartelectronicsupermarket.pos.payment.dto.CompleteCashPaymentRequest;
import com.smartelectronicsupermarket.pos.payment.service.PosPaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/orders")
@ConditionalOnProperty(
        prefix = "sasms.features.pos-cash-payment-api",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PosCashPaymentController {

    private final PosPaymentService service;

    public PosCashPaymentController(PosPaymentService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/payments/cash")
    public ResponseEntity<CashPaymentResponse> completeCashPayment(
            @PathVariable UUID orderId,
            @RequestBody(required = false) CompleteCashPaymentRequest request,
            Authentication authentication) {
        UUID actorId = TrustedActorAccess.actorId(authentication);
        CashPaymentResponse response = service.completeCashPayment(orderId, request, actorId);
        return ResponseEntity.ok(response);
    }
}
