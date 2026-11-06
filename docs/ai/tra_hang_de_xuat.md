# Đề xuất trả hàng POS đơn giản

Trạng thái: người dùng đã đồng ý bắt đầu triển khai phương án trả đúng số lượng từng món. Xem [thiết kế đã chốt](tra_hang_thiet_ke.md) và [kết quả](tra_hang_ket_qua.md); nội dung bên dưới lưu lại đề xuất ban đầu.

## Cách làm đề xuất

Thu ngân tiếp nhận hàng thật tại quầy và tra hóa đơn gốc bằng mã, chọn sản phẩm/số lượng trả, ghi lý do và tình trạng hàng. Hệ thống tạo phiếu trả riêng và chuyển khoản hoàn sang chờ xác nhận. Thu ngân hoàn tiền thực tế rồi bấm nút xác nhận đang có. Không có bước khách gửi yêu cầu qua Shop hoặc giao hàng trả về.

Hướng này dùng lại các API `/api/orders/code/{orderCode}`, `/api/orders/{id}/return`, `/api/orders/{id}/returns`, `/api/orders/returns/{returnId}/refund` cùng bảng phiếu trả hiện có. Một quy trình khách gửi yêu cầu rồi quản lý duyệt sẽ tăng trạng thái và màn hình mà phạm vi POS hiện tại chưa cần.

## Luồng thao tác

1. Tại POS có lối vào tra cứu/trả hóa đơn, dùng chung màn hình đơn và phiếu trả để tránh hai chức năng trùng nhau. Tra đúng mã qua API, không phụ thuộc 100 đơn đầu đã tải.
2. Chỉ cho xử lý hóa đơn `IN_STORE` đã thanh toán/xuất kho, còn ít nhất một món chưa trả hết. Chặn nghiệp vụ trả/hoàn mới cho đơn online lịch sử.
3. Hiển thị từng dòng: đã mua, đã trả, còn được trả. Mặc định chưa chọn món nào; thu ngân chọn đúng hàng khách mang đến. Đề xuất cho chọn số lượng từ 1 đến lượng còn được trả; tổng nhiều lần trả không vượt lượng đã mua.
4. Với mỗi món được chọn, ghi lý do và chọn bán lại hoặc không bán lại. Hàng bán lại chỉ vào lô gốc còn hạn/hoạt động; hàng hỏng, đã mở hoặc không xác định được lô không tăng tồn bán được.
5. Khi một món xuất từ nhiều lô, trả một phần phải chỉ rõ lô trên hàng thực tế. API/bảng hiện tại chỉ nhận một dòng cho mỗi mặt hàng trong một phiếu: nếu trả nhiều tình trạng/lô mà không thể gộp an toàn, lập phiếu riêng; không tự đoán lô hoặc đổi cấu trúc bảng trong bản đơn giản.
6. Tiền hoàn dựa trên giá lúc mua và giảm giá đã lưu, không dùng giá catalog mới. Tổng các khoản hoàn đã ghi/đang chờ không vượt tiền thực thu; từng lần trả phải tính cả các lần trước và xử lý phần làm tròn.
7. Nhận hàng tạo phiếu `REFUND_PENDING`; thu ngân thực hiện hoàn theo phương thức thanh toán ban đầu và xác nhận bằng nút hiện có. Bấm lặp không nhập kho/hoàn tiền hai lần.
8. Còn hàng chưa trả: đơn `PARTIALLY_RETURNED`; hết hàng: `RETURNED`. Tiền hoàn có trạng thái riêng. Không tạo trạng thái giao hàng cho POS.

Quyền ghi giữ như API hiện tại: `ADMIN`/`STAFF`; `MANAGER` chỉ xem và không thấy nút ghi. Không tự thêm thời hạn trả cố định hoặc quy trình duyệt mới khi chưa thống nhất chính sách cửa hàng.

## Ví dụ cần chốt

Hóa đơn có 3 hộp sữa và 2 gói bánh. Đề xuất cho trả 1 hộp sữa, còn quyền trả 2 hộp sữa và 2 gói bánh ở các lần sau. Phương án đơn giản hơn là chọn dòng sữa thì trả cả 3 hộp; người dùng đang được hỏi chọn giữa hai cách này.

## Các phần phải sửa sau khi chốt

- `SalesOrderServiceImpl`: kiểm tra POS, số lượng từng dòng, tiền hoàn, trạng thái trả một phần, chống lặp và giới hạn tổng hoàn; bảo vệ cả xác nhận hoàn phiếu.
- `ReturnPolicy`: tính tiền hoàn từ giao dịch gốc và giảm giá, có kiểm tra số lượng/giới hạn tiền; dùng Java thuần để tái sử dụng với NoSQL.
- `SalesOrdersPage` và lối vào POS: tìm hóa đơn, chọn món/lượng, chọn lô khi cần, quyền nút thao tác và thông báo rõ hàng đã nhận/tiền chưa hoàn. Giữ nút xác nhận hoàn tiền hiện có.
- Controller/API description, test trả POS, generator, seed và mô tả báo cáo: khớp chính sách đã chọn. Doanh thu ròng giảm theo hàng trả; dòng tiền chỉ giảm khi xác nhận hoàn tiền.

Không dựng lại database hoặc ghi đè event/JSON/Parquet. Khi triển khai vẫn giữ giới hạn RAM: kiểm tra tĩnh trước, chưa chạy dịch vụ/build/JUnit nếu người dùng chưa thay đổi yêu cầu.
