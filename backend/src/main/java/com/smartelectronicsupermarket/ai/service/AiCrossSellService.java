package com.smartelectronicsupermarket.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartelectronicsupermarket.ai.adapter.GeminiAdapter;
import com.smartelectronicsupermarket.ai.adapter.LlmAdapter;
import com.smartelectronicsupermarket.ai.adapter.OpenAiAdapter;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess;
import com.smartelectronicsupermarket.ai.data.AiCrossSellDataAccess.VerifiedProduct;
import com.smartelectronicsupermarket.ai.data.AiRecommendationRecorder;
import com.smartelectronicsupermarket.ai.dto.CartItemInput;
import com.smartelectronicsupermarket.ai.dto.CrossSellCandidate;
import com.smartelectronicsupermarket.ai.dto.CrossSellItem;
import com.smartelectronicsupermarket.ai.dto.CrossSellRequest;
import com.smartelectronicsupermarket.ai.dto.CrossSellResult;
import com.smartelectronicsupermarket.security.authorization.PermissionChecker;

@Service
public class AiCrossSellService {

    private static final Logger log = LoggerFactory.getLogger(AiCrossSellService.class);
    private static final long TIMEOUT_LIMIT_MS = 2500L; // BR-09: 2500ms hard limit
    static final String AI_USE_PERMISSION = "ai.use";

    /** D52 v2 section 2.1: main devices (serial tracked) by category; everything else is quantity-based. */
    private static final Set<String> SERIALIZED_CATEGORIES = Set.of(
            "SMARTPHONE", "LAPTOP", "TELEVISION", "WASHING_MACHINE", "REFRIGERATOR", "AIR_CONDITIONER");

    private final OpenAiAdapter openAiAdapter;
    private final GeminiAdapter geminiAdapter;
    private final AiRuleFallbackEngine ruleFallbackEngine;
    private final AiCacheManager cacheManager;
    private final PermissionChecker permissionChecker;
    private final AiCrossSellDataAccess dataAccess;

    static final String RULES_VERSION = "v1.0";
    private static final int LLM_MAX_CONCURRENT_CALLS = 64;

    private final AiCircuitBreaker circuitBreaker;
    private final AtomicInteger threadCounter = new AtomicInteger();
    private final AiSingleFlight<LlmOutcome> singleFlight = new AiSingleFlight<>();
    /** Bulkhead for LLM calls; saturation is treated as an LLM failure, never as a POS error. */
    private final ThreadPoolExecutor llmExecutor = new ThreadPoolExecutor(
            0, LLM_MAX_CONCURRENT_CALLS, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "ai-llm-" + threadCounter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
    private static final int MAX_RECORDED_ROWS = 100;
    private volatile AiRecommendationRecorder recorder = AiRecommendationRecorder.NOOP;
    /** Single writer, bounded queue: audit rows are best effort and must never back-pressure the POS. */
    private final ThreadPoolExecutor recordExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(256), runnable -> {
                Thread thread = new Thread(runnable, "ai-recommendation-writer");
                thread.setDaemon(true);
                return thread;
            });
    private volatile String activeProvider = "OPENAI"; // OPENAI or GEMINI

    @Autowired
    public AiCrossSellService(
            OpenAiAdapter openAiAdapter,
            GeminiAdapter geminiAdapter,
            AiRuleFallbackEngine ruleFallbackEngine,
            AiCacheManager cacheManager,
            PermissionChecker permissionChecker,
            AiCrossSellDataAccess dataAccess) {
        this(openAiAdapter, geminiAdapter, ruleFallbackEngine, cacheManager, permissionChecker, dataAccess,
                new AiCircuitBreaker());
    }

    public AiCrossSellService(
            OpenAiAdapter openAiAdapter,
            GeminiAdapter geminiAdapter,
            AiRuleFallbackEngine ruleFallbackEngine,
            AiCacheManager cacheManager,
            PermissionChecker permissionChecker,
            AiCrossSellDataAccess dataAccess,
            AiCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        this.openAiAdapter = openAiAdapter;
        this.geminiAdapter = geminiAdapter;
        this.ruleFallbackEngine = ruleFallbackEngine;
        this.cacheManager = cacheManager;
        this.permissionChecker = permissionChecker;
        this.dataAccess = dataAccess;
    }

