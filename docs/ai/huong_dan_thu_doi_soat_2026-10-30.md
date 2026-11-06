# Thử báo cáo và đối soát trên bộ dữ liệu hiện tại

Ngày nghiệp vụ: **30/10/2026, giờ Việt Nam**. Dữ liệu học tập được tạo qua API thật; tên sản phẩm, khách và ghi chú hiển thị bình thường. Không xóa hay thay giao dịch hiện có. Không lấy bộ này làm dữ liệu thu thập từ siêu thị thật.

## Cách xem

1. Đăng nhập tài khoản quản trị tại http://localhost:3000.
2. Mở Đối soát & Big Data → Dòng tiền & đối soát.
3. Chọn từ **30/10/2026** đến **30/10/2026**, bấm tra cứu.
4. Xem tiền thu, hoàn và các cột chênh lệch. Mở phần nhật ký JSON, yêu cầu đọc đối chiếu.
5. Chuyển Bán hàng & tồn kho để xem bán chạy, ABC, hàng sắp hết hạn; mở Giá nhập theo lô để xem biến động giá nhập.

## Số liệu đã quan sát qua API sau đồng bộ

| Nội dung | Giá trị |
|---|---:|
| Hóa đơn POS | 8 |
| Tiền hàng trước giảm | 834.000 đồng |
| Giảm giá | 169.500 đồng |
| Tiền đã thu | 664.500 đồng |
| Tiền đã hoàn xác nhận | 9.000 đồng |
| Tiền thu ròng | 655.500 đồng |
| Một phiếu trả còn chờ hoàn | 27.200 đồng |
| Tiền hàng sau trừ lượng đã nhận trả | 628.300 đồng |
| Lô sắp hết hạn trong 7 ngày | 2 |
| Lô trong báo cáo giá nhập | 9 |

Phiếu trả 1 thuộc đơn 1 đã hoàn 9.000 đồng; phiếu 2 thuộc đơn 2 còn chờ hoàn 27.200 đồng. Vì vậy tiền hàng sau trả và tiền thu ròng hiện khác nhau 27.200 đồng. Không xác nhận phiếu chờ nếu muốn giữ tình huống minh họa này; nếu xác nhận, hệ thống phải phát sinh khoản hoàn mới và các báo cáo sẽ thay đổi tương ứng.

Đối soát ngày này quan sát **PASS**, 8 khoản thu và 1 khoản hoàn khớp MySQL–Cassandra, toàn bộ delta tiền bằng 0. Nhật ký JSON trả 9 dòng, tất cả **MATCH**. Ba quy tắc chất lượng dữ liệu đều có issue_count bằng 0; không suy ra điều đó chứng minh mọi quy tắc nghiệp vụ đều đúng.

Query ID đối soát: `151b9bbe-2e55-e124-c8e6-ffb76b49e859`.
Query ID dòng tiền: `151b9bbe-aa1e-5393-5c43-017646b07458`.
Query ID JSON: `151b9bbd-b996-fa96-a2c7-378fa794b3bd`.

Các con số trên là mốc kiểm tra, có thể đổi sau khi người dùng thao tác thêm. Chưa chạy bộ unit/integration test.

## SQL và script

- SQL đã chạy qua backend: `drill/multisource/reconciliation-2026-10-30.sql`, `drill/multisource/cash-flow-2026-10-30.sql`.
- Script chuẩn bị hóa đơn/nhận trả/hoàn: `database/prepare-analytics-operations.py`. Mặc định chỉ lên kế hoạch; `--apply` ghi qua API. Giữ nguyên journal để không tạo trùng; không xóa journal rồi chạy lại khi một yêu cầu chưa rõ kết quả.
- Hai lô gần hết hạn được bổ sung bằng `database/prepare-pos-discounts.py --apply --near-expiry`. Không dùng để đặt lại giá hoặc ghi đè sản phẩm đang kinh doanh.
- Kết quả đọc API và SQL chi tiết: `C:/Users/Admin/supermarket-run/analytics-current/`.
- Các seed/manifest 5.000 hóa đơn và benchmark cũ là bộ lịch sử, không đại diện bộ 8 hóa đơn này.

## Lịch sử kho và giới hạn

Bộ hiện tại phát sinh trong ngày 30/10; snapshot chỉ chốt ngày đã kết thúc và phải dựng từ sổ kho đủ để đối chiếu. Vì thế phần lịch sử chốt ngày đang trống, không phải lỗi SQL và không tự lấp số 0 cho các ngày chưa có lịch sử.

Đã sửa thêm cách chuyển kiểu Cassandra: DATE của adapter là số ngày từ epoch, TIMESTAMP là mili giây. Truy vấn khoảng ngày dùng giá trị số tương ứng và chuẩn hóa thời gian trước khi lọc. Hiện phần lọc này có thể quét nguồn Cassandra, chưa có partition pruning; giới hạn số dòng trả về không giới hạn số dòng quét. Cần đo riêng khi tăng dữ liệu, không dùng benchmark kiến trúc cũ để khẳng định hiệu năng đường đọc mới.
