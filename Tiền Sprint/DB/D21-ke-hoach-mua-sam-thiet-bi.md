# D21 — Kế hoạch mua sắm và sử dụng thiết bị

| Thuộc tính | Nội dung |
|---|---|
| Dự án / đầu việc | SASMS — KD-10, phần Kiên phụ trách tiền Sprint |
| Người soạn | Trần Trung Kiên — Architect / DBA |
| Thời kỳ theo phân công | 05–18/09/2026; hạn đầu ra KD-10 là 18/09/2026 |
| Ngày dựng lại hồ sơ | 07/11/2026; không dùng ngày lịch sử làm ngày ký hoặc ngày mua |
| Phiên bản / trạng thái | 1.0 — đề xuất để PM/Sponsor review; chưa có phê duyệt mua, báo giá hoặc biên bản nhận thiết bị |
| Phối hợp | Trịnh Quốc An — PM; Vũ Duy Thái — Dev Lead; Huy — BA; Phát — QA |

## 1. Mục tiêu và căn cứ

Chuẩn bị phương án thiết bị để kiểm chứng nhập kho Serial/IMEI, đọc QR và trình bày chứng từ POS. D21 bàn giao yêu cầu kỹ thuật, phương án sử dụng/mua, dự toán có điều kiện, quy trình chọn–nhận–nghiệm thu và người chịu trách nhiệm. D22 quản lý máy chủ, database, môi trường và CI/CD; không đưa chi phí hạ tầng vào ngân sách thiết bị.

| Nguồn | Căn cứ và cách sử dụng |
|---|---|
| Bảng phân công tiền Sprint, “Theo người” dòng 26; “Phân chia công việc” dòng 16 | Kiên làm chính KD-10, 12 giờ cho cả D21/D22; An và Thái hỗ trợ. Yêu cầu danh sách thiết bị/ngân sách được duyệt là điều kiện nghiệm thu, không phải bằng chứng đã được duyệt |
| [D09](../PM/D09.docx), đoạn 17–19, 59–67 | Gói kiến trúc/DB/security/CI do Kiên phụ trách; phối hợp Dev Lead cho đầu vào thiết bị |
| [D12](../PM/D12.xlsx), sheet 1, E4:E13 | Baseline chi phí tổng 500.000.000 VND; chưa có dòng máy in hoặc scanner |
| [D19](../PM/D19.docx), đoạn 49–64; [D20](../PM/D20.docx), đoạn 34–36 | Nguồn ghi CR-TV4-W6-001 được duyệt 18/10/2026: hoãn máy in nhiệt 80 mm vật lý, dùng HTML/PDF và Browser Print Preview |
| [D33](../BA/D33.docx), đoạn 166–172, KS41/KS45 | QR payload, model máy in, giao tiếp, driver và thiết bị thử còn là câu hỏi cần xác nhận |

Nguồn được dùng làm dữ liệu truy vết. D33 chưa cung cấp kết quả khảo sát; trạng thái và chữ ký trong hồ sơ PM cũ không phê duyệt ngầm cho D21 mới.

## 2. Phân biệt kế hoạch ban đầu và phương án hiện hành

**Kế hoạch tiền Sprint được phục dựng:** tại mốc tháng 9, chuẩn bị yêu cầu để có thể mượn/thuê/mua một đầu quét QR và một máy in POS 80 mm nếu phạm vi được duyệt. Hồ sơ hiện được lập sau tháng 9 nên không ghi các thiết bị đã được đặt, nhận hoặc kiểm thử vào thời điểm đó.

**Đề xuất tại ngày 07/11/2026:** thực hiện theo phạm vi cập nhật trong D19/D20: ưu tiên camera điện thoại sẵn có, mượn scanner 2D nếu cần đối chiếu và dùng HTML/PDF/Print Preview. Không phát hành đơn mua máy in nhiệt hoặc mở lại tích hợp driver từ D21 này. Muốn khôi phục phần cứng vật lý, PM phải xác nhận CR/phạm vi mới trước khi xin ngân sách.

Số điện thoại, scanner, laptop hoặc máy in đang có thực tế chưa được kiểm kê trong repo. “Dùng sẵn/mượn” là phương án cần người sở hữu xác nhận; không đồng nghĩa thiết bị đã sẵn sàng hay tổng chi phí bằng 0.

