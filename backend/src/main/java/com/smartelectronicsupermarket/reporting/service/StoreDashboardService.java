package com.smartelectronicsupermarket.reporting.service;

import java.time.Instant;
import java.util.UUID;
import com.smartelectronicsupermarket.reporting.data.StoreDashboardDataAccess;
import com.smartelectronicsupermarket.reporting.dto.StoreDashboardResponse;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class StoreDashboardService {
    private final StoreDashboardDataAccess data; private final PermissionChecker permissions;
    public StoreDashboardService(StoreDashboardDataAccess data,PermissionChecker permissions){this.data=data;this.permissions=permissions;}
    public StoreDashboardResponse load(UUID storeId,Instant from,Instant to,UUID actorId){
        if(storeId==null||from==null||to==null||actorId==null) throw new IllegalArgumentException("store, range and actor are required");
        if(!to.isAfter(from)) throw new IllegalArgumentException("to must be after from");
        if(!permissions.hasPermission(actorId,"reports.read",storeId)) throw new AccessDeniedException("Access denied");
        return StoreDashboardResponse.from(storeId,from,to,data.summary(storeId,from,to),data.topProducts(storeId,from,to,5),
                data.aiEffectiveness(storeId,from,to));
    }
}
