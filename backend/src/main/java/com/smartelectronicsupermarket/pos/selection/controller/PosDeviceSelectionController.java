package com.smartelectronicsupermarket.pos.selection.controller;

import java.util.UUID;

import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.pos.selection.dto.ReleasedDeviceResponse;
import com.smartelectronicsupermarket.pos.selection.dto.SelectDeviceRequest;
import com.smartelectronicsupermarket.pos.selection.dto.SelectedDeviceResponse;
import com.smartelectronicsupermarket.pos.selection.service.PosDeviceSelectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/orders")
@ConditionalOnProperty(
        prefix = "sasms.features.pos-serial-api",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class PosDeviceSelectionController {

    private final PosDeviceSelectionService service;

    public PosDeviceSelectionController(PosDeviceSelectionService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/items/{orderItemId}/devices")
    public SelectedDeviceResponse selectDevice(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId,
            @RequestBody SelectDeviceRequest request,
            Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        UUID deviceId = service.lockDevice(
                request.storeId(),
                orderId,
                orderItemId,
                request.serial(),
                TrustedActorAccess.actorId(authentication));
        return new SelectedDeviceResponse(deviceId, "SELECTED");
    }

    @DeleteMapping("/{orderId}/devices/{deviceId}")
    public ReleasedDeviceResponse releaseDevice(
            @PathVariable UUID orderId,
            @PathVariable UUID deviceId,
            Authentication authentication) {
        boolean released = service.releaseDevice(
                orderId,
                deviceId,
                TrustedActorAccess.actorId(authentication));
        return new ReleasedDeviceResponse(deviceId, released);
    }
}
