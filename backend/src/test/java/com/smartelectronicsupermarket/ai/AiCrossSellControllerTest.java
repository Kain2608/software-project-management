package com.smartelectronicsupermarket.ai;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.smartelectronicsupermarket.ai.adapter.GeminiAdapter;
import com.smartelectronicsupermarket.ai.adapter.OpenAiAdapter;
import com.smartelectronicsupermarket.ai.controller.AiCrossSellController;
import com.smartelectronicsupermarket.ai.controller.AiExceptionHandler;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess.VerifiedProduct;
import com.smartelectronicsupermarket.ai.service.AiCacheManager;
import com.smartelectronicsupermarket.ai.service.AiCrossSellService;
import com.smartelectronicsupermarket.ai.service.AiRuleFallbackEngine;
import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;
import com.smartelectronicsupermarket.security.identity.AuthenticatedActor;
import com.smartelectronicsupermarket.security.identity.TrustedActorAuthenticationToken;

class AiCrossSellControllerTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PHONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID CASE_ID = UUID.fromString("e1a2b3c4-d5e6-4f8a-9b0c-1d2e3f4a5b6c");

    private MockMvc mockMvc;
    private AiCrossSellService service;
    private AiCacheManager cacheManager;
    private PermissionChecker permissionChecker;
    private AiCrossSellDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        cacheManager = new AiCacheManager();
        permissionChecker = mock(PermissionChecker.class);
        dataAccess = mock(AiCrossSellDataAccess.class);
        service = new AiCrossSellService(new OpenAiAdapter(), new GeminiAdapter(),
                new AiRuleFallbackEngine(), cacheManager, permissionChecker, dataAccess);

        when(permissionChecker.hasPermission(ACTOR, "ai.use", STORE)).thenReturn(true);
        when(dataAccess.warehouseBelongsToStore(WAREHOUSE, STORE)).thenReturn(true);
        when(dataAccess.findProductIdsBySku(anyCollection())).thenReturn(Map.of("PHONE-IP16-128", PHONE_ID));
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of(
                new VerifiedProduct(CASE_ID, "ACC-CASE-IP16", "Op lung DB", new BigDecimal("455000.00"), 7, "Phu kien")));

        mockMvc = MockMvcBuilders.standaloneSetup(new AiCrossSellController(service))
                .setControllerAdvice(new AiExceptionHandler(), new ApiExceptionHandler())
                .build();
    }

    private static RequestPostProcessor actor(UUID id) {
        return request -> {
            request.setUserPrincipal(new TrustedActorAuthenticationToken(new AuthenticatedActor(id)));
            return request;
        };
    }

    private static String body(String items) {
        return """
                {"store_id": "%s", "warehouse_id": "%s", "items": %s, "limit": 4}
                """.formatted(STORE, WAREHOUSE, items);
    }

    private static final String PHONE_ITEMS =
            "[{\"product_id\": \"" + PHONE_ID + "\", \"sku\": \"phone-ip16-128\"}]";

    @Test
    void returnsProductIdPriceAndStockFromDatabaseNotFromAdapter() throws Exception {
        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("AI")))
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].product_id", is(CASE_ID.toString())))
                .andExpect(jsonPath("$.suggestions[0].name", is("Op lung DB")))
                .andExpect(jsonPath("$.suggestions[0].list_price", is(455000.00)))
                .andExpect(jsonPath("$.suggestions[0].available_quantity", is(7)))
                .andExpect(jsonPath("$.suggestions[0].is_serialized", is(false)))
                .andExpect(jsonPath("$.suggestions[0].reason", containsString("Ốp lưng")));
    }

    @Test
    void candidatesMissingFromDatabaseAreDroppedAndEmptyListIs200() throws Exception {
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(0)));
    }

    @Test
    void missingAiUseForStoreReturns403ForbiddenScopeWithoutTouchingData() throws Exception {
        when(permissionChecker.hasPermission(ACTOR, "ai.use", STORE)).thenReturn(false);

        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_SCOPE")));
        verify(dataAccess, never()).findSellableProducts(any(), any());
        verify(dataAccess, never()).findProductIdsBySku(any());
    }

    @Test
    void warehouseOutsideStoreReturns403ForbiddenScope() throws Exception {
        when(dataAccess.warehouseBelongsToStore(WAREHOUSE, STORE)).thenReturn(false);

        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_SCOPE")));
        verify(dataAccess, never()).findSellableProducts(any(), any());
    }

    @Test
    void unknownCartSkuReturns422SkuNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"sku\": \"NOPE-1\"}]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("SKU_NOT_FOUND")));
    }

    @Test
    void productIdThatDoesNotMatchSkuReturns422() throws Exception {
        String items = "[{\"product_id\": \"" + CASE_ID + "\", \"sku\": \"PHONE-IP16-128\"}]";
        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(items)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("SKU_NOT_FOUND")));
    }

    @Test
    void invalidUuidAndMissingFieldsReturn400InvalidArgument() throws Exception {
        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\": \"ST-HCM-01\", \"warehouse_id\": \"" + WAREHOUSE
                                + "\", \"sku_ids\": [\"PHONE-IP16-128\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_ARGUMENT")));
        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouse_id\": \"" + WAREHOUSE + "\", \"sku_ids\": [\"X\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_ARGUMENT")));
        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"store_id\": \"" + STORE + "\", \"sku_ids\": [\"X\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondCallServesCachedCandidatesButStillVerifiesAgainstDatabase() throws Exception {
        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(jsonPath("$.source", is("AI")));
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of(
                new VerifiedProduct(CASE_ID, "ACC-CASE-IP16", "Op lung DB", new BigDecimal("500000.00"), 2, "Phu kien")));

        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("CACHE")))
                .andExpect(jsonPath("$.suggestions[0].list_price", is(500000.00)))
                .andExpect(jsonPath("$.suggestions[0].available_quantity", is(2)));
    }

    @Test
    void circuitBreakerOpenUsesRuleFallbackCandidatesVerifiedInDatabase() throws Exception {
        service.setCircuitBreakerOpen(true);
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of(
                new VerifiedProduct(CASE_ID, "ACC-DEF-CASE", "Op lung DB", new BigDecimal("250000.00"), 3, "Phu kien")));

        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("RULE_FALLBACK")))
                .andExpect(jsonPath("$.suggestions[0].reason", containsString("Luật dự phòng")));
    }

    @Test
    void serializedFlagFollowsCategoryRule() throws Exception {
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of(
                new VerifiedProduct(CASE_ID, "ACC-CASE-IP16", "Main device", new BigDecimal("1.00"), 1, "Television")));

        mockMvc.perform(post("/api/v1/ai/cross-sell").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(jsonPath("$.suggestions[0].is_serialized", is(true)));
    }

    @Test
    void requestWithoutTrustedActorIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/ai/cross-sell")
                        .contentType(MediaType.APPLICATION_JSON).content(body(PHONE_ITEMS)))
                .andExpect(status().isForbidden());
        verify(permissionChecker, never()).hasPermission(any(), any(), any());
    }

    @Test
    void statusEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.active_provider", is("OPENAI")))
                .andExpect(jsonPath("$.timeout_limit_ms", is(2500)));
    }

    @Test
    void simulateRequiresGlobalAiUseAndReturnsSkuCandidatesOnly() throws Exception {
        String json = "{\"store_id\": \"" + STORE + "\", \"warehouse_id\": \"" + WAREHOUSE
                + "\", \"sku_ids\": [\"AC-DAIK-12000\"]}";

        mockMvc.perform(post("/api/v1/ai/simulate?provider=GEMINI").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_SCOPE")));

        when(permissionChecker.hasPermission(ACTOR, "ai.use", null)).thenReturn(true);
        mockMvc.perform(post("/api/v1/ai/simulate?provider=GEMINI").with(actor(ACTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider_tested", is("GEMINI")))
                .andExpect(jsonPath("$.candidates[0].sku", is("ACC-GEN-ESSENTIAL")))
                .andExpect(jsonPath("$.candidates[0].list_price").doesNotExist())
                .andExpect(jsonPath("$.call_log.modelVersion", is("gemini-2.5-flash")));
    }
}
