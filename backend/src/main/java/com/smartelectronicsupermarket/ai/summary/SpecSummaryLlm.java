package com.smartelectronicsupermarket.ai.summary;

/** Port to the language model. Returns the raw model text, which is untrusted until validated. */
public interface SpecSummaryLlm {

    String providerName();

    String modelVersion();

    String complete(String systemPrompt, String userPrompt);
}
