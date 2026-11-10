# DS1 — Phần mềm Tăng trưởng Sprint 1: Khung Dịch vụ AI & Tích hợp Bán kèm

| Thuộc tính | Giá trị |
| --- | --- |
| Mã sản phẩm bàn giao | **DS1** |
| Tên tài liệu / sản phẩm | Phần mềm tăng trưởng Sprint 1 (Demo được) — Phân hệ AI: Dựng khung dịch vụ AI để Sprint 2 tích hợp được ngay |
| Mã công việc | **S1-13** (Giai đoạn 5. Lập trình — Sprint 1) |
| Người thực hiện chính | **Nguyễn Tiến Vũ — Data / AI Lead** |
| Phối hợp & Đánh giá | **Trần Trung Kiên** (Architect / DBA), **Vũ Duy Thái** (Fullstack Lead), **Nguyễn Quang Huy** (Product Owner / BA) |
| Căn cứ đầu vào | [D55 Thiết kế AI: prompt, RAG, cache, luật dự phòng](../../Sprint%200/AI/D55-thiet-ke-ai-prompt-rag-cache-fallback.md)<br>[D52 Đặc tả API (OpenAPI)](../../Sprint%200/Fullstack/TV4/docs/sprint0/d52/D52_OPENAPI_INVENTORY_POS_AI_DRAFT.yaml) |
| Kỳ Sprint | Sprint 1 (27/09/2026 – 10/10/2026) |
| Cột mốc nghiệm thu | **Sprint 1 Review / Cổng Quality Gate M2** (10/10/2026) |
| Trạng thái kỹ thuật | **ĐÃ HOÀN THÀNH — ĐÃ KIỂM THỬ ĐẠT CHUẨN (Demo sẵn sàng)** |
| Minh chứng cốt lõi đính kèm | 1. Endpoint kiểm tra trả 200: [`Sprint 1/AI/evidence/ai-service-endpoint-200.log`](evidence/ai-service-endpoint-200.log)<br>2. Log gọi thử OpenAI & Gemini: [`Sprint 1/AI/evidence/ai-openai-gemini-call.log`](evidence/ai-openai-gemini-call.log)<br>3. Báo cáo kiểm thử JSON: [`Sprint 1/AI/evidence/ai-sprint1-demo-summary.json`](evidence/ai-sprint1-demo-summary.json) |

---

## 1. Mục tiêu và Phạm vi Tăng trưởng Sprint 1 (S1-13)

Trong Sprint 1, nhiệm vụ trọng tâm của **AI Lead (Nguyễn Tiến Vũ)** là hiện thực hóa bản thiết kế **D55** thành khung dịch vụ phần mềm hoạt động thực tế (**DS1**), bám sát hợp đồng giao tiếp chuẩn OpenAPI đã định nghĩa trong **D52** (`/api/v1/ai/cross-sell`).

### Các mục tiêu cụ thể đạt được trong Sprint 1:
1. **Dựng khung mã nguồn AI Gateway (`com.smartelectronicsupermarket.ai`):** Xây dựng module dịch vụ AI hoàn chỉnh trong backend Spring Boot của dự án, bao gồm đầy đủ Controller, Service, Cache Manager, Fallback Engine và Lớp Adapter kết nối LLM.
2. **Hiện thực hóa Hợp đồng API D52:** Endpoint `POST /api/v1/ai/cross-sell` nhận danh sách SKU trong giỏ hàng, trả về danh sách phụ kiện gợi ý kèm trường nguồn dữ liệu chuẩn hóa (`source: "AI" | "CACHE" | "RULE_FALLBACK"`) và thời gian thực thi (`took_ms`).
3. **Tích hợp Đa Bộ chuyển đổi LLM (Multi-Provider Adapters):** Cung cấp sẵn adapter kết nối với **OpenAI (`gpt-4o-mini`)** và **Google Gemini (`gemini-2.5-flash`)**, hỗ trợ chuyển đổi linh hoạt và chế độ Sandbox cho môi trường Staging/Dev nội bộ.
4. **Cơ chế Caching & Bảo vệ Hệ thống (Resilience):** Cài đặt Cache In-memory với TTL 5 phút (theo D55) để đạt độ trễ phản hồi < 5ms; thiết lập Circuit Breaker và Timeout cưỡng bức **2.500 ms** (theo BR-09) nhằm kích hoạt Động cơ Luật dự phòng, đảm bảo tuyệt đối không làm gián đoạn thanh toán tại quầy POS.
5. **Cung cấp Đầy đủ Minh chứng Nghiệm thu:** Đáp ứng tiêu chí nghiệm thu của Workbook Sprint 1: *Endpoint kiểm tra trả 200; Log gọi thử OpenAI/Gemini; Kịch bản Demo, PR Merge và Biên bản Review ký bởi Product Owner*.

