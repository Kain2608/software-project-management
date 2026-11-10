package com.smartelectronicsupermarket.ai.controller;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartelectronicsupermarket.ai.adapter.LlmAdapter;
import com.smartelectronicsupermarket.ai.dto.AiStatusResponse;
import com.smartelectronicsupermarket.ai.dto.CrossSellRequest;
import com.smartelectronicsupermarket.ai.dto.CrossSellResult;
import com.smartelectronicsupermarket.ai.service.AiCrossSellService;

@RestController
@RequestMapping("/api/v1/ai")
public class AiCrossSellController {

    private static final Logger log = LoggerFactory.getLogger(AiCrossSellController.class);

    private final AiCrossSellService aiCrossSellService;

    @Autowired
    public AiCrossSellController(AiCrossSellService aiCrossSellService) {
        this.aiCrossSellService = aiCrossSellService;
    }

    /**
     * FR08 - Request SKU-only AI recommendations with deterministic fallback per D52 OpenAPI specification.
     */
    @PostMapping("/cross-sell")
    public ResponseEntity<CrossSellResult> getCrossSell(@RequestBody CrossSellRequest request) {
        log.info("[API] POST /api/v1/ai/cross-sell received request for store: {}, skus: {}",
                request.storeId(), request.skuIds());

        CrossSellResult result = aiCrossSellService.getCrossSellRecommendations(request);
        return ResponseEntity.ok(result);
    }

    /**
     * AI Subsystem status & model provider health check.
     */
    @GetMapping("/status")
    public ResponseEntity<AiStatusResponse> getStatus() {
        AiStatusResponse status = new AiStatusResponse(
                "UP",
                "SASMS AI Gateway - Sprint 1 Increment (DS1)",
                aiCrossSellService.getActiveProvider(),
                "MemoryCache-5m-TTL",
                aiCrossSellService.isCircuitBreakerOpen() ? "OPEN" : "CLOSED",
                2500L,
                List.of("SMARTPHONE", "LAPTOP", "TELEVISION", "WASHING_MACHINE", "REFRIGERATOR", "AIR_CONDITIONER")
        );
        return ResponseEntity.ok(status);
    }

    /**
     * Simulation and test endpoint to verify both OpenAI and Gemini adapters and return execution logs.
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulateAdapterCall(
            @RequestParam(defaultValue = "OPENAI") String provider,
            @RequestBody CrossSellRequest request) {

        aiCrossSellService.setActiveProvider(provider);
        CrossSellResult result = aiCrossSellService.getCrossSellRecommendations(request);

        LlmAdapter.LlmCallLog callLog = "GEMINI".equalsIgnoreCase(provider)
                ? aiCrossSellService.getLastGeminiLog()
                : aiCrossSellService.getLastOpenAiLog();

        return ResponseEntity.ok(Map.of(
                "provider_tested", provider,
                "result", result,
                "call_log", callLog
        ));
    }
}
