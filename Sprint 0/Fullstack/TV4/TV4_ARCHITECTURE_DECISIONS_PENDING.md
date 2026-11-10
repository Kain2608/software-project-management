# SASMS Sprint 0 — Tờ trình 3 quyết định kiến trúc & tích hợp còn chờ duyệt của TV4

| Thuộc tính | Nội dung |
|---|---|
| **Dự án** | Smart Electronic Supermarket Management System (SASMS / Vendora) |
| **Giai đoạn** | Sprint 0 — Handoff TV4 sang repository quản lý dự án của PM An |
| **Tài liệu** | `Sprint 0/Fullstack/TV4/TV4_ARCHITECTURE_DECISIONS_PENDING.md` |
| **Người lập** | Vũ Duy Thái (TV4 — Fullstack Lead) |
| **Ngày lập** | 07/11/2026 |
| **Repository đích** | `D:\software-project-management-an` (nhánh `feat/tv4-sprint0-handoff`) |
| **Repository nguồn tham chiếu** | `D:\smart-electronic-supermarket-sprint0` (nhánh `feat/tv4-sprint0-postgresql`, commit `12918f7`) |
| **Trạng thái chung** | **CẢ 3 QUYẾT ĐỊNH ĐÃ ĐƯỢC PHÊ DUYỆT TOÀN PHẦN (`APPROVED`): PM An, Fullstack Lead Thái và Architect/DBA Kiên đều đã chấp thuận Phương án A1, B1, C2; Architect Kiên đã đồng bộ và bổ sung vào D50 ngày 11/11/2026.** |

---

## 1. Kiểm tra mã quyết định hiện có trong hồ sơ kiến trúc (tránh trùng mã)

Trước khi lập tài liệu này, đã đối chiếu toàn bộ hệ thống mã quyết định/điểm mở đang tồn tại trong repository `D:\software-project-management-an`:

| Nguồn tài liệu | Dải mã đã định nghĩa | Phạm vi của mã hiện có |
|---|---|---|
| [Sprint 0/DB/D50-kien-truc-he-thong.md](../../DB/D50-kien-truc-he-thong.md) (mục 7, dòng 162–170) | `ARC-01` → `ARC-07` | Các quyết định kiến trúc cấp hệ thống do Trần Trung Kiên định nghĩa. Trong đó **`ARC-06` đã tồn tại** với nội dung: *"Backend, cloud, backup, RPO/RTO và SLA — Người phối hợp: Thái, PM — Điều kiện đóng: D22, dự toán và thử khôi phục được review"* (và mục 5 dòng 146: *"Backend/framework, cloud, object storage, nhà cung cấp và chi phí chờ D22/Dev Lead chốt"*). |
| [Sprint 0/DB/D126-ho-so-review-M1.md](../../DB/D126-ho-so-review-M1.md) (mục 3, dòng 57–68) | `M1-01` → `M1-10` | 10 điểm mở cấp Quality Gate M1 của Kiên. |
| [docs/sprint0/d53/D53_DECISION_REGISTER.md](docs/sprint0/d53/D53_DECISION_REGISTER.md) | `DEC-01` → `DEC-10` | 10 xung đột nghiệp vụ Kho/QR/POS giữa D34, D35, D40, D51. |
| [docs/sprint0/d52/D52_API_DESIGN_NOTES.md](docs/sprint0/d52/D52_API_DESIGN_NOTES.md) | `API-P01` → `API-P12` | 12 điểm chờ chốt hợp đồng OpenAPI D52. |

### Nguyên tắc đặt mã trong tài liệu này

- **Tuyệt đối không định nghĩa lại `ARC-01..ARC-07`** và không chiếm dụng mã `ARC-06` (vì `ARC-06` trong D50 còn bao gồm cả cloud, backup, RPO/RTO, SLA và D22).
- Ba quyết định cần chốt để mở khóa việc tích hợp source skeleton của TV4 vào repo PM An được đặt dưới namespace riêng của đợt bàn giao TV4:
  - **`TV4-S0-ARCH-01`**: Vị trí đặt source skeleton (`backend/`, `frontend/`) ở root repository hay dưới `Sprint 0/Fullstack/`? *(Liên kết phụ thuộc tới D63, D64 và cấu trúc repo An).*
  - **`TV4-S0-ARCH-02`**: Baseline biên dịch và runtime Java cho backend là `JDK 17` hay `Java 26`? *(Đóng góp trực tiếp vào phần "Backend/framework" của `ARC-06` trong D50 và mục 2 của D65).*
  - **`TV4-S0-ARCH-03`**: Kết nối PostgreSQL của backend và JDBC smoke test dùng tài khoản owner (`sasms_owner`) hay login kế thừa group role `sasms_app`? *(Thực thi mục 4 của [D54](../../DB/D54-bao-mat-phan-quyen.md) và [database/runtime-roles.sql](../../../database/runtime-roles.sql)).*

