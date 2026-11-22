package com.smartelectronicsupermarket.ai.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecSummaryValidatorTest {

    private static final Map<String, String> PHONE = specs(
            "screen_inches", "6.1", "display", "OLED", "ram_gb", "8", "storage", "128GB", "chip", "A18",
            "battery", "Pin 3561 mAh");

    private static Map<String, String> specs(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static String json(String highlightsJson, String audience) {
        return "{\"status\":\"SUCCESS\",\"highlights\":" + highlightsJson + ",\"target_audience\":\""
                + audience + "\"}";
    }

    @Test
    void groundedHighlightsAreAccepted() {
        var result = SpecSummaryValidator.validate(
                json("[\"Màn hình OLED 6.1 inch\",\"RAM 8 GB, bộ nhớ 128GB\"]", "Người dùng cần máy gọn"), PHONE);
        assertNotNull(result);
        assertEquals(2, result.highlights().size());
        assertEquals(0, result.rejected().size());
        assertEquals("Người dùng cần máy gọn", result.targetAudience());
    }

    @Test
    void numberNotInSpecsIsRejected() {
        var result = SpecSummaryValidator.validate(
                json("[\"Màn hình OLED 6.1 inch\",\"Pin 5000 mAh dùng 2 ngày\"]", "x"), PHONE);
        assertNotNull(result);
        assertEquals(List.of("Màn hình OLED 6.1 inch"), result.highlights());
        assertEquals(List.of("Pin 5000 mAh dùng 2 ngày"), result.rejected());
    }

    @Test
    void acronymNotInSpecsIsRejectedEvenWithoutDigits() {
        var result = SpecSummaryValidator.validate(
                json("[\"Chip A18 mạnh mẽ\",\"Màn hình OLED có Face ID\",\"Hỗ trợ 5G và NFC\"]", "x"), PHONE);
        assertNotNull(result);
        assertEquals(List.of("Chip A18 mạnh mẽ"), result.highlights());
        assertEquals(2, result.rejected().size());
    }

    @Test
    void highlightQuotingNothingFromSpecsIsRejected() {
        assertNull(SpecSummaryValidator.validate(
                json("[\"Sản phẩm tuyệt vời cho mọi nhà\"]", "x"), PHONE));
    }

    @Test
    void numberFormattingDifferencesStillMatch() {
        Map<String, String> tv = specs("screen_inches", "55", "refresh_hz", "120");
        var result = SpecSummaryValidator.validate(
                json("[\"Màn hình 55,0 inch, 120 Hz\"]", "x"), tv);
        assertNotNull(result);
        assertEquals(1, result.highlights().size());
    }

    @Test
    void diacriticsAreFoldedWhenAnchoring() {
        Map<String, String> fridge = specs("công_suất", "150 W", "dung_tích", "320 lít");
        var result = SpecSummaryValidator.validate(json("[\"Dung tich 320 lit\"]", "x"), fridge);
        assertNotNull(result);
    }

    @Test
    void tooLongHighlightIsRejected() {
        String longText = "Màn hình OLED " + "rất ".repeat(25) + "đẹp";
        assertNull(SpecSummaryValidator.validate(json("[\"" + longText + "\"]", "x"), PHONE));
    }

    @Test
    void unusableModelOutputReturnsNull() {
        assertNull(SpecSummaryValidator.validate("not json at all", PHONE));
        assertNull(SpecSummaryValidator.validate("[1,2,3]", PHONE));
        assertNull(SpecSummaryValidator.validate("{\"status\":\"INSUFFICIENT_DATA\"}", PHONE));
        assertNull(SpecSummaryValidator.validate("{\"status\":\"SUCCESS\"}", PHONE));
        assertNull(SpecSummaryValidator.validate("{\"status\":\"SUCCESS\",\"highlights\":\"text\"}", PHONE));
        assertNull(SpecSummaryValidator.validate("", PHONE));
        assertNull(SpecSummaryValidator.validate(null, PHONE));
        assertNull(SpecSummaryValidator.validate(json("[\"RAM 8\"]", "x"), Map.of()));
    }

    @Test
    void ungroundedAudienceIsDroppedButHighlightsSurvive() {
        var result = SpecSummaryValidator.validate(
                json("[\"Chip A18\"]", "Game thủ chuyên nghiệp dùng 24 giờ liên tục"), PHONE);
        assertNotNull(result);
        assertNull(result.targetAudience());
    }

    @Test
    void atMostFiveHighlightsAreKept() {
        var result = SpecSummaryValidator.validate(json(
                "[\"Chip A18\",\"RAM 8\",\"OLED\",\"Storage 128GB\",\"Display OLED\",\"Screen 6.1\",\"Chip A18 lần 2\"]",
                "x"), PHONE);
        assertNotNull(result);
        assertEquals(5, result.highlights().size());
    }
}
