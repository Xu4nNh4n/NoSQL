# Thiết kế trả hàng POS đã chốt

Người dùng đồng ý triển khai phương án trả từng món và chọn số lượng thực nhận: mua 3 hộp sữa được trả 1 hộp. Luồng và phạm vi theo [đề xuất](tra_hang_de_xuat.md); bổ sung quyết định kỹ thuật bên dưới để việc hoàn tiền không mất phần lẻ hoặc vượt số tiền đã thu.

- Thu ngân tra mã hóa đơn gốc tại màn hình đơn từ lối vào POS; chọn lượng trả, lý do, tình trạng và lô gốc khi cần. Giữ API và bảng hiện có; không thêm Shop, duyệt nhiều bước, thời hạn trả cố định hay database mới.
- Chỉ hóa đơn POS đã thu tiền/xuất kho và còn lượng được trả mới tạo phiếu. Tổng lượng trả không vượt đã mua. Bấm lặp cùng request key trả lại cùng kết quả, không nhập kho hai lần.
- Hàng bán lại về lô gốc còn hiệu lực; hàng không bán lại tăng lượng hỏng/cách ly, không tăng tồn khả dụng. Một mặt hàng trong một phiếu có một tình trạng/lô chỉ định; hàng từ nhiều lô/tình trạng khác nhau có thể lập các phiếu riêng để giữ schema hiện tại.
- Thu ngân chọn món; mọi dòng ban đầu có lượng trả bằng 0. Trả hết một dòng từ nhiều lô có thể dùng phân bổ gốc; trả một phần phải chọn lô thực tế. Giới hạn số lượng của lô thể hiện trên form.
- Tiền hoàn được phân bổ từ tổng thực trả theo phần giá gốc của từng dòng. Dùng các khoảng cộng dồn theo thứ tự ID dòng để phân bổ phần lẻ cố định: tổng hoàn khi trả hết bằng đúng tổng tiền hóa đơn, không phụ thuộc thứ tự các lần trả. Kiểm tra cả biên nhận thu/hoàn và các phiếu đang chờ trước khi ghi.
- Mỗi phiếu nhận hàng có trạng thái chờ hoàn tiền riêng, giữ nút xác nhận hoàn tiền đang có. Hoàn theo phương thức đã thu và ghi payment/outbox khi xác nhận. Đơn còn hàng là `PARTIALLY_RETURNED`, trả hết là `RETURNED`; POS không phát sinh giao hàng.
- `ADMIN`/`STAFF` được nhận trả/hoàn; `MANAGER` được xem. Backend kiểm tra lại quyền, loại đơn, tiền và tồn, không tin dữ liệu form.
- Giữ nguyên dữ liệu/schema/migration lịch sử. Không khởi chạy dịch vụ/JVM/build/JUnit khi giới hạn RAM còn hiệu lực; viết test nguồn và chạy kiểm tra tĩnh/lint nhẹ, ghi rõ phần chưa xác minh.