---

## 2. Tóm tắt 3 quyết định cần phê duyệt

| Mã quyết định | Câu hỏi cần chốt | Tham chiếu liên quan | Phương án khuyến nghị | Người có quyền quyết định | Trạng thái |
|---|---|---|---|---|---|
| **`TV4-S0-ARCH-01`** | Đặt `backend/` và `frontend/` ở **root repository** hay trong **`Sprint 0/Fullstack/`**? | D63 mục 2, D64 mục 4, D65 mục 3–10 | **Phương án A1:** Đặt `backend/` và `frontend/` tại **root** `D:\software-project-management-an\`, kèm `backend/.gitignore` và `frontend/.gitignore` riêng để không sửa `.gitignore` root | **Trịnh Quốc An** (PM — chủ repo) + **Trần Trung Kiên** (Architect) | **APPROVED** (PM An & Architect Kiên đồng thuận Phương án A1; Kiên đã cập nhật vào D50) |
| **`TV4-S0-ARCH-02`** | Chọn **`JDK 17`** (theo workbook) hay **`Java 26` + Spring Boot `4.1.1`** (theo source hiện có)? | D50 (`ARC-06`), D64 mục 3, D65 mục 2, Workbook S0-24 | **Phương án B1 (cho bàn giao Sprint 0):** Giữ nguyên `Java 26` + `Spring Boot 4.1.1` để bảo toàn tính toàn vẹn của bằng chứng test đã chạy | **Trần Trung Kiên** (Architect) + **Trịnh Quốc An** (PM) + **Vũ Duy Thái** (Fullstack Lead) | **APPROVED** (PM An, TV4 Lead Thái & Architect Kiên đồng thuận B1; Kiên đã chốt tại ARC-06 trong D50) |
| **`TV4-S0-ARCH-03`** | Backend (`application-postgresql.properties`) và `PostgreSqlConnectionSmokeTest` dùng **`sasms_owner`** hay login thuộc **`sasms_app`**? | D54 mục 4, `database/runtime-roles.sql`, `database/tests/runtime_acl.sql`, D64, D65 | **Phương án C2:** Tách bạch 2 lớp tài khoản — `sasms_owner` **chỉ** dùng cho khởi tạo DDL/seed/provisioning; ứng dụng backend và JDBC smoke test bắt buộc dùng một login riêng kế thừa group role `sasms_app` | **Trần Trung Kiên** (Architect / DBA) + **Vũ Duy Thái** (Fullstack Lead), **Trịnh Quốc An** (PM) xác nhận | **APPROVED** (PM An, Lead Thái & DBA Kiên đồng thuận C2; Kiên đã bổ sung vào D50 mục 5.3) |

---

## 3. Phân tích chi tiết từng vấn đề

---

### Vấn đề A (`TV4-S0-ARCH-01`): Đặt source `backend/` và `frontend/` ở root repository hay trong `Sprint 0/Fullstack/`?

#### A.1. Hiện trạng thực tế

1. **Tại repository nguồn (`D:\smart-electronic-supermarket-sprint0`, commit `12918f7`):**
   - Mã nguồn được tổ chức ở cấp root gồm `backend/` (21 file: Maven Wrapper, `pom.xml`, 8 class Java, `application.properties`, `application-postgresql.properties`, 2 class test) và `frontend/` (15 file: `package.json`, `package-lock.json`, `vite.config.js`, `index.html`, `.env.example`, `src/**`).
   - Toàn bộ lệnh trong `D63`, `D64`, `D65` (`cd backend`, `cd frontend`, `backend/pom.xml`, `frontend/package.json`) đều giả định hai thư mục này nằm ở root.
2. **Tại repository đích (`D:\software-project-management-an`, nhánh `feat/tv4-sprint0-handoff`):**
   - Repo đích kết hợp **hai kiểu tổ chức**:
     - **Tài liệu theo giai đoạn & vai trò:** `Tiền Sprint/<Vai trò>/`, `Sprint 0/<Vai trò>/`, `Sprint 1/<Vai trò>/` (hiện đã có `Sprint 0/Fullstack/.gitkeep`, `Sprint 1/Fullstack/.gitkeep` và `Sprint 0/Fullstack/TV4/docs/sprint0/`).
     - **Hạ tầng & mã kỹ thuật dùng chung xuyên các Sprint đặt ở root:** trong commit `ca24ffe` (PR #1), Kiên đã đặt `database/`, `scripts/`, `docker-compose.yml`, `.env.example` và `.github/workflows/database.yml` trực tiếp ở **root** của `D:\software-project-management-an`, trong khi tài liệu thuyết minh đặt tại `Sprint 0/DB/`.
   - Hiện tại `backend/` và `frontend/` **chưa được sao chép** sang repo đích.
   - File `.gitignore` ở root repo đích mới chỉ có rule cho Python và `database/.local/`, chưa có rule loại trừ `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/.env*`.

#### A.2. Các phương án

- **Phương án A1 — Đặt `backend/` và `frontend/` ở root repository (`D:\software-project-management-an\backend`, `...\frontend`), tài liệu giữ tại `Sprint 0/Fullstack/TV4/`:**
  - Theo đúng mô hình mà Kiên đã áp dụng (`database/` và `scripts/` ở root, tài liệu ở `Sprint 0/DB/`).
  - Đặt thêm `backend/.gitignore` và `frontend/.gitignore` bên trong từng thư mục source để Git tự động bỏ qua `target/`, `node_modules/`, `dist/`, `.env*` mà **không cần chỉnh sửa `.gitignore` ở root** của An/Kiên.
- **Phương án A2 — Đặt source bên trong `Sprint 0/Fullstack/TV4/` (hoặc `Sprint 0/Fullstack/`):**
  - Toàn bộ code nằm tại `Sprint 0/Fullstack/TV4/backend/` và `Sprint 0/Fullstack/TV4/frontend/` (hoặc `Sprint 0/Fullstack/backend/`).
- **Phương án A3 — Giữ code ở repo riêng, repo của An chỉ lưu tài liệu:**
  - Không copy `backend/` và `frontend/` vào `D:\software-project-management-an`; chỉ dẫn link sang repo code riêng.

#### A.3. Ưu / nhược điểm từng phương án

| Phương án | Ưu điểm | Nhược điểm / Rủi ro |
|---|---|---|
| **A1 — Đặt ở root (`backend/`, `frontend/`)** | • Nhất quán 100% với cách Kiên đặt `database/`, `scripts/`, `docker-compose.yml` ở root.<br>• Không bị nhân bản mã nguồn khi chuyển sang `Sprint 1/Fullstack/`, `Sprint 2/...`; lịch sử `git blame`/`git log` của code liên tục xuyên suốt dự án.<br>• Đường dẫn không chứa khoảng trắng (`Sprint 0`), tránh lỗi đối với một số script/plugin Maven, Vite hoặc đường dẫn Windows dài.<br>• Giữ nguyên tất cả đường dẫn `backend/...`, `frontend/...` đã viết trong D63, D64, D65.<br>• Dùng `backend/.gitignore` và `frontend/.gitignore` nội bộ nên không chạm vào `.gitignore` root. | • Tạo thêm 2 thư mục cấp 1 ở root repository (cần PM An đồng ý). |
| **A2 — Đặt trong `Sprint 0/Fullstack/...`** | • Cô lập toàn bộ sản phẩm của TV4 trong một thư mục con duy nhất của Sprint 0. | • **Vi phạm tính liên tục của mã nguồn:** Sang Sprint 1 (`Sprint 1/Fullstack/`) sẽ phải copy lại toàn bộ `backend/` và `frontend/` sang thư mục mới hoặc sửa code Sprint 1 ngay trong thư mục mang tên `Sprint 0`.<br>• Đường dẫn chứa khoảng trắng (`Sprint 0`) dễ gây lỗi shell/script khi truyền tham số không quote.<br>• Làm lệch toàn bộ đường dẫn `cd backend`, `cd frontend` trong D63, D64, D65 và lệch cấu trúc mô tả trong D64.<br>• Khác biệt với cách Kiên đặt `database/` ở root. |
| **A3 — Không đưa source vào repo An** | • Repo An chỉ thuần chứa văn bản và SQL. | • Deliverable S0-24 / D64 trong repo bàn giao chính thức bị thiếu hiện vật mã nguồn (`backend/`, `frontend/`); người chấm/review clone repo An không thể chạy thử Maven test hay JDBC smoke test. |

#### A.4. Ảnh hưởng tới source, build, CI và DB

| Khía cạnh | Phương án A1 (Root) | Phương án A2 (`Sprint 0/Fullstack/...`) |
|---|---|---|
| **Source** | Giữ nguyên 100% file từ repo nguồn; chỉ thêm `backend/.gitignore` và `frontend/.gitignore`. | Giữ nguyên nội dung file nhưng đường dẫn vật lý bị lồng sâu thêm 2–3 cấp và có dấu cách. |
| **Build (Maven / Vite)** | Chạy `cd backend; .\mvnw.cmd clean test` và `cd frontend; npm.cmd ci; npm.cmd run build` ngay từ root đúng như D64/D65. | Phải `cd "Sprint 0/Fullstack/TV4/backend"`; nguy cơ đường dẫn dài trên Windows (`MAX_PATH`) đối với `node_modules` và Maven wrapper. |
| **CI (`.github/workflows/`)** | Khi bổ sung workflow CI cho backend/frontend ở các sprint sau, `working-directory: backend` và `working-directory: frontend` gọn, chuẩn và không phụ thuộc tên Sprint. | CI phải hardcode `working-directory: "Sprint 0/Fullstack/..."` và phải sửa lại workflow mỗi khi sang Sprint mới. |
| **Database & Docker** | Không ảnh hưởng `database/` hay `docker-compose.yml`; đứng ở root vừa chạy `docker compose -p tv4_s0_check` vừa `cd backend` thuận tiện. | Phải di chuyển qua lại nhiều cấp thư mục (`..\..\..`) để gọi `docker compose` ở root. |

#### A.5. Khuyến nghị có căn cứ

- **Khuyến nghị:** Chọn **Phương án A1** — đặt `backend/` và `frontend/` tại root `D:\software-project-management-an\`, kèm `backend/.gitignore` và `frontend/.gitignore` nội bộ; còn tài liệu, báo cáo và thư mục `evidence/` của TV4 đặt tại `Sprint 0/Fullstack/TV4/`.
- **Căn cứ:** Hoàn toàn đồng bộ với tiền lệ kiến trúc đã được merge vào `main` ở PR #1 (`edc96e3` / `ca24ffe`), trong đó Kiên đặt `database/`, `scripts/`, `docker-compose.yml` ở root và đặt tài liệu `D50, D51, D54, D57, D126` tại `Sprint 0/DB/`.

#### A.6. Người có quyền quyết định & Trạng thái

- **Người có quyền quyết định:** **Trịnh Quốc An** (PM — quản lý cấu trúc repository chung) và **Trần Trung Kiên** (Architect).
- **Trạng thái:** **`PENDING`**.

---

### Vấn đề B (`TV4-S0-ARCH-02`, đóng góp cho `ARC-06` trong D50): Chọn `JDK 17` hay `Java 26`?

#### B.1. Hiện trạng thực tế

1. **Trong Workbook phân công Sprint 0 (`Sprint 0/DB/inputs/phan-cong-sprint-0.xlsx`, dòng S0-24 / D65):**
   - Cột minh chứng môi trường phát triển ghi yêu cầu: *"JDK 17, Node 18+, Python 3.10, Docker"*.
2. **Trong tài liệu kiến trúc [D50](../../DB/D50-kien-truc-he-thong.md) của Kiên:**
   - Mục 5 (dòng 146): *"Backend/framework, cloud, object storage, nhà cung cấp và chi phí chờ D22/Dev Lead chốt; không chọn đồng thời các phương án Java/Node trong D08."*
   - Mục 7 (`ARC-06`, dòng 169): *"Backend, cloud, backup, RPO/RTO và SLA — Thái, PM"*.
3. **Trong source thực tế tại repo nguồn (`D:\smart-electronic-supermarket-sprint0\backend\pom.xml`):**
   - Khai báo `spring-boot-starter-parent` version **`4.1.1`** và `<java.version>26</java.version>` (được khởi tạo từ commit `8026553` / `84a4558` và giữ nguyên ở `12918f7`).
   - Toàn bộ bằng chứng build và test của TV4 ngày 07/11/2026 (`mvnw.cmd clean test`, `spring-boot:run`, `PostgreSqlConnectionSmokeTest`) được thực thi thành công trên **Oracle JDK `26.0.2.1`** và Apache Maven `3.9.16`.
   - Khi biên dịch với `<java.version>26</java.version>`, bytecode sinh ra là class file version `70.0`, không thể chạy hoặc compile bằng `JDK 17` (version `61.0`).

#### B.2. Các phương án

- **Phương án B1 — Chốt `Java 26` + `Spring Boot 4.1.1` làm baseline hiện tại:**
  - Giữ nguyên `backend/pom.xml` như source đã kiểm chứng; PM cập nhật/ghi nhận ngoại lệ so với dòng mô tả ban đầu trong workbook.
- **Phương án B2 — Chuyển ngay về `JDK 17` LTS trước khi bàn giao Sprint 0:**
  - Sửa `backend/pom.xml` sang `<java.version>17</java.version>` và điều chỉnh phiên bản `spring-boot-starter-parent` (ví dụ dòng Spring Boot `3.x` LTS tương thích JDK 17 nếu `4.1.1` hoặc môi trường nhóm yêu cầu), sau đó chạy lại toàn bộ Maven test và JDBC smoke test trên JDK 17.
- **Phương án B3 — Lộ trình 2 bước (Giữ nguyên `Java 26` khi nhận bàn giao Sprint 0, mở Change Request hạ về `JDK 17` LTS hoặc `JDK 21` LTS ở đầu Sprint 1 nếu máy các thành viên/CI không cài JDK 26):**
  - Sao chép nguyên trạng `backend/pom.xml` từ `12918f7` để bảo toàn lịch sử kiểm chứng Sprint 0; ngay sau khi thống nhất toolchain toàn nhóm, thực hiện một commit riêng biệt chỉ đổi version trong `pom.xml` và kiểm tra lại.

#### B.3. Ưu / nhược điểm từng phương án

| Phương án | Ưu điểm | Nhược điểm / Rủi ro |
|---|---|---|
| **B1 — Giữ `Java 26` + Spring Boot `4.1.1`** | • Không phải sửa `backend/pom.xml` hay tái tạo lại bằng chứng đã PASS ở commit `12918f7`.<br>• Khớp 100% với môi trường máy thực hiện hiện tại (`JDK 26.0.2.1`). | • Lệch với chữ "JDK 17" trong workbook phân công Sprint 0.<br>• Các thành viên khác trong nhóm, GitHub Actions runner (`actions/setup-java`) hoặc base image Docker chuẩn có thể chưa sẵn bản phân phối `JDK 26` ổn định. |
| **B2 — Đổi ngay về `JDK 17` trong đợt bàn giao này** | • Khớp 100% với văn bản workbook (`JDK 17`).<br>• `JDK 17` là bản LTS rất phổ biến trên mọi CI runner (`ubuntu-latest`) và Docker image. | • Phải sửa `backend/pom.xml` (và có thể phải đổi version Spring Boot parent), làm file `pom.xml` không còn khớp commit nguồn `12918f7`.<br>• Toàn bộ bằng chứng test lịch sử trên `Java 26.0.2.1` trong D64/D65/Remediation không còn phản ánh đúng file `pom.xml` sau sửa nếu không cài JDK 17 và chạy lại toàn bộ. |
| **B3 — Nhận bàn giao nguyên trạng (`Java 26`), chuyển sang `JDK 17` LTS bằng Change Request riêng nếu cần** | • **Bảo toàn tính trung thực của hồ sơ Sprint 0:** code copy sang khớp 100% với commit `12918f7` và báo cáo kiểm toán.<br>• Vẫn cho phép nhóm chuẩn hóa về `JDK 17` LTS một cách có kiểm soát (có commit riêng, log test riêng trên JDK 17) trước khi code nghiệp vụ Sprint 1. | • Cần thêm 1 bước duyệt CR nhỏ ở đầu Sprint 1 nếu chọn hạ xuống JDK 17. |

#### B.4. Ảnh hưởng tới source, build, CI và DB

| Khía cạnh | Giữ `Java 26` (B1 / giai đoạn 1 của B3) | Chuyển sang `JDK 17` (B2 / giai đoạn 2 của B3) |
|---|---|---|
| **Source (`backend/`)** | Không sửa dòng nào trong `pom.xml` hoặc `src/`. Code Java hiện tại chỉ dùng cú pháp cơ bản (records, switch/standard API đã có từ JDK 17), không dùng tính năng độc quyền của Java 26. | Phải sửa `<java.version>26</java.version>` → `17` trong `backend/pom.xml` và kiểm tra tương thích với `spring-boot-starter-parent`. |
| **Build (`mvnw.cmd`)** | Yêu cầu `JAVA_HOME` trỏ tới JDK 26. | Yêu cầu `JAVA_HOME` trỏ tới JDK 17 (hoặc JDK ≥ 17 với `--release 17`). |
| **CI (`.github/workflows/`)** | Workflow `database.yml` hiện tại của Kiên chỉ dùng Python 3.13 + PostgreSQL 16 nên **không bị ảnh hưởng**. Khi thêm workflow build backend sau này, cần kiểm tra `actions/setup-java` có hỗ trợ bản `26` trên distro được chọn hay không. | Mọi distro (`temurin`, `zulu`, `liberica`) trên `actions/setup-java@v4` đều hỗ trợ `java-version: '17'` mặc định. |
| **Database (`PostgreSQL 16`)** | Không ảnh hưởng. Driver `org.postgresql:postgresql` chạy độc lập với việc chọn JDK 17 hay 26. | Không ảnh hưởng. |

#### B.5. Khuyến nghị có căn cứ

- **Khuyến nghị:** Chọn **Phương án B3** (hoặc **B1** nếu cả nhóm đồng ý dùng JDK 26):
  1. Trong bước tích hợp bàn giao Sprint 0 hiện tại, **giữ nguyên `backend/pom.xml` (`Java 26`, `Spring Boot 4.1.1`)** để khớp tuyệt đối với commit nguồn `12918f7` và bằng chứng kiểm toán đã ghi nhận.
  2. PM An và Architect Kiên ghi quyết định vào mục `ARC-06`: hoặc công nhận `Java 26` cho môi trường local của nhóm, hoặc giao TV4 tạo một PR/commit riêng ngay sau khi merge handoff để hạ `<java.version>` về `17` LTS phục vụ CI/Sprint 1.

#### B.6. Người có quyền quyết định & Trạng thái

- **Người có quyền quyết định:** **Trần Trung Kiên** (Architect) + **Trịnh Quốc An** (PM) + **Vũ Duy Thái** (Fullstack Lead), theo đúng phân công tại `ARC-06` trong D50.
- **Trạng thái:** **`PENDING`**.

---

### Vấn đề C (`TV4-S0-ARCH-03`): Kết nối PostgreSQL của backend dùng DB owner (`sasms_owner`) hay role ứng dụng (`sasms_app`)?

#### C.1. Hiện trạng thực tế

1. **Trong thiết kế bảo mật [D54](../../DB/D54-bao-mat-phan-quyen.md) và [database/README.md](../../../database/README.md) của Kiên:**
   - [D54](../../DB/D54-bao-mat-phan-quyen.md) mục 4 (dòng 79–80, 91–102):
     - **Owner/migrator (`sasms_owner`):** *"Tạo schema, DDL, migration trong database SASMS — Chỉ pipeline migration/DBA; **không gắn vào ứng dụng**."*
     - **Runtime ứng dụng (`sasms_app`):** *"Không superuser, không `CREATEDB`/`CREATEROLE`, **không owner**."*
     - *"DBA cấp group role phù hợp cho LOGIN bí mật riêng của backend; LOGIN đó **không được đồng thời là owner/migrator** hoặc thuộc role khác có quyền ghi rộng."*
   - [database/runtime-roles.sql](../../../database/runtime-roles.sql) (dòng 7–25) đã hiện thực hóa đúng thiết kế này:
     - Tạo group role `sasms_app` với `NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS`.
     - Cấp `USAGE` trên schema `sasms` và `SELECT` trên toàn bộ bảng/view cho `sasms_app`.
     - Chỉ cấp `INSERT (id, code, idempotency_key, store_id, warehouse_id, customer_id, channel, created_by)` trên `sasms.sales_orders` (cố tình chặn truyền `status`, `total_amount`, `paid_at`, `created_at`).
     - Cấp `INSERT, UPDATE, DELETE` trên `sales_order_items`, `order_devices`, `payments`.
     - Cấp `EXECUTE` trên 4 hàm giao dịch: `receive_device`, `reserve_order`, `complete_sale`, `cancel_order`.
   - [database/tests/runtime_acl.sql](../../../database/tests/runtime_acl.sql) (dòng 6–57) kiểm chứng tự động rằng `sasms_app` **bị từ chối (`insufficient_privilege`)** nếu cố `UPDATE sasms.devices`, `UPDATE sasms.sales_orders SET total_amount=...`, `INSERT INTO sasms.sales_orders(..., status)` hoặc `INSERT INTO sasms.audit_logs`.
2. **Trong source backend tại repo nguồn (`12918f7`):**
   - `backend/src/main/resources/application-postgresql.properties` đọc biến môi trường:
     ```properties
     spring.datasource.url=${SASMS_DB_URL:jdbc:postgresql://127.0.0.1:5432/sasms}
     spring.datasource.username=${SASMS_DB_USERNAME}
     spring.datasource.password=${SASMS_DB_PASSWORD}
     spring.datasource.driver-class-name=org.postgresql.Driver
     spring.sql.init.mode=never
     ```
   - `PostgreSqlConnectionSmokeTest.java` mở kết nối read-only bằng `SASMS_DB_URL`, `SASMS_DB_USERNAME`, `SASMS_DB_PASSWORD` và chạy 5 câu truy vấn:
     1. `SHOW transaction_read_only`
     2. `SHOW server_version`
     3. `SELECT current_database()`
     4. `SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sasms' AND table_type = 'BASE TABLE'`
     5. `SELECT count(*) FROM sasms.roles` và `SELECT count(*) FROM sasms.products`
   - Khi chạy tại repo nguồn (lúc chưa có `runtime-roles.sql` của Kiên), developer chỉ mới có tài khoản `sasms_owner` của container Docker.
   - **Điểm kỹ thuật quan trọng cần lưu ý đối với `PostgreSqlConnectionSmokeTest`:** Trong PostgreSQL, view `information_schema.tables` chỉ trả về những bảng mà `current_user` là owner hoặc có ít nhất một quyền (`SELECT`, `INSERT`, `UPDATE`, ...). Vì `database/runtime-roles.sql` dòng 19 đã cấp `GRANT SELECT ON ALL TABLES IN SCHEMA sasms TO sasms_app;`, bất kỳ login nào kế thừa (`IN ROLE`) `sasms_app` có `inherit` mặc định đều nhìn thấy đủ **37 bảng** trong `information_schema.tables` và đọc được `sasms.roles` (9) cũng như `sasms.products` (2) — tức là **`PostgreSqlConnectionSmokeTest` hoàn toàn chạy PASS bằng login thuộc `sasms_app` mà không cần sửa code Java**.

#### C.2. Các phương án

- **Phương án C1 — Cho phép backend dùng `sasms_owner` trong môi trường local Dev:**
  - Đặt `SASMS_DB_USERNAME=sasms_owner` khi chạy `spring-boot:run` và `PostgreSqlConnectionSmokeTest`.
- **Phương án C2 — Bắt buộc tách bạch: `sasms_owner` chỉ dùng cho DDL/seed; backend và smoke test dùng login kế thừa `sasms_app`:**
  - Sau khi chạy `schema.sql`, `seed.sql` và `runtime-roles.sql` bằng `sasms_owner` trên container cô lập (`-p tv4_s0_check`), tạo một login local (ví dụ `sasms_app_dev` với `LOGIN INHERIT IN ROLE sasms_app`, mật khẩu truyền từ biến môi trường process, không ghi vào Git) và cấu hình `SASMS_DB_USERNAME=sasms_app_dev` cho backend cũng như `PostgreSqlConnectionSmokeTest`.
- **Phương án C3 — Sửa `database/runtime-roles.sql` hoặc `docker-compose.yml` để tạo sẵn user `LOGIN` mặc định:**
  - Thêm lệnh tạo user có mật khẩu vào SQL hoặc Compose. *(Không phù hợp vì vi phạm quy tắc không sửa file DB/Compose của Kiên và không hardcode mật khẩu trong SQL).*

#### C.3. Ưu / nhược điểm từng phương án

| Phương án | Ưu điểm | Nhược điểm / Rủi ro |
|---|---|---|
| **C1 — Dùng `sasms_owner` cho backend** | • Bớt được 1 lệnh tạo role `LOGIN` sau khi bật container Docker. | • **Vi phạm trực tiếp thiết kế bảo mật D54 (mục 4) và `database/README.md` của Kiên.**<br>• Mọi lỗi code ở tầng service/repository trong Sprint 1 (ví dụ vô tình ghi thẳng vào `devices`, `audit_logs`, hoặc truyền `status='PAID'` khi `INSERT sales_orders`) sẽ **không bị PostgreSQL chặn** vì `sasms_owner` là chủ bảng, dẫn đến lọt lỗi nghiêm trọng qua môi trường Dev và chỉ nổ khi lên môi trường dùng `sasms_app`. |
| **C2 — Dùng login kế thừa `sasms_app` cho backend & smoke test (`sasms_owner` chỉ dùng cho admin/DDL)** | • **Tuân thủ tuyệt đối D54, `runtime-roles.sql` và `runtime_acl.sql` của Kiên.**<br>• Phát hiện ngay lập tức mọi truy vấn backend vi phạm ranh giới quyền (ví dụ ghi trực tiếp vào `devices`, `inventory_movements`, `audit_logs`, `outbox_events` thay vì gọi 4 hàm giao dịch).<br>• Không cần sửa `application-postgresql.properties`, `PostgreSqlConnectionSmokeTest.java`, `runtime-roles.sql` hay `docker-compose.yml`.<br>• Vẫn vượt qua đầy đủ 100% assertion trong `PostgreSqlConnectionSmokeTest` (37 bảng, 9 roles, 2 products). | • Cần chạy thêm 1 câu lệnh `psql` cấp `LOGIN ... IN ROLE sasms_app` trên container local trước khi bật backend hoặc chạy smoke test (có thể ghi rõ trong D65). |
| **C3 — Sửa `runtime-roles.sql` để tạo sẵn `LOGIN`** | • Tự động có user khi nạp file SQL. | • **Vi phạm giới hạn bàn giao:** sửa file của Kiên và trái với nguyên tắc ở dòng 2 của `runtime-roles.sql` (*"These are group roles, never public logins. Assign a real LOGIN via secret tooling"*). |

#### C.4. Ảnh hưởng tới source, build, CI và DB

| Khía cạnh | Phương án C1 (`sasms_owner`) | Phương án C2 (Login kế thừa `sasms_app`) |
|---|---|---|
| **Source (`backend/`)** | Không sửa file, nhưng biến môi trường truyền vào trái với D54. | **Không phải sửa bất kỳ file source nào** (`application-postgresql.properties` và `PostgreSqlConnectionSmokeTest.java` đã nhận username/password qua biến môi trường `SASMS_DB_USERNAME` / `SASMS_DB_PASSWORD`). |
| **Build & Smoke Test** | Smoke test PASS nhưng không kiểm chứng được quyền của `sasms_app`. | Smoke test vẫn **PASS** (`SHOW transaction_read_only`, `SHOW server_version`, `current_database()`, `37` bảng trong `information_schema.tables`, `9` roles, `2` products) và đồng thời chứng minh login ít quyền hoạt động đúng với JDBC. |
| **CI (`.github/workflows/`)** | Không nhất quán với `database.yml` và `runtime_acl.sql` (nơi Kiên đã kiểm tra `SET LOCAL ROLE sasms_app`). | Đồng bộ hoàn toàn với triết lý kiểm thử ACL trong `.github/workflows/database.yml` và `database/tests/runtime_acl.sql`. |
| **Database (`database/`)** | Làm vô hiệu hóa lớp bảo vệ cấp cột/bảng của `runtime-roles.sql` khi chạy ứng dụng. | Giữ nguyên 100% các file trong `database/`; phát huy đúng tác dụng của `runtime-roles.sql` ngay từ lúc phát triển backend. |

#### C.5. Khuyến nghị có căn cứ

- **Khuyến nghị:** Chọn **Phương án C2**:
  1. **Nghiêm cấm** cấu hình `sasms_owner` vào `SASMS_DB_USERNAME` khi chạy ứng dụng backend. `sasms_owner` chỉ được dùng trong các lệnh quản trị nạp `schema.sql`, `seed.sql`, `runtime-roles.sql`.
  2. Trên môi trường Docker cô lập (`-p tv4_s0_check`), sau khi nạp `runtime-roles.sql`, tạo một role đăng nhập local kế thừa `sasms_app` (không sửa file SQL của Kiên, không lưu mật khẩu vào repo) và dùng role đó cho cả `spring-boot:run -Dspring-boot.run.profiles=postgresql` lẫn `PostgreSqlConnectionSmokeTest`.
- **Căn cứ:** D54 mục 4 (dòng 79–102), `database/README.md` mục 2 & 5, `database/runtime-roles.sql` (dòng 1–28) và `database/tests/runtime_acl.sql`.

#### C.6. Người có quyền quyết định & Trạng thái

- **Người có quyền quyết định:** **Trần Trung Kiên** (Architect / DBA — chủ sở hữu D54 và `runtime-roles.sql`) và **Vũ Duy Thái** (Fullstack Lead — chủ sở hữu cấu hình backend), **Trịnh Quốc An** (PM) xác nhận.
- **Trạng thái:** **`PENDING`**.

---

## 4. Bảng ghi nhận phê duyệt (Sign-off Record)

> Chỉ cập nhật trạng thái từ `PENDING` sang `APPROVED` khi có xác nhận thực tế của người có thẩm quyền. Không tự điền ngày hoặc chữ ký giả định.

| Mã quyết định | Phương án được chọn | Người phê duyệt | Vai trò | Trạng thái | Ngày / Minh chứng (PR comment / biên bản) |
|---|---|---|---|---|---|
| **`TV4-S0-ARCH-01`** (Vị trí đặt `backend/` & `frontend/`) | **A1 — Root repo kèm `.gitignore` nội bộ** | Trịnh Quốc An (PM)<br>Trần Trung Kiên (Architect) | PM<br>Architect | **APPROVED** | PM An đồng ý 07/11/2026; Architect Kiên phê duyệt & cập nhật D50 v1.1 mục 5.1 ngày 11/11/2026 |
| **`TV4-S0-ARCH-02`** (`JDK 17` vs `Java 26`, thuộc phạm vi `ARC-06`) | **B1 — Giữ `Java 26` + `Spring Boot 4.1.1` cho Sprint 0** | Trịnh Quốc An (PM)<br>Vũ Duy Thái (Fullstack Lead)<br>Trần Trung Kiên (Architect) | PM<br>Fullstack Lead<br>Architect | **APPROVED** | PM An & TV4 đồng ý 07/11/2026; Architect Kiên phê duyệt & chốt ARC-06 trong D50 v1.1 ngày 11/11/2026 |
| **`TV4-S0-ARCH-03`** (DB Owner vs `sasms_app` login) | **C2 — Runtime backend dùng login thuộc nhóm quyền `sasms_app`** | Trịnh Quốc An (PM)<br>Vũ Duy Thái (Fullstack Lead)<br>Trần Trung Kiên (Architect/DBA) | PM<br>Fullstack Lead<br>Architect/DBA | **APPROVED** | PM An & TV4 đồng ý 07/11/2026; DBA Kiên phê duyệt & bổ sung D50 mục 5.3 ngày 11/11/2026 |
