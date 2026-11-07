# D63 — Quy ước Coding, Git và Pull Request

**Dự án:** Smart Electronic Supermarket Management System (SASMS / Vendora)

**Hạng mục:** S0-24

**Trạng thái:** Bản dự thảo; các nội dung ghi **“Đề xuất - chờ duyệt”** chỉ có hiệu lực sau khi PM/Architect phê duyệt.

## 1. Mục đích và phạm vi áp dụng

Tài liệu này thống nhất cách tổ chức mã nguồn, làm việc với Git, tạo Pull Request (PR), review và kiểm thử cho repository SASMS. Phạm vi áp dụng gồm:

- backend Spring Boot/Maven;
- frontend React/Vite;
- cấu hình chạy ứng dụng;
- thay đổi PostgreSQL schema;
- tài liệu kỹ thuật đi kèm thay đổi mã nguồn.

D63 cụ thể hóa định hướng phối hợp trong D23: GitHub lưu mã nguồn, PR và Issue Log; System Architect/Technical Lead chịu trách nhiệm thiết kế kỹ thuật và review code; QA Lead chịu trách nhiệm xác nhận chất lượng; mục tiêu chất lượng là Zero Bug P1. D63 không xác nhận branch protection, CI hoặc cơ chế tự động nào đã được bật.

## 2. Cấu trúc repository và tổ chức tài liệu

### 2.1 Cấu trúc đã xác lập tại repository quản lý dự án (`D:\software-project-management-an`)

Repository đích của nhóm hiện được PM Trịnh Quốc An và Architect/DBA Trần Trung Kiên tổ chức theo giai đoạn (`Tiền Sprint`, `Sprint N/<Vai trò>/`) kết hợp với bộ baseline PostgreSQL 16 ở root:

```text
repository-root/
├── .github/workflows/
│   └── database.yml                 # CI kiểm chứng PostgreSQL 16 của Kiên
├── database/                        # Schema 37 bảng, seed, runtime-roles, checks, tests, evidence
├── scripts/                         # validate_db.py, generate_db_artifacts.py
├── Tiền Sprint/
│   └── BA/ · DB/ · PM/
├── Sprint 0/
│   ├── AI/ · BA/ · DB/ · PM/ · Tester/
│   └── Fullstack/
│       └── TV4/
│           ├── README.md
│           └── docs/sprint0/        # Tài liệu bàn giao Sprint 0 của TV4
├── Sprint 1/
│   └── AI/ · BA/ · DB/ · Fullstack/ · PM/ · Tester/
├── .env.example
├── .gitattributes
├── .gitignore
└── docker-compose.yml
```

### 2.2 Khung mã nguồn ứng dụng Spring Boot + React (**Đề xuất - chờ duyệt tích hợp**)

Tại repository nguồn tham chiếu (`D:\smart-electronic-supermarket-sprint0`), khung mã nguồn ứng dụng có cấu trúc:

```text
<vị-trí-chờ-PM-Architect-duyệt>/
├── backend/
│   └── pom.xml             # Spring Boot 4.1.1, Maven Wrapper, Java 26 (đang chờ duyệt so với JDK 17)
└── frontend/
    └── package.json        # React 18.3.1, Vite 5.4.10, Axios 1.7.7
```

Hai thư mục `backend/` và `frontend/` hiện **chưa được sao chép vào repo đích** và đang chờ PM/Architect duyệt vị trí đặt source (đề xuất đặt ở root cạnh `database/`).

### 2.3 Quy ước phạm vi thay đổi (phân biệt quy ước đã có và đề xuất chờ duyệt)

