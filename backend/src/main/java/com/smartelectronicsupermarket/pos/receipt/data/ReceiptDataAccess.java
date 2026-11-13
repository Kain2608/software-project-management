package com.smartelectronicsupermarket.pos.receipt.data;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptDataAccess {

    Optional<ReceiptRecord> findByOrderId(UUID actorId, UUID orderId);
}
