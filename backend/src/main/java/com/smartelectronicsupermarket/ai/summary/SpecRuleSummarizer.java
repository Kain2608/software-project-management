package com.smartelectronicsupermarket.ai.summary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based fallback (D55 section 6.3): highlights are "field: value" pairs copied verbatim from the catalog,
 * so they cannot contain anything that is not in the source. Core fields come first.
 */
public final class SpecRuleSummarizer {

    static final int MAX_HIGHLIGHTS = 5;
    private static final List<String> PRIORITY = List.of(
            "screen", "display", "man_hinh", "resolution", "do_phan_giai", "chip", "cpu", "ram", "storage",
            "rom", "bo_nho", "battery", "pin", "camera", "capacity", "dung_tich", "power", "cong_suat",
            "inverter", "size", "inch");

    private SpecRuleSummarizer() {
    }

    public static List<String> highlights(Map<String, String> specs) {
        Set<String> chosen = new LinkedHashSet<>();
        for (String wanted : PRIORITY) {
            for (String key : specs.keySet()) {
                if (chosen.size() < MAX_HIGHLIGHTS && key.toLowerCase(Locale.ROOT).contains(wanted)) {
                    chosen.add(key);
                }
            }
        }
        for (String key : specs.keySet()) {
            if (chosen.size() < MAX_HIGHLIGHTS) {
                chosen.add(key);
            }
        }
        List<String> out = new ArrayList<>();
        for (String key : chosen) {
            out.add(humanize(key) + ": " + specs.get(key));
        }
        return out;
    }

    static String humanize(String key) {
        String spaced = key.replace('_', ' ').replace('.', ' ').strip();
        return spaced.isEmpty() ? key : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