- **Quy ước thư mục tài liệu đã có trong repo An:** Tài liệu bàn giao theo giai đoạn và vai trò đặt tại `Sprint N/<Vai trò>/` (hoặc `Tiền Sprint/<Vai trò>/`); riêng hồ sơ TV4 Sprint 0 đặt tại `Sprint 0/Fullstack/TV4/` (`Sprint 0/Fullstack/TV4/docs/sprint0/`). Không tự tạo thư mục `docs/` ở root repository của An và không thay đổi quy định chung của nhóm nếu PM chưa phê duyệt.
- **Quy ước bảo vệ tài nguyên chung:** Không tự ý sửa `database/`, `scripts/`, `docker-compose.yml`, `.env.example`, `.gitattributes`, `.gitignore` ở root hoặc tài liệu trong các nhánh `PM/`, `BA/`, `DB/`, `Tester/`, `AI/` của thành viên khác khi chưa được chủ sở hữu và PM/Architect phê duyệt.
- **Đề xuất - chờ duyệt (áp dụng sau khi `backend/` và `frontend/` được tích hợp):**
  - Không di chuyển `backend/`, `frontend/` hoặc đổi mô hình Spring Boot + React nếu chưa có phê duyệt kiến trúc.
  - Mã backend thuộc `backend/`; mã frontend thuộc `frontend/`.
  - Không commit thư mục sinh tự động: `backend/target/`, `frontend/node_modules/`, `frontend/dist/`.
  - Không commit cấu hình IDE (`.idea/`, `.vscode/`, `*.iml`), file hệ điều hành hoặc `*.log`.

## 3. Quy tắc đặt tên branch

> **Đề xuất - chờ duyệt:** D23 có mục Branching Strategy nhưng chưa ghi quy tắc chi tiết.

Định dạng đề xuất:

```text
<type>/<task-id>-<mo-ta-ngan>
```

- `type`: `feature`, `fix`, `docs`, `test`, `refactor`, `chore`, `hotfix`.
- `task-id`: mã công việc, ví dụ `S0-24`.
- Mô tả dùng chữ thường không dấu, số và dấu gạch ngang; không dùng khoảng trắng.
- Một branch phục vụ một task hoặc một thay đổi có phạm vi rõ ràng.

Ví dụ: `docs/S0-24-coding-git-pr-conventions`, `feature/POS-12-create-sale`, `fix/IMEI-08-duplicate-check`.

Tên nhánh chính, nhánh tích hợp và quy tắc cho `hotfix` phải được PM/Architect chốt trước khi áp dụng. Không mặc định repository đang dùng `main`, `develop` hoặc GitFlow.

## 4. Quy tắc commit

> **Đề xuất - chờ duyệt:** D23 chưa quy định cú pháp commit.

Định dạng đề xuất:

```text
<type>(<scope>): <mo-ta-ngan>
```

