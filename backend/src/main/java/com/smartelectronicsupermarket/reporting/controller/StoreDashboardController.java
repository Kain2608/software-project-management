package com.smartelectronicsupermarket.reporting.controller;

import java.time.Instant;
import java.util.UUID;
import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import com.smartelectronicsupermarket.reporting.dto.StoreDashboardResponse;
import com.smartelectronicsupermarket.reporting.service.StoreDashboardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@ConditionalOnProperty(prefix="sasms.features.store-dashboard-api",name="enabled",havingValue="true",matchIfMissing=false)
public class StoreDashboardController {
    private final StoreDashboardService service;
    public StoreDashboardController(StoreDashboardService service){this.service=service;}
    @GetMapping("/store-summary")
    public StoreDashboardResponse load(@RequestParam("store_id") UUID storeId,@RequestParam Instant from,
            @RequestParam Instant to,Authentication authentication){
        return service.load(storeId,from,to,TrustedActorAccess.actorId(authentication));
    }
}