## 3. Danh mục yêu cầu và lựa chọn

| Mã | Số lượng mục tiêu | Yêu cầu chức năng cần kiểm chứng | Phương án hiện hành | Điều kiện chuyển sang mua/thuê |
|---|---|---|---|---|
| EQ-01 | 1 máy POS/laptop thử nghiệm | Chạy trình duyệt được Thái lựa chọn; có camera hoặc cổng kết nối scanner; hiển thị POS/Print Preview; thiết bị không lưu credential DB trong giao diện | Dùng máy đội dự án, chờ chủ máy đồng ý | Chỉ khi thiếu máy đáp ứng cấu hình ứng dụng và PM duyệt phương án nguồn lực |
| EQ-02 | 1 điện thoại Android và 1 iPhone thử | Camera đọc được QR mẫu thật, trình duyệt/cấp quyền camera phù hợp ứng dụng; kiểm tra điều kiện ánh sáng và mạng thực tế | Mượn theo lịch QA; ghi model, OS, browser thật khi nhận | Cân nhắc thuê/mượn bổ sung trước mua; không mua điện thoại chỉ dựa trên hồ sơ này |
| EQ-03 | 1 đầu quét **2D có đọc QR** | Nhà cung cấp phải xác nhận đọc QR và mã Serial tuyến tính cần dùng; có chế độ nhập phù hợp máy POS, cấu hình layout và ký tự kết thúc; dữ liệu không mất/kèm ký tự ngoài ý muốn | Mượn để đối chiếu với camera, chưa có model xác nhận | Có mẫu QR/Serial và thử tương thích thành công; có báo giá và ngân sách được duyệt |
| EQ-04 | 1 máy in nhiệt giấy 80 mm | Chỉ nếu phần cứng được đưa lại vào phạm vi: có giao tiếp/driver hoặc adapter được Dev Lead chấp nhận; in được tiếng Việt, QR và khổ chứng từ mẫu | **Hoãn mua theo CR hiện có**; dùng HTML/PDF/Browser Print Preview | CR mở lại phạm vi; model/driver/giao tiếp được thử; có ngân sách phê duyệt |
| EQ-05 | 1 bộ tem/giấy/cáp và vật tư thử | Tem QR rõ, đúng payload, bám trên mẫu thiết bị; giấy/cáp đúng model được chọn; vật tư không chứa dữ liệu khách hàng thật | Tận dụng vật tư được phép, in tem thử qua thiết bị sẵn có nếu phù hợp | Phát sinh nhu cầu thực tế và báo giá được An duyệt theo thẩm quyền |

Không coi scanner chỉ đọc mã 1D là đáp ứng QR. Không cam kết một model hỗ trợ giao tiếp, tiếng Việt hoặc tốc độ chỉ từ tên thương mại; yêu cầu tài liệu nhà cung cấp và thử trên đúng cấu hình sẽ sử dụng. D21 không đưa ra model, giá thị trường, hãng hoặc nhà cung cấp khi chưa có báo giá/tài liệu đối chiếu.

Nếu nhà cung cấp đề xuất USB HID, Thái phải xác nhận ứng dụng nhận được luồng nhập, layout bàn phím và suffix; nếu dùng serial/LAN/SDK, phải review adapter, quyền truy cập và công triển khai trước khi chọn. Khả năng phần cứng riêng lẻ không chứng minh tích hợp trình duyệt đã hoạt động.

## 4. Ngân sách và dự toán có điều kiện

Đối chiếu D12: nhân sự **460.800.000**, cloud **15.000.000**, AI **8.000.000**, dự phòng PMO **16.200.000**; tổng **500.000.000 VND**. Cloud 15 triệu không phải quỹ thiết bị. D12 không dành sẵn một khoản scanner/máy in, vì vậy chưa có số tiền phần cứng được phép chi.

D20 đoạn 77 mô tả mô hình Lean 540 giờ và 100–150 USD, khác D12/D13 có 5.220 giờ và 500 triệu VND. PM/Sponsor phải thống nhất baseline trước khi mua. Không cộng cả hai mô hình, tự quy đổi USD hoặc khẳng định dự phòng đã được cấp cho Kiên.