    public CrossSellResult getCrossSellRecommendations(UUID actorId, CrossSellRequest request) {
        long startTime = System.currentTimeMillis();
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }

        // 1. Validate shape (400)
        UUID storeId = parseUuid(request.storeId(), "store_id");
        UUID warehouseId = parseUuid(request.warehouseId(), "warehouse_id");
        List<String> cartSkus = request.getEffectiveSkuIds();
        if (cartSkus.isEmpty()) {
            throw new IllegalArgumentException("Request must contain at least 1 valid SKU");
        }
        if (cartSkus.size() > CrossSellRequest.MAX_CART_SKUS) {
            throw new IllegalArgumentException("Too many cart SKUs");
        }
        Map<String, UUID> declaredProductIds = declaredProductIds(request);

        // 2. ai.use within store scope, then warehouse must belong to that store (403)
        if (!permissionChecker.hasPermission(actorId, AI_USE_PERMISSION, storeId)) {
            throw new AiScopeDeniedException("ai.use is not granted for the requested store");
        }
        if (!dataAccess.warehouseBelongsToStore(warehouseId, storeId)) {
            throw new AiScopeDeniedException("warehouse does not belong to the requested store");
        }

        // 3. Cart SKUs (and any declared product_id) must exist in the catalog (422)
        Map<String, UUID> cartProducts = dataAccess.findProductIdsBySku(cartSkus);
        for (String sku : cartSkus) {
            UUID actual = cartProducts.get(sku);
            if (actual == null) {
                throw new AiSkuNotFoundException("SKU not found: " + sku);
            }
            UUID declared = declaredProductIds.get(sku);
            if (declared != null && !declared.equals(actual)) {
                throw new AiSkuNotFoundException("product_id does not match SKU: " + sku);
            }
        }

        // 4. Candidate SKUs (cache -> AI -> rule fallback). Never prices or stock.
        LlmAdapter adapter = currentAdapter();
        String cacheKey = "ai:crosssell:v1:" + RULES_VERSION + ":" + adapter.getLastCallLog().modelVersion()
                + ":" + warehouseId + ":" + String.join(",", new TreeSet<>(cartSkus))
                + ":vi:" + request.limit();
        List<CrossSellCandidate> cached = cacheManager.get(cacheKey);
        String source;
        String fallbackReason = "NONE";
        List<CrossSellCandidate> candidates;
        if (cached != null && !cached.isEmpty()) {
            candidates = cached;
            source = "CACHE";
        } else {
            // Concurrent misses for the same key share one model call (single-flight), then fall back together.
            LlmOutcome outcome = singleFlight.run(cacheKey, () -> lead(adapter, cacheKey, cartSkus, storeId),
                    new LlmOutcome(null, "LLM_TIMEOUT_EXCEEDED_2500MS"), TIMEOUT_LIMIT_MS + 500);
            if (outcome.candidates() != null && !outcome.candidates().isEmpty()) {
                candidates = outcome.candidates();
                source = outcome.fromCache() ? "CACHE" : "AI";
            } else {
                candidates = ruleFallbackEngine.generateFallbackCandidates(cartSkus);
                source = "RULE_FALLBACK";
                fallbackReason = outcome.reason();
            }
        }

        // 5. Verify against the database; drop anything unknown, inactive, out of stock or already in cart.
        List<CrossSellItem> verified = verify(candidates, cartSkus, warehouseId, request.limit());
        if (verified.isEmpty() && !"RULE_FALLBACK".equals(source)) {
            verified = verify(ruleFallbackEngine.generateFallbackCandidates(cartSkus), cartSkus, warehouseId,
                    request.limit());
            if (!verified.isEmpty()) {
                source = "RULE_FALLBACK";
                fallbackReason = "AI_CANDIDATES_UNAVAILABLE";
            }
        }

        recordAsync(cartProducts, verified, source, adapter);

