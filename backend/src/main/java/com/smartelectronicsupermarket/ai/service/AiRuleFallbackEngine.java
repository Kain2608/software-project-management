package com.smartelectronicsupermarket.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-DEF-CASE".getBytes()).toString(),
                        "ACC-DEF-CASE",
                        "Ốp lưng bảo vệ tiêu chuẩn (Luật dự phòng)",
                        new BigDecimal("250000.00"),
                        20,
                        false,
                        "Ốp lưng tiêu chuẩn theo danh mục bảo vệ (Luật dự phòng SASMS)"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-DEF-CBL".getBytes()).toString(),
                        "ACC-DEF-CBL",
                        "Cáp sạc Type-C tiêu chuẩn (Luật dự phòng)",
                        new BigDecimal("180000.00"),
                        35,
                        false,
                        "Cáp sạc Type-C tiêu chuẩn (Luật dự phòng SASMS)"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("SRV-WAR-12M".getBytes()).toString(),
                        "SRV-WAR-12M",
                        "Gói bảo hành tiêu chuẩn 12 tháng tại cửa hàng",
                        new BigDecimal("800000.00"),
                        999,
                        false,
                        "Gói bảo hành tiêu chuẩn 12 tháng tại cửa hàng"
                ));
            } else if (upper.contains("TV") || upper.contains("OLED") || upper.contains("QLED")) {
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-DEF-MNT".getBytes()).toString(),
                        "ACC-DEF-MNT",
                        "Khung treo tường tivi chịu lực (Luật dự phòng)",
                        new BigDecimal("450000.00"),
                        15,
                        false,
                        "Khung treo tường tivi chịu lực (Luật dự phòng SASMS)"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-DEF-HDMI".getBytes()).toString(),
                        "ACC-DEF-HDMI",
                        "Cáp HDMI kết nối chuẩn (Luật dự phòng)",
                        new BigDecimal("250000.00"),
                        20,
                        false,
                        "Cáp HDMI kết nối chuẩn (Luật dự phòng SASMS)"
                ));
            } else {
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-DEF-GENERAL".getBytes()).toString(),
                        "ACC-DEF-GENERAL",
                        "Phụ kiện cơ bản cho thiết bị (Luật dự phòng)",
                        new BigDecimal("150000.00"),
                        25,
                        false,
                        "Phụ kiện cơ bản cho thiết bị (Luật dự phòng SASMS)"
                ));
            }
        }

        if (items.isEmpty()) {
            items.add(new CrossSellItem(
                    UUID.nameUUIDFromBytes("SRV-DEF-WARRANTY".getBytes()).toString(),
                    "SRV-DEF-WARRANTY",
                    "Gói bảo hành mở rộng chính hãng",
                    new BigDecimal("500000.00"),
                    999,
                    false,
                    "Gói bảo hành tiêu chuẩn chính hãng"
            ));
        }

        return items;
    }
}
