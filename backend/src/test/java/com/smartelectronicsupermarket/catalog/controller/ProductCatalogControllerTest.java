package com.smartelectronicsupermarket.catalog.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartelectronicsupermarket.catalog.data.ProductCatalogRecord;
import com.smartelectronicsupermarket.catalog.service.ProductCatalogQueryService;
import com.smartelectronicsupermarket.catalog.service.ProductCatalogService;
import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class ProductCatalogControllerTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID BRAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final BigDecimal PRICE = new BigDecimal("12990000.00");

    private ProductCatalogService commandService;
    private ProductCatalogQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = org.mockito.Mockito.mock(ProductCatalogService.class);
        queryService = org.mockito.Mockito.mock(ProductCatalogQueryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductCatalogController(
                        commandService,
                        queryService,
                        JsonMapper.builder().build()))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsAndUpdatesUsingTrustedActorAndExpectedRevision() throws Exception {
        when(commandService.createProduct(
                ACTOR_ID, "TV-001", BRAND_ID, CATEGORY_ID, "Smart TV", "{}", PRICE, 24))
                .thenReturn(PRODUCT_ID);
        when(commandService.updateProduct(
                ACTOR_ID, PRODUCT_ID, 3, "Smart TV 2026", "{}", PRICE, 24, false))
                .thenReturn(4);

        mockMvc.perform(post("/api/v1/products")
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product_id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.revision").value(0));

        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_revision":3,"name":"Smart TV 2026","specifications":{},
                                 "price":12990000.00,"warranty_months":24,"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(4))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void readsListsAndSoftDeletesWithoutPhysicalDeleteContract() throws Exception {
        ProductCatalogRecord record = product();
        when(queryService.findById(ACTOR_ID, PRODUCT_ID)).thenReturn(Optional.of(record));
        when(queryService.listProducts(ACTOR_ID, "TV", true, 20, 0)).thenReturn(List.of(record));
        when(queryService.deactivateProduct(ACTOR_ID, PRODUCT_ID, 3)).thenReturn(4);

        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID).principal(trustedActor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TV-001"))
                .andExpect(jsonPath("$.revision").value(3));
        mockMvc.perform(get("/api/v1/products")
                        .principal(trustedActor())
                        .queryParam("query", "TV")
                        .queryParam("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].product_id").value(PRODUCT_ID.toString()));
        mockMvc.perform(delete("/api/v1/products/{id}", PRODUCT_ID)
                        .principal(trustedActor())
                        .queryParam("expected_revision", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(4))
                .andExpect(jsonPath("$.active").value(false));
        verify(queryService).deactivateProduct(ACTOR_ID, PRODUCT_ID, 3);
    }

    @Test
    void permissionDuplicateAndRevisionErrorsUseSafeHttpContracts() throws Exception {
        when(queryService.listProducts(ACTOR_ID, null, null, 20, 0))
                .thenThrow(new AccessDeniedException("internal permission details"));
        when(commandService.createProduct(
                ACTOR_ID, "TV-001", BRAND_ID, CATEGORY_ID, "Smart TV", "{}", PRICE, 24))
                .thenThrow(new DataIntegrityViolationException(
                        "INSERT INTO sasms.products",
                        new SQLException("duplicate SKU", "23505")));
        when(commandService.updateProduct(
                ACTOR_ID, PRODUCT_ID, 3, "Smart TV 2026", "{}", PRICE, 24, false))
                .thenThrow(new ConcurrencyFailureException(
                        "SELECT sasms.update_product",
                        new SQLException("stale revision", "40001")));

        mockMvc.perform(get("/api/v1/products").principal(trustedActor()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("internal permission"))));
        mockMvc.perform(post("/api/v1/products")
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(content().string(not(containsString("INSERT"))));
        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID)
                        .principal(trustedActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expected_revision":3,"name":"Smart TV 2026","specifications":{},
                                 "price":12990000.00,"warranty_months":24,"active":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_CONFLICT"))
                .andExpect(content().string(not(containsString("SELECT"))));
    }

    private static String createBody() {
        return """
                {"sku":"TV-001","brand_id":"%s","category_id":"%s","name":"Smart TV",
                 "specifications":{},"price":12990000.00,"warranty_months":24}
                """.formatted(BRAND_ID, CATEGORY_ID);
    }

    private static ProductCatalogRecord product() {
        return new ProductCatalogRecord(
                PRODUCT_ID,
                "TV-001",
                BRAND_ID,
                CATEGORY_ID,
                "Smart TV",
                "{}",
                PRICE,
                24,
                true,
                3,
                OffsetDateTime.parse("2026-11-09T10:00:00+07:00"));
    }

    private static TrustedActorAuthenticationToken trustedActor() {
        return new TrustedActorAuthenticationToken(new AuthenticatedActor(ACTOR_ID));
    }
}
