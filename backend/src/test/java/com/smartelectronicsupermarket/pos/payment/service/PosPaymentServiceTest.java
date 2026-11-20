package com.smartelectronicsupermarket.pos.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.pos.order.data.PosOrderRecord;
import com.smartelectronicsupermarket.pos.order.service.PosOrderValidationException;
import com.smartelectronicsupermarket.pos.payment.data.ExistingPaymentRecord;
import com.smartelectronicsupermarket.pos.payment.data.PosPaymentDataAccess;
import com.smartelectronicsupermarket.pos.payment.dto.CashPaymentResponse;
import com.smartelectronicsupermarket.pos.payment.dto.CompleteCashPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class PosPaymentServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final BigDecimal TOTAL_AMOUNT = new BigDecimal("10000000.00");

    private PosPaymentDataAccess dataAccess;
    private PosPaymentService service;

    @BeforeEach
    void setUp() {
        dataAccess = Mockito.mock(PosPaymentDataAccess.class);
        service = new PosPaymentService(dataAccess);
    }

    @Test
    void completeCashPaymentSuccess() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "RESERVED", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), Instant.now().plusSeconds(600));

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.completeSale(ORDER_ID, ACTOR_ID)).thenReturn(ORDER_ID);
        when(dataAccess.findInvoiceNumber(ORDER_ID)).thenReturn(Optional.of("RECEIPT-ORD-001"));

        CashPaymentResponse response = service.completeCashPayment(
                ORDER_ID, new CompleteCashPaymentRequest(TOTAL_AMOUNT), ACTOR_ID);

        assertNotNull(response);
        assertEquals(ORDER_ID, response.orderId());
        assertEquals("PAID", response.status());
        assertEquals("RECEIPT-ORD-001", response.invoiceNumber());
        assertEquals(TOTAL_AMOUNT, response.amount());
        assertEquals("CASH", response.paymentMethod());

        verify(dataAccess).insertCapturedCashPayment(any(), eq(ORDER_ID), eq(TOTAL_AMOUNT), eq("CASH-" + ORDER_ID), eq(ACTOR_ID), any());
        verify(dataAccess).completeSale(ORDER_ID, ACTOR_ID);
    }

    @Test
    void completeCashPaymentThrowsValidationWhenRequestOrAmountMissing() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "RESERVED", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), Instant.now().plusSeconds(600));

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);

        PosOrderValidationException ex1 = assertThrows(PosOrderValidationException.class, () ->
                service.completeCashPayment(ORDER_ID, null, ACTOR_ID));
        assertEquals("amount", ex1.getField());

        PosOrderValidationException ex2 = assertThrows(PosOrderValidationException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(null), ACTOR_ID));
        assertEquals("amount", ex2.getField());
    }

    @Test
    void completeCashPaymentThrowsConflictWhenPaidOrderHasNoCashPayment() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "PAID", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), null);

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.findExistingCapturedPayment(ORDER_ID)).thenReturn(Optional.empty());

        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(TOTAL_AMOUNT), ACTOR_ID));
        assertEquals(true, ex.getMessage().contains("no recorded CASH payment"));
    }

    @Test
    void completeCashPaymentThrowsValidationWhenPaidOrderReplayedWithDifferentAmount() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "PAID", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), null);
        UUID paymentId = UUID.randomUUID();
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.findExistingCapturedPayment(ORDER_ID)).thenReturn(Optional.of(
                new ExistingPaymentRecord(paymentId, ORDER_ID, TOTAL_AMOUNT, capturedAt, "CASH", "CAPTURED")));

        PosOrderValidationException ex = assertThrows(PosOrderValidationException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(new BigDecimal("999999.00")), ACTOR_ID));
        assertEquals("amount", ex.getField());
    }

    @Test
    void completeCashPaymentIdempotentWhenAlreadyPaid() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "PAID", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), null);
        UUID paymentId = UUID.randomUUID();
        OffsetDateTime capturedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.findExistingCapturedPayment(ORDER_ID)).thenReturn(Optional.of(
                new ExistingPaymentRecord(paymentId, ORDER_ID, TOTAL_AMOUNT, capturedAt, "CASH", "CAPTURED")));
        when(dataAccess.findInvoiceNumber(ORDER_ID)).thenReturn(Optional.of("RECEIPT-ORD-001"));

        CashPaymentResponse response = service.completeCashPayment(
                ORDER_ID, new CompleteCashPaymentRequest(TOTAL_AMOUNT), ACTOR_ID);

        assertEquals(ORDER_ID, response.orderId());
        assertEquals(paymentId, response.paymentId());
        assertEquals("PAID", response.status());
        assertEquals("RECEIPT-ORD-001", response.invoiceNumber());
        assertEquals(TOTAL_AMOUNT, response.amount());
        assertEquals(capturedAt, response.paidAt());
        assertEquals("CASH", response.paymentMethod());

        verify(dataAccess, never()).insertCapturedCashPayment(any(), any(), any(), any(), any(), any());
        verify(dataAccess, never()).completeSale(any(), any());
    }

    @Test
    void completeCashPaymentThrowsNotFoundWhenOrderMissing() {
        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(TOTAL_AMOUNT), ACTOR_ID));
    }

    @Test
    void completeCashPaymentThrowsForbiddenWhenLacksPermission() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "RESERVED", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), null);

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(TOTAL_AMOUNT), ACTOR_ID));
    }

    @Test
    void completeCashPaymentThrowsConflictWhenOrderDraft() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), null);

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(TOTAL_AMOUNT), ACTOR_ID));
    }

    @Test
    void completeCashPaymentThrowsValidationWhenAmountMismatches() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "RESERVED", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), null);

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);

        PosOrderValidationException ex = assertThrows(PosOrderValidationException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(new BigDecimal("5000000.00")), ACTOR_ID));
        assertEquals("amount", ex.getField());
    }

    @Test
    void completeCashPaymentThrowsValidationWhenAmountNegativeOrZero() {
        PosOrderRecord order = new PosOrderRecord(
                ORDER_ID, "ORD-001", UUID.randomUUID(), STORE_ID, WAREHOUSE_ID, null,
                "POS", "RESERVED", TOTAL_AMOUNT, ACTOR_ID, Instant.now(), null);

        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);

        assertThrows(PosOrderValidationException.class, () ->
                service.completeCashPayment(ORDER_ID, new CompleteCashPaymentRequest(BigDecimal.ZERO), ACTOR_ID));
    }
}