---

## 2. Kiến trúc Khung Dịch vụ AI (AI Service Skeleton Architecture)

Khung dịch vụ AI được tổ chức theo kiến trúc phân tầng chuẩn của Spring Boot, bảo đảm nguyên tắc Single Responsibility và Dependency Inversion:

```mermaid
flowchart TD
    subgraph ClientLayer ["Lớp Ứng dụng & Trình duyệt"]
        POSClient["Web POS / Storefront Client"]
    end

    subgraph ControllerLayer ["Lớp REST Controller"]
        AiController["AiCrossSellController (/api/v1/ai/*)"]
    end

    subgraph ServiceLayer ["Lớp Nghiệp vụ & Điều phối (AiCrossSellService)"]
        CacheMgr["AiCacheManager (MemoryCache - TTL 5m)"]
        CircuitBrk["Circuit Breaker (Ngưỡng 50% / Timeout 2.5s)"]
        RuleEngine["AiRuleFallbackEngine (Ma trận phụ kiện cứng D55)"]
    end

    subgraph AdapterLayer ["Lớp Bộ chuyển đổi Mô hình (LLM Adapters)"]
        OpenAiAdp["OpenAiAdapter (gpt-4o-mini)"]
        GeminiAdp["GeminiAdapter (gemini-2.5-flash)"]
    end

    subgraph ExternalServices ["Nhà cung cấp Trí tuệ Nhân tạo"]
        OpenAiCloud["OpenAI API Service"]
        GeminiCloud["Google Gemini API Service"]
    end

    POSClient -->|POST /api/v1/ai/cross-sell| AiController
    POSClient -->|GET /api/v1/ai/status| AiController
    POSClient -->|POST /api/v1/ai/simulate| AiController

    AiController --> CacheMgr
    CacheMgr -->|1. Cache Hit (< 5ms)| AiController

    CacheMgr -->|2. Cache Miss| CircuitBrk
    CircuitBrk -->|Circuit OPEN / Timeout > 2.5s| RuleEngine
    RuleEngine -->|Gán source='RULE_FALLBACK'| AiController

    CircuitBrk -->|Circuit CLOSED| OpenAiAdp
    CircuitBrk -->|Chuyển Provider| GeminiAdp

    OpenAiAdp -.->|REST HTTPS| OpenAiCloud
    GeminiAdp -.->|REST HTTPS| GeminiCloud

    OpenAiAdp -->|Thành công (source='AI')| CacheMgr
    GeminiAdp -->|Thành công (source='AI')| CacheMgr
```

### Danh mục Lớp Mã nguồn đã Đóng gói trong Sprint 1:

| Tên lớp Java | Gói (Package) | Trách nhiệm và Chức năng chính |
| --- | --- | --- |
| `AiCrossSellController` | `ai.controller` | Tiếp nhận HTTP request cho `/api/v1/ai/cross-sell`, `/api/v1/ai/status`, `/api/v1/ai/simulate`. |
| `AiCrossSellService` | `ai.service` | Bộ điều phối luồng: Kiểm tra Cache -> Gọi LLM Adapter -> Xử lý Timeout 2.5s -> Kích hoạt Fallback. |
| `AiCacheManager` | `ai.service` | Quản lý bộ nhớ đệm luồng an toàn (ConcurrentHashMap), quản lý TTL 5 phút, thống kê Hit/Miss. |
| `AiRuleFallbackEngine` | `ai.service` | Ma trận luật phụ kiện cứng của 6 ngành hàng điện máy, trả kết quả dự phòng tức thì khi AI gặp sự cố. |
| `LlmAdapter` (Interface) | `ai.adapter` | Giao diện chuẩn hóa cho các bộ chuyển đổi LLM ngoài. |
| `OpenAiAdapter` | `ai.adapter` | Hiện thực kết nối OpenAI API (`gpt-4o-mini-2024-07-18`), ghi log prompt/completion tokens. |
| `GeminiAdapter` | `ai.adapter` | Hiện thực kết nối Google Gemini API (`gemini-2.5-flash`), ghi log prompt/completion tokens. |
| `CrossSellRequest` | `ai.dto` | DTO tiếp nhận danh sách `sku_ids` và `store_id` (Khớp 100% schema D52). |
| `CrossSellResult` | `ai.dto` | DTO phản hồi chứa mảng `suggestions`, `source` (`AI`, `CACHE`, `RULE_FALLBACK`) và `took_ms`. |
| `CrossSellItem` | `ai.dto` | DTO chi tiết từng gợi ý gồm `sku_id` và `reason` giải thích lý do mua kèm. |
| `AiStatusResponse` | `ai.dto` | DTO báo cáo sức khỏe phân hệ AI, mô hình hoạt động và trạng thái Circuit Breaker. |

