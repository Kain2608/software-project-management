package com.smartelectronicsupermarket.pos.receipt.service;

import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.ResourceConflictException;
import com.smartelectronicsupermarket.pos.receipt.data.ReceiptDataAccess;
import com.smartelectronicsupermarket.pos.receipt.data.ReceiptRecord;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {

    private final ReceiptDataAccess dataAccess;

    public ReceiptService(ReceiptDataAccess dataAccess) {
        this.dataAccess = dataAccess;
    }

    public Optional<ReceiptRecord> findPaidReceipt(UUID actorId, UUID orderId) {
        if (actorId == null || orderId == null) {
            throw new IllegalArgumentException("actorId and orderId are required");
        }
        Optional<ReceiptRecord> receipt = dataAccess.findByOrderId(actorId, orderId);
        if (receipt.isPresent() && !"PAID".equals(receipt.orElseThrow().status())) {
            throw new ResourceConflictException("Only paid orders have printable receipt data");
        }
        return receipt;
    }
}
