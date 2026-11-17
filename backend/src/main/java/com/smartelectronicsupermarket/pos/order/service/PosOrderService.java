package com.smartelectronicsupermarket.pos.order.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.pos.order.data.CatalogProductRecord;
import com.smartelectronicsupermarket.pos.order.data.PosOrderDataAccess;
import com.smartelectronicsupermarket.pos.order.data.PosOrderItemRecord;
import com.smartelectronicsupermarket.pos.order.data.PosOrderRecord;
import com.smartelectronicsupermarket.pos.order.dto.CreatePosOrderRequest;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderItemRequest;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderItemResponse;
import com.smartelectronicsupermarket.pos.order.dto.PosOrderResponse;
import com.smartelectronicsupermarket.pos.order.dto.ReservePosOrderResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PosOrderService {

    private final PosOrderDataAccess dataAccess;

    public PosOrderService(PosOrderDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    @Transactional
    public PosOrderResponse createDraftOrder(CreatePosOrderRequest request, UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.storeId() == null) {
            throw new PosOrderValidationException("store_id is required", "store_id");
        }
        if (request.warehouseId() == null) {
            throw new PosOrderValidationException("warehouse_id is required", "warehouse_id");
        }

        // Verify actor has sales.checkout permission at this store
        if (!dataAccess.hasCheckoutPermission(actorId, request.storeId())) {
            throw new AccessDeniedException("Actor does not have sales.checkout permission at store: " + request.storeId());
        }

        // Verify warehouse belongs to the store
        if (!dataAccess.isWarehouseInStore(request.warehouseId(), request.storeId())) {
            throw new PosOrderValidationException(
                    "Warehouse " + request.warehouseId() + " does not belong to store " + request.storeId(),
                    "warehouse_id");
        }

        // Validate items presence and check structure & duplicates upfront
        if (request.items() == null || request.items().isEmpty()) {
            throw new PosOrderValidationException("Order must contain at least one item", "items");
        }

        Set<UUID> seenProducts = new HashSet<>();
        for (PosOrderItemRequest itemReq : request.items()) {
            if (itemReq.productId() == null) {
                throw new PosOrderValidationException("product_id is required for all items", "product_id");
            }
            if (itemReq.quantity() == null || itemReq.quantity() <= 0) {
                throw new PosOrderValidationException("quantity must be a positive integer", "quantity");
            }
            if (!seenProducts.add(itemReq.productId())) {
                throw new PosOrderValidationException(
                        "Duplicate product_id in order items: " + itemReq.productId(), "items");
            }
        }

        // Validate and resolve idempotency key scoped by store and actor
        UUID idempotencyKey = resolveIdempotencyKey(request.rawIdempotencyKey(), request.storeId(), actorId);

        Optional<PosOrderRecord> existing = dataAccess.findOrderByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            PosOrderRecord existingOrder = existing.get();
            List<PosOrderItemRecord> existingItems = dataAccess.findOrderItems(existingOrder.id());
            if (matchesPayload(existingOrder, existingItems, request)) {
                return toOrderResponse(existingOrder, existingItems);
            } else {
                throw new ResourceConflictException(
                        "Idempotency key reused with a different payload: " + idempotencyKey);
            }
        }

        // Lookup price from Catalog DB and build order lines
        List<PosOrderItemRecord> orderItemsToCreate = new ArrayList<>();
        UUID orderId = UUID.randomUUID();

        for (PosOrderItemRequest itemReq : request.items()) {
            CatalogProductRecord product = dataAccess.findActiveProduct(itemReq.productId());
            if (product == null || !product.isActive()) {
                throw new PosOrderValidationException(
                        "Product not found or inactive in catalog: " + itemReq.productId(), "product_id");
            }

            BigDecimal unitPrice = product.listPrice();
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new PosOrderValidationException(
                        "Product has invalid catalog price: " + itemReq.productId(), "unit_price");
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            orderItemsToCreate.add(new PosOrderItemRecord(
                    UUID.randomUUID(),
                    orderId,
                    product.id(),
                    product.sku(),
                    product.name(),
                    itemReq.quantity(),
                    unitPrice,
                    lineTotal));
        }

        String orderCode = "SO-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Optional<PosOrderRecord> createdOrderOpt = dataAccess.createOrderIfAbsent(
                orderId,
                orderCode,
                idempotencyKey,
                request.storeId(),
                request.warehouseId(),
                request.customerId(),
                actorId,
                orderItemsToCreate);

        PosOrderRecord targetOrder;
        if (createdOrderOpt.isPresent()) {
            targetOrder = createdOrderOpt.get();
        } else {
            // Concurrent race: INSERT ON CONFLICT DO NOTHING returned 0.
            // Transaction is healthy and not aborted; fetch and verify existing order.
            PosOrderRecord concurrentOrder = dataAccess.findOrderByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Order disappeared after idempotency conflict: " + idempotencyKey));
            List<PosOrderItemRecord> existingItems = dataAccess.findOrderItems(concurrentOrder.id());
            if (matchesPayload(concurrentOrder, existingItems, request)) {
                return toOrderResponse(concurrentOrder, existingItems);
            } else {
                throw new ResourceConflictException(
                        "Idempotency key reused with a different payload: " + idempotencyKey);
            }
        }

        List<PosOrderItemRecord> savedItems = dataAccess.findOrderItems(targetOrder.id());
        return toOrderResponse(targetOrder, savedItems);
    }

    public static UUID resolveIdempotencyKey(Object rawKey, UUID storeId, UUID actorId) {
        if (rawKey == null) {
            return UUID.randomUUID();
        }
        if (rawKey instanceof UUID u) {
            return u;
        }
        String str = rawKey.toString().trim();
        if (str.isEmpty()) {
            throw new PosOrderValidationException("Idempotency key must not be blank", "Idempotency-Key");
        }
        if (str.length() > 255) {
            throw new PosOrderValidationException("Idempotency key must not exceed 255 characters", "Idempotency-Key");
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException ex) {
            // Non-UUID string: derive deterministic UUID scoped by store and actor to prevent cross-tenant collision
            String scoped = "store:" + storeId + ":actor:" + actorId + ":" + str;
            return UUID.nameUUIDFromBytes(scoped.getBytes(StandardCharsets.UTF_8));
        }
    }

    public ReservePosOrderResponse reserveOrder(UUID orderId, UUID actorId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }

        PosOrderRecord order = dataAccess.findOrderById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!dataAccess.hasCheckoutPermission(actorId, order.storeId())) {
            throw new AccessDeniedException("Actor does not have sales.checkout permission at store: " + order.storeId());
        }

        BigDecimal totalAmount = dataAccess.reserveOrder(orderId, actorId);
        return new ReservePosOrderResponse(orderId, "RESERVED", totalAmount);
    }

    private static boolean matchesPayload(
            PosOrderRecord existing,
            List<PosOrderItemRecord> existingItems,
            CreatePosOrderRequest request) {
        if (!Objects.equals(existing.storeId(), request.storeId())) return false;
        if (!Objects.equals(existing.warehouseId(), request.warehouseId())) return false;
        if (!Objects.equals(existing.customerId(), request.customerId())) return false;
        if (request.items() == null || existingItems.size() != request.items().size()) return false;

        for (PosOrderItemRequest reqItem : request.items()) {
            boolean found = existingItems.stream().anyMatch(saved ->
                    saved.productId().equals(reqItem.productId()) &&
                    saved.quantity() == reqItem.quantity());
            if (!found) return false;
        }
        return true;
    }

    private static PosOrderResponse toOrderResponse(PosOrderRecord order, List<PosOrderItemRecord> items) {
        List<PosOrderItemResponse> itemResponses = items.stream()
                .map(item -> new PosOrderItemResponse(
                        item.id(),
                        item.productId(),
                        item.sku(),
                        item.productName(),
                        item.quantity(),
                        item.unitPrice(),
                        item.lineTotal()))
                .toList();

        return new PosOrderResponse(
                order.id(),
                order.code(),
                order.idempotencyKey(),
                order.storeId(),
                order.warehouseId(),
                order.customerId(),
                order.channel(),
                order.status(),
                order.totalAmount(),
                order.createdBy(),
                order.createdAt(),
                order.holdExpiresAt(),
                itemResponses);
    }
}