**Phương án hiện hành:** đề xuất chi mua mới thiết bị là **0 VND**. Chi phí thuê/mượn có phí, vận chuyển, bảo quản và vật tư chưa có báo giá nên còn chưa xác định; phải ghi vào phiếu đề nghị trước khi phát sinh. Không suy ra TCO bằng 0.

**Kịch bản phần cứng dự phòng**, chỉ xem xét khi CR và ngân sách được mở lại:

| Hạng mục | SL | Trần đơn giá kế hoạch (VND) | Trần thành tiền (VND) | Trạng thái |
|---|---:|---:|---:|---|
| Scanner 2D/QR | 1 | 2.000.000 | 2.000.000 | Chưa chọn model; chưa có báo giá |
| Máy in nhiệt 80 mm | 1 | 3.000.000 | 3.000.000 | Hoãn trong phạm vi hiện hành |
| Tem/giấy/cáp/vật tư | 1 bộ | 500.000 | 500.000 | Chỉ khi cần và tương thích |
| Vận chuyển/thiết lập phục vụ nghiệm thu | 1 gói | 500.000 | 500.000 | Cần xác nhận VAT/phí/phạm vi |
| Cộng cơ sở | | | **6.000.000** | Trần lập kế hoạch |
| Dự phòng kịch bản 10% | | | **600.000** | Đề xuất, chưa được cấp |
| Tổng trần xin xem xét | | | **6.600.000** | Không phải ngân sách mua đã duyệt |

Các con số là **giới hạn đề xuất cho bài toán lập kế hoạch**, không phải giá thị trường hoặc báo giá của nhà cung cấp. Báo giá thực phải chỉ rõ VAT, phí giao, bảo hành, vật tư kèm theo và hiệu lực. Nếu vượt trần hoặc không tìm được cấu hình phù hợp trong trần, dùng phương án mượn/thuê hoặc lập CR; không tự giảm tiêu chí kỹ thuật để vừa giá.

Khoản 6,6 triệu chưa nằm trong các dòng D12. Nếu được chấp thuận, PM phải chỉ rõ dòng chi được điều chuyển và baseline sửa đổi; nếu dùng dự phòng PMO thì phần còn lại dự kiến là 9,6 triệu, chỉ có hiệu lực sau quyết định thực tế. Nếu không điều chuyển được, phải xin tăng tổng ngân sách qua CCB/Sponsor; không ghi tổng vẫn 500 triệu rồi thêm khoản chi ngoài sổ.

[Bảng dự toán hỗ trợ](D21-du-toan-thiet-bi.xlsx) tách ba phần: kịch bản có điều kiện, đối chiếu D12 và danh mục tài sản chờ kiểm kê. File có công thức và cached value; mở Excel để tính lại khi thay dữ liệu. Không điền số serial thiết bị, phiếu mua hoặc ngày nhận giả.

## 5. Quy trình chọn và phê duyệt

1. **Kiên/Huy/Thái làm rõ nhu cầu:** chốt mẫu QR/Serial, thiết bị POS/OS/browser, cách xuất chứng từ; kiểm kê máy sẵn có và khả năng mượn. Kết quả là phiếu yêu cầu kỹ thuật, chưa phải đơn mua.
2. **An xác nhận phạm vi và kinh phí:** đối chiếu CR hiện hành; xác định người có thẩm quyền và dòng ngân sách. Chỉ thu thập phương án phần cứng đã có nhu cầu được phép xét.
3. **Kiên lập bảng so sánh:** tối thiểu hai phương án nguồn cung khi có thể, gồm mượn/thuê/mua; so chi phí đầy đủ, tương thích, công tích hợp, lead time, bảo hành và điều kiện trả hàng. Mọi báo giá phải lưu nguồn/ngày và không tự gắn nhãn “đã duyệt”.
4. **Thái/Phát thử mẫu:** chỉ nhận phương án đạt các tiêu chí bắt buộc ở mục 7. Nếu chưa có mẫu, ghi “chưa kiểm chứng” và nêu rủi ro trong hồ sơ lựa chọn.
5. **An trình PM/Sponsor/CCB theo thẩm quyền:** Kiên ký đánh giá kỹ thuật, không thay chữ ký duyệt chi. Phiếu mua/thuê chỉ phát hành sau khi người có thẩm quyền xác nhận. Không tự suy ra một hạn mức phê duyệt tiền khi D19 chưa quy định rõ.
6. **Nhận, nghiệm thu và bàn giao:** đối chiếu hàng thực với phiếu được duyệt; ghi serial/model, tình trạng, chứng từ và người giữ. Thiết bị không đạt được cách ly, thông báo nhà cung cấp/chủ cho mượn và xử lý đổi/trả theo điều khoản.

