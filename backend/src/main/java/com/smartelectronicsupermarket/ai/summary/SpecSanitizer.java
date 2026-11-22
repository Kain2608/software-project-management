package com.smartelectronicsupermarket.ai.summary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns catalog JSONB into a flat, bounded, control-character-free key/value map (D55 section 3.2
 * "sanitized_specs_json"). Everything the model or the UI sees about a product comes through here.
 */
public final class SpecSanitizer {

    static final int MAX_FIELDS = 30;
    static final int MAX_KEY_CHARS = 60;
    static final int MAX_VALUE_CHARS = 200;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SpecSanitizer() {
    }

    public static Map<String, String> sanitize(String specificationsJson) {
        Map<String, String> out = new LinkedHashMap<>();
        if (specificationsJson == null || specificationsJson.isBlank()) {
            return out;
        }
        JsonNode root;
        try {
            root = JSON.readTree(specificationsJson);
        } catch (JacksonException e) {
            return out;
        }
        if (root == null || !root.isObject()) {
            return out;
        }
        flatten("", root, out);
        return out;
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (out.size() >= MAX_FIELDS) {
                return;
            }
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isObject()) {
                flatten(key, value, out);
            } else if (value.isArray()) {
                StringBuilder joined = new StringBuilder();
                for (JsonNode element : value) {
                    if (element != null && !element.isObject() && !element.isArray() && !element.isNull()) {
                        if (joined.length() > 0) {
                            joined.append(", ");
                        }
                        joined.append(element.asString());
                    }
                }
                put(out, key, joined.toString());
            } else {
                put(out, key, value.asString());
            }
        }
    }

    private static void put(Map<String, String> out, String key, String rawValue) {
        String cleanKey = clean(key, MAX_KEY_CHARS);
        String cleanValue = clean(rawValue, MAX_VALUE_CHARS);
        if (!cleanKey.isEmpty() && !cleanValue.isEmpty() && out.size() < MAX_FIELDS) {
            out.put(cleanKey, cleanValue);
        }
    }

    private static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        String stripped = text.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        return stripped.length() > max ? stripped.substring(0, max) : stripped;
    }

    /** D55 section 5.2: SHA-256 of the original specifications JSON; any catalog edit changes the cache key. */
    public static String fingerprint(String specificationsJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((specificationsJson == null ? "" : specificationsJson).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
