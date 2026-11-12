# Sprint 2 — Phần DB của Trần Trung Kiên

Nhánh thực hiện: **`DB`**.  
Kỳ Sprint trong kế hoạch: **11/10/2026 – 24/10/2026 (Tuần 6–7)**.  
Chủ đề Sprint 2: **POS, Bán lẻ tại quầy và AI gợi ý**.  
Phạm vi nghiệp vụ: **US-04, US-05, US-06 (Should), US-07**. Cam kết: 21 điểm Must, 3 điểm Should.  
Cột mốc kiểm soát: **Quality Gate M3 (20/10/2026)** và **Quality Gate M4 (24/10/2026)**.

---

## 1. Đầu việc chính do Trần Trung Kiên (Architect / DBA) phụ trách

Theo bảng phân công chính thức tại [inputs/phan-cong-sprint-2.csv](inputs/phan-cong-sprint-2.csv), Kiên làm chính 3 đầu việc với tổng cộng **56 giờ**:

| Mã việc | Công việc | Hạn hoàn thành | Giờ ước lượng | Tài liệu đầu ra | Minh chứng yêu cầu theo phân công |
|---|---|---|---|---|---|
| **S2-03** (TSK-02-02) | API tìm kiếm và khóa chọn Serial khi bán | 15/10/2026 | 24 giờ | [DS2 — Thiết kế và bàn giao DB](DS2-thiet-ke-va-ban-giao-DB.md) | PR đã merge; test 2 máy POS chọn cùng Serial chỉ một máy giữ được (Pessimistic locking / SELECT FOR UPDATE) |
| **S2-12** | Thiết lập Staging và pipeline deploy tự động | 20/10/2026 | 16 giờ | [D22 — Kế hoạch môi trường & CI/CD Sprint 2](D22-ke-hoach-moi-truong-va-CICD-sprint2.md) | Sơ đồ môi trường Staging hoàn chỉnh; log GitHub Actions deploy Staging thành công |
| **S2-06** (TSK-02-05) | API truy vấn tồn kho real-time theo siêu thị | 22/10/2026 | 16 giờ | [DS2 — Thiết kế và bàn giao DB](DS2-thiet-ke-va-ban-giao-DB.md) | PR đã merge; số tồn đổi trong vài giây sau khi bán (View real-time, index tối ưu < 10ms) |

---

## 2. Đầu việc Kiên tham gia hỗ trợ

Kiên tham gia hỗ trợ 3 đầu việc chuyên môn theo bảng phân công:

| Mã việc | Công việc | Người làm chính | Đóng góp chuyên môn DB của Kiên |
|---|---|---|---|
| **S2-08** (TSK-02-07) | AI Recommendation Engine (cross-selling, cache, luật dự phòng) | Nguyễn Tiến Vũ (Data / AI Lead) | Cung cấp View và Stored Function `sasms.get_cross_sell_fallback_rules` để gợi ý phụ kiện còn tồn kho tại quầy khi LLM timeout/mất mạng; tối ưu cache truy vấn catalog |
| **S2-15** | Họp CCB xử lý CR, theo dõi chi phí token AI (R-07) và EVM | Trịnh Quốc An (PM / Scrum Master) | Cung cấp số liệu kỹ thuật DB, đánh giá tải hệ thống, phương án giảm chi phí token AI bằng DB caching |
| **S2-14** | Refinement cho Sprint 3 (US-08..11) và kiểm tra DoR | Nguyễn Quang Huy (BA / PO) | Rà soát cấu trúc CSDL cho các User Story Sprint 3 (đặt hàng online, điều phối giao hàng, kích hoạt bảo hành điện tử và đổi trả thiết bị) |

---

## 3. Bộ hồ sơ và sản phẩm bàn giao Sprint 2

1. **Tài liệu kỹ thuật & kiến trúc:**
   - [DS2 — Thiết kế và bàn giao DB Sprint 2](DS2-thiet-ke-va-ban-giao-DB.md): Đặc tả chi tiết cơ chế khóa Serial, view tồn kho real-time, hàm dự phòng AI và hợp đồng API/SQL với Backend.
   - [D22 — Kế hoạch môi trường Dev / Staging / Prod và CI/CD (Sprint 2)](D22-ke-hoach-moi-truong-va-CICD-sprint2.md): Kiến trúc môi trường Staging và sơ đồ tự động hóa triển khai.
   - [D128 — Hồ sơ DB cho Quality Gate M3](D128-ho-so-review-DB-M3.md): Hồ sơ kỹ thuật phục vụ nghiệm thu Cổng kiểm soát chất lượng M3 (20/10/2026: Omnichannel POS & Cart Operable).
   - [D129 — Hồ sơ DB cho Quality Gate M4](D129-ho-so-review-DB-M4.md): Hồ sơ kỹ thuật phục vụ nghiệm thu Cổng kiểm soát chất lượng M4 (24/10/2026: AI Cross-selling & Consultation Live).

2. **Mã nguồn và script kiểm chứng CSDL:**
   - [database/sprint2/realtime_stock_and_locking.sql](../../database/sprint2/realtime_stock_and_locking.sql): Stored functions, view tồn kho và index tối ưu hóa.
   - [database/sprint2/demo.sql](../../database/sprint2/demo.sql): Script chạy thử nghiệm các tính năng Sprint 2 trong transaction an toàn.
   - [database/tests/sprint2/test_sprint2_concurrency_and_stock.sql](../../database/tests/sprint2/test_sprint2_concurrency_and_stock.sql): Kịch bản kiểm chứng tự động chống tranh chấp Serial và trừ tồn tức thời.

3. **Tự động hóa CI/CD:**
   - [.github/workflows/sprint2-staging.yml](../../.github/workflows/sprint2-staging.yml): Workflow GitHub Actions tự động kiểm thử CSDL, kiểm tra concurrency và deploy Staging.

---

## 4. Kiểm tra Cổng vào (DoR) và Cổng ra (DoD) Sprint 2 của Kiên

- [x] **DoR:** Đã chốt mockup Figma và DB Schema liên quan (Trần Trung Kiên xác nhận khớp với schema Sprint 1 + Sprint 2 extensions).
- [x] **DoD:** 100% Pull Request DB được Kiên duyệt và kiểm thử trước khi merge vào main.
- [x] **DoD:** Đạt hiệu năng CSDL: quét Serial/QR < 10ms (chuẩn hệ thống < 1s), truy vấn tồn kho real-time < 15ms (chuẩn POS < 2s), luật dự phòng AI < 10ms (chuẩn AI < 3s).
- [x] **DoD:** Thiết lập pipeline tự động deploy thành công lên Staging.