---

## 3. Đặc tả Giao diện API REST Triển khai (Khớp Chuẩn D52)

### 3.1. Endpoint Gợi ý Bán kèm: `POST /api/v1/ai/cross-sell`
- **Tác nhân gọi:** Thu ngân tại quầy Web POS hoặc Khách hàng trên trang Giỏ hàng Online.
- **Header:** `Content-Type: application/json`
- **Request Body mẫu:**
```json
{
  "sku_ids": ["PHONE-IP16-128"],
  "store_id": "ST-HCM-01"
}
```

- **Response Body mẫu (HTTP 200 OK — Nguồn `AI`):**
```json
{
  "suggestions": [
    {
      "sku_id": "ACC-CASE-PHONE-IP16-128",
      "reason": "Ốp lưng chống sốc chuẩn thiết kế cho PHONE-IP16-128"
    },
    {
      "sku_id": "ACC-CHG-20W",
      "reason": "Củ sạc nhanh Type-C công suất cao 20W"
    },
    {
      "sku_id": "SRV-WAR-EXT12",
      "reason": "Gói bảo hành rơi vỡ mở rộng 12 tháng tại SASMS"
    }
  ],
  "source": "AI",
  "took_ms": 684
}
```

- **Response Body mẫu khi Cache Hit (HTTP 200 OK — Nguồn `CACHE`):**
```json
{
  "suggestions": [
    {
      "sku_id": "ACC-CASE-PHONE-IP16-128",
      "reason": "Ốp lưng chống sốc chuẩn thiết kế cho PHONE-IP16-128"
    },
    {
      "sku_id": "ACC-CHG-20W",
      "reason": "Củ sạc nhanh Type-C công suất cao 20W"
    },
    {
      "sku_id": "SRV-WAR-EXT12",
      "reason": "Gói bảo hành rơi vỡ mở rộng 12 tháng tại SASMS"
    }
  ],
  "source": "CACHE",
  "took_ms": 2
}
```

---

### 3.2. Endpoint Kiểm tra Trạng thái Phân hệ: `GET /api/v1/ai/status`
- **Mục đích:** Health check và giám sát trạng thái mô hình hoạt động cho hệ thống BI/Admin Dashboard.
- **Response Body (HTTP 200 OK):**
```json
{
  "status": "UP",
  "service_name": "SASMS AI Gateway - Sprint 1 Increment (DS1)",
  "active_provider": "OPENAI",
  "cache_engine": "MemoryCache-5m-TTL",
  "circuit_breaker": "CLOSED",
  "timeout_limit_ms": 2500,
  "supported_categories": [
    "SMARTPHONE",
    "LAPTOP",
    "TELEVISION",
    "WASHING_MACHINE",
    "REFRIGERATOR",
    "AIR_CONDITIONER"
  ]
}
```

---

## 4. Chi tiết Bộ Chuyển đổi OpenAI & Gemini (LLM Adapters)

Để phục vụ bài toán so sánh chất lượng và dự phòng nhà cung cấp, hệ thống hỗ trợ song song 2 adapter với cấu hình tham số chuẩn hóa:

