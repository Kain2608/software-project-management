# Sprint 1 — Phân hệ AI / Hồ sơ Phần mềm Tăng trưởng của Nguyễn Tiến Vũ

| Thuộc tính | Nội dung |
| --- | --- |
| Phụ trách | **Nguyễn Tiến Vũ — Data / AI Lead** |
| Phối hợp kỹ thuật | **Trần Trung Kiên** (Architect / DBA), **Vũ Duy Thái** (Fullstack Lead), **Nguyễn Quang Huy** (Product Owner / BA) |
| Căn cứ phân công | [Workbook Sprint 1](../03%20-%20Sprint%201%20(27-09%20đến%2010-10)%20-%20Phân%20chia%20công%20việc.xlsx), sheet “Phân chia công việc” dòng 15 (`S1-13`); sheet “Theo người” dòng 38 |
| Nhiệm vụ chính | **S1-13 — Dựng khung dịch vụ AI để Sprint 2 tích hợp được ngay** |
| Cột mốc nghiệm thu | **Sprint 1 Review / Cổng Quality Gate M2** (10/10/2026) |
| Thư mục bàn giao | `Sprint 1/AI` |

---

## 1. Danh mục Sản phẩm Bàn giao Sprint 1

| Mã tài liệu / Mã việc | Tên tài liệu / Sản phẩm | Định dạng | Trạng thái | Vị trí lưu trữ |
| --- | --- | --- | :---: | --- |
| **DS1** / `S1-13` | **Phần mềm tăng trưởng Sprint 1: Khung dịch vụ AI** | Word (.docx) | **HOÀN THÀNH** | [DS1_Phan_mem_tang_truong_AI_Sprint1.docx](DS1_Phan_mem_tang_truong_AI_Sprint1.docx) |
| **DS1** / `S1-13` | **Phần mềm tăng trưởng Sprint 1: Khung dịch vụ AI** | Markdown | **HOÀN THÀNH** | [DS1-phan-mem-tang-truong-ai-sprint1.md](DS1-phan-mem-tang-truong-ai-sprint1.md) |
| **Source Code** | Khung mã nguồn AI Gateway (`com.smartelectronicsupermarket.ai`) | Java (Spring Boot) | **HOÀN THÀNH** | `backend/src/main/java/com/smartelectronicsupermarket/ai/` |
| **Evidence 1** | Endpoint kiểm tra trả 200 (5 Test Cases Trace) | LOG | **HOÀN THÀNH** | [evidence/ai-service-endpoint-200.log](evidence/ai-service-endpoint-200.log) |
| **Evidence 2** | Nhật ký gọi thử OpenAI (gpt-4o-mini) & Gemini (gemini-2.5-flash) | LOG | **HOÀN THÀNH** | [evidence/ai-openai-gemini-call.log](evidence/ai-openai-gemini-call.log) |
| **Evidence 3** | Báo cáo kiểm thử tự động JSON & SLA Verification | JSON | **HOÀN THÀNH** | [evidence/ai-sprint1-demo-summary.json](evidence/ai-sprint1-demo-summary.json) |
| **Automated Test** | Bộ kiểm thử MockMvc Controller và Service | Java Test | **HOÀN THÀNH** | `backend/src/test/java/com/smartelectronicsupermarket/ai/AiCrossSellControllerTest.java` |

---

## 2. Tóm tắt Kết quả Thực hiện S1-13 (DS1)

1. **Khung dịch vụ AI hoạt động thực tế:**
   - Hoàn thành module AI Gateway tại `com.smartelectronicsupermarket.ai` tích hợp vào Spring Boot backend.
   - Endpoint `POST /api/v1/ai/cross-sell` đáp ứng 100% hợp đồng OpenAPI **D52**.
   - Hỗ trợ đầy đủ 3 nguồn dữ liệu: `AI` (LLM Inference), `CACHE` (Memory Cache TTL 5m) và `RULE_FALLBACK` (Ma trận quy tắc tương thích điện máy theo **D55**).
2. **Tích hợp Đa mô hình OpenAI & Gemini:**
   - `OpenAiAdapter`: Kết nối mô hình `gpt-4o-mini-2024-07-18`, độ trễ trung bình 684 ms.
   - `GeminiAdapter`: Kết nối mô hình `gemini-2.5-flash`, độ trễ trung bình 512 ms.
3. **Minh chứng nghiệm thu đầy đủ:**
   - 100% endpoint kiểm tra trả mã HTTP 200 OK.
   - Log gọi thử chi tiết của cả OpenAI và Gemini được lưu vết rõ ràng.
   - 5/5 test case tự động Maven chạy thành công không có lỗi (`BUILD SUCCESS`).
   - Mẫu minh chứng đính kèm: Kịch bản mô tả ảnh Demo UI POS, thông tin Pull Request đã merge và Biên bản Review ký bởi Product Owner Nguyễn Quang Huy.
