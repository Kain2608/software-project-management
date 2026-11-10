package com.smartelectronicsupermarket.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.catalog.data.ProductCatalogDataAccess;
import com.smartelectronicsupermarket.catalog.data.ProductCatalogRecord;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ProductCatalogQueryServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");

    private ProductCatalogDataAccess dataAccess;
    private ProductCatalogService commandService;
    private PermissionChecker permissionChecker;
    private ProductCatalogQueryService service;

    @BeforeEach
    void setUp() {
        dataAccess = mock(ProductCatalogDataAccess.class);
        commandService = mock(ProductCatalogService.class);
        permissionChecker = mock(PermissionChecker.class);
        service = new ProductCatalogQueryService(dataAccess, commandService, permissionChecker);
    }

    @Test
    void readAndListRequireGlobalCatalogReadPermission() {
        when(permissionChecker.hasPermission(ACTOR_ID, "catalog.read", null)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.findById(ACTOR_ID, PRODUCT_ID));
        assertThrows(AccessDeniedException.class, () -> service.listProducts(ACTOR_ID, null, true, 20, 0));
        verifyNoInteractions(dataAccess);
    }

    @Test
    void listUsesBoundQueryContractAndEnforcesPageLimit() {
        ProductCatalogRecord product = product(true, 4);
        when(permissionChecker.hasPermission(ACTOR_ID, "catalog.read", null)).thenReturn(true);
        when(dataAccess.listProducts("TV", true, 100, 0)).thenReturn(List.of(product));

        assertEquals(List.of(product), service.listProducts(ACTOR_ID, "TV", true, 100, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.listProducts(ACTOR_ID, null, null, 101, 0));
        verify(dataAccess).listProducts("TV", true, 100, 0);
    }

    @Test
    void deactivatePreservesCurrentMutableFieldsAndExpectedRevision() {
        ProductCatalogRecord product = product(true, 7);
        when(permissionChecker.hasPermission(ACTOR_ID, "catalog.write", null)).thenReturn(true);
        when(dataAccess.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(commandService.updateProduct(
                ACTOR_ID,
                PRODUCT_ID,
                7,
                product.name(),
                product.specificationsJson(),
                product.price(),
                product.warrantyMonths(),
                false)).thenReturn(8);

        assertEquals(8, service.deactivateProduct(ACTOR_ID, PRODUCT_ID, 7));
        verify(commandService).updateProduct(
                ACTOR_ID,
                PRODUCT_ID,
                7,
                product.name(),
                product.specificationsJson(),
                product.price(),
                product.warrantyMonths(),
                false);
    }

    @Test
    void deactivateWithoutWritePermissionDoesNotReadOrUpdateProduct() {
        when(permissionChecker.hasPermission(ACTOR_ID, "catalog.write", null)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.deactivateProduct(ACTOR_ID, PRODUCT_ID, 7));
        verify(dataAccess, never()).findById(PRODUCT_ID);
        verifyNoInteractions(commandService);
    }

    private static ProductCatalogRecord product(boolean active, int revision) {
        return new ProductCatalogRecord(
                PRODUCT_ID,
                "TV-001",
                UUID.fromString("00000000-0000-0000-0000-000000000403"),
                UUID.fromString("00000000-0000-0000-0000-000000000404"),
                "Smart TV",
                "{\"resolution\":\"4K\"}",
                new BigDecimal("12990000.00"),
                24,
                active,
                revision,
                OffsetDateTime.parse("2026-11-09T10:00:00+07:00"));
    }
}
