package com.smartelectronicsupermarket.reporting.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.smartelectronicsupermarket.reporting.data.*;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StoreDashboardServiceTest {
    private final StoreDashboardDataAccess data=mock(StoreDashboardDataAccess.class);
    private final PermissionChecker permissions=mock(PermissionChecker.class);
    private final StoreDashboardService service=new StoreDashboardService(data,permissions);
    private final UUID store=UUID.randomUUID(),actor=UUID.randomUUID();
    private final Instant from=Instant.parse("2026-11-01T00:00:00Z"),to=Instant.parse("2026-12-01T00:00:00Z");
    @Test void authorizedStoreReturnsRealMetricsAndAiIsNotAvailable(){
        when(permissions.hasPermission(actor,"reports.read",store)).thenReturn(true);
        when(data.summary(store,from,to)).thenReturn(new StoreSummaryRecord(new BigDecimal("1200.00"),2,5,3,2,1));
        when(data.topProducts(store,from,to,5)).thenReturn(List.of());
        var result=service.load(store,from,to,actor); assertEquals(new BigDecimal("1200.00"),result.grossPaidRevenue());
        assertNull(result.aiAcceptanceRate()); assertEquals("N/A_NO_ACCEPTANCE_EVENTS",result.aiAcceptanceState());
    }
    @Test void deniedStoreNeverQueriesAggregates(){
        when(permissions.hasPermission(actor,"reports.read",store)).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->service.load(store,from,to,actor)); verifyNoInteractions(data);
    }
    @Test void invalidRangeFailsBeforePermissionOrDatabase(){
        assertThrows(IllegalArgumentException.class,()->service.load(store,to,from,actor));
        verifyNoInteractions(permissions,data);
    }
}
