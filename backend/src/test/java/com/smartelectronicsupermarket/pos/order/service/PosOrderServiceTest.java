package com.smartelectronicsupermarket.pos.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.pos.order.data.CatalogProductRecord;
import com.smartelectronicsupermarket.pos.order.data.PosOrderDataAccess;
import com.smartelectronicsupermarket.pos.order.data.PosOrderItemRecord;
import com.smartelectronicsupermarket.pos.order.data.PosOrderRecord;
import com.smartelectronicsupermarket.pos.order.dto.CreatePosOrderRequest;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderItemRequest;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderResponse;
import com.smartelectronicsupermarket.pos.order.dto.ReservePosOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PosOrderServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID PRODUCT_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PRODUCT_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Mock
    private PosOrderDataAccess dataAccess;

    private PosOrderService service;

    @BeforeEach
    void setUp() {
        service = new PosOrderService(dataAccess);
    }

    @Test
    void createDraftOrder_successWithAuthoritativeCatalogPrices() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.findOrderByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        when(dataAccess.findActiveProduct(PRODUCT_ID_1)).thenReturn(
                new CatalogProductRecord(PRODUCT_ID_1, "SKU-001", "Product 1", new BigDecimal("500000"), true));
        when(dataAccess.findActiveProduct(PRODUCT_ID_2)).thenReturn(
                new CatalogProductRecord(PRODUCT_ID_2, "SKU-002", "Product 2", new BigDecimal("1200000"), true));

        PosOrderRecord savedRecord = new PosOrderRecord(
                ORDER_ID, "SO-2026-TEST", IDEMPOTENCY_KEY, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null);
        when(dataAccess.createOrder(any(), any(), eq(IDEMPOTENCY_KEY), eq(STORE_ID), eq(WAREHOUSE_ID), any(), eq(ACTOR_ID), any()))
                .thenReturn(savedRecord);

        List<PosOrderItemRecord> savedItems = List.of(
                new PosOrderItemRecord(UUID.randomUUID(), ORDER_ID, PRODUCT_ID_1, "SKU-001", "Product 1", 2, new BigDecimal("500000"), new BigDecimal("1000000")),
                new PosOrderItemRecord(UUID.randomUUID(), ORDER_ID, PRODUCT_ID_2, "SKU-002", "Product 2", 1, new BigDecimal("1200000"), new BigDecimal("1200000")));
        when(dataAccess.findOrderItems(ORDER_ID)).thenReturn(savedItems);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID,
                WAREHOUSE_ID,
                null,
                IDEMPOTENCY_KEY,
                List.of(
                        new PosOrderItemRequest(PRODUCT_ID_1, 2),
                        new PosOrderItemRequest(PRODUCT_ID_2, 1)));

        PosOrderResponse response = service.createDraftOrder(request, ACTOR_ID);

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("500000");
        assertThat(response.items().get(0).lineTotal()).isEqualByComparingTo("1000000");
        assertThat(response.items().get(1).unitPrice()).isEqualByComparingTo("1200000");
    }

    @Test
    void createDraftOrder_deniesActorWithoutCheckoutPermission() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(false);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, null,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 1)));

        assertThatThrownBy(() -> service.createDraftOrder(request, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("sales.checkout");

        verify(dataAccess, never()).createOrder(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createDraftOrder_rejectsWarehouseNotBelongingToStore() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(false);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, null,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 1)));

        assertThatThrownBy(() -> service.createDraftOrder(request, ACTOR_ID))
                .isInstanceOf(PosOrderValidationException.class)
                .hasMessageContaining("does not belong to store");
    }

    @Test
    void createDraftOrder_rejectsEmptyItems() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, null, List.of());

        assertThatThrownBy(() -> service.createDraftOrder(request, ACTOR_ID))
                .isInstanceOf(PosOrderValidationException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void createDraftOrder_rejectsNonPositiveQuantity() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, null,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 0)));

        assertThatThrownBy(() -> service.createDraftOrder(request, ACTOR_ID))
                .isInstanceOf(PosOrderValidationException.class)
                .hasMessageContaining("positive integer");
    }

    @Test
    void createDraftOrder_rejectsDuplicateProductInItems() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, null,
                List.of(
                        new PosOrderItemRequest(PRODUCT_ID_1, 1),
                        new PosOrderItemRequest(PRODUCT_ID_1, 2)));

        assertThatThrownBy(() -> service.createDraftOrder(request, ACTOR_ID))
                .isInstanceOf(PosOrderValidationException.class)
                .hasMessageContaining("Duplicate product_id");
    }

    @Test
    void createDraftOrder_rejectsInactiveProduct() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.findActiveProduct(PRODUCT_ID_1)).thenReturn(
                new CatalogProductRecord(PRODUCT_ID_1, "SKU-001", "Inactive Product", new BigDecimal("100000"), false));

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, null,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 1)));

        assertThatThrownBy(() -> service.createDraftOrder(request, ACTOR_ID))
                .isInstanceOf(PosOrderValidationException.class)
                .hasMessageContaining("Product not found or inactive");
    }

    @Test
    void createDraftOrder_idempotentReplayWhenPayloadMatches() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);

        PosOrderRecord existing = new PosOrderRecord(
                ORDER_ID, "SO-EXISTING", IDEMPOTENCY_KEY, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null);
        when(dataAccess.findOrderByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        List<PosOrderItemRecord> existingItems = List.of(
                new PosOrderItemRecord(UUID.randomUUID(), ORDER_ID, PRODUCT_ID_1, "SKU-001", "Product 1", 2, new BigDecimal("500000"), new BigDecimal("1000000")));
        when(dataAccess.findOrderItems(ORDER_ID)).thenReturn(existingItems);

        CreatePosOrderRequest matchingRequest = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, IDEMPOTENCY_KEY,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 2)));

        PosOrderResponse response = service.createDraftOrder(matchingRequest, ACTOR_ID);

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.code()).isEqualTo("SO-EXISTING");
        verify(dataAccess, never()).createOrder(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createDraftOrder_rejectsReusedIdempotencyKeyWithDifferentPayload() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);

        PosOrderRecord existing = new PosOrderRecord(
                ORDER_ID, "SO-EXISTING", IDEMPOTENCY_KEY, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null);
        when(dataAccess.findOrderByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        List<PosOrderItemRecord> existingItems = List.of(
                new PosOrderItemRecord(UUID.randomUUID(), ORDER_ID, PRODUCT_ID_1, "SKU-001", "Product 1", 2, new BigDecimal("500000"), new BigDecimal("1000000")));
        when(dataAccess.findOrderItems(ORDER_ID)).thenReturn(existingItems);

        // Different quantity: 5 instead of 2
        CreatePosOrderRequest differingRequest = new CreatePosOrderRequest(
                STORE_ID, WAREHOUSE_ID, null, IDEMPOTENCY_KEY,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 5)));

        assertThatThrownBy(() -> service.createDraftOrder(differingRequest, ACTOR_ID))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Idempotency key reused with a different payload");
    }

    @Test
    void reserveOrder_success() {
        PosOrderRecord existing = new PosOrderRecord(
                ORDER_ID, "SO-001", IDEMPOTENCY_KEY, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null);
        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(existing));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.reserveOrder(ORDER_ID, ACTOR_ID)).thenReturn(new BigDecimal("1500000"));

        ReservePosOrderResponse response = service.reserveOrder(ORDER_ID, ACTOR_ID);

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(response.totalAmount()).isEqualByComparingTo("1500000");
    }

    @Test
    void reserveOrder_throwsNotFoundWhenOrderMissing() {
        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveOrder(ORDER_ID, ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void reserveOrder_deniesActorWithoutPermission() {
        PosOrderRecord existing = new PosOrderRecord(
                ORDER_ID, "SO-001", IDEMPOTENCY_KEY, STORE_ID, WAREHOUSE_ID, null,
                "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null);
        when(dataAccess.findOrderById(ORDER_ID)).thenReturn(Optional.of(existing));
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.reserveOrder(ORDER_ID, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("sales.checkout");
    }

    @Test
    void createDraftOrder_concurrentRace_returnsOrderWhenPayloadMatches() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.findOrderByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty()) // Initial check before insert
                .thenReturn(Optional.of(new PosOrderRecord(
                        ORDER_ID, "SO-CONCURRENT", IDEMPOTENCY_KEY, STORE_ID, WAREHOUSE_ID, null,
                        "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null))); // Check after race

        when(dataAccess.findActiveProduct(PRODUCT_ID_1)).thenReturn(
                new CatalogProductRecord(PRODUCT_ID_1, "SKU-001", "Product 1", new BigDecimal("500000"), true));

        when(dataAccess.createOrder(any(), any(), eq(IDEMPOTENCY_KEY), eq(STORE_ID), eq(WAREHOUSE_ID), any(), eq(ACTOR_ID), any()))
                .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint"));

        List<PosOrderItemRecord> existingItems = List.of(
                new PosOrderItemRecord(UUID.randomUUID(), ORDER_ID, PRODUCT_ID_1, "SKU-001", "Product 1", 2, new BigDecimal("500000"), new BigDecimal("1000000")));
        when(dataAccess.findOrderItems(ORDER_ID)).thenReturn(existingItems);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID,
                WAREHOUSE_ID,
                null,
                IDEMPOTENCY_KEY,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 2)));

        PosOrderResponse response = service.createDraftOrder(request, ACTOR_ID);

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.code()).isEqualTo("SO-CONCURRENT");
    }

    @Test
    void createDraftOrder_concurrentRace_throwsConflictWhenPayloadDiffers() {
        when(dataAccess.hasCheckoutPermission(ACTOR_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.isWarehouseInStore(WAREHOUSE_ID, STORE_ID)).thenReturn(true);
        when(dataAccess.findOrderByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty()) // Initial check before insert
                .thenReturn(Optional.of(new PosOrderRecord(
                        ORDER_ID, "SO-CONCURRENT", IDEMPOTENCY_KEY, STORE_ID, WAREHOUSE_ID, null,
                        "POS", "DRAFT", BigDecimal.ZERO, ACTOR_ID, Instant.now(), null))); // Check after race

        when(dataAccess.findActiveProduct(PRODUCT_ID_1)).thenReturn(
                new CatalogProductRecord(PRODUCT_ID_1, "SKU-001", "Product 1", new BigDecimal("500000"), true));

        when(dataAccess.createOrder(any(), any(), eq(IDEMPOTENCY_KEY), eq(STORE_ID), eq(WAREHOUSE_ID), any(), eq(ACTOR_ID), any()))
                .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint"));

        // Saved items differ (quantity 99 instead of 2)
        List<PosOrderItemRecord> existingItems = List.of(
                new PosOrderItemRecord(UUID.randomUUID(), ORDER_ID, PRODUCT_ID_1, "SKU-001", "Product 1", 99, new BigDecimal("500000"), new BigDecimal("49500000")));
        when(dataAccess.findOrderItems(ORDER_ID)).thenReturn(existingItems);

        CreatePosOrderRequest request = new CreatePosOrderRequest(
                STORE_ID,
                WAREHOUSE_ID,
                null,
                IDEMPOTENCY_KEY,
                List.of(new PosOrderItemRequest(PRODUCT_ID_1, 2)));

        assertThatThrownBy(() -> service.createDraftOrder(request, ACTOR_ID))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Idempotency key reused with a different payload");
    }
}
