package com.smartelectronicsupermarket.ai.summary;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FR-09 response. {@code specifications} is always the raw catalog table so the UI can render it when AI is
 * unavailable (AC-09-3); {@code disclaimer} is always present (advisory only).
 */
public record SpecSummaryResult(
        @JsonProperty("status") String status,
        @JsonProperty("source") String source,
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("product_name") String productName,
        @JsonProperty("highlights") List<String> highlights,
        @JsonProperty("target_audience") String targetAudience,
        @JsonProperty("specifications") Map<String, String> specifications,
        @JsonProperty("notice") String notice,
        @JsonProperty("disclaimer") String disclaimer,
        @JsonProperty("took_ms") long tookMs) {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
    public static final String STATUS_SPEC_TABLE_ONLY = "SPEC_TABLE_ONLY";
    public static final String DISCLAIMER = "Nội dung do AI tóm tắt từ thông số trong hệ thống, chỉ mang tính tham khảo. "
            + "Vui lòng đối chiếu bảng thông số gốc.";
}
