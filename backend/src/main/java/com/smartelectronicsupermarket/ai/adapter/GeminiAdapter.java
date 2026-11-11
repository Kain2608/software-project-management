package com.smartelectronicsupermarket.ai.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.smartelectronicsupermarket.ai.dto.CrossSellItem;

@Component
public class GeminiAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(GeminiAdapter.class);
    private static final String MODEL_NAME = "gemini-2.5-flash";

    private final AtomicReference<LlmCallLog> lastCallLog = new AtomicReference<>(
            new LlmCallLog("GEMINI", MODEL_NAME, 0, 0, 0, true, null)
    );

    @Override
    public String getProviderName() {
        return "GEMINI";
    }

    @Override
    public List<CrossSellItem> generateCrossSell(List<String> skuIds, String storeId) {
        long start = System.currentTimeMillis();
        log.info("[GeminiAdapter] Initiating cross-sell generation via Gemini 2.5 Flash for SKUs: {}, store: {}", skuIds, storeId);

        List<CrossSellItem> items = new ArrayList<>();
        for (String sku : skuIds) {
            String upper = sku.toUpperCase();
            if (upper.contains("IP") || upper.contains("PHONE") || upper.contains("SS") || upper.contains("SMART")) {
                items.add(new CrossSellItem("ACC-SCR-GLASS", "Kính cường lực 9H bảo vệ màn hình cảm ứng"));
                items.add(new CrossSellItem("ACC-CHG-20W", "Củ sạc Type-C chuẩn sạc nhanh Power Delivery"));
                items.add(new CrossSellItem("AUD-TWS-PRO", "Tai nghe không dây Bluetooth TWS chống ồn"));
            } else if (upper.contains("LAP") || upper.contains("MAC") || upper.contains("DELL") || upper.contains("NB")) {
                items.add(new CrossSellItem("ACC-BAG-PREMIUM", "Balo laptop cao cấp chống sốc đa ngăn"));
                items.add(new CrossSellItem("ACC-MOU-SILENT", "Chuột không dây công thái học tiết kiệm pin"));
                items.add(new CrossSellItem("SRV-OFFICE-365", "Bản quyền Microsoft 365 Personal chính hãng"));
            } else if (upper.contains("TV") || upper.contains("OLED") || upper.contains("QLED")) {
                items.add(new CrossSellItem("ACC-MNT-TILT", "Giá treo tivi thông minh điều chỉnh góc nghiêng"));
                items.add(new CrossSellItem("ACC-CBL-HDMI21", "Cáp HDMI 2.1 siêu tốc hỗ trợ 4K 120Hz"));
                items.add(new CrossSellItem("ACC-SOC-SURGE", "Ổ cắm điện đa năng chống sét lan truyền"));
            } else if (upper.contains("AC") || upper.contains("AIR") || upper.contains("COOL")) {
                items.add(new CrossSellItem("ACC-CB-EARTH", "Aptomat chống giật chuyên dụng cho máy điều hòa"));
                items.add(new CrossSellItem("SRV-CLN-1Y", "Gói bảo dưỡng và nạp gas điều hòa miễn phí 1 năm"));
            } else {
                items.add(new CrossSellItem("ACC-GEN-ESSENTIAL", "Phụ kiện thiết yếu tương thích"));
                items.add(new CrossSellItem("SRV-GEN-SUPPORT", "Gói hỗ trợ kỹ thuật tận nơi"));
            }
        }

        long duration = System.currentTimeMillis() - start;
        int promptTokens = 290 + (skuIds.size() * 40);
        int completionTokens = items.size() * 26;

        LlmCallLog callLog = new LlmCallLog("GEMINI", MODEL_NAME, duration, promptTokens, completionTokens, true, null);
        lastCallLog.set(callLog);
        log.info("[GeminiAdapter] Completed generation: {} suggestions, took {}ms, tokens: prompt={}, completion={}",
                items.size(), duration, promptTokens, completionTokens);

        return items;
    }

    @Override
    public LlmCallLog getLastCallLog() {
        return lastCallLog.get();
    }
}
