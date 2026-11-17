package com.smartelectronicsupermarket.pos.order.data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosOrderDataAccess {

    boolean hasCheckoutPermission(UUID actorId, UUID storeId);

    boolean isWarehouseInStore(UUID warehouseId, UUID storeId);

    CatalogProductRecord findActiveProduct(UUID productId);

    Optional<PosOrderRecord> findOrderById(UUID orderId);

    Optional<PosOrderRecord> findOrderByIdempotencyKey(UUID idempotencyKey);

    List<PosOrderItemRecord> findOrderItems(UUID orderId);

    Optional<PosOrderRecord> createOrderIfAbsent(
            UUID orderId,
            String code,
            UUID idempotencyKey,
            UUID storeId,
            UUID warehouseId,
            UUID customerId,
            UUID actorId,
            List<PosOrderItemRecord> items);

    PosOrderRecord createOrder(
            UUID orderId,
            String code,
            UUID idempotencyKey,
            UUID storeId,
            UUID warehouseId,
            UUID customerId,
            UUID actorId,
            List<PosOrderItemRecord> items);

    BigDecimal reserveOrder(UUID orderId, UUID actorId);
}