## 6. Trách nhiệm và lịch phụ thuộc

| Hoạt động | R — thực hiện | A — chịu trách nhiệm cuối | C / I |
|---|---|---|---|
| Yêu cầu kỹ thuật, kiểm kê và so sánh | Kiên | Kiên | C: Thái, Huy, Phát; I: An |
| Quyết định phạm vi/CR | An | An | C: CCB/Sponsor theo quy chế; I: cả nhóm |
| Đề nghị điều chuyển ngân sách | An | An | C: Kiên; I: Sponsor |
| Cho phép chi theo phê duyệt dự án | An trình | Sponsor/người được ủy quyền hợp lệ | C: Kiên, Thái; không coi đây là phê duyệt đã có |
| Mua/thuê và quản lý chứng từ | An | An | C: Kiên; I: Phát |
| Kiểm tra tương thích ứng dụng | Thái | Thái | C: Kiên, Phát |
| Nghiệm thu kỹ thuật thiết bị | Kiên, Phát | Kiên | C: Thái, người giao; I: An |
| Quản lý mượn/trả và người giữ thiết bị | Người nhận được ghi trong biên bản | An | C: Kiên; I: chủ thiết bị |

Mỗi dòng có một đầu mối A. Chức danh Sponsor/người được ủy quyền ở dòng duyệt chi cần PM ghi đúng người thực tế; không dùng tên giữ chỗ làm chữ ký.

| Mốc kế hoạch ban đầu | Đầu ra dự kiến / phụ thuộc | Trạng thái khi dựng lại |
|---|---|---|
| 12–14/09 | Kiểm kê, nhu cầu, yêu cầu mẫu QR/Serial | Kế hoạch lịch sử; chưa có biên bản hoàn thành |
| 15–16/09 | So sánh mượn/thuê/mua, thử tương thích, đối chiếu chi phí | Chưa có model/báo giá/test phần cứng trong repo |
| 17/09 | Hoàn thiện đề nghị và điều kiện phê duyệt | Chưa có chữ ký cho bản này |
| 18/09 | Bàn giao D21/D22 cho Cổng khởi động | Mốc theo workbook, không tự xác nhận gate đã qua |
| Trước lần demo thực tế hiện hành | An xác nhận máy mượn; Thái/Phát kiểm tra camera/Print Preview; backup phương án nhập tay có kiểm soát | Lập lịch mới sau khi người sở hữu xác nhận; không backdate |

## 7. Tiêu chí nghiệm thu và bằng chứng

| Mã | Kiểm tra cần thực hiện trên thiết bị thật | Điều kiện chấp nhận | Bằng chứng cần lưu |
|---|---|---|---|
| AC-EQ01 | Đọc bộ tem QR gồm token thiết bị và mẫu Serial BA cung cấp | Dữ liệu nhận trùng nội dung mẫu; không mất ký tự/không thêm suffix ngoài hợp đồng nhập | Model/serial, OS/browser/layout; bộ tem và file kết quả đối chiếu |
| AC-EQ02 | Thử camera Android/iOS và scanner mượn trong ánh sáng/khoảng cách dự kiến | Không nhận nhầm thiết bị; đo từng cấu hình riêng, nêu số mẫu và lỗi | Video/số đo có thời điểm; số lỗi; người thử thật |
| AC-EQ03 | Gửi lại/trùng Serial, IMEI, QR lạ hoặc payload lỗi | API/DB từ chối hoặc xử lý lặp đúng hợp đồng; quét QR không được thực thi thành SQL/lệnh | Request đã che dữ liệu nhạy cảm, log kết quả và DB không đổi sai |
| AC-EQ04 | HTML/PDF/Browser Print Preview hiện hành | Nội dung, tổng tiền và Serial trùng giao dịch; không lộ PII không cần thiết; in lại không tạo giao dịch mới | Bản preview mẫu và đối chiếu với đơn mô phỏng |
| AC-EQ05 | Chỉ khi máy in vật lý được mở lại: tiếng Việt/QR, cắt giấy, khổ, kết nối mất–nối lại | Mẫu in đọc được; driver/adapter đúng cấu hình; lỗi in không tạo lại đơn/thu tiền | Bản in mẫu, phiên bản driver/firmware và log lỗi/retry |
| AC-EQ06 | Nhận/mượn và bàn giao | Đúng số lượng/model/phụ kiện/tình trạng; có người giữ, hạn trả hoặc điều khoản bảo hành | Phiếu nhận thật, chứng từ mua/thuê hoặc xác nhận mượn |

