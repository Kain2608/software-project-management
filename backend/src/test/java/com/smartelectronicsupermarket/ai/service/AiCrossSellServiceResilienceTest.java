package com.smartelectronicsupermarket.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.smartelectronicsupermarket.ai.adapter.GeminiAdapter;
import com.smartelectronicsupermarket.ai.adapter.OpenAiAdapter;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess.VerifiedProduct;
import com.smartelectronicsupermarket.ai.dto.CrossSellCandidate;
import com.smartelectronicsupermarket.ai.dto.CrossSellRequest;
import com.smartelectronicsupermarket.ai.dto.CrossSellResult;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;

class AiCrossSellServiceResilienceTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID WH_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID WH_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID PHONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");

    private AiCrossSellDataAccess dataAccess;
    private PermissionChecker permissions;

    @BeforeEach
    void setUp() {
        dataAccess = mock(AiCrossSellDataAccess.class);
        permissions = mock(PermissionChecker.class);
        when(permissions.hasPermission(ACTOR, "ai.use", STORE)).thenReturn(true);
        when(dataAccess.warehouseBelongsToStore(WH_A, STORE)).thenReturn(true);
        when(dataAccess.warehouseBelongsToStore(WH_B, STORE)).thenReturn(true);
        when(dataAccess.findProductIdsBySku(anyCollection())).thenReturn(Map.of("PHONE-IP16-128", PHONE_ID));
        // Both the LLM SKU and the rule-fallback SKU exist in stock, so the source is decided by the engine alone.
        when(dataAccess.findSellableProducts(anyCollection(), eq(WH_A))).thenReturn(List.of(
                product("ACC-CASE-IP16"), product("ACC-DEF-CASE")));
        when(dataAccess.findSellableProducts(anyCollection(), eq(WH_B))).thenReturn(List.of(
                product("ACC-CASE-IP16"), product("ACC-DEF-CASE")));
    }

    private static VerifiedProduct product(String sku) {
        return new VerifiedProduct(UUID.nameUUIDFromBytes(sku.getBytes()), sku, "Name " + sku,
                new BigDecimal("100000.00"), 5, "Phu kien");
    }

    private static CrossSellRequest request(UUID warehouse) {
        return new CrossSellRequest(STORE.toString(), warehouse.toString(), null, List.of("PHONE-IP16-128"), 4);
    }

    private AiCrossSellService service(OpenAiAdapter llm, AiCircuitBreaker breaker) {
        return new AiCrossSellService(llm, new GeminiAdapter(), new AiRuleFallbackEngine(), new AiCacheManager(),
                permissions, dataAccess, breaker);
    }

    private static OpenAiAdapter sleeping(long millis, AtomicInteger calls) {
        return new OpenAiAdapter() {
            @Override
            public List<CrossSellCandidate> generateCrossSell(List<String> skuIds, String storeId) {
                calls.incrementAndGet();
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(new CrossSellCandidate("ACC-CASE-IP16", "llm reason"));
            }
        };
    }

    private static OpenAiAdapter failing(AtomicInteger calls) {
        return new OpenAiAdapter() {
            @Override
            public List<CrossSellCandidate> generateCrossSell(List<String> skuIds, String storeId) {
                calls.incrementAndGet();
                throw new IllegalStateException("provider 503");
            }
        };
    }

    @Test
    void llmHangingPastTimeoutFallsBackWithinBr09Budget() {
        AiCrossSellService service = service(sleeping(10_000, new AtomicInteger()), new AiCircuitBreaker());

        long start = System.nanoTime();
        CrossSellResult result = service.getCrossSellRecommendations(ACTOR, request(WH_A));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals("RULE_FALLBACK", result.source());
        assertFalse(result.suggestions().isEmpty());
        assertEquals("ACC-DEF-CASE", result.suggestions().get(0).sku());
        assertTrue(elapsedMs >= 2400 && elapsedMs < 3000, "fallback must land near 2.5s but under 3s: " + elapsedMs);
    }

    @Test
    void llmErrorFallsBackImmediately() {
        AiCrossSellService service = service(failing(new AtomicInteger()), new AiCircuitBreaker());

        CrossSellResult result = service.getCrossSellRecommendations(ACTOR, request(WH_A));

        assertEquals("RULE_FALLBACK", result.source());
        assertFalse(result.suggestions().isEmpty());
        assertTrue(result.tookMs() < 500);
    }

    @Test
    void breakerOpensAfterRepeatedFailuresAndStopsCallingLlm() {
        AtomicInteger calls = new AtomicInteger();
        AiCrossSellService service = service(failing(calls), new AiCircuitBreaker());

        for (int i = 0; i < 20; i++) {
            service.getCrossSellRecommendations(ACTOR, request(WH_A));
        }
        assertTrue(service.isCircuitBreakerOpen());
        int callsWhenOpened = calls.get();

        CrossSellResult result = service.getCrossSellRecommendations(ACTOR, request(WH_A));

        assertEquals("RULE_FALLBACK", result.source());
        assertEquals(callsWhenOpened, calls.get(), "no LLM call while the breaker is open");
        assertTrue(result.tookMs() < 100, "open breaker answers from rules in < 100 ms");
    }

    @Test
    void cacheIsScopedPerWarehouse() {
        AtomicInteger calls = new AtomicInteger();
        AiCrossSellService service = service(sleeping(1, calls), new AiCircuitBreaker());

        assertEquals("AI", service.getCrossSellRecommendations(ACTOR, request(WH_A)).source());
        assertEquals("CACHE", service.getCrossSellRecommendations(ACTOR, request(WH_A)).source());
        assertEquals("AI", service.getCrossSellRecommendations(ACTOR, request(WH_B)).source());
        assertEquals(2, calls.get());
    }

    @Test
    void llmSuggestionsOutOfStockFallBackToRuleCandidatesFromDatabase() {
        when(dataAccess.findSellableProducts(anyCollection(), eq(WH_A))).thenAnswer(invocation -> {
            java.util.Collection<String> skus = invocation.getArgument(0);
            return skus.contains("ACC-CASE-IP16") ? List.of() : List.of(product("ACC-DEF-CASE"));
        });
        AiCrossSellService service = service(sleeping(1, new AtomicInteger()), new AiCircuitBreaker());

        CrossSellResult result = service.getCrossSellRecommendations(ACTOR, request(WH_A));

        assertEquals("RULE_FALLBACK", result.source());
        assertEquals("ACC-DEF-CASE", result.suggestions().get(0).sku());
    }
}
