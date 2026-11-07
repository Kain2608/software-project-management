# Sprint 0 — Phân hệ AI / Hồ sơ Thiết kế của Nguyễn Tiến Vũ

| Thuộc tính | Nội dung |
| --- | --- |
| Phụ trách | **Nguyễn Tiến Vũ — Data / AI Lead** |
| Hỗ trợ kỹ thuật | **Trần Trung Kiên** (Architect / DBA), **Vũ Duy Thái** (Fullstack Lead), **Nguyễn Quang Huy** (BA / PO) |
| Căn cứ phân công | [Workbook Sprint 0](../DB/inputs/phan-cong-sprint-0.xlsx), sheet “Phân chia công việc” dòng 12 (S0-22); sheet “Theo người” dòng 22 |
| Kỳ Sprint | Sprint 0: 12/09/2026 – 25/09/2026 |
| Cổng nghiệm thu | **Quality Gate M1** (25/09/2026) |
| Thư mục bàn giao | `Sprint 0/AI` |

---

## 1. Danh mục Sản phẩm Bàn giao Sprint 0

| Mã tài liệu / Mã việc | Tên tài liệu / Sản phẩm | Định dạng | Trạng thái | Vị trí lưu trữ |
| --- | --- | --- | :---: | --- |
| **D55** / `S0-22` | **Thiết kế AI: Prompt, RAG, Cache, Luật dự phòng** | Word (.docx) | **HOÀN THÀNH** | [D55_Thiet_ke_AI_SASMS.docx](D55_Thiet_ke_AI_SASMS.docx) |
| **D55** / `S0-22` | **Thiết kế AI: Prompt, RAG, Cache, Luật dự phòng** | Markdown | **HOÀN THÀNH** | [D55-thiet-ke-ai-prompt-rag-cache-fallback.md](D55-thiet-ke-ai-prompt-rag-cache-fallback.md) |
| **Evidence** / `S0-22` | Báo cáo Thống kê Độ trễ Thực nghiệm (Benchmark JSON) | JSON | **HOÀN THÀNH** | [evidence/ai-latency-benchmark.json](evidence/ai-latency-benchmark.json) |
| **Evidence** / `S0-22` | Nhật ký Gọi thử Chi tiết 100 Requests (Test Call Log) | LOG | **HOÀN THÀNH** | [evidence/ai-test-call-log.log](evidence/ai-test-call-log.log) |
| **Script** | Kịch bản Mô phỏng Kiểm thử Tải 50 Concurrency | Python | **HOÀN THÀNH** | [scripts/simulate_ai_benchmark.py](scripts/simulate_ai_benchmark.py) |

---

## 2. Tóm tắt Nội dung Thiết kế D55

Tài liệu **D55** đặc tả toàn diện 4 trụ cột công nghệ của Phân hệ AI cho hệ thống bán lẻ điện máy SASMS:

1. **Prompt Engineering:**
   - **FR-08:** AI Gợi ý bán kèm (Cross-sell/Up-sell) tại quầy POS và Storefront với System Prompt, User Prompt template và JSON Schema validation.
   - **FR-09:** AI Tóm tắt thông số kỹ thuật (Spec Summarizer) với nguyên tắc trung thực tuyệt đối với nguồn (AC-09-1), không bịa đặt tính năng.
   - **Post-generation Validator:** Kiểm tra sản phẩm gợi ý có thuộc danh sách candidate do backend cung cấp, còn `is_active`, lấy giá niêm yết từ `products.list_price` (AC-08-3), và có tồn kho khả dụng (`stock_availability.available > 0`).

2. **RAG Pipeline (Retrieval-Augmented Generation):**
   - **Hybrid Retrieval hai tầng:** Giai đoạn 1 lọc thuộc tính có cấu trúc (Category, Brand, Stock) bằng PostgreSQL index; Giai đoạn 2 xếp hạng ngữ nghĩa bằng Cosine Similarity vector thông số.
   - **Structured Chunking:** Phân mảnh thông số kỹ thuật theo nhóm logic (kết nối, kích thước, công suất, phân khúc).
   - **Token Budget:** Khống chế ngữ cảnh đầu vào < 1.300 tokens để giữ độ trễ thấp và tối ưu chi phí.

3. **Redis Caching Đa tầng:**
   - Kiến trúc Cache-Aside với Cache L1 (In-memory 60s) và L2 (Redis Cluster).
   - Chuẩn hóa Cache Key theo hợp đồng S0-19-22-23: `ai:crosssell:v1:<catalogFingerprint>:<rulesVersion>:<modelVersion>:<warehouseId>:<sourceProductId>:<locale>:<limit>`.
   - TTL: 5 phút cho gợi ý bán kèm, 24 giờ cho tóm tắt thông số kỹ thuật; hỗ trợ Event-driven Invalidation và Lazy Check tại quầy thanh toán.
   - Fail-Open Policy: Lỗi Redis không bao giờ làm gián đoạn luồng bán hàng.

4. **Động cơ Luật Dự phòng (Rule-based Fallback Engine) & Circuit Breaker:**
   - **Tuân thủ AC-08-2 & BR-09:** "AI lỗi không bao giờ chặn POS".
   - **Ma trận Bán kèm theo Ngành hàng Điện máy:** Xác lập quy tắc tương thích cứng cho Smartphone, Laptop, Tivi, Máy giặt, Tủ lạnh, Điều hòa.
   - **Circuit Breaker:** Ngưỡng timeout cứng **2.500 ms**; ngắt kết nối chuyển sang trạng thái OPEN khi tỷ lệ lỗi > 50% trong 20 requests; chuyển ngay sang Rule Fallback trong < 10 ms.

---

## 3. Minh chứng Đạt chuẩn Cổng Nghiệm thu M1

- **Chỉ tiêu NFR-03 (FRS_V2):** Độ trễ p95 < **3,0 giây** dưới tải **50 request đồng thời**.
- **Kết quả Thực nghiệm:**
  - **p50:** **646,28 ms**
  - **p90:** **2.509,34 ms**
  - **p95:** **2.510,79 ms** (Đạt chuẩn < 3.000 ms, pass SLA NFR-03).
  - **Max:** **2.514,88 ms** (Được chặn và cắt dứt điểm bởi Timeout 2.5s theo BR-09).
  - Tỷ lệ hoàn thành: 100/100 requests (100% không có lỗi crash, các ca timeout đều fallback thành công).
