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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.smartelectronicsupermarket.ai.adapter.GeminiAdapter;
import com.smartelectronicsupermarket.ai.adapter.OpenAiAdapter;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess.VerifiedProduct;
import com.smartelectronicsupermarket.ai.data.AiRecommendationRecorder;
import com.smartelectronicsupermarket.ai.dto.CrossSellRequest;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;

/** S4-03: the engine must persist what it recommended (D55 section 8.1) without ever hurting the POS answer. */
class AiRecommendationRecordingTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PHONE = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID CASE = UUID.fromString("e1a2b3c4-d5e6-4f8a-9b0c-1d2e3f4a5b6c");

    private AiCrossSellService service;
    private AiCrossSellDataAccess dataAccess;
    private final List<AiRecommendationRecorder.Row> recorded = new CopyOnWriteArrayList<>();
    private CountDownLatch written = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        PermissionChecker permissions = mock(PermissionChecker.class);
        dataAccess = mock(AiCrossSellDataAccess.class);
        when(permissions.hasPermission(ACTOR, "ai.use", STORE)).thenReturn(true);
        when(dataAccess.warehouseBelongsToStore(WAREHOUSE, STORE)).thenReturn(true);
        when(dataAccess.findProductIdsBySku(anyCollection())).thenReturn(Map.of("PHONE-IP16-128", PHONE));
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of(
                new VerifiedProduct(CASE, "ACC-CASE-IP16", "Op lung", new BigDecimal("455000.00"), 7, "Phu kien")));
        service = new AiCrossSellService(new OpenAiAdapter(), new GeminiAdapter(), new AiRuleFallbackEngine(),
                new AiCacheManager(), permissions, dataAccess);
        service.setRecommendationRecorder(rows -> {
            recorded.addAll(rows);
            written.countDown();
        });
    }

    private CrossSellRequest request() {
        return new CrossSellRequest(STORE.toString(), WAREHOUSE.toString(), null, List.of("PHONE-IP16-128"), 4);
    }

    @Test
    void recordsCartProductToSuggestionRowsWithTableSourceValues() throws Exception {
        service.getCrossSellRecommendations(ACTOR, request());
        assertTrue(written.await(2, TimeUnit.SECONDS));
        assertEquals(1, recorded.size());
        AiRecommendationRecorder.Row row = recorded.get(0);
        assertEquals(PHONE, row.sourceProductId());
        assertEquals(CASE, row.recommendedProductId());
        assertEquals("LLM", row.source());
        assertEquals(new BigDecimal("1.0000"), row.score());
    }

    @Test
    void cacheHitIsRecordedAsCacheAndRuleFallbackAsRule() throws Exception {
        service.getCrossSellRecommendations(ACTOR, request());
        assertTrue(written.await(2, TimeUnit.SECONDS));
        written = new CountDownLatch(1);
        recorded.clear();
        service.getCrossSellRecommendations(ACTOR, request());
        assertTrue(written.await(2, TimeUnit.SECONDS));
        assertEquals("CACHE", recorded.get(0).source());
    }

    @Test
    void ruleFallbackIsRecordedAsRule() throws Exception {
        service.setCircuitBreakerOpen(true);
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of(
                new VerifiedProduct(CASE, "ACC-DEF-CASE", "Op lung", new BigDecimal("99000.00"), 3, "Phu kien")));
        var result = service.getCrossSellRecommendations(ACTOR, request());
        assertEquals("RULE_FALLBACK", result.source());
        assertTrue(written.await(2, TimeUnit.SECONDS));
        assertEquals("RULE", recorded.get(0).source());
    }

    @Test
    void recorderFailureNeverBreaksThePosResponse() {
        service.setRecommendationRecorder(rows -> {
            throw new IllegalStateException("permission denied for table ai_recommendations");
        });
        var result = service.getCrossSellRecommendations(ACTOR, request());
        assertEquals(1, result.suggestions().size());
    }

    @Test
    void nothingIsRecordedWhenThereIsNoVerifiedSuggestion() throws Exception {
        when(dataAccess.findSellableProducts(anyCollection(), eq(WAREHOUSE))).thenReturn(List.of());
        service.getCrossSellRecommendations(ACTOR, request());
        assertFalse(written.await(300, TimeUnit.MILLISECONDS));
        assertTrue(recorded.isEmpty());
    }
}
