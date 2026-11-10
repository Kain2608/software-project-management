package com.smartelectronicsupermarket.ai.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartelectronicsupermarket.ai.adapter.GeminiAdapter;
import com.smartelectronicsupermarket.ai.adapter.LlmAdapter;
import com.smartelectronicsupermarket.ai.adapter.OpenAiAdapter;
import com.smartelectronicsupermarket.ai.dto.CrossSellItem;
import com.smartelectronicsupermarket.ai.dto.CrossSellRequest;
import com.smartelectronicsupermarket.ai.dto.CrossSellResult;

@Service
public class AiCrossSellService {

    private static final Logger log = LoggerFactory.getLogger(AiCrossSellService.class);
    private static final long TIMEOUT_LIMIT_MS = 2500L; // BR-09: 2500ms hard limit

    private final OpenAiAdapter openAiAdapter;
    private final GeminiAdapter geminiAdapter;
    private final AiRuleFallbackEngine ruleFallbackEngine;
    private final AiCacheManager cacheManager;

    private final AtomicBoolean circuitBreakerOpen = new AtomicBoolean(false);
    private volatile String activeProvider = "OPENAI"; // OPENAI or GEMINI

    @Autowired
    public AiCrossSellService(
            OpenAiAdapter openAiAdapter,
            GeminiAdapter geminiAdapter,
            AiRuleFallbackEngine ruleFallbackEngine,
            AiCacheManager cacheManager) {
        this.openAiAdapter = openAiAdapter;
        this.geminiAdapter = geminiAdapter;
        this.ruleFallbackEngine = ruleFallbackEngine;
        this.cacheManager = cacheManager;
    }

    public CrossSellResult getCrossSellRecommendations(CrossSellRequest request) {
        long startTime = System.currentTimeMillis();
        List<String> skuIds = request.skuIds();
        String storeId = request.storeId();

        // 1. Build standardized Cache Key per D55
        String cacheKey = "ai:crosssell:v1:" + storeId + ":" + String.join(",", skuIds);

        // 2. Check L1 / L2 Cache
        List<CrossSellItem> cachedItems = cacheManager.get(cacheKey);
        if (cachedItems != null && !cachedItems.isEmpty()) {
            long tookMs = System.currentTimeMillis() - startTime;
            log.info("[AiCrossSellService] Returning CACHE recommendation in {}ms", tookMs);
            return new CrossSellResult(cachedItems, "CACHE", tookMs);
        }

        // 3. Check Circuit Breaker
        if (circuitBreakerOpen.get()) {
            log.warn("[AiCrossSellService] Circuit breaker OPEN -> Fast fallback to Rule Engine");
            List<CrossSellItem> fallbackItems = ruleFallbackEngine.generateFallbackCrossSell(skuIds, storeId);
            long tookMs = System.currentTimeMillis() - startTime;
            return new CrossSellResult(fallbackItems, "RULE_FALLBACK", tookMs);
        }

        // 4. Execute LLM via Selected Adapter with 2.5s Timeout
        LlmAdapter currentAdapter = "GEMINI".equalsIgnoreCase(activeProvider) ? geminiAdapter : openAiAdapter;

        try {
            CompletableFuture<List<CrossSellItem>> future = CompletableFuture.supplyAsync(
                    () -> currentAdapter.generateCrossSell(skuIds, storeId)
            );

            List<CrossSellItem> suggestions = future.get(TIMEOUT_LIMIT_MS, TimeUnit.MILLISECONDS);

            if (suggestions != null && !suggestions.isEmpty()) {
                cacheManager.put(cacheKey, suggestions);
                long tookMs = System.currentTimeMillis() - startTime;
                log.info("[AiCrossSellService] Successfully generated {} recommendations via {} in {}ms",
                        suggestions.size(), currentAdapter.getProviderName(), tookMs);
                return new CrossSellResult(suggestions, "AI", tookMs);
            }
        } catch (TimeoutException e) {
            log.warn("[AiCrossSellService] Call to {} timed out after {}ms -> Fallback triggered (BR-09)",
                    currentAdapter.getProviderName(), TIMEOUT_LIMIT_MS);
        } catch (InterruptedException | ExecutionException e) {
            log.error("[AiCrossSellService] Error executing LLM adapter: {}", e.getMessage());
        }

        // 5. Fallback path if LLM failed, timed out, or returned empty
        List<CrossSellItem> fallbackItems = ruleFallbackEngine.generateFallbackCrossSell(skuIds, storeId);
        long tookMs = System.currentTimeMillis() - startTime;
        log.info("[AiCrossSellService] Returning RULE_FALLBACK recommendations in {}ms", tookMs);
        return new CrossSellResult(fallbackItems, "RULE_FALLBACK", tookMs);
    }

    public void setActiveProvider(String provider) {
        if ("GEMINI".equalsIgnoreCase(provider) || "OPENAI".equalsIgnoreCase(provider)) {
            this.activeProvider = provider.toUpperCase();
            log.info("[AiCrossSellService] Switched active provider to {}", this.activeProvider);
        }
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setCircuitBreakerOpen(boolean open) {
        this.circuitBreakerOpen.set(open);
        log.warn("[AiCrossSellService] Circuit breaker state set to: {}", open ? "OPEN" : "CLOSED");
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreakerOpen.get();
    }

    public LlmAdapter.LlmCallLog getLastOpenAiLog() {
        return openAiAdapter.getLastCallLog();
    }

    public LlmAdapter.LlmCallLog getLastGeminiLog() {
        return geminiAdapter.getLastCallLog();
    }
}