| Tiêu chí kỹ thuật | Bộ chuyển đổi OpenAI (`OpenAiAdapter`) | Bộ chuyển đổi Google Gemini (`GeminiAdapter`) |
| --- | --- | --- |
| **Mô hình mục tiêu** | `gpt-4o-mini-2024-07-18` | `gemini-2.5-flash` |
| **Giao thức kết nối** | REST API (`https://api.openai.com/v1/chat/completions`) | REST API (`https://generativelanguage.googleapis.com/...`) |
| **Cấu hình nhiệt độ (Temperature)** | `0.2` (Tập trung tính chính xác, chống ảo giác) | `0.2` (Tập trung tính tương thích kỹ thuật) |
| **Định dạng phản hồi** | `json_object` | `application/json` |
| **Thời gian phản hồi thực tế** | **684 ms** (trung bình) | **512 ms** (trung bình) |
| **Tiêu hao Token (Prompt / Output)** | 365 tokens / 84 tokens (Tổng: 449) | 330 tokens / 78 tokens (Tổng: 408) |
| **Độ ổn định & An toàn** | Tuân thủ 100% Guardrails chống PII và Injection | Tuân thủ 100% Guardrails chống PII và Injection |

---

## 5. Hướng dẫn Khởi chạy và Trình diễn Phần mềm (Demo Guide)

Khung dịch vụ AI trong Sprint 1 được thiết kế để có thể chạy và demo độc lập hoặc tích hợp trong toàn bộ hệ thống backend Spring Boot.

### 5.1. Khởi chạy Dịch vụ Backend
Tại thư mục gốc dự án:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
*Dịch vụ sẽ khởi động tại cổng mặc định `http://localhost:8080`.*

### 5.2. Các Lệnh Gọi Kiểm tra Trực tiếp (cURL / PowerShell)

#### Bước 1: Kiểm tra trạng thái AI Service (Health Check)
```powershell
curl -X GET http://localhost:8080/api/v1/ai/status
```
*Kết quả mong đợi: HTTP 200 OK, `status: "UP"`.*

#### Bước 2: Gọi gợi ý bán kèm lần đầu (Inference từ LLM)
```powershell
curl -X POST http://localhost:8080/api/v1/ai/cross-sell `
  -H "Content-Type: application/json" `
  -d '{"sku_ids": ["PHONE-IP16-128"], "store_id": "ST-HCM-01"}'
```
*Kết quả mong đợi: HTTP 200 OK, `source: "AI"`, thời gian thực thi ~680ms.*

#### Bước 3: Gọi lại chính xác sản phẩm đó (Kiểm tra Cache Hit)
```powershell
curl -X POST http://localhost:8080/api/v1/ai/cross-sell `
  -H "Content-Type: application/json" `
  -d '{"sku_ids": ["PHONE-IP16-128"], "store_id": "ST-HCM-01"}'
```
*Kết quả mong đợi: HTTP 200 OK, `source: "CACHE"`, thời gian thực thi < 5ms.*

#### Bước 4: Kiểm tra bộ chuyển đổi Google Gemini
```powershell
curl -X POST "http://localhost:8080/api/v1/ai/simulate?provider=GEMINI" `
  -H "Content-Type: application/json" `
  -d '{"sku_ids": ["TV-LG-OLED65"], "store_id": "ST-HN-02"}'
```
*Kết quả mong đợi: HTTP 200 OK, `provider_tested: "GEMINI"`, `modelVersion: "gemini-2.5-flash"`.*

---

## 6. Minh chứng Kỹ thuật Nghiệm thu (Technical Evidence)

Toàn bộ các tài liệu minh chứng yêu cầu của Sprint 1 đã được tạo và lưu trữ đầy đủ trong thư mục `Sprint 1/AI/evidence/`:

### 6.1. Minh chứng 1: Endpoint Kiểm tra Trả về 200 OK
Trích lục từ file nhật ký [`Sprint 1/AI/evidence/ai-service-endpoint-200.log`](evidence/ai-service-endpoint-200.log):
- **100% (5/5) kịch bản kiểm thử trả về mã trạng thái HTTP 200 OK**:
  - `TC-EP-01`: `GET /api/v1/ai/status` -> **200 OK** (1.8 ms).
  - `TC-EP-02`: `POST /api/v1/ai/cross-sell` (OpenAI) -> **200 OK** (684.5 ms, source: `AI`).
  - `TC-EP-03`: `POST /api/v1/ai/cross-sell` (Cache Hit) -> **200 OK** (2.4 ms, source: `CACHE`).
  - `TC-EP-04`: `POST /api/v1/ai/simulate` (Gemini) -> **200 OK** (520.1 ms, source: `AI`).
  - `TC-EP-05`: `POST /api/v1/ai/cross-sell` (Rule Fallback) -> **200 OK** (3.2 ms, source: `RULE_FALLBACK`).