        long tookMs = System.currentTimeMillis() - startTime;
        // Audit line for D55 section 8 / S2-08 evidence: source, latency, fallback reason; no PII or secrets.
        log.info("[AI-AUDIT] source={} latency_ms={} suggestions={} fallback_reason={} provider={} breaker={}",
                source, tookMs, verified.size(), fallbackReason, adapter.getProviderName(), circuitBreaker.state());
        return new CrossSellResult(verified, source, tookMs);
    }

    /** Wired by Spring; tests that do not care about persistence keep the no-op recorder. */
    @Autowired(required = false)
    public void setRecommendationRecorder(AiRecommendationRecorder recorder) {
        this.recorder = recorder == null ? AiRecommendationRecorder.NOOP : recorder;
    }

    /**
     * D55 section 8.1: store (cart product -> suggested product) rows off the request thread. The queue is bounded
     * and failures are only logged, so the POS answer never waits for, or fails because of, this write.
     */
    private void recordAsync(
            Map<String, UUID> cartProducts, List<CrossSellItem> verified, String source, LlmAdapter adapter) {
        if (verified.isEmpty() || recorder == AiRecommendationRecorder.NOOP) {
            return;
        }
        String tableSource = switch (source) {
            case "AI" -> "LLM";
            case "CACHE" -> "CACHE";
            default -> "RULE";
        };
        String model = "RULE_FALLBACK".equals(source) ? "rules-" + RULES_VERSION : adapter.getLastCallLog().modelVersion();
        List<AiRecommendationRecorder.Row> rows = new ArrayList<>();
        for (UUID cartProduct : cartProducts.values()) {
            for (int rank = 0; rank < verified.size() && rows.size() < MAX_RECORDED_ROWS; rank++) {
                CrossSellItem item = verified.get(rank);
                if (!cartProduct.equals(item.productId())) {
                    // Rank score in (0,1]; it orders suggestions, it is not a model confidence.
                    rows.add(new AiRecommendationRecorder.Row(cartProduct, item.productId(), tableSource,
                            BigDecimal.valueOf(Math.max(0.1, 1.0 - 0.1 * rank)).setScale(4, RoundingMode.HALF_UP),
                            model));
                }
            }
        }
        try {
            recordExecutor.execute(() -> {
                try {
                    recorder.record(rows);
                } catch (RuntimeException e) {
                    log.warn("[AiCrossSellService] ai_recommendations write skipped: {}", e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("[AiCrossSellService] ai_recommendations queue full, row batch dropped");
        }
    }

    private record LlmOutcome(List<CrossSellCandidate> candidates, String reason, boolean fromCache) {
        LlmOutcome(List<CrossSellCandidate> candidates, String reason) {
            this(candidates, reason, false);
        }
    }

    /** Runs once per key at a time: re-check the cache, ask the breaker, call the model, cache a usable answer. */
    private LlmOutcome lead(LlmAdapter adapter, String cacheKey, List<String> cartSkus, UUID storeId) {
        List<CrossSellCandidate> again = cacheManager.get(cacheKey);
        if (again != null && !again.isEmpty()) {
            return new LlmOutcome(again, "NONE", true);
        }
        if (!circuitBreaker.allowRequest()) {
            log.warn("[AiCrossSellService] Circuit breaker {} -> Fast fallback to Rule Engine", circuitBreaker.state());
            return new LlmOutcome(null, "CIRCUIT_OPEN");
        }
        LlmOutcome outcome = callLlm(adapter, cartSkus, storeId);
        if (outcome.candidates() != null && !outcome.candidates().isEmpty()) {
            cacheManager.put(cacheKey, outcome.candidates());
        }
        return outcome;
    }

    private LlmAdapter currentAdapter() {
        return "GEMINI".equalsIgnoreCase(activeProvider) ? geminiAdapter : openAiAdapter;
    }

    private LlmOutcome callLlm(LlmAdapter adapter, List<String> cartSkus, UUID storeId) {
        Future<List<CrossSellCandidate>> future = null;
        try {
            future = llmExecutor.submit(() -> adapter.generateCrossSell(cartSkus, storeId.toString()));
            List<CrossSellCandidate> result = future.get(TIMEOUT_LIMIT_MS, TimeUnit.MILLISECONDS);
            circuitBreaker.recordSuccess();
            if (result == null || result.isEmpty()) {
                return new LlmOutcome(null, "LLM_EMPTY_RESULT");
            }
            return new LlmOutcome(result, "NONE");
        } catch (TimeoutException e) {
            future.cancel(true);
            circuitBreaker.recordFailure();
            log.warn("[AiCrossSellService] Call to {} timed out after {}ms -> Fallback triggered (BR-09)",
                    adapter.getProviderName(), TIMEOUT_LIMIT_MS);
            return new LlmOutcome(null, "LLM_TIMEOUT_EXCEEDED_2500MS");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            circuitBreaker.recordFailure();
            return new LlmOutcome(null, "LLM_INTERRUPTED");
        } catch (ExecutionException e) {
            circuitBreaker.recordFailure();
            log.error("[AiCrossSellService] Error executing LLM adapter: {}", e.getCause() == null
                    ? e.getMessage() : e.getCause().getClass().getSimpleName());
            return new LlmOutcome(null, "LLM_ERROR");
        } catch (RejectedExecutionException e) {
            circuitBreaker.recordFailure();
            return new LlmOutcome(null, "LLM_BULKHEAD_FULL");
        }
    }

    private List<CrossSellItem> verify(
            List<CrossSellCandidate> candidates, List<String> cartSkus, UUID warehouseId, int limit) {
        Map<String, String> reasonBySku = new LinkedHashMap<>();
        for (CrossSellCandidate candidate : candidates) {
            if (!cartSkus.contains(candidate.sku())) {
                reasonBySku.putIfAbsent(candidate.sku(), candidate.reason());
            }
        }
        if (reasonBySku.isEmpty()) {
            return List.of();
        }
        Map<String, VerifiedProduct> bySku = new LinkedHashMap<>();
        for (VerifiedProduct product : dataAccess.findSellableProducts(reasonBySku.keySet(), warehouseId)) {
            bySku.put(product.sku(), product);
        }
        List<CrossSellItem> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : reasonBySku.entrySet()) {
            VerifiedProduct product = bySku.get(entry.getKey());
            if (product == null) {
                continue;
            }
            items.add(new CrossSellItem(
                    product.productId(),
                    product.sku(),
                    product.name(),
                    product.listPrice(),
                    product.availableQuantity(),
                    isSerialized(product.categoryName()),
                    entry.getValue()));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    static boolean isSerialized(String categoryName) {
        if (categoryName == null) {
            return false;
        }
        String normalized = categoryName.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return SERIALIZED_CATEGORIES.contains(normalized);
    }

    private static Map<String, UUID> declaredProductIds(CrossSellRequest request) {
        Map<String, UUID> declared = new LinkedHashMap<>();
        if (request.items() == null) {
            return declared;
        }
        for (CartItemInput item : request.items()) {
            if (item == null || item.sku() == null || item.sku().isBlank()
                    || item.productId() == null || item.productId().isBlank()) {
                continue;
            }
            declared.put(item.sku().strip().toUpperCase(Locale.ROOT), parseUuid(item.productId(), "product_id"));
        }
        return declared;
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    public void setActiveProvider(String provider) {
        if ("GEMINI".equalsIgnoreCase(provider) || "OPENAI".equalsIgnoreCase(provider)) {
            this.activeProvider = provider.toUpperCase(Locale.ROOT);
            log.info("[AiCrossSellService] Switched active provider to {}", this.activeProvider);
        }
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setCircuitBreakerOpen(boolean open) {
        circuitBreaker.force(open);
        log.warn("[AiCrossSellService] Circuit breaker state forced to: {}", circuitBreaker.state());
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.state() == AiCircuitBreaker.State.OPEN;
    }

    public AiCircuitBreaker.State circuitBreakerState() {
        return circuitBreaker.state();
    }

    /** Admin/diagnostic: raw adapter candidates, no price/stock, requires global ai.use. */
    public List<CrossSellCandidate> simulate(UUID actorId, String provider, List<String> skuIds) {
        if (!permissionChecker.hasPermission(actorId, AI_USE_PERMISSION, null)) {
            throw new AiScopeDeniedException("global ai.use is required");
        }
        LlmAdapter adapter = "GEMINI".equalsIgnoreCase(provider) ? geminiAdapter : openAiAdapter;
        return adapter.generateCrossSell(skuIds, "simulate");
    }

    public LlmAdapter.LlmCallLog getLastOpenAiLog() {
        return openAiAdapter.getLastCallLog();
    }

    public LlmAdapter.LlmCallLog getLastGeminiLog() {
        return geminiAdapter.getLastCallLog();
    }
}
