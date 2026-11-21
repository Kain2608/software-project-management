# Mã nguồn phần DB — Sprint 3

Thư mục này gom mã nguồn do Kiên bàn giao cho Sprint 3, giữ nguyên đường dẫn tương đối như repo chung để đối chiếu và tích hợp. [Manifest](../code-manifest.json) ghi đường dẫn nguồn, đường dẫn trong phần DB và SHA-256 chuẩn hóa xuống dòng LF. Tài liệu, phân công và evidence nằm ở [thư mục DB phía trên](../README.md).

| Thư mục | Nội dung |
|---|---|
| `database/migrations/` | V006 logistics/bảo hành và V007 SMS outbox |
| `database/sprint3/` | Runtime role, demo SQL, schema DBML, ERD, từ điển |
| `database/tests/sprint3/` | Kiểm quyền, lịch, bảo hành, outbox/retry |
| `backend/src/main/java/` | Adapter/worker SMS và cấu hình JWT |
| `backend/src/test/java/` | Kiểm thử SMS/JWT, tích hợp PostgreSQL MOCK |
| `scripts/` | Validator Sprint 3, compatibility regression và script đóng gói |
| `.github/workflows/` | Workflow kiểm chứng Sprint 3 |

Chạy ứng dụng và kiểm thử từ **gốc repo** để dùng `backend/pom.xml`, baseline DB, migration trước đó và các script dùng chung đã có trên `main`. Hướng dẫn chi tiết ở [runbook](../../../database/sprint3/README.md).

Sau khi sửa file tại đường dẫn tích hợp ở gốc repo:

```powershell
python scripts/package_sprint3_db.py
python scripts/package_sprint3_db.py --check
```

Lệnh đầu cập nhật các file trong gói theo allowlist; lệnh `--check` chỉ đọc và báo lỗi nếu file thiếu, thừa hoặc khác nguồn. Các file ngoài allowlist được giữ để người dùng kiểm tra, không bị tự xóa. Chế độ SMS bàn giao là MOCK theo lựa chọn của người dùng.
