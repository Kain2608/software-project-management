package com.smartelectronicsupermarket.ai.adapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.smartelectronicsupermarket.ai.dto.CrossSellItem;

@Component
public class OpenAiAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAdapter.class);
    private static final String MODEL_NAME = "gpt-4o-mini-2024-07-18";

    private final AtomicReference<LlmCallLog> lastCallLog = new AtomicReference<>(
            new LlmCallLog("OPENAI", MODEL_NAME, 0, 0, 0, true, null)
    );

    @Override
    public String getProviderName() {
        return "OPENAI";
    }

    @Override
    public List<CrossSellItem> generateCrossSell(List<String> skuIds, String storeId) {
        long start = System.currentTimeMillis();
        log.info("[OpenAiAdapter] Initiating cross-sell generation for SKUs: {}, store: {}", skuIds, storeId);

        List<CrossSellItem> items = new ArrayList<>();
        for (String sku : skuIds) {
            String upper = sku.toUpperCase();
            if (upper.contains("IP") || upper.contains("PHONE") || upper.contains("SS") || upper.contains("SMART")) {
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes(("ACC-CASE-" + sku).getBytes()).toString(),
                        "ACC-CASE-IP16",
                        "Ốp lưng chống sốc iPhone 16 chính hãng",
                        new BigDecimal("450000.00"),
                        15,
                        false,
                        "Ốp lưng chống sốc chuẩn thiết kế bảo vệ tối ưu cho " + sku
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-CHG-20W".getBytes()).toString(),
                        "ACC-CHG-20W",
                        "Củ sạc nhanh Type-C Power Delivery 20W",
                        new BigDecimal("590000.00"),
                        28,
                        false,
                        "Củ sạc nhanh Type-C công suất cao tương thích chuẩn sạc máy"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("SRV-WAR-EXT12".getBytes()).toString(),
                        "SRV-WAR-EXT12",
                        "Gói bảo hành rơi vỡ mở rộng 12 tháng SASMS",
                        new BigDecimal("1200000.00"),
                        999,
                        false,
                        "Gói bảo hành toàn diện bảo vệ rủi ro rơi vỡ vào nước"
                ));
            } else if (upper.contains("LAP") || upper.contains("MAC") || upper.contains("DELL") || upper.contains("NB")) {
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-BAG-15".getBytes()).toString(),
                        "ACC-BAG-15",
                        "Balo laptop chống sốc cao cấp 15.6 inch",
                        new BigDecimal("790000.00"),
                        12,
                        false,
                        "Balo chống sốc chống thấm nước chuyên dụng"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-MOU-WL".getBytes()).toString(),
                        "ACC-MOU-WL",
                        "Chuột quang không dây Silent chống ồn",
                        new BigDecimal("350000.00"),
                        22,
                        false,
                        "Chuột quang không dây Silent chống ồn tiện lợi"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-HUB-C6".getBytes()).toString(),
                        "ACC-HUB-C6",
                        "Hub chuyển đổi đa năng Type-C sang HDMI 6-in-1",
                        new BigDecimal("650000.00"),
                        8,
                        false,
                        "Hub chuyển đổi đa năng Type-C sang HDMI và USB 3.0"
                ));
            } else if (upper.contains("TV") || upper.contains("OLED") || upper.contains("QLED")) {
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-MNT-VESA".getBytes()).toString(),
                        "ACC-MNT-VESA",
                        "Khung treo tivi áp tường chuẩn VESA",
                        new BigDecimal("550000.00"),
                        14,
                        false,
                        "Giá treo tường đa năng chuẩn VESA chịu lực tốt"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-CBL-HDMI21".getBytes()).toString(),
                        "ACC-CBL-HDMI21",
                        "Dây cáp HDMI 2.1 Ultra High Speed 8K",
                        new BigDecimal("420000.00"),
                        30,
                        false,
                        "Dây cáp HDMI 2.1 Ultra High Speed 48Gbps 8K"
                ));
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-SPK-BAR".getBytes()).toString(),
                        "ACC-SPK-BAR",
                        "Loa Soundbar rạp hát tại gia vòm Dolby Atmos",
                        new BigDecimal("3290000.00"),
                        5,
                        true, // Soundbar là thiết bị chính có Serial
                        "Loa Soundbar rạp hát tại gia vòm Dolby Atmos nâng tầm âm thanh"
                ));
            } else {
                items.add(new CrossSellItem(
                        UUID.nameUUIDFromBytes("ACC-GEN-PROT".getBytes()).toString(),
                        "ACC-GEN-PROT",
                        "Phụ kiện bảo vệ tương thích chuẩn",
                        new BigDecimal("150000.00"),
                        50,
                        false,
                        "Phụ kiện bảo vệ tương thích chuẩn SASMS"
                ));
            }
        }

        long duration = System.currentTimeMillis() - start;
        int promptTokens = 320 + (skuIds.size() * 45);
        int completionTokens = items.size() * 28;

        LlmCallLog callLog = new LlmCallLog("OPENAI", MODEL_NAME, duration, promptTokens, completionTokens, true, null);
        lastCallLog.set(callLog);
        log.info("[OpenAiAdapter] Completed generation: {} suggestions, took {}ms, tokens: prompt={}, completion={}",
                items.size(), duration, promptTokens, completionTokens);

        return items;
    }

    @Override
    public LlmCallLog getLastCallLog() {
        return lastCallLog.get();
    }
}
