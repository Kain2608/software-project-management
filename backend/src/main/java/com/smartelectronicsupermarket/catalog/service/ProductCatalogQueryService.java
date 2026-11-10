package com.smartelectronicsupermarket.catalog.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.catalog.data.ProductCatalogDataAccess;
import com.smartelectronicsupermarket.catalog.data.ProductCatalogRecord;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductCatalogDataAccess dataAccess;
    private final ProductCatalogService commandService;
    private final PermissionChecker permissionChecker;

    public ProductCatalogQueryService(
            ProductCatalogDataAccess dataAccess,
            ProductCatalogService commandService,
            PermissionChecker permissionChecker) {
        this.dataAccess = dataAccess;
        this.commandService = commandService;
        this.permissionChecker = permissionChecker;
    }

    public Optional<ProductCatalogRecord> findById(UUID actorId, UUID productId) {
        requireValue(actorId, "actorId");
        requireValue(productId, "productId");
        requireGlobalPermission(actorId, "catalog.read");
        return dataAccess.findById(productId);
    }

    public List<ProductCatalogRecord> listProducts(
            UUID actorId,
            String query,
            Boolean active,
            int limit,
            int offset) {
        requireValue(actorId, "actorId");
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        String normalizedQuery = normalizeQuery(query);
        requireGlobalPermission(actorId, "catalog.read");
        return dataAccess.listProducts(normalizedQuery, active, limit, offset);
    }

    public int deactivateProduct(UUID actorId, UUID productId, int expectedRevision) {
        requireValue(actorId, "actorId");
        requireValue(productId, "productId");
        requireGlobalPermission(actorId, "catalog.write");

        ProductCatalogRecord current = dataAccess.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product was not found"));
        return commandService.updateProduct(
                actorId,
                productId,
                expectedRevision,
                current.name(),
                current.specificationsJson(),
                current.price(),
                current.warrantyMonths(),
                false);
    }

    private void requireGlobalPermission(UUID actorId, String permissionCode) {
        if (!permissionChecker.hasPermission(actorId, permissionCode, null)) {
            throw new AccessDeniedException("Required global catalog permission is missing");
        }
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        if (query.length() > 200) {
            throw new IllegalArgumentException("query must not exceed 200 characters");
        }
        return query;
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
