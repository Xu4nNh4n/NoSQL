# Tiến độ nâng cấp đa nguồn — 30/10/2026

Nhánh: `nang-cap-drill-da-nguon`. Các thay đổi kế thừa trong working tree được giữ nguyên. Theo yêu cầu gần nhất, ứng dụng/container đang dừng, chưa tạo/xóa dữ liệu và không chạy bộ test.

## Đã triển khai trong code

- Gỡ phụ thuộc MongoDB và catalog sync khỏi backend, cập nhật báo cáo dùng thông tin sản phẩm MySQL.
- Thêm chương trình khuyến mãi theo sản phẩm, phần trăm, khoảng thời gian Việt Nam và trạng thái. Chọn ưu đãi lớn nhất, không cộng dồn.
- POS lấy báo giá server và xác nhận lại khi giá thay đổi; dòng hóa đơn lưu giá gốc, giá thu thực tế và snapshot khuyến mãi. Hoàn tiền dùng giá đã thu, kể cả giá bằng 0.
- Thêm trang quản lý khuyến mãi, cập nhật giỏ/chi tiết đơn/in hóa đơn.
- Thêm nhập/xem trước/xác nhận CSV báo giá nhà cung cấp; kiểm tra dữ liệu và lưu phiên bản trước ngoài workspace active.
- Thêm báo cáo MySQL + CSV và MySQL + JSON nhật ký thanh toán, với API tương ứng.
- Thay truy vấn sự kiện Cassandra qua file bằng storage plugin Cassandra trực tiếp; cấu hình nguồn và SQL kiểm tra chỉ được viết, chưa áp dụng/chạy thực tế.
- Ghi JSON thu/hoàn từ outbox theo ngày UTC và event ID ổn định, xử lý retry độc lập với Cassandra.
- Cập nhật UI nguồn dữ liệu, bỏ yêu cầu xuất Cassandra trước khi xem báo cáo và bỏ nút đồng bộ catalog.
- Đối soát phân biệt tổng tiền gốc với tổng tiền sau giảm; đơn lịch sử thiếu giá net được giữ quy tắc riêng.

## Bằng chứng biên dịch đã có

- Backend: Maven 3.9.16, `-Dmaven.test.skip=true compile`, BUILD SUCCESS (121 source files ở lần build đầu ghép các worker).
- Frontend: `npm.cmd run build`, TypeScript và Vite 8.3.4 thành công ở lần build đầu.
- Build sạch cuối: Maven `-Dmaven.test.skip=true clean compile`, BUILD SUCCESS, biên dịch 126 source files và xóa target cũ để không giữ class MongoDB đã gỡ. Lần compile xác nhận sau ghép báo “all classes are up to date”, BUILD SUCCESS lúc 09:14 ngày 30/10/2026.
- Frontend build cuối sau ghép lịch sử kho và sửa đối soát gross/net: TypeScript/Vite thành công, 1.941 modules, bundle JS 490,87 kB trước gzip.
- Không chạy unit/integration test, truy vấn Drill live hoặc thao tác database để chứng minh nghiệp vụ.

## Cần làm trước khi chạy ứng dụng mới

1. Chọn đúng một cách chuẩn bị schema: database mới dùng `SQL/schema.sql`; database hiện có dùng `SQL/migrations/2026-10-30-promotions.sql` một lần. Chưa thực hiện cả hai.
2. Đối chiếu cấu hình backend và Drill cùng schema MySQL/keyspace Cassandra, thư mục CSV/JSON host tương ứng volume Drill.
3. Áp dụng plugin Cassandra và xác minh trên môi trường thật trước khi gọi đường đọc trực tiếp là đã hoạt động.
4. Nạp CSV qua giao diện khi cần; file mẫu chỉ chứa header, chưa có báo giá hoặc giao dịch được tạo trong lượt triển khai.
5. Client tạo hóa đơn phải dùng quoteToken; script nạp đơn cũ không được chạy nguyên trạng vì chưa theo hợp đồng báo giá mới.

## Giới hạn và phần để sau

- Đã có `StockSnapshotReader`, `StockSnapshotService`, endpoint ADMIN dựng snapshot theo yêu cầu và DDL `database/cassandra/stock-daily-snapshots.cql`; không có tác vụ tự ghi lúc khởi động. Lịch sử thiếu hoặc không khớp số dư được từ chối với lý do; sản phẩm chưa từng nhập và không tồn được bỏ qua có số đếm. Trang “Lịch sử kho” và GET `/api/analytics/stock-history` chỉ đọc qua Drill.
- Các API mới đã có code: `/api/promotions`, `/api/orders/quote`, `/api/supplier-prices`, `/api/analytics/purchase-prices`, `/api/analytics/payment-log`, `/api/analytics/stock-history`. Chưa gọi runtime để chứng minh phản hồi.
- Rà soát cuối đã sửa đọc payload outbox bằng BigDecimal, trả giá CSV dạng chuỗi thập phân và hiển thị đúng tiền gốc/net trong đối soát. Kiểm tra tĩnh không thay thế kiểm thử tài chính.

- Chưa thay bộ dữ liệu hoặc chuyển namespace; các ảnh/benchmark cũ vẫn thuộc kiến trúc cũ.
- JSON nhật ký là dữ liệu dẫn xuất từ khoản thu/hoàn đã commit, không phải log ngân hàng hoặc bằng chứng độc lập về thanh toán thẻ.
- CSV báo giá không quyết định giá vốn lô đã nhập và không tự tạo phiếu nhập hàng.
- Tồn hiện tại MySQL phải phân biệt với lịch sử chốt ngày Cassandra. Các thao tác dựng snapshot chỉ được thực hiện khi có lịch sử đủ để đối chiếu.
- Không nhận build thành công là bằng chứng tất cả API, SQL và nghiệp vụ runtime đều đúng.


## Thay báo giá CSV bằng giá nhập thực tế theo lô

Theo yêu cầu mới, đã gỡ import/template CSV, dependency Commons CSV và workspace business. Trang /purchase-costs thay cho /supplier-prices (URL cũ chuyển hướng); API /api/analytics/purchase-costs đọc inventory_batches, products và suppliers bằng Drill. Có ngày nhập, giá nhập từng lô, giá lô trước, chênh lệch giá, giá trị nhập và giá trị tồn hiện tại. Kho & lô hàng có cột Giá nhập/đơn vị. Không sửa dữ liệu kho trong thay đổi này.

Backend clean compile và frontend build thành công. API báo cáo cuối trả COMPLETED, 7 dòng, query ID 151b9fc9-7199-694d-96f1-cca834a4c869. Backend và frontend đang chạy; không chạy bộ test hoặc tự tạo giao dịch bán/hoàn.
