package com.smartelectronicsupermarket.ai.summary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic stand-in for a real LLM (no network call). It reads the same prompt a real model would
 * receive and answers from the INPUT_SPECIFICATIONS block only. Replace with a real client behind
 * {@link SpecSummaryLlm}; the validator and fallbacks do not change.
 */
@Component
public class MockSpecSummaryLlm implements SpecSummaryLlm {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public String modelVersion() {
        return "mock-spec-v1";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, String> specs = readSpecs(userPrompt);
        if (specs.isEmpty()) {
            return "{\"status\":\"INSUFFICIENT_DATA\"}";
        }
        List<String> highlights = new ArrayList<>();
        for (Map.Entry<String, String> entry : specs.entrySet()) {
            if (highlights.size() >= 4) {
                break;
            }
            highlights.add(SpecRuleSummarizer.humanize(entry.getKey()) + ": " + entry.getValue());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "SUCCESS");
        out.put("highlights", highlights);
        out.put("target_audience", "Khách hàng cần thông số đúng như nhà sản xuất công bố");
        return JSON.writeValueAsString(out);
    }

    private static Map<String, String> readSpecs(String userPrompt) {
        int start = userPrompt.indexOf(SpecSummaryPrompts.SPECS_OPEN);
        int end = userPrompt.indexOf(SpecSummaryPrompts.SPECS_CLOSE);
        if (start < 0 || end < start) {
            return Map.of();
        }
        String json = userPrompt.substring(start + SpecSummaryPrompts.SPECS_OPEN.length(), end);
        Map<String, String> specs = new LinkedHashMap<>();
        for (Map.Entry<String, tools.jackson.databind.JsonNode> e : JSON.readTree(json).properties()) {
            specs.put(e.getKey(), e.getValue().asString());
        }
        return specs;
    }
}
