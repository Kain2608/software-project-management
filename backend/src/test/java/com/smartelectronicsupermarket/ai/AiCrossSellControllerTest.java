package com.smartelectronicsupermarket.ai;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.smartelectronicsupermarket.ai.adapter.GeminiAdapter;
import com.smartelectronicsupermarket.ai.adapter.OpenAiAdapter;
import com.smartelectronicsupermarket.ai.controller.AiCrossSellController;
import com.smartelectronicsupermarket.ai.service.AiCacheManager;
import com.smartelectronicsupermarket.ai.service.AiCrossSellService;
import com.smartelectronicsupermarket.ai.service.AiRuleFallbackEngine;
import com.smartelectronicsupermarket.common.web.ApiExceptionHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AiCrossSellControllerTest {

    private MockMvc mockMvc;
    private AiCrossSellService aiCrossSellService;
    private AiCacheManager cacheManager;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        OpenAiAdapter openAiAdapter = new OpenAiAdapter();
        GeminiAdapter geminiAdapter = new GeminiAdapter();
        AiRuleFallbackEngine ruleFallbackEngine = new AiRuleFallbackEngine();
        cacheManager = new AiCacheManager();
        cacheManager.clear();

        aiCrossSellService = new AiCrossSellService(openAiAdapter, geminiAdapter, ruleFallbackEngine, cacheManager);
        AiCrossSellController controller = new AiCrossSellController(aiCrossSellService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void testCrossSellEndpointReturns200WithAiSource() throws Exception {
        String requestJson = """
                {
                    "sku_ids": ["PHONE-IP16-128"],
                    "store_id": "ST-HCM-01"
                }
                """;

        mockMvc.perform(post("/api/v1/ai/cross-sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("AI")))
                .andExpect(jsonPath("$.suggestions", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.suggestions[0].sku_id", notNullValue()))
                .andExpect(jsonPath("$.suggestions[0].reason", notNullValue()))
                .andExpect(jsonPath("$.took_ms", greaterThanOrEqualTo(0)));
    }

    @Test
    void testCrossSellEndpointReturns200WithCacheHit() throws Exception {
        String requestJson = """
                {
                    "sku_ids": ["LAP-MAC-M3-16"],
                    "store_id": "ST-HN-02"
                }
                """;

        // First call -> AI (Cache Miss)
        mockMvc.perform(post("/api/v1/ai/cross-sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("AI")));

        // Second call with same parameters -> CACHE (Cache Hit)
        mockMvc.perform(post("/api/v1/ai/cross-sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("CACHE")))
                .andExpect(jsonPath("$.suggestions", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void testCrossSellEndpointReturns200WithRuleFallbackWhenCircuitBreakerOpen() throws Exception {
        aiCrossSellService.setCircuitBreakerOpen(true);

        String requestJson = """
                {
                    "sku_ids": ["TV-LG-OLED65"],
                    "store_id": "ST-DN-01"
                }
                """;

        mockMvc.perform(post("/api/v1/ai/cross-sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source", is("RULE_FALLBACK")))
                .andExpect(jsonPath("$.suggestions", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.suggestions[0].reason", org.hamcrest.Matchers.containsString("Luật dự phòng")));
    }

    @Test
    void testStatusEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.active_provider", is("OPENAI")))
                .andExpect(jsonPath("$.timeout_limit_ms", is(2500)));
    }

    @Test
    void testSimulateGeminiAdapterReturns200() throws Exception {
        String requestJson = """
                {
                    "sku_ids": ["AC-DAIK-12000"],
                    "store_id": "ST-HCM-01"
                }
                """;

        mockMvc.perform(post("/api/v1/ai/simulate?provider=GEMINI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider_tested", is("GEMINI")))
                .andExpect(jsonPath("$.result.source", is("AI")))
                .andExpect(jsonPath("$.call_log.provider", is("GEMINI")))
                .andExpect(jsonPath("$.call_log.modelVersion", is("gemini-2.5-flash")))
                .andExpect(jsonPath("$.call_log.success", is(true)));
    }
}
