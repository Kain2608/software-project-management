# D22 — Môi trường và CI/CD Sprint 2

S2-12, phụ trách **Trần Trung Kiên**, phối hợp **Vũ Duy Thái**. Hạn kế hoạch 20/10/2026, 16 giờ ước lượng; rà soát thực tế 13/11/2026. Trạng thái: có kiểm chứng DB/readiness trong repo; **chưa có bằng chứng deploy Staging thật**.

## 1. Kế thừa kế hoạch môi trường

Giữ ranh giới của [D22 tiền Sprint](../../Ti%E1%BB%81n%20Sprint/DB/D22-ke-hoach-moi-truong-va-CICD.md), [ma trận môi trường](../../infra/environment-matrix.json) và [runbook vận hành](../../infra/README.md). Dev/CI là PostgreSQL 16 cô lập, dữ liệu mô phỏng; Staging/Prod là môi trường riêng cần hạ tầng, DNS, secret và triển khai thực. Host như `db.staging.sasms.internal` trong bản cũ chỉ là ví dụ, không phải endpoint đã cấp phát.

| Credential / môi trường | Trách nhiệm |
|---|---|
| Owner/migrator | DDL, migration, provisioning và ACL; không dùng làm credential app |
| Backend runtime | Login riêng thuộc group `sasms_app`, xác thực/kiểm scope ở API, không phát credential cho browser |
| Đọc catalog/AI | Quyền đọc hẹp; lọc active/scope/projection và không gửi PII/secret sang LLM |
| CI service | Credential tạm chỉ dùng trong PostgreSQL service của job; không phải password Staging |
| Staging/Prod | Secret tách môi trường, DB private, app HTTPS; trạng thái cấp phát theo hồ sơ thực tế |

## 2. Pipeline đang hiện thực

Nguồn: [.github/workflows/sprint2-staging.yml](../../.github/workflows/sprint2-staging.yml).

```mermaid
flowchart LR
    Source[Push / PR / workflow_dispatch] --> Validate[PostgreSQL 16: regression S0/S1 và SQL Sprint 2]
    Validate --> Tests[ACL, authorization, hai kết nối Serial và tồn kho]
    Tests --> Evidence[Log và summary theo kết quả thật]
    Evidence --> Readiness[Báo cáo readiness]
    Readiness -. Cần hạ tầng và release riêng .-> Staging[Deploy Staging thật và health check]
```

Job `validate-db-and-concurrency` chạy validator; `staging-readiness` tổng hợp kết quả. Khi SQL/test lỗi, process trả mã khác 0 và job validation **FAIL**; readiness phản ánh FAILED/BLOCKED/CANCELLED theo kết quả, không được đổi lỗi thành SUCCESS. Khi validation đạt, readiness chỉ được ghi **READY_FOR_STAGING**, cùng **`deployment_status: NOT_DEPLOYED`**.

Các lệnh `psql` phải có `-X -v ON_ERROR_STOP=1`, runner kiểm return code; pipeline ghi log qua `tee` phải giữ mã lỗi bằng `pipefail`. Upload evidence dùng `if: always()` để còn log khi thất bại; upload thành công không có nghĩa test thành công. Không dùng `continue-on-error` để bỏ lỗi test/SQL và không xuất `backend_health: UP` hoặc `database_status: MIGRATIONS_UP_TO_DATE` từ JSON hằng.

Artifact kiểm chứng DB gồm `summary.json`, `validation.log` và regression Sprint 1/baseline. Báo cáo phải gắn với SHA/run đã chạy; chưa có URL GitHub Actions mới trong hồ sơ này. Các run Sprint 0/1 cũ trong D22 tiền Sprint không chứng minh Sprint 2 đã qua CI.

Đã chạy local trên PostgreSQL **16.15**, **13/11/2026, 23:14:15–23:14:31 (UTC+7)**: 33 assertions Sprint 2, 76 Sprint 1, 56 ràng buộc và 7 ACL Sprint 0 đạt; xem [summary](../../database/evidence/sprint2/summary.json). Hai [failure probes CLI trên bản sao nguồn](../../database/evidence/sprint2/ci-failure-probes.json) gây lỗi SQL `42703` và lỗi assertion đều trả CLI 1, psql 3, summary failed; DDL extension rollback khi lỗi. Kết quả này kiểm đường truyền lỗi local, không tự tạo một run GitHub Actions hoặc bằng chứng Staging.

[Readiness probes local](../../database/evidence/sprint2/ci-readiness-probes.json) đã thực thi đoạn Python workflow với kết quả success/failure/cancelled/skipped mô phỏng: trạng thái READY_FOR_STAGING/FAILED/CANCELLED/BLOCKED tương ứng, exit 0/1/1/1 và `NOT_DEPLOYED` trong mọi ca. Ngữ cảnh GitHub của probe là dữ liệu mô phỏng, không phải run hosted.

## 3. Nâng cấp và kiểm thử

Database thử mới: baseline/seed/runtime S0 → regression → V001/V002 → runtime S1 → SQL Sprint 2 → ACL/authorization/concurrency/stock. Chi tiết lệnh tại [runbook Sprint 2](../../database/sprint2/README.md). File extension Sprint 2 chưa được ghi vào ledger V001/V002; giữ đúng source/hash đã kiểm, không sửa migration cũ để giả lịch sử.

Database có dữ liệu: backup và thử trên bản sao trước; chỉ áp dụng migration còn thiếu rồi SQL Sprint 2 bằng owner/migrator trong transaction. Không chạy lại baseline/seed lên DB đã nâng cấp vì seed có thể khôi phục grant bị thu hồi có chủ ý. Không chạy validator tạo fixture bằng owner trên Prod/Staging có dữ liệu nghiệp vụ.

Hai kết nối thử phải có backend PID khác nhau, cùng tranh một Serial khi transaction giữ còn mở. Tình huống commit kiểm `55P03` và quyền giữ độc nhất; rollback kiểm người khác giữ được sau rollback. Kiểm permission bằng `SET ROLE sasms_app`, user active/inactive/sai store và caller không được EXECUTE. Bộ SQL chạy một phiên là regression chức năng, không đủ chứng cứ concurrency.

## 4. Điều kiện hoàn thành Staging thật

Theo workbook, S2-12 cần **log GitHub Actions deploy Staging thành công**. Readiness hiện tại chưa đáp ứng điều kiện đó. Phần phát hành cần bổ sung:

1. An/Thái/Kiên cấu hình môi trường được phép: host, network, secret, runtime login và artifact ứng dụng có phiên bản.
2. Lưu backup và manifest, kiểm khả năng restore trên database cô lập; dùng credential migrator riêng để nâng cấp.
3. Deploy backend/frontend đúng commit, áp dụng SQL/ACL đã kiểm, kiểm migration/schema thực tế.
4. Gọi health và smoke nghiệp vụ thật, lưu response/exit code/timestamp; `/api/pos/status` hiện trả skeleton nên không đủ nghiệm thu POS.
5. Phát chạy test ứng dụng và lưu D76/D79; Huy/An xác nhận theo thẩm quyền, lưu run URL/commit/deploy log.

Mục tiêu RPO/RTO và lịch backup tiếp tục theo D22 tiền Sprint, chờ chủ hệ thống chốt. Không còn dùng số “restore < 10 giây” từ bản cũ vì không có phép đo Staging tương ứng. Lỗi DDL đang áp dụng phải rollback transaction; sau commit xử lý bằng thay đổi đã review và đối soát, không xóa schema/dữ liệu nghiệp vụ để thử lại.
