package com.smartelectronicsupermarket.pos.payment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.pos.order.data.PosOrderRecord;
import com.smartelectronicsupermarket.pos.order.service.PosOrderValidationException;
import com.smartelectronicsupermarket.pos.payment.data.ExistingPaymentRecord;
import com.smartelectronicsupermarket.pos.payment.data.PosPaymentDataAccess;
import com.smartelectronicsupermarket.pos.payment.dto.CashPaymentResponse;
import com.smartelectronicsupermarket.pos.payment.dto.CompleteCashPaymentRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PosPaymentService {

    private final PosPaymentDataAccess dataAccess;

    public PosPaymentService(PosPaymentDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    @Transactional
    public CashPaymentResponse completeCashPayment(
            UUID orderId,
            CompleteCashPaymentRequest request,
            UUID actorId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (actorId == null) {
            throw new AccessDeniedException("Authenticated cashier identity is required");
        }

        PosOrderRecord order = dataAccess.findOrderById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " was not found"));

        if (!dataAccess.hasCheckoutPermission(actorId, order.storeId())) {
            throw new AccessDeniedException("User " + actorId + " lacks sales.checkout permission at store " + order.storeId());
        }

        // Idempotent replay: if already PAID, return the existing payment/invoice result
        if ("PAID".equalsIgnoreCase(order.status())) {
            ExistingPaymentRecord existingPayment = dataAccess.findExistingCapturedPayment(orderId)
                    .orElse(null);
            String invoiceNumber = dataAccess.findInvoiceNumber(orderId)
                    .orElse("RECEIPT-" + order.code());
            return new CashPaymentResponse(
                    order.id(),
                    existingPayment != null ? existingPayment.id() : order.id(),
                    "PAID",
                    invoiceNumber,
                    order.totalAmount(),
                    existingPayment != null ? existingPayment.capturedAt() : OffsetDateTime.now(ZoneOffset.UTC),
                    "CASH");
        }

        // Order must be RESERVED before initiating payment
        if (!"RESERVED".equalsIgnoreCase(order.status())) {
            throw new ResourceConflictException("Order must be in RESERVED status before payment (current status: " + order.status() + ")");
        }

        BigDecimal requestedAmount = request != null && request.amount() != null
                ? request.amount()
                : order.totalAmount();

        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PosOrderValidationException("Payment amount must be positive", "amount");
        }

        if (order.totalAmount() == null || requestedAmount.compareTo(order.totalAmount()) != 0) {
            throw new PosOrderValidationException(
                    "Payment amount (" + requestedAmount + ") must match order total amount (" + order.totalAmount() + ") exactly",
                    "amount");
        }

        UUID paymentId = UUID.randomUUID();
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String providerReference = "CASH-" + orderId;

        // 1. Record CAPTURED cash payment in DB
        dataAccess.insertCapturedCashPayment(
                paymentId,
                orderId,
                order.totalAmount(),
                providerReference,
                actorId,
                capturedAt);

        // 2. Call complete_sale function (marks order PAID, devices SOLD, inventory movement, invoice, audit, outbox)
        dataAccess.completeSale(orderId, actorId);

        String invoiceNumber = dataAccess.findInvoiceNumber(orderId)
                .orElse("RECEIPT-" + order.code());

        return new CashPaymentResponse(
                order.id(),
                paymentId,
                "PAID",
                invoiceNumber,
                order.totalAmount(),
                capturedAt,
                "CASH");
    }
}
