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
| **D52 OpenAPI Contract (YAML)** | YAML (OpenAPI 3.0.3) | **HOÀN THÀNH** | [D52_AI_POS_INTEGRATION_CONTRACT.yaml](D52_AI_POS_INTEGRATION_CONTRACT.yaml) |
| **D52 Hợp đồng Tích hợp AI - POS (Word)** | Word (.docx) | **HOÀN THÀNH** | [D52_Hop_dong_tich_hop_AI_POS_Sprint2.docx](D52_Hop_dong_tich_hop_AI_POS_Sprint2.docx) |
| **D52 Hợp đồng Tích hợp AI - POS (Markdown)** | Markdown | **HOÀN THÀNH** | [D52-hop-dong-tich-hop-ai-pos-sprint2.md](D52-hop-dong-tich-hop-ai-pos-sprint2.md) |

---

## 2. Điểm Nhấn Kỹ thuật Đã Thống nhất với Backend
1. **Chuẩn hóa Endpoint `POST /api/v1/ai/cross-sell`:** Đầy đủ thông tin `product_id`, `sku`, `name`, `list_price`, `available_quantity`, `is_serialized`, `reason`, `source`.
2. **Quy tắc phân định `is_serialized`:** Phụ kiện bán theo số lượng (`is_serialized = false`), thiết bị máy chính bắt buộc quét Serial (`is_serialized = true`) mà không cần sửa cấu trúc CSDL hiện hữu.
3. **Phân quyền chặt chẽ:** Kiểm tra quyền `ai.use` và phạm vi `store_id` qua token Bearer JWT.
4. **Xác thực Giá & Tồn kho:** Giá lấy từ `products.list_price`, tồn kho lấy từ `stock_availability.available` của đúng `warehouse_id`.