### 6.2. Minh chứng 2: Nhật ký Gọi thử OpenAI & Gemini (Live Call Trace)
Trích lục từ file nhật ký [`Sprint 1/AI/evidence/ai-openai-gemini-call.log`](evidence/ai-openai-gemini-call.log):
- **Giao dịch OpenAI:** Gửi System Prompt chống PII và User Prompt sản phẩm `PHONE-IP16-128`; nhận phản hồi JSON chứa 3 SKU phụ kiện (`ACC-CASE`, `ACC-CHG-20W`, `SRV-WAR-EXT12`); tiêu thụ 365 prompt tokens, 84 completion tokens trong 684 ms.
- **Giao dịch Gemini:** Gửi System Instruction và User Prompt sản phẩm `TV-LG-OLED65`; nhận phản hồi JSON chứa giá treo nghiêng (`ACC-MNT-TILT`), cáp HDMI 2.1 (`ACC-CBL-HDMI21`) và ổ cắm chống sét (`ACC-SOC-SURGE`); tiêu thụ 330 prompt tokens, 78 completion tokens trong 512 ms.
- **Giao dịch Luật dự phòng:** Khi mô phỏng sự cố mạng vượt quá 2.500 ms, bộ đếm ngắt CompletableFuture kích hoạt chính xác tại mốc 2.500 ms và trả về danh sách phụ kiện từ `AiRuleFallbackEngine` trong 8 ms tiếp theo, đảm bảo luồng bán hàng không bị gián đoạn.

### 6.3. Minh chứng 3: Kết quả Thực thi Bộ Kiểm thử Tự động Maven
Kết quả chạy lệnh `.\mvnw.cmd test -Dtest=AiCrossSellControllerTest`:
```text
[INFO] Running com.smartelectronicsupermarket.ai.AiCrossSellControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.585 s
[INFO] BUILD SUCCESS
```

---

## 7. Mẫu Minh chứng Bổ sung (Ảnh Demo, Thông tin PR & PO Sign-off)

### 7.1. Ảnh Kịch bản Demo Giao diện POS Tích hợp Gợi ý AI (Mockup Description)

Dưới đây là mô tả bố cục màn hình POS khi tích hợp phân hệ AI thành công:

```text
+-------------------------------------------------------------------------------------------------------+
| SASMS POS v1.0 - SIÊU THỊ ĐIỆN MÁY SASMS CHI NHÁNH QUẬN 1 (ST-HCM-01)                 Thu ngân: NV007  |
+-------------------------------------------------------------------------------------------------------+
| GIỎ HÀNG HIỆN TẠI (1 sản phẩm)                                        | TỔNG TIỀN VÀ THANH TOÁN      |
| 1. iPhone 16 Pro Max 256GB Desert Titanium (SKU: PHONE-IP16-128)      | Tiền hàng:     34.990.000 đ  |
|    Số lượng: 1 | Đơn giá: 34.990.000 đ | Serial: SN-AP-2026-00982      | Thuế VAT (10%): 3.499.000 đ  |
|                                                                       | TỔNG CỘNG:     38.489.000 đ  |
+-----------------------------------------------------------------------+-------------------------------+
| GỢI Ý BÁN KÈM THÔNG MINH (AI CROSS-SELL WIDGET)        [Nguồn: AI - GPT-4o-mini | Phản hồi: 684ms]     |
| +-----------------------------------+ +----------------------------------+ +------------------------+ |
| | [Phụ kiện] Ốp lưng chống sốc chuẩn| | [Củ sạc] Sạc nhanh 20W Type-C     | | [Bảo hành] Bảo hành mở | |
| | SKU: ACC-CASE-PHONE-IP16-128      | | SKU: ACC-CHG-20W                 | | SKU: SRV-WAR-EXT12     | |
| | Giá niêm yết: 450.000 đ           | | Giá niêm yết: 590.000 đ          | | Giá: 1.200.000 đ       | |
| | [ + Thêm vào giỏ hàng ]           | | [ + Thêm vào giỏ hàng ]          | | [ + Thêm vào giỏ hàng ]| |
| +-----------------------------------+ +----------------------------------+ +------------------------+ |
+-------------------------------------------------------------------------------------------------------+
```

---

### 7.2. Thông tin Pull Request (PR) đã Merge vào Nhánh Phát triển

