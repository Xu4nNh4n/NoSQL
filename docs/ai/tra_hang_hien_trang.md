# Hiện trạng trả hàng POS

Ngày khảo sát: 2026-10-16. Tài liệu này mô tả code hiện tại; chưa thay đổi chính sách hay code trả hàng.

## Luồng đang chạy trong code

1. Nhân viên/Admin gọi `PUT /api/orders/{id}/return` (`SalesOrderController`), gửi `requestKey` và danh sách mặt hàng. Endpoint yêu cầu vai trò `ADMIN` hoặc `STAFF`; chưa có endpoint để khách tự tạo yêu cầu trả.
2. `SalesOrderServiceImpl.returnSalesOrder` khóa đơn, kiểm tra trạng thái. POS được trả khi đơn `IN_STORE` đã `PAID`; nhánh online còn chấp nhận đơn `DELIVERED`, `RETURN_IN_TRANSIT` và trạng thái lịch sử `PARTIALLY_RETURNED`. Đơn phải đánh dấu hàng đã xuất kho.
3. Mỗi dòng hiện bắt buộc trả đúng toàn bộ số lượng còn lại của dòng đó, đồng thời phiếu phải chứa mọi dòng đơn còn hàng. Vì vậy hiện tại POS không cho chọn riêng một mặt hàng trong đơn hoặc trả một phần số lượng của mặt hàng.
4. Cùng request đó, hệ thống xem như đã nhận hàng thật: `InventoryBatchService.receiveReturn` chạy ngay, cập nhật lô/tồn và stock movement, tăng `returned_quantity`, tạo `sales_order_returns` + `sales_order_return_items`, rồi đặt đơn sang `RETURNED`. Không có trạng thái “khách vừa gửi yêu cầu, cửa hàng chưa nhận hàng”.
5. Số hoàn tính từ `sales_order_items.unit_price × quantity trả`, không dùng giá catalog hiện tại. Code chưa phân bổ `discount_amount` cấp đơn vào từng dòng; nếu một đơn được giảm giá, số này có thể cao hơn phần tiền thực trả cho dòng trả.
6. Nếu đã thu tiền, phiếu chuyển `REFUND_PENDING`. Nhân viên/Admin xác nhận riêng bằng `PUT /api/orders/returns/{returnId}/refund`; số tiền gửi phải bằng đúng số của phiếu. `payment_records` ghi giao dịch REFUND có `return_id`, rồi payment status đơn được cập nhật `REFUND_PENDING`, `PARTIALLY_REFUNDED` hoặc `REFUNDED`.

## Quy tắc tồn kho và lô

- Hàng được đánh dấu bán lại chỉ trở lại lô đã cấp lúc bán. Nếu lô gốc không còn `ACTIVE` hoặc đã hết hạn, code từ chối đưa vào tồn bán được.
- Hàng không bán lại được ghi nhận vào lượng hỏng/cách ly, không làm tăng tồn có thể bán.
- Đơn cũ không có `batch_allocations` không có nguồn gốc lô để chứng minh; code chỉ nhận vào lô cách ly với `restock=false`, không tự bịa lô gốc.
- Với một mặt hàng đã xuất từ nhiều lô mà chỉ trả một phần, nhân viên phải chọn `batchId`; trả toàn bộ lượng còn lại có thể phân bổ theo các lô gốc.

## Dữ liệu và báo cáo Big Data

- `SalesOrderReturn`/`SalesOrderReturnItem` lưu phiếu, `request_key`, số tiền, trạng thái hoàn, dòng hàng, số lượng, tình trạng bán lại và lý do. `SalesOrderItem.returned_quantity` giúp kiểm tra tổng lượng trả không vượt lượng đã mua. Bảng trả được tạo ở Liquibase `012-partial-returns.sql`; `payment_records.return_id` nối khoản hoàn với phiếu.
- Khi nhận hàng, stock movement được `DataSyncPublisher` đổi thành `INVENTORY_RETURNED` hoặc `INVENTORY_QUARANTINED`; sự kiện đơn là `RETURN_RECEIVED`. Khi xác nhận tiền, sự kiện payment là `REFUND_CONFIRMED` và sự kiện đơn `RETURN_REFUND_CONFIRMED`; Outbox chuyển các event sang Cassandra/Astra.
- Báo cáo doanh thu `OperationalAnalyticsService.saleLines` trừ `returned_quantity` khỏi lượng bán. Báo cáo dòng tiền cộng `PAYMENT_RECEIVED` và trừ `REFUND_CONFIRMED`. Đối soát so số lượng/giá trị thanh toán và hoàn giữa `payment_records` MySQL với event đã xuất; số liệu ngày chỉ khớp khi tập giao dịch, ngày UTC và export cùng phạm vi.
- Nếu bỏ online, phải lọc doanh thu và đối soát theo `sales_orders.order_type='IN_STORE'`. Payment event riêng lẻ không có cột channel chuẩn; truy vấn cần nối qua `order_id` hoặc định dạng metadata đã xác minh, không suy luận chỉ từ `event_type`.

## Điểm chưa phù hợp nếu dùng như nghiệp vụ POS thực tế

- Thu ngân có quyền vừa khai báo “đã nhận đủ hàng” vừa làm thay toàn bộ bước nhận/kiểm kê; điều này gộp yêu cầu trả và quyết định kho trong một lần ghi.
- Buộc trả hết toàn bộ đơn khiến khách không thể trả riêng một loại hàng trong hóa đơn nhiều loại. Cấu trúc `returned_quantity` và nhiều phiếu đã hỗ trợ lưu nhiều lần trả, nhưng validator hiện chặn trường hợp đó.
- Cho phép boolean “bán lại/hỏng” ở cấp dòng chỉ hợp lý khi toàn bộ số lượng trong dòng có cùng tình trạng. Nếu cùng một dòng trả có cả món dùng được và món hỏng, cần tách số lượng theo tình trạng lúc nhận.
- Giá hoàn theo giá đơn vị gốc là đúng hướng, nhưng chiết khấu cấp đơn chưa được phân bổ. Chính sách phải xác định trả theo giá đã thanh toán sau chiết khấu để tổng tiền hoàn không vượt tiền thực thu.
- Hạn trả, yêu cầu hóa đơn, vai trò duyệt, phương thức hoàn tiền theo giao dịch gốc và quy tắc cho thực phẩm đã mở/chưa mở chưa được thể hiện thành chính sách cấu hình trong luồng hiện tại.

## Phạm vi cố định cho phiên này

Chỉ khảo sát và thống nhất chính sách trả hàng POS với người dùng. Chưa sửa code trả hàng, schema trả hàng, test trả hàng hoặc báo cáo trả hàng cho tới khi người dùng chốt thiết kế.
