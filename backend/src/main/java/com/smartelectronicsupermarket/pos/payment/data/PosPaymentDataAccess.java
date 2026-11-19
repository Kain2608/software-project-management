package com.smartelectronicsupermarket.pos.payment.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.pos.order.data.PosOrderRecord;

public interface PosPaymentDataAccess {

    void lockOrderPayment(UUID orderId);

    Optional<PosOrderRecord> findOrderById(UUID orderId);

    boolean hasCheckoutPermission(UUID actorId, UUID storeId);

    Optional<ExistingPaymentRecord> findExistingCapturedPayment(UUID orderId);

    void insertCapturedCashPayment(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String providerReference,
            UUID actorId,
            OffsetDateTime capturedAt);

    UUID completeSale(UUID orderId, UUID actorId);

    Optional<String> findInvoiceNumber(UUID orderId);
}