Mục tiêu QR dưới một giây trong nguồn là mục tiêu toàn luồng camera/trình duyệt/API; không suy ra từ tốc độ decode của scanner hoặc SQL lookup. Quy mô mẫu và ngưỡng lỗi cần Huy/Phát xác nhận trước pilot. Các kiểm thử SQL đã có ở Sprint 0/1 chỉ là bằng chứng DB, chưa nghiệm thu phần cứng theo các hàng trên.

## 8. Quản lý tài sản, rủi ro và bàn giao

Sổ thiết bị cần có mã tài sản nội bộ, model/serial thật, hình thức sở hữu, người/chứng từ giao, ngày nhận, tình trạng, phụ kiện, người giữ, vị trí, thời hạn mượn/trả hoặc bảo hành và lịch sử lỗi/sửa. Người nhận kiểm đếm với bên giao, chụp tình trạng khi được phép và lưu file tại vị trí An chỉ định. Thiết bị mượn không được thay firmware/driver/cấu hình ngoài phạm vi chủ sở hữu cho phép; trước trả xóa dữ liệu thử/ghép nối và đối chiếu phụ kiện. Hiện chưa có thiết bị nào được đánh dấu đã nhận trong hồ sơ này.

| Rủi ro | Dấu hiệu / ứng phó | Phụ trách |
|---|---|---|
| Thiếu thiết bị hoặc lịch mượn đổi | Xác nhận trước demo; chuẩn bị thiết bị thay thế đã kiểm tra; không coi mượn miệng là sẵn sàng | An / Kiên |
| Scanner không đọc QR hoặc sai layout/suffix | Đối chiếu yêu cầu 2D và thử mẫu trước chọn; đổi cấu hình hoặc đổi thiết bị | Kiên / Thái |
| Printer/driver kéo lại scope đã hoãn | Dừng đề nghị mua, đối chiếu CR; dùng preview theo phạm vi hiện hành | An / Thái |
| Chi phí ngoài baseline | Chưa phát hành đơn; trình nguồn chi/CR và đánh giá tác động trước phát sinh | An |
| Camera thiếu quyền, ánh sáng/mạng không đạt | Pilot đúng điện thoại/browser; chuẩn bị thao tác nhập mã thủ công có kiểm tra và audit | Thái / Phát |
| Hỏng hoặc thất lạc thiết bị mượn | Ghi người giữ, tình trạng và hạn trả; báo ngay An/chủ thiết bị, không tự sửa ngoài thỏa thuận | Người giữ / An |

## 9. Trạng thái phê duyệt

| Nội dung | Người xác nhận cần có | Trạng thái của bản 1.0 |
|---|---|---|
| Yêu cầu QR/Serial và chứng từ | Huy, đại diện kho/thu ngân | Chờ xác nhận |
| Tương thích thiết bị/app | Thái, Kiên, Phát | Chưa có test thiết bị thật |
| Phạm vi mua/thuê hiện hành | An, CCB theo quy chế | Chờ review; máy in vật lý đang hoãn theo nguồn |
| Ngân sách/dòng điều chuyển | An, Sponsor/người có thẩm quyền | Chưa duyệt D21 hoặc trần 6,6 triệu |
| Nhận và bàn giao | Người giao, người nhận thực tế | Chưa phát sinh biên bản |

Kết quả bàn giao là kế hoạch D21 và bảng dự toán có điều kiện. Chữ ký ngân sách, báo giá, chứng từ mua/thuê, nhận tài sản và kết quả kiểm tra phần cứng chỉ được bổ sung khi có sự kiện và bằng chứng thật.
