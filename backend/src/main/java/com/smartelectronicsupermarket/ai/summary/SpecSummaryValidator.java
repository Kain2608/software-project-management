package com.smartelectronicsupermarket.ai.summary;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Post-generation validator (D55 section 3.3) and anti-fabrication check (AC-09-1).
 * Output of the model is accepted only per highlight: it must (1) be a short string, (2) contain no number that
 * is absent from the catalog specs, (3) contain no acronym/model token (5G, NFC, OLED, IP68...) that is absent
 * from the specs, and (4) quote at least one spec value or field name. Anything else is dropped.
 *
 * <p>Known limit: a paraphrased claim with no digits, no acronyms and no quoted spec text cannot be detected
 * mechanically; the prompt guardrail and the advisory disclaimer cover that residual risk.
 */
public final class SpecSummaryValidator {

    public static final int MAX_HIGHLIGHTS = 5;
    public static final int MAX_HIGHLIGHT_WORDS = 20;
    public static final int MAX_AUDIENCE_WORDS = 25;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern ACRONYM = Pattern.compile("\\b(?=[\\p{L}\\d]*\\d)[A-Za-z0-9]{2,}\\b|\\b[A-Z]{2,}[A-Za-z0-9]*\\b");

    public record Result(List<String> highlights, String targetAudience, List<String> rejected) {
    }

    private SpecSummaryValidator() {
    }

    /** @return the accepted subset, or null if the raw text is not a usable SUCCESS payload. */
    public static Result validate(String raw, Map<String, String> specs) {
        if (raw == null || raw.isBlank() || specs.isEmpty()) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(raw);
        } catch (JacksonException e) {
            return null;
        }
        if (root == null || !root.isObject() || !"SUCCESS".equals(text(root.get("status")))) {
            return null;
        }
        JsonNode highlightNodes = root.get("highlights");
        if (highlightNodes == null || !highlightNodes.isArray()) {
            return null;
        }
        Corpus corpus = new Corpus(specs);
        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (JsonNode node : highlightNodes) {
            String candidate = text(node);
            if (candidate == null) {
                continue;
            }
            candidate = candidate.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
            if (accepted.size() < MAX_HIGHLIGHTS && isGrounded(candidate, corpus, MAX_HIGHLIGHT_WORDS, true)) {
                if (!accepted.contains(candidate)) {
                    accepted.add(candidate);
                }
            } else {
                rejected.add(candidate);
            }
        }
        if (accepted.isEmpty()) {
            return null;
        }
        String audience = text(root.get("target_audience"));
        if (audience != null) {
            audience = audience.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
            if (!isGrounded(audience, corpus, MAX_AUDIENCE_WORDS, false)) {
                audience = null;
            }
        }
        return new Result(List.copyOf(accepted), audience, List.copyOf(rejected));
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    private static boolean isGrounded(String sentence, Corpus corpus, int maxWords, boolean requireAnchor) {
        if (sentence.isEmpty() || sentence.split(" ").length > maxWords) {
            return false;
        }
        Matcher numbers = NUMBER.matcher(sentence);
        while (numbers.find()) {
            if (!corpus.numbers.contains(canonicalNumber(numbers.group()))) {
                return false;
            }
        }
        Matcher acronyms = ACRONYM.matcher(sentence);
        while (acronyms.find()) {
            String token = fold(acronyms.group());
            if (!NUMBER.matcher(acronyms.group()).matches() && !corpus.text.contains(token)) {
                return false;
            }
        }
        if (!requireAnchor) {
            return true;
        }
        String folded = fold(sentence);
        for (String anchor : corpus.anchors) {
            if (folded.contains(anchor)) {
                return true;
            }
        }
        return false;
    }

    static String canonicalNumber(String number) {
        String dotted = number.replace(',', '.');
        if (dotted.contains(".")) {
            dotted = dotted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return dotted;
    }

    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    private static final class Corpus {
        final Set<String> numbers = new HashSet<>();
        final Set<String> anchors = new HashSet<>();
        final String text;

        Corpus(Map<String, String> specs) {
            StringBuilder all = new StringBuilder();
            for (Map.Entry<String, String> entry : specs.entrySet()) {
                String key = fold(entry.getKey().replace('_', ' ').replace('.', ' '));
                String value = fold(entry.getValue());
                all.append(key).append(' ').append(value).append(' ');
                Matcher m = NUMBER.matcher(entry.getKey() + " " + entry.getValue());
                while (m.find()) {
                    numbers.add(canonicalNumber(m.group()));
                }
                if (key.length() >= 3) {
                    anchors.add(key);
                }
                if (value.length() >= 2) {
                    anchors.add(value);
                }
            }
            this.text = all.toString();
        }
    }
}
