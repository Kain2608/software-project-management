package com.smartelectronicsupermarket.devicehistory.controller;

import java.util.UUID;
import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.devicehistory.dto.DeviceHistoryResponse;
import com.smartelectronicsupermarket.devicehistory.service.DeviceHistoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices")
@ConditionalOnProperty(prefix="sasms.features.device-history-api",name="enabled",havingValue="true",matchIfMissing=false)
public class DeviceHistoryController {
    private final DeviceHistoryService service;
    public DeviceHistoryController(DeviceHistoryService service){this.service=service;}
    @GetMapping("/{deviceId}/history")
    public DeviceHistoryResponse find(@PathVariable UUID deviceId,
            @RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset,
            Authentication authentication){
        return service.find(deviceId,TrustedActorAccess.actorId(authentication),limit,offset);
    }
}
