# Sprint 2 — Phân hệ AI / Hồ sơ Tích hợp POS & Khuyến nghị của Nguyễn Tiến Vũ

| Thuộc tính | Nội dung |
| --- | --- |
| Phụ trách | **Nguyễn Tiến Vũ — Data / AI Lead** |
| Phối hợp kỹ thuật | **Vũ Duy Thái** (Fullstack Lead), **Trần Trung Kiên** (Architect / DBA), **Nguyễn Quang Huy** (Product Owner / BA) |
| Căn cứ phân công | [Workbook Sprint 2](../04%20-%20Sprint%202%20(11-10%20đến%2024-10)%20-%20Phân%20chia%20công%20việc.xlsx), sheet “Phân chia công việc” dòng 7 (`S2-08`), dòng 8 (`S2-10`) |
| Nhiệm vụ chính | **S2-08 — TSK-02-07 AI Recommendation Engine (cross-selling, cache, luật dự phòng)** |
| Cột mốc nghiệm thu | **Quality Gate M4 – AI Cross-selling & Consultation Live (24/10/2026)** |
| Thư mục bàn giao | `Sprint 2/AI` |

---

## 1. Danh mục Sản phẩm Bàn giao Sprint 2

| Mã tài liệu / Tên tệp | Định dạng | Trạng thái | Vị trí lưu trữ |
| --- | :---: | :---: | --- |
| **D52 OpenAPI Contract (YAML)** | YAML (OpenAPI 3.0.3) | **HOÀN THÀNH** | [D52_AI_POS_INTEGRATION_CONTRACT.yaml](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/D52_AI_POS_INTEGRATION_CONTRACT.yaml) |
| **D52 Hợp đồng Tích hợp AI - POS (Word)** | Word (.docx) | **HOÀN THÀNH** | [D52_Hop_dong_tich_hop_AI_POS_Sprint2.docx](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/D52_Hop_dong_tich_hop_AI_POS_Sprint2.docx) |
| **D52 Hợp đồng Tích hợp AI - POS (Markdown)** | Markdown | **HOÀN THÀNH** | [D52-hop-dong-tich-hop-ai-pos-sprint2.md](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/D52-hop-dong-tich-hop-ai-pos-sprint2.md) |
| **Báo cáo Kiểm thử Hợp đồng API (Word)** | Word (.docx) | **HOÀN THÀNH** | [D52_Bao_cao_kiem_thu_hop_dong_API.docx](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/D52_Bao_cao_kiem_thu_hop_dong_API.docx) |
| **Báo cáo Kiểm thử Hợp đồng API (Markdown)** | Markdown | **HOÀN THÀNH** | [D52-bao-cao-kiem-thu-hop-dong-api.md](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/D52-bao-cao-kiem-thu-hop-dong-api.md) |
| **Minh chứng Chạy Test Hợp đồng API (Log)** | Text / Log | **HOÀN THÀNH** | [api-contract-test-execution.log](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/evidence/api-contract-test-execution.log) |
| **Bộ mẫu Request/Response 7 kịch bản (JSON)** | JSON | **HOÀN THÀNH** | [sample-request-response.json](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/evidence/sample-request-response.json) |
| **Tổng hợp Kết quả Kiểm thử (JSON Summary)** | JSON | **HOÀN THÀNH** | [api-contract-summary.json](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/evidence/api-contract-summary.json) |

---

## 2. Mã Nguồn & Tự Động Hóa Kiểm Thử
1. **Controller & Service Test Suite:**
   - [AiCrossSellControllerTest.java](file:///E:/QLDAPM/software-project-management/backend/src/test/java/com/smartelectronicsupermarket/ai/AiCrossSellControllerTest.java): Kiểm thử 7 kịch bản MockMvc tích hợp Spring Security JWT (100% PASS, 0 failures, 0 errors).
2. **Scripts Tự Động Hóa:**
   - [generate_contract_evidence.py](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/scripts/generate_contract_evidence.py): Script thực thi và xuất khẩu minh chứng kiểm thử kèm bộ mẫu JSON chuẩn.
   - [convert_contract_report_to_docx.py](file:///E:/QLDAPM/software-project-management/Sprint%202/AI/scripts/convert_contract_report_to_docx.py): Script biên dịch báo cáo Markdown sang Word theo chuẩn định dạng SASMS.

---

## 2. Điểm Nhấn Kỹ thuật Đã Thống nhất với Backend
1. **Chuẩn hóa Endpoint `POST /api/v1/ai/cross-sell`:** Đầy đủ thông tin `product_id`, `sku`, `name`, `list_price`, `available_quantity`, `is_serialized`, `reason`, `source`.
2. **Quy tắc phân định `is_serialized`:** Phụ kiện bán theo số lượng (`is_serialized = false`), thiết bị máy chính bắt buộc quét Serial (`is_serialized = true`) mà không cần sửa cấu trúc CSDL hiện hữu.
3. **Phân quyền chặt chẽ:** Kiểm tra quyền `ai.use` và phạm vi `store_id` qua token Bearer JWT.
4. **Xác thực Giá & Tồn kho:** Giá lấy từ `products.list_price`, tồn kho lấy từ `stock_availability.available` của đúng `warehouse_id`.