- `type`: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`.
- `scope`: vùng tác động như `backend`, `frontend`, `pos`, `serial-imei`, `db`, `docs`.
- Dòng tiêu đề dùng câu mệnh lệnh, mô tả đúng thay đổi, không kết thúc bằng dấu chấm.
- Commit phải nhỏ, tự hoàn chỉnh và không trộn refactor không liên quan với tính năng/sửa lỗi.
- Ghi mã task trong phần nội dung hoặc footer khi có hệ thống quản lý task thống nhất.
- Không commit secret, generated files, mã đang không build được hoặc đoạn mã debug tạm thời.
- Không dùng thông điệp mơ hồ như `update`, `fix bug`, `done`.

Ví dụ: `docs(sprint0): add S0-24 coding conventions`.

## 5. Git workflow và Pull Request

D23 xác nhận GitHub là nơi lưu mã nguồn, PR và Issue Log. Quy trình chi tiết dưới đây là **Đề xuất - chờ duyệt**:

1. Nhận task đã rõ phạm vi và acceptance criteria; tạo branch từ nhánh gốc do nhóm chỉ định.
2. Cập nhật branch với nhánh gốc trước khi mở PR; tự xử lý conflict và kiểm tra lại sau khi xử lý.
3. Chạy các kiểm tra phù hợp với phạm vi thay đổi; ghi kết quả thực tế vào PR.
4. Mở PR cho một mục tiêu duy nhất. PR phải nêu: mục tiêu, mã task, phạm vi, cách kiểm thử, kết quả, ảnh hưởng API/schema/config và rủi ro còn lại.
5. Gắn reviewer theo phạm vi: Architect/Technical Lead cho thay đổi kỹ thuật/kiến trúc; QA Lead cho bằng chứng kiểm thử; chủ sở hữu nghiệp vụ khi acceptance criteria bị tác động.
6. Tác giả xử lý từng nhận xét, cập nhật code hoặc giải thích có căn cứ; không tự đóng nhận xét chưa được thống nhất.
7. Chỉ merge khi checklist mục 12 đạt yêu cầu và không còn blocker.

Không ghi “CI pass”, “branch protection pass” hoặc “required checks pass” nếu repository chưa có bằng chứng các cơ chế đó đang hoạt động. Phương thức merge, số approval tối thiểu, người có quyền merge và quy tắc xóa branch đang chờ PM/Architect duyệt.

## 6. Quy trình code review

Reviewer kiểm tra theo thứ tự:

1. **Đúng phạm vi:** đáp ứng task/acceptance criteria, không thêm business rule hoặc module ngoài phạm vi.
2. **Đúng thiết kế:** không thay đổi kiến trúc; API, dữ liệu, lỗi và cấu hình nhất quán.
3. **Đúng hành vi:** happy path, validation, lỗi, dữ liệu biên và tương thích ngược đã được xem xét.
4. **Chất lượng mã:** tên rõ nghĩa, hàm/lớp/component có trách nhiệm tập trung, không lặp mã đáng kể, không còn mã chết hoặc log nhạy cảm.
5. **Kiểm thử:** test phù hợp đã có và chạy; kết quả dùng đúng trạng thái `PASS`, `FAIL`, `NOT RUN`, `BLOCKED` theo checklist hiện tại.
6. **Vận hành và bảo mật:** không lộ secret; thay đổi config/schema có hướng dẫn và phương án rollback khi cần.
7. **Tài liệu:** README/API/tài liệu liên quan phản ánh đúng trạng thái repository nếu thay đổi tác động đến chúng.

Nhận xét review cần chỉ rõ vị trí, vấn đề, ảnh hưởng và hướng xử lý mong đợi. Phân loại đề xuất: `BLOCKER` (phải sửa trước merge), `MAJOR` (nên sửa trước merge), `MINOR` (cải thiện nhỏ), `QUESTION` (cần làm rõ). **Đề xuất - chờ duyệt:** nhóm chưa xác nhận bộ nhãn và thời hạn review cụ thể.

## 7. Quy ước Java/Spring Boot

Source hiện tại dùng Spring Boot `4.1.1`, Java `26`, Maven và `spring-boot-starter-webmvc`. Không tự hạ/nâng phiên bản hoặc thay framework.

> Các quy tắc dưới đây là **Đề xuất - chờ duyệt** nếu chưa được cấu hình bằng formatter/static analysis trong repository.

- Package dùng chữ thường, bắt đầu từ namespace dự án `com.smartelectronicsupermarket`.
- Class/interface/enum dùng `PascalCase`; method/biến dùng `camelCase`; hằng số dùng `UPPER_SNAKE_CASE`.
- Controller chỉ xử lý HTTP và điều phối; business logic đặt ở service; truy cập dữ liệu không đặt trực tiếp trong controller.
- Dùng constructor injection; tránh field injection.
- Request/response API dùng DTO rõ ràng; không để entity persistence trở thành contract API mặc định.
- Validation đặt tại biên vào; lỗi trả về có HTTP status và cấu trúc nhất quán, không lộ stack trace hoặc dữ liệu nhạy cảm.
- Không bắt `Exception` chung nếu không xử lý được lỗi; không bỏ qua exception.
- Log đủ ngữ cảnh để chẩn đoán nhưng không log password, token, secret, dữ liệu thanh toán hoặc thông tin nhạy cảm.
- Public API hoặc logic khó hiểu cần Javadoc ngắn gọn; không viết comment lặp lại code.
- Dependency mới, thay đổi version Java/Spring Boot hoặc thay đổi ranh giới module cần Architect phê duyệt.

## 8. Quy ước React/JavaScript

Source hiện tại dùng React `18.3.1`, React DOM `18.3.1`, Vite `5.4.10` và Axios `1.7.7`. `package.json` hiện có script `dev`, `build`, `preview`; chưa có script `test` hoặc `lint`.

> Các quy tắc dưới đây là **Đề xuất - chờ duyệt**; repository chưa thể hiện ESLint/Prettier hoặc test runner trong `package.json`.

- Component dùng `PascalCase`; function, biến và hook dùng `camelCase`; custom hook bắt đầu bằng `use`.
- Component tập trung vào một trách nhiệm; tách xử lý gọi API khỏi phần trình bày khi logic bắt đầu phức tạp hoặc cần tái sử dụng.
- Không gọi API bằng URL rải rác trong component; dùng cấu hình base URL và lớp/module API thống nhất.
- Mọi trạng thái gọi API phải xử lý tối thiểu: loading, success, empty và error khi có thể xảy ra.
- Không mutating trực tiếp state; side effect đặt trong hook phù hợp và khai báo dependency đầy đủ.
- Không dùng `dangerouslySetInnerHTML` nếu chưa có biện pháp sanitize và review bảo mật.
- Không đưa secret vào source hoặc biến môi trường phía client; mọi giá trị gửi xuống trình duyệt phải được coi là công khai.
- Không thêm dependency, state-management framework hoặc đổi build tool nếu chưa được Architect duyệt.
- Trước PR frontend, chạy `npm run build`. Chỉ ghi `npm test`/`npm run lint` sau khi các script tương ứng được bổ sung và phê duyệt.

## 9. Tiêu chuẩn unit test và integration test

Checklist hiện tại đã dùng `./mvnw.cmd clean test` cho backend và `npm run build` cho frontend; các endpoint đã được kiểm tra gồm `/api/health`, `/api/pos/status`, `/api/serial-imei/status`. Frontend dev server mặc định được checklist theo dõi ở cổng `5173`; backend ở cổng `8080`.

Yêu cầu theo phạm vi:

- Mỗi bug fix phải có test tái hiện lỗi khi hạ tầng test hiện có hỗ trợ.
- Unit test không phụ thuộc mạng, thời gian thực hoặc dữ liệu dùng chung; tên test thể hiện điều kiện và kết quả mong đợi.
- Integration test kiểm tra ranh giới thật cần thiết như HTTP, persistence hoặc tích hợp module; dữ liệu test phải độc lập và dọn dẹp được.
- Test bao phủ happy path, validation quan trọng, lỗi nghiệp vụ và trường hợp biên liên quan đến thay đổi.
- Không bỏ qua test lỗi để merge; nếu `NOT RUN` hoặc `BLOCKED`, PR phải nêu lý do, rủi ro và người chấp thuận.
- Bằng chứng test trong PR phải ghi lệnh, môi trường và kết quả thực tế.

**Đề xuất - chờ duyệt:** chuẩn coverage tối thiểu, thư viện test backend, test runner frontend và môi trường PostgreSQL integration test. `pom.xml` hiện chưa khai báo dependency test riêng; `package.json` chưa có script test. Không đặt ngưỡng coverage trước khi nhóm bổ sung công cụ và phê duyệt tiêu chuẩn.

## 10. Quy định xử lý secrets và cấu hình

- Tuyệt đối không commit password, API key, access token, private key, connection string có thông tin xác thực hoặc dữ liệu thật nhạy cảm.
- `.gitignore` tại root repo đích hiện đã loại trừ `.env`, đồng thời giữ lại `!.env.example`. Tại repo nguồn, `.gitignore` còn loại trừ `frontend/.env` và `frontend/.env.*`, cho phép `!frontend/.env.example`; các quy tắc này cần được bổ sung (hoặc đặt trong `frontend/.gitignore`) khi sao chép `frontend/` sang repo đích.
- `.env.example` ở root và `frontend/.env.example` chỉ chứa tên biến và giá trị mẫu không bí mật.
- Biến môi trường frontend được bundle vào trình duyệt không được xem là secret.
- Nếu secret đã vào Git, phải báo ngay PM/Architect, thu hồi/rotate secret và đánh giá phạm vi lộ; chỉ xóa khỏi commit mới là chưa đủ.
- Không ghi secret vào log, ảnh chụp, PR description, test fixture hoặc tài liệu.

**Đề xuất - chờ duyệt:** chuẩn tên biến môi trường, nơi quản lý secret theo từng môi trường và quy tắc ignore/example cho cấu hình backend. `.gitignore` hiện chưa thể hiện ngoại lệ cụ thể cho file cấu hình bí mật backend, vì vậy mọi file backend có thông tin xác thực phải được kiểm tra thủ công trước commit.

## 11. Quy định thay đổi PostgreSQL schema

D23 giao System Architect/Technical Lead thiết kế ERD PostgreSQL. `pom.xml` hiện đã có PostgreSQL JDBC driver scope `runtime`, nhưng chưa có Flyway, Liquibase hoặc công cụ migration khác. JDBC driver không đồng nghĩa cơ chế migration tự động đã tồn tại.

Mọi thay đổi schema phải:

- liên kết với task/acceptance criteria và mô tả tác động dữ liệu;
- được Architect review, đồng thời có QA đánh giá kịch bản kiểm thử;
- cập nhật ERD/data dictionary/API liên quan khi cấu trúc dữ liệu thay đổi;
- nêu cách áp dụng, thứ tự triển khai, tương thích ngược và phương án rollback/khôi phục;
- tránh sửa/xóa dữ liệu sản xuất bằng thao tác thủ công không có script và review;
- không sửa migration đã được dùng ở môi trường dùng chung; tạo migration kế tiếp để hiệu chỉnh.

**Đề xuất - chờ duyệt:** chọn Flyway hoặc Liquibase, quy ước tên/version migration, baseline hiện tại và chính sách destructive migration. Cho đến khi được duyệt, không tự thêm công cụ migration hay thay đổi schema ngoài phạm vi task.

## 12. Checklist trước khi merge PR

- [ ] PR liên kết đúng task và mô tả rõ phạm vi/ngoài phạm vi.
- [ ] Không có thay đổi kiến trúc, dependency hoặc schema chưa được phê duyệt.
- [ ] Tác giả đã tự review diff; không còn debug code, generated files hoặc thay đổi ngoài ý muốn.
- [ ] Không có secret hoặc dữ liệu nhạy cảm trong code, config, log, test và tài liệu.
- [ ] Backend liên quan đã chạy `./mvnw.cmd clean test` và ghi kết quả, hoặc ghi rõ `NOT RUN`/`BLOCKED`.
- [ ] Frontend liên quan đã chạy `npm run build` và ghi kết quả, hoặc ghi rõ `NOT RUN`/`BLOCKED`.
- [ ] Endpoint/luồng bị ảnh hưởng đã được kiểm tra; có bằng chứng phù hợp.
- [ ] Test mới/cập nhật bao phủ thay đổi trong khả năng hạ tầng hiện tại.
- [ ] API, schema, config và tài liệu liên quan đã được cập nhật đồng bộ.
- [ ] Conflict đã xử lý và kiểm tra lại trên phiên bản mới nhất của nhánh gốc được nhóm chỉ định.
- [ ] Nhận xét `BLOCKER`/`MAJOR` đã được xử lý hoặc có chấp thuận ngoại lệ rõ ràng.
- [ ] Không còn bug P1 mở liên quan đến thay đổi.
- [ ] Reviewer/PR evidence đã được đính kèm; không tự nhận CI hoặc branch protection đã chạy.
- [ ] Đã có approval theo quy tắc được PM/Architect phê duyệt.

## 13. Definition of Done

Một thay đổi được coi là Done khi:

- đáp ứng acceptance criteria và không vượt phạm vi MVP đã duyệt;
- code, config, schema và tài liệu liên quan nhất quán;
- các kiểm tra phù hợp đã chạy và có bằng chứng; mọi `FAIL` đã được xử lý;
- không còn bug P1 liên quan; các lỗi còn lại được ghi nhận, đánh giá và chấp thuận đúng thẩm quyền;
- không lộ secret, không đưa generated files vào Git;
- code review hoàn tất, các nhận xét bắt buộc đã xử lý;
- PR được merge vào nhánh đích do nhóm chỉ định và trạng thái task được cập nhật.

**Đề xuất - chờ duyệt:** điều kiện approval tối thiểu, bằng chứng demo/E2E bắt buộc theo loại task và người có thẩm quyền xác nhận Done. D23 nêu cam kết DoD nhưng chưa cung cấp tiêu chí chi tiết trong nội dung đọc được.

## 14. Các vấn đề cần PM/Architect phê duyệt

1. Tên và vai trò của nhánh chính, nhánh tích hợp; mô hình branching chính thức.
2. Cú pháp branch/commit đề xuất và cách gắn mã task.
3. Số approval tối thiểu, reviewer bắt buộc, người được merge, phương thức merge và quy tắc xóa branch.
4. SLA review và bộ nhãn `BLOCKER`/`MAJOR`/`MINOR`/`QUESTION`.
5. Có bật branch protection/required checks hay không; CI nào sẽ được triển khai. Hiện tại tài liệu này không xác nhận các cơ chế đó đã bật.
6. Formatter, linter, static analysis và coding style tự động cho Java/JavaScript.
7. Dependency/framework test, test runner frontend và ngưỡng coverage.
8. Chuẩn quản lý secret/config cho backend và các môi trường triển khai.
9. Công cụ migration PostgreSQL, baseline, quy ước version và chính sách destructive change.
10. Tiêu chí approval cuối cùng và bằng chứng bắt buộc để xác nhận Definition of Done.
