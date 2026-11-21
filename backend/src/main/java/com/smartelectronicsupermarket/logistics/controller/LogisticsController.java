package com.smartelectronicsupermarket.logistics.controller;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.logistics.dto.DeliveryResponse;
import com.smartelectronicsupermarket.logistics.dto.RecordDeliveryResultRequest;
import com.smartelectronicsupermarket.logistics.dto.ScheduleDeliveryRequest;
import com.smartelectronicsupermarket.logistics.service.LogisticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/logistics/deliveries")
@ConditionalOnProperty(prefix = "sasms.features.logistics-api", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class LogisticsController {
    private final LogisticsService service;

    public LogisticsController(LogisticsService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> schedule(@RequestBody ScheduleDeliveryRequest request,
                                                       Authentication authentication) {
        UUID id = service.schedule(request.orderId(), request.technicianId(), request.scheduledStart(),
                request.scheduledEnd(), request.address(), TrustedActorAccess.actorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/logistics/deliveries/" + id))
                .body(Map.of("delivery_id", id));
    }

    @GetMapping("/{deliveryId}")
    public DeliveryResponse find(@PathVariable UUID deliveryId, Authentication authentication) {
        return DeliveryResponse.from(service.find(deliveryId, TrustedActorAccess.actorId(authentication)));
    }

    @PostMapping("/{deliveryId}/result")
    public Map<String, UUID> result(@PathVariable UUID deliveryId,
                                     @RequestBody RecordDeliveryResultRequest request,
                                     Authentication authentication) {
        UUID id = service.recordResult(deliveryId, request.result(), request.evidenceReference(),
                TrustedActorAccess.actorId(authentication));
        return Map.of("delivery_id", id);
    }
}
