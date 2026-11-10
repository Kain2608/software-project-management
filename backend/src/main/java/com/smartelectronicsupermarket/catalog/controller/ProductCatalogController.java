package com.smartelectronicsupermarket.catalog.controller;

import java.util.List;
import java.util.UUID;

import com.smartelectronicsupermarket.catalog.data.ProductCatalogRecord;
import com.smartelectronicsupermarket.catalog.dto.CreateProductRequest;
import com.smartelectronicsupermarket.catalog.dto.ProductListResponse;
import com.smartelectronicsupermarket.catalog.dto.ProductMutationResponse;
import com.smartelectronicsupermarket.catalog.dto.ProductResponse;
import com.smartelectronicsupermarket.catalog.dto.UpdateProductRequest;
import com.smartelectronicsupermarket.catalog.service.ProductCatalogQueryService;
import com.smartelectronicsupermarket.catalog.service.ProductCatalogService;
import com.smartelectronicsupermarket.common.web.ResourceNotFoundException;
import com.smartelectronicsupermarket.common.web.TrustedActorAccess;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/products")
public class ProductCatalogController {

    private final ProductCatalogService commandService;
    private final ProductCatalogQueryService queryService;
    private final ObjectMapper objectMapper;

    public ProductCatalogController(
            ProductCatalogService commandService,
            ProductCatalogQueryService queryService,
            ObjectMapper objectMapper) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ProductMutationResponse> create(
            @RequestBody CreateProductRequest request,
            Authentication authentication) {
        requireValue(request, "request");
        requireValue(request.warrantyMonths(), "warrantyMonths");
        UUID productId = commandService.createProduct(
                TrustedActorAccess.actorId(authentication),
                request.sku(),
                request.brandId(),
                request.categoryId(),
                request.name(),
                toJson(request.specifications()),
                request.price(),
                request.warrantyMonths());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProductMutationResponse(productId, 0, true));
    }

    @GetMapping("/{productId}")
    public ProductResponse findById(
            @PathVariable UUID productId,
            Authentication authentication) {
        ProductCatalogRecord product = queryService
                .findById(TrustedActorAccess.actorId(authentication), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product was not found"));
        return toResponse(product);
    }

    @GetMapping
    public ProductListResponse list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            Authentication authentication) {
        List<ProductResponse> items = queryService.listProducts(
                        TrustedActorAccess.actorId(authentication),
                        query,
                        active,
                        limit,
                        offset)
                .stream()
                .map(this::toResponse)
                .toList();
        return new ProductListResponse(items, limit, offset, items.size());
    }

    @PutMapping("/{productId}")
    public ProductMutationResponse update(
            @PathVariable UUID productId,
            @RequestBody UpdateProductRequest request,
            Authentication authentication) {
        requireUpdateFields(request);
        int revision = commandService.updateProduct(
                TrustedActorAccess.actorId(authentication),
                productId,
                request.expectedRevision(),
                request.name(),
                toJson(request.specifications()),
                request.price(),
                request.warrantyMonths(),
                request.active());
        return new ProductMutationResponse(productId, revision, request.active());
    }

    @DeleteMapping("/{productId}")
    public ProductMutationResponse deactivate(
            @PathVariable UUID productId,
            @RequestParam(name = "expected_revision") int expectedRevision,
            Authentication authentication) {
        int revision = queryService.deactivateProduct(
                TrustedActorAccess.actorId(authentication),
                productId,
                expectedRevision);
        return new ProductMutationResponse(productId, revision, false);
    }

    private ProductResponse toResponse(ProductCatalogRecord product) {
        try {
            return new ProductResponse(
                    product.productId(),
                    product.sku(),
                    product.brandId(),
                    product.categoryId(),
                    product.name(),
                    objectMapper.readTree(product.specificationsJson()),
                    product.price(),
                    product.warrantyMonths(),
                    product.active(),
                    product.revision(),
                    product.updatedAt());
        } catch (JacksonException exception) {
            throw new DataRetrievalFailureException("Stored product specifications are invalid", exception);
        }
    }

    private String toJson(JsonNode value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("specifications could not be serialized", exception);
        }
    }

    private static void requireUpdateFields(UpdateProductRequest request) {
        requireValue(request, "request");
        requireValue(request.expectedRevision(), "expectedRevision");
        requireValue(request.warrantyMonths(), "warrantyMonths");
        requireValue(request.active(), "active");
    }

    private static void requireValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
