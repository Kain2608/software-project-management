package com.smartelectronicsupermarket.qr.controller;

import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.qr.data.QrDeviceRecord;
import com.smartelectronicsupermarket.qr.dto.QrLookupRequest;
import com.smartelectronicsupermarket.qr.dto.QrLookupResponse;
import com.smartelectronicsupermarket.qr.service.QrDeviceLookupService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class QrDeviceLookupController {

    private static final int MAX_PAYLOAD_LENGTH = 2048;

    private final QrDeviceLookupService lookupService;

    public QrDeviceLookupController(QrDeviceLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @PostMapping("/qr-lookup")
    public QrLookupResponse lookup(
            @RequestBody QrLookupRequest request,
            Authentication authentication) {
        if (request == null || request.payload() == null || request.payload().length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("payload is required and must not exceed 2048 characters");
        }

        QrDeviceRecord device = lookupService
                .lookup(request.payload(), TrustedActorAccess.actorId(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Device was not found"));
        return new QrLookupResponse(
                device.deviceId(),
                device.productId(),
                device.warehouseId(),
                device.status());
    }
}
