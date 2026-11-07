# D53 Figma – Manifest link frame chờ QA và PO review

**File:** https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55

**Số lượng theo manifest công cụ:** 52 frame; 7 A + 10 B + 10 C + 9 D + 8 E + 8 F.
**Trạng thái bằng chứng:** Có record và node link trong manifest; chưa xác minh trực quan độc lập, chưa QA, chưa PO duyệt, prototype chưa xác minh.

## Quy ước trạng thái bằng chứng

- **Có record:** Có dòng frame trong manifest do công cụ tạo ra. Không thay thế kiểm tra Figma trực tuyến.
- **Có link:** Có URL node cụ thể. Link tồn tại trong tài liệu không chứng minh frame hiển thị đúng hoặc người review có quyền truy cập.
- **QA:** Chỉ ghi PASS khi có kiểm tra trực quan và bằng chứng theo checklist. Hiện toàn bộ là `NOT VERIFIED`.
- **PO:** Chỉ ghi APPROVED khi có comment/ảnh/biên bản gắn đúng frame và phiên bản. Hiện toàn bộ là `PENDING`.

Tất cả 52 dòng bên dưới mặc định có trạng thái: `RECORD=YES`, `LINK=RECORDED`, `QA=NOT VERIFIED`, `PO=PENDING`, trừ khi có bằng chứng mới được điền vào hồ sơ review.

## Đối chiếu độ phủ với đặc tả

| Nhóm | Record/link trong manifest | Dấu vết mobile | QA | PO | Khoảng trống cần review |
| --- | ---: | --- | --- | --- | --- |
| A Inventory | 7/7 | A07 | NOT VERIFIED | PENDING | Mã A02-A06 khác thứ tự handoff; cần map theo variant |
| B Receiving | 10/10 | B10 | NOT VERIFIED | PENDING | Handoff dự kiến hai trạng thái mobile; manifest chỉ có B10; B10 có nguy cơ tràn bố cục |
| C QR | 10/10 | Không có frame mobile được ghi rõ | NOT VERIFIED | PENDING | Thiếu dấu vết `Mobile Found` và `Forbidden Device` so với handoff |
| D POS | 9/9 | Không có frame mobile được ghi rõ | NOT VERIFIED | PENDING | Thiếu `Mobile Product To Cart`, populated cart và reservation uncertain; TTL expired còn PENDING nghiệp vụ |
| E Payment | 8/8 | Không có frame mobile được ghi rõ | NOT VERIFIED | PENDING | Thiếu `Mobile Status` và forbidden theo handoff |
| F Receipt | 8/8 | Không có frame mobile được ghi rõ | NOT VERIFIED | PENDING | Thiếu `Mobile Receipt`, print success và 80 mm pending frame theo handoff |

`Không có dấu vết mobile` nghĩa là manifest không có frame được đặt tên/ghi nhận là mobile; không kết luận Figma chắc chắn không có layout mobile ngoài manifest. C-F cần QA/PO xác minh trực tiếp.

## A – Inventory Overview

| Frame | Variant | Figma |
|---|---|---|
| A01 | Tổng quan tồn kho default | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=3-2) |
| A02 | Empty | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-2) |
| A03 | Loading | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-89) |
| A04 | Forbidden | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-176) |
| A05 | Load error | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-263) |
| A06 | Filtered results | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-350) |
| A07 | Mobile | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=17-2) |

## B – Goods Receiving

| Frame | Variant | Figma |
|---|---|---|
| B01 | Nhập kho theo PO | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=8-2) |
| B02 | Validation errors | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=9-2) |
| B03 | Serial duplicate | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-437) |
| B04 | IMEI checksum error | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-545) |
| B05 | Forbidden scope | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-653) |
| B06 | Confirmation | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-761) |
| B07 | Success | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-869) |
| B08 | Unknown result | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-977) |
| B09 | Submitting | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=12-1085) |
| B10 | Mobile – cần kiểm tra tràn bố cục | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=17-31) |

## C – QR Scanner / Lookup

| Frame | Variant | Figma |
|---|---|---|
| C01 | Ready | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-2) |
| C02 | Scanning | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-58) |
| C03 | Lookup success | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-114) |
| C04 | Invalid QR | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-170) |
| C05 | Not found | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-226) |
| C06 | Camera denied | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-282) |
| C07 | No camera | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-338) |
| C08 | Invalid token | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-394) |
| C09 | Loading | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-450) |
| C10 | Network error | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=13-506) |

## D – POS Selection

| Frame | Variant | Figma |
|---|---|---|
| D01 | Product selection | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-2) |
| D02 | Serial selection | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-48) |
| D03 | QR scan | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-94) |
| D04 | Empty cart | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-140) |
| D05 | Reservation conflict | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-186) |
| D06 | Out of stock | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-232) |
| D07 | Reservation expired | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-278) |
| D08 | AI fallback | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-324) |
| D09 | Forbidden | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-370) |

## E – Payment

| Frame | Variant | Figma |
|---|---|---|
| E01 | Review order | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-416) |
| E02 | Processing | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-462) |
| E03 | Paid | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-508) |
| E04 | Failed | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-554) |
| E05 | Pending | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-600) |
| E06 | Unknown | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-646) |
| E07 | Reconciliation | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-692) |
| E08 | Idempotent retry | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-738) |

## F – Receipt

| Frame | Variant | Figma |
|---|---|---|
| F01 | Paid receipt | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-784) |
| F02 | Print preview | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-830) |
| F03 | Printing | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-876) |
| F04 | Print failed | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-922) |
| F05 | Reprint | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-968) |
| F06 | E-invoice pending | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-1014) |
| F07 | Order details | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-1060) |
| F08 | Forbidden | [Mở frame](https://www.figma.com/design/2cWV3DbiqSbmA8259WOY55?node-id=16-1106) |

Lưu ý: Figma Starter chặn thao tác tiếp theo do hạn mức MCP. B10 mobile phải được kiểm tra theo `D53_B10_MOBILE_QA_CHECKLIST.md` và điều chỉnh khi có quyền thao tác trở lại. Không tự coi frame này hoặc bất kỳ frame nào là đã QA PASS.
