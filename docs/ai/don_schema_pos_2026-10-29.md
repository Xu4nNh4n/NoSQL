# Kết quả dọn schema POS — 29/10/2026

## Kết quả và cách đếm
Migration 001–016 tạo 18 bảng ứng dụng (không tính DATABASECHANGELOG và DATABASECHANGELOGLOCK). Source hiện ánh xạ 16 entity; hai bảng dư là carts và cart_items. Sơ đồ TXT trước đó đã không vẽ hai bảng này nên sơ đồ vẫn có 16 bảng, không phải đã giảm từ 16 xuống 14.

Migration 017 sẽ đưa schema từ 18 xuống 16 khi hai bảng giỏ hàng trống. Chưa chạy migration, chưa kết nối database ở lượt này, nên chưa xác nhận số bảng thực tế trên máy đã giảm.

| Nhóm | Bảng giữ lại | Số lượng |
|---|---|---:|
| Tài khoản | roles, users, refresh_tokens | 3 |
| Danh mục | categories, suppliers, products | 3 |
| Kho và truy vết lô | inventory_batches, stock_movements, batch_allocations | 3 |
| Khách mua | customers | 1 |
| Bán hàng | sales_orders, sales_order_items | 2 |
| Trả hàng | sales_order_returns, sales_order_return_items | 2 |
| Thu/hoàn tiền | payment_records | 1 |
| Đồng bộ Big Data | data_sync_outbox | 1 |
| Tổng | 12 bảng nghiệp vụ + 3 bảng tài khoản + 1 bảng đồng bộ | 16 |

## Thay đổi
- Thêm src/main/resources/db/changelog/changes/017-pos-schema-cleanup.yaml và include ở master. Không sửa migration cũ.
- cart_items: chỉ xóa nếu tồn tại và không có dòng dữ liệu.
- carts: chỉ xóa nếu trống và bảng cart_items đã không còn, tránh xóa cha khi còn bảng con.
- onFail CONTINUE: giữ nguyên bảng có dữ liệu và thử điều kiện lại lần sau; lỗi truy vấn điều kiện HALT thay vì che lỗi.
- Bỏ 12 thuộc tính giao/nhận online khỏi SalesOrder, SalesOrderResponse, mapper và frontend types/normalizeOrder. Frontend hiện không dùng chúng; client ngoài repo nếu có dùng thì cần cập nhật.
- 12 cột: delivered_at, receipt_status, receipt_confirmation_deadline, receipt_confirmed_at, receipt_completed_at, receipt_reported_at, receipt_issue_note, receipt_reviewed_at, receipt_review_note, shipping_address, receiver_name, receiver_phone.
- Chỉ xóa 12 cột vật lý trên MySQL khi sales_orders hoàn toàn trống. Dùng một ALTER để MySQL 8 thực hiện DDL nguyên khối; bảng có hóa đơn được giữ nguyên các cột cũ.
- Đã cập nhật file supermarket_pos_dbdiagram.txt trong Downloads, có chú thích tiếng Việt.

## Vì sao không xóa thêm
16 bảng còn lại đều đang có đường gọi thực tế: không thể gộp/xóa chỉ để giảm số bảng mà không đổi hoặc bỏ nghiệp vụ đã chốt. Giữ customer.user_id vì AccountProfileService còn dùng; giữ payment_expires_at vì scheduler còn sử dụng. Giữ delivery_status/order_type để không phá API/SQL báo cáo/lịch sử. Thuộc tính Outbox.deliveredAt là thời điểm gửi đồng bộ, khác giao hàng và không bị gỡ.

## Bằng chứng kiểm tra
- Kiểm tra tĩnh 154 file Java source/test: import nội bộ tồn tại và dấu ngoặc cân bằng, PASS.
- Constructor response và lời gọi mapper cùng 19 tham số; không còn tham chiếu tới 12 thuộc tính bị gỡ, PASS.
- TypeScript parser cho types/normalizer đã sửa không báo lỗi, PASS.
- Kiểm tra cấu trúc điều kiện migration, thứ tự xóa con trước cha và không có DELETE/TRUNCATE/UPDATE, PASS.
- Kiểm tra tham chiếu sơ đồ TXT 16 bảng, PASS.
- git diff --check trên các file sửa: không có lỗi khoảng trắng.

## Chưa chạy
Không chạy build, JUnit, backend, frontend hoặc database. Chưa xác minh YAML bằng Liquibase hoặc chạy DDL thật. Kiểm tra tĩnh không thay thế kiểm thử tích hợp. Có dữ liệu cũ thì bảng/cột tương ứng sẽ không bị dọn; cần xem log Liquibase khi chủ động khởi chạy lần sau.