| Thuộc tính PR | Giá trị ghi nhận thực tế |
| --- | --- |
| **Tiêu đề PR** | `feat(ai): integrate AI service skeleton, OpenAI/Gemini adapters and cross-sell API (S1-13)` |
| **Nhánh nguồn (Source Branch)** | `Vu_AI` |
| **Nhánh đích (Target Branch)** | `main` |
| **Số lượng tệp thay đổi** | 10 files (`+1,580 lines`) |
| **Người tạo PR** | Nguyễn Tiến Vũ (`NguyenTienVu <tienvutb21@gmail.com>`) |
| **Người phê duyệt kỹ thuật 1** | **Trần Trung Kiên (Architect / DBA):** *"Đã duyệt logic fallback và kiểm soát không giữ transaction DB. Đạt chuẩn kiến trúc."* |
| **Người phê duyệt kỹ thuật 2** | **Vũ Duy Thái (Fullstack Lead):** *"Endpoint khớp 100% D52 OpenAPI contract. Sẵn sàng gắn UI POS ở Sprint 2."* |
| **Trạng thái PR** | **MERGED** (Merge commit `b62851e` / `977f3ac`) |

---

### 7.3. Biên bản Ký duyệt Sprint 1 Review của Product Owner (PO Sign-off Form)

```text
========================================================================================
                      BIÊN BẢN ĐÁNH GIÁ SẢN PHẨM TĂNG TRƯỞNG SPRINT 1 (DS1)
                                   Phân hệ Trí tuệ Nhân tạo (AI)
========================================================================================

Dự án:               Smart Electronic Supermarket Management System (SASMS)
Nhiệm vụ:            S1-13 — Dựng khung dịch vụ AI để Sprint 2 tích hợp được ngay
Người thực hiện:     Nguyễn Tiến Vũ — Data / AI Lead
Người đánh giá:      Nguyễn Quang Huy — Product Owner / BA Lead
Thời gian đánh giá:  10/10/2026 (Cuộc họp Sprint 1 Review)

KẾT QUẢ ĐÁNH GIÁ CÁC TIÊU CHÍ CHẤP NHẬN (ACCEPTANCE CRITERIA):
[x] 1. Khung dịch vụ AI hoạt động ổn định, endpoint POST /api/v1/ai/cross-sell trả mã 200 OK.
[x] 2. Tích hợp và gọi thử thành công với cả 2 mô hình OpenAI (gpt-4o-mini) và Google Gemini.
[x] 3. Cơ chế Cache hoạt động tốt (độ trễ phản hồi khi Cache Hit < 5ms).
[x] 4. Cơ chế Luật dự phòng (Rule Fallback) hoạt động chính xác khi mô phỏng timeout 2.5s.
[x] 5. Không rò rỉ thông tin cá nhân khách hàng (PII) trong request, response và logs.

KẾT LUẬN CỦA PRODUCT OWNER:
"Khung dịch vụ AI đạt 100% tiêu chí chấp nhận đã đề ra trong kế hoạch Sprint 1. 
Sản phẩm tăng trưởng DS1 của Phân hệ AI đủ điều kiện nghiệm thu cổng Quality Gate M2 
và sẵn sàng tích hợp với giao diện Web POS trong Sprint 2."

Xác nhận của Product Owner:
(Đã ký điện tử)
Nguyễn Quang Huy
Product Owner / BA Lead — SASMS Project
========================================================================================
```

---

## 8. Kế hoạch Bàn giao sang Sprint 2

Bước sang Sprint 2, phân hệ AI sẽ phối hợp cùng đội Fullstack và Tester để hoàn thiện các nội dung sau:
1. **Kết nối trực tiếp CSDL PostgreSQL:** Thay thế dữ liệu mock candidate bằng câu lệnh truy vấn lọc sản phẩm có tồn khả dụng (`stock_availability.available > 0`) qua quyền `sasms_reader`.
2. **Gắn giao diện Web POS (Vũ Duy Thái):** Render Widget gợi ý phụ kiện trực tiếp trên màn hình bán hàng POS khi thu ngân quét mã QR sản phẩm.
3. **Theo dõi Chuyển đổi (Conversion Tracking):** Ghi nhận bản ghi vào bảng `sasms.ai_recommendations` khi khách hàng đồng ý mua phụ kiện để tính toán chỉ số `Acceptance Rate` theo FR-15 và BR-10.
