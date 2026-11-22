# Sprint 3 — Phân hệ AI / Hồ sơ của Nguyễn Tiến Vũ (Data / AI Lead)

| Thuộc tính | Nội dung |
| --- | --- |
| Phụ trách | **Nguyễn Tiến Vũ — Data / AI Lead** (hỗ trợ: Nguyễn Quang Huy) |
| Căn cứ phân công | [Workbook Sprint 3](../05%20-%20Sprint%203%20(25-10%20đến%2007-11)%20-%20Phân%20chia%20công%20việc.xlsx), dòng `S3-03` |
| Nhiệm vụ chính | **S3-03 — TSK-03-01 Prompt AI tóm tắt thông số sản phẩm (RAG chống bịa)** |
| Thư mục bàn giao | `Sprint 3/AI` |

## Danh mục sản phẩm bàn giao

| Tài liệu / tệp | Định dạng | Trạng thái |
| --- | :---: | :---: |
| **DS3 Phần mềm tăng trưởng Sprint 3 — AI tóm tắt thông số** — [md](DS3-phan-mem-tang-truong-ai-sprint3.md), [docx](DS3_Phan_mem_tang_truong_AI_Sprint3.docx) | Markdown + Word | **CÓ — chờ video demo, PR merge, PO ký** |
| 6 mẫu tóm tắt theo ngành hàng (khớp thông số + cảnh báo tham khảo) — [ds3-ai-summary-samples.json](evidence/ds3-ai-summary-samples.json) | JSON | **CÓ** (fixture + LLM mô phỏng) |
| 11 kịch bản chống bịa / lỗi / phân quyền — [ds3-ai-summary-scenarios.json](evidence/ds3-ai-summary-scenarios.json) | JSON | **CÓ** (11/11 đạt) |
| Độ trễ 100 request / 50 đồng thời — [ds3-ai-summary-latency-benchmark.json](evidence/ds3-ai-summary-latency-benchmark.json) | JSON | **CÓ** (LLM mô phỏng) |
| Log `[AI-AUDIT]` — [ds3-ai-summary-source.log](evidence/ds3-ai-summary-source.log) | Log | **CÓ** |
| Tổng hợp kiểm thử từ surefire — [ds3-ai-engine-summary.json](evidence/ds3-ai-engine-summary.json) | JSON | **CÓ** |

## Mã nguồn và tự động hóa

- Gói `backend/src/main/java/com/smartelectronicsupermarket/ai/summary` + `AiSpecSummaryController`; `AiSingleFlight` (dùng chung với bán kèm).
- Test: `SpecSummaryValidatorTest` (10), `AiSpecSummaryServiceTest` (17), `AiSpecSummaryControllerTest` (4), `AiSingleFlightTest` (4), `AiSprint3EvidenceTest` (sinh minh chứng).
- Sinh lại minh chứng (từ thư mục gốc repo):
  ```powershell
  $env:SASMS_AI_EVIDENCE_DIR_S3 = "$PWD\Sprint 3\AI\evidence"
  backend\mvnw.cmd test
  python "Sprint 3\AI\scripts\generate_ds3_summary.py"
  python "Sprint 3\AI\scripts\convert_ds3_to_docx.py"
  ```

## Điều cần biết

LLM là bộ mô phỏng xác định; tỷ lệ "bịa" của mô hình thật chưa đo. Xem DS3 mục 9 (giới hạn) và mục 10 (trạng thái minh chứng thực tế).
