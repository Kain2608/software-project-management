package com.smartelectronicsupermarket.ai.service;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.smartelectronicsupermarket.ai.dto.CrossSellItem;

@Component
public class AiRuleFallbackEngine {

    private static final Logger log = LoggerFactory.getLogger(AiRuleFallbackEngine.class);

    public List<CrossSellItem> generateFallbackCrossSell(List<String> skuIds, String storeId) {
        log.warn("[RuleFallback] Executing deterministic rule-based fallback for SKUs: {}, store: {}", skuIds, storeId);

        List<CrossSellItem> items = new ArrayList<>();
        for (String sku : skuIds) {
            String upper = sku.toUpperCase();
            if (upper.contains("IP") || upper.contains("PHONE") || upper.contains("SS") || upper.contains("SMART")) {
                items.add(new CrossSellItem("ACC-DEF-CASE", "Ốp lưng tiêu chuẩn theo danh mục bảo vệ (Luật dự phòng SASMS)"));
                items.add(new CrossSellItem("ACC-DEF-CBL", "Cáp sạc Type-C tiêu chuẩn (Luật dự phòng SASMS)"));
                items.add(new CrossSellItem("SRV-WAR-12M", "Gói bảo hành tiêu chuẩn 12 tháng tại cửa hàng"));
            } else if (upper.contains("LAP") || upper.contains("MAC") || upper.contains("DELL") || upper.contains("NB")) {
                items.add(new CrossSellItem("ACC-DEF-BAG", "Túi chống sốc laptop cơ bản (Luật dự phòng SASMS)"));
                items.add(new CrossSellItem("ACC-DEF-MOU", "Chuột có dây cổng USB tiện dụng (Luật dự phòng SASMS)"));
            } else if (upper.contains("TV") || upper.contains("OLED") || upper.contains("QLED")) {
                items.add(new CrossSellItem("ACC-DEF-MNT", "Khung treo tường tivi chịu lực (Luật dự phòng SASMS)"));
                items.add(new CrossSellItem("ACC-DEF-HDMI", "Cáp HDMI kết nối chuẩn (Luật dự phòng SASMS)"));
            } else if (upper.contains("WM") || upper.contains("WASH")) {
                items.add(new CrossSellItem("ACC-DEF-PAD", "Chân đế cao su máy giặt (Luật dự phòng SASMS)"));
            } else if (upper.contains("AC") || upper.contains("AIR")) {
                items.add(new CrossSellItem("ACC-DEF-CB", "Aptomat bảo vệ nguồn điện (Luật dự phòng SASMS)"));
            } else {
                items.add(new CrossSellItem("ACC-DEF-GENERAL", "Phụ kiện cơ bản cho thiết bị (Luật dự phòng SASMS)"));
            }
        }

        if (items.isEmpty()) {
            items.add(new CrossSellItem("SRV-DEF-WARRANTY", "Gói bảo hành tiêu chuẩn chính hãng"));
        }

        return items;
    }
}
