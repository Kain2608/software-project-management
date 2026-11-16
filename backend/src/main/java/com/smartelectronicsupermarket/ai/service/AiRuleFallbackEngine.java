package com.smartelectronicsupermarket.ai.service;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.smartelectronicsupermarket.ai.dto.CrossSellCandidate;

/**
 * Deterministic rule engine used when the LLM times out or fails. It proposes SKUs only;
 * the service verifies them against the database like any AI candidate.
 */
@Component
public class AiRuleFallbackEngine {

    private static final Logger log = LoggerFactory.getLogger(AiRuleFallbackEngine.class);

    public List<CrossSellCandidate> generateFallbackCandidates(List<String> skuIds) {
        log.warn("[RuleFallback] Executing deterministic rule-based fallback for {} cart SKUs", skuIds.size());

        List<CrossSellCandidate> items = new ArrayList<>();
        for (String sku : skuIds) {
            String upper = sku.toUpperCase();
            if (upper.contains("IP") || upper.contains("PHONE") || upper.contains("SS") || upper.contains("SMART")) {
                items.add(candidate("ACC-DEF-CASE", "Ốp lưng tiêu chuẩn theo danh mục bảo vệ"));
                items.add(candidate("ACC-DEF-CBL", "Cáp sạc Type-C tiêu chuẩn"));
                items.add(candidate("SRV-WAR-12M", "Gói bảo hành tiêu chuẩn 12 tháng tại cửa hàng"));
            } else if (upper.contains("TV") || upper.contains("OLED") || upper.contains("QLED")) {
                items.add(candidate("ACC-DEF-MNT", "Khung treo tường tivi chịu lực"));
                items.add(candidate("ACC-DEF-HDMI", "Cáp HDMI kết nối chuẩn"));
            } else {
                items.add(candidate("ACC-DEF-GENERAL", "Phụ kiện cơ bản cho thiết bị"));
            }
        }

        if (items.isEmpty()) {
            items.add(candidate("SRV-DEF-WARRANTY", "Gói bảo hành tiêu chuẩn chính hãng"));
        }
        return items;
    }

    private static CrossSellCandidate candidate(String sku, String reason) {
        return new CrossSellCandidate(sku, reason + " (Luật dự phòng SASMS)");
    }
}
