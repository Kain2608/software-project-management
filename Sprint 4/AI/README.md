# Sprint 4 — Phân hệ AI / BI — Hồ sơ của Nguyễn Tiến Vũ (Data / AI Lead)

| Thuộc tính | Nội dung |
| --- | --- |
| Phụ trách | **Nguyễn Tiến Vũ — Data / AI Lead** (hỗ trợ: Trần Trung Kiên) |
| Căn cứ phân công | [Workbook Sprint 4](../06%20-%20Sprint%204%20(08-11%20đến%2020-11)%20-%20Phân%20chia%20công%20việc.xlsx), dòng `S4-03`; hỗ trợ `S4-06`, `S4-07`, `S4-11` |
| Nhiệm vụ chính | **S4-03 — TSK-04-02 BI Analytics Dashboard doanh thu, kho, hiệu quả AI** |
| Thư mục bàn giao | `Sprint 4/AI` |

## Danh mục sản phẩm bàn giao

| Tài liệu / tệp | Định dạng | Trạng thái |
| --- | :---: | :---: |
| **DS4 Phần mềm tăng trưởng Sprint 4 — BI Dashboard** — [md](DS4-phan-mem-tang-truong-ai-sprint4.md), [docx](DS4_Phan_mem_tang_truong_AI_Sprint4.docx) | Markdown + Word | **CÓ — chờ ảnh Dashboard, PR merge, PO ký** |
| Đối soát số liệu trên PostgreSQL thật (35 phép kiểm) — [ds4-ai-dashboard-reconciliation.json](evidence/ds4-ai-dashboard-reconciliation.json) | JSON | **CÓ** (PostgreSQL 18.7) |
| Hồi quy bán kèm sau thay đổi engine — [benchmark](evidence/ds4-ai-crosssell-latency-benchmark.json), [kịch bản](evidence/ds4-ai-crosssell-demo-scenarios.json), [log](evidence/ds4-ai-crosssell-source.log) | JSON / Log | **CÓ** (LLM mô phỏng) |
| Tổng hợp kiểm thử — [ds4-ai-engine-summary.json](evidence/ds4-ai-engine-summary.json) | JSON | **CÓ** |
| **Ảnh Dashboard** | Ảnh | **CHƯA CÓ** (cần backend + frontend + flag bật) |

## Mã nguồn và tự động hóa

- Backend: `ai/data/AiRecommendationRecorder`, `JdbcAiRecommendationRecorder`, `AiCrossSellService.recordAsync`; `reporting/**` (`AI_SQL`, `AiEffectivenessRecord`, `StoreDashboardResponse.ai_effectiveness`); `ai/service/AiSingleFlight`; `database/runtime-roles.sql` (GRANT INSERT).
- Frontend: `frontend/src/features/dashboard/StoreDashboardPage.jsx`, `dashboardView.js` (tự làm mới 60 s).
- Đối soát PostgreSQL (DB cách ly `sasms_s4_*`/`sasms_ai_*` đã áp dụng `schema.sql` + migrations + `runtime-roles.sql`; mọi dữ liệu được ROLLBACK):
  ```powershell
  python scripts\validate_ai_sprint34.py --psql <psql.exe> --dsn "dbname=sasms_s4_x host=… user=…" --evidence-dir "Sprint 4\AI\evidence"
  ```
- Tổng hợp & Word:
  ```powershell
  backend\mvnw.cmd test
  python "Sprint 4\AI\scripts\generate_ds4_summary.py"
  python "Sprint 4\AI\scripts\convert_ds4_to_docx.py"
  ```

## Điều cần biết

Hệ thống chưa có sự kiện hiển thị/chấp nhận gợi ý, nên dashboard hiển thị **doanh thu có gợi ý AI (ước lượng quy gán)** và `N/A` cho tỷ lệ chấp nhận. Xem DS4 mục 4 và mục 11.
