# Kết quả gỡ luồng bán hàng online

Ngày: 2026-10-16 (Asia/Bangkok). Nhánh: `go-online-tra-hang`.

## Đã thay đổi

- Gỡ Shop khỏi route, menu và header; `/` và `/shop` chuyển tới đăng nhập. Giữ `CartContext` React vì POS dùng nó làm giỏ tạm trong nội bộ.
- Gỡ backend Cart, đăng ký tài khoản khách hàng, API đơn của khách, thanh toán/giao hàng trực tuyến và xác nhận nhận hàng. Backend chỉ nhận đơn mới `IN_STORE`, danh sách và báo cáo vận hành chỉ lấy POS. Gỡ dịch vụ xác nhận nhận hàng tự động; giữ job hết hạn để giải phóng reservation của đơn cũ.
- Giữ nguyên nghiệp vụ POS dùng chung như kho/FEFO, thanh toán, hủy, trả hàng và outbox. Không xóa bảng, changeset đã áp dụng, dữ liệu MySQL, event JSON/Parquet hiện có. Seed/generator dùng cho lần chạy mới chuyển sang hồ sơ khách không có tài khoản đăng nhập và đơn POS.
- Drill và SQL mẫu lọc doanh thu/đơn theo `order_type='IN_STORE'`; event Cassandra được đối chiếu với đơn POS qua `order_id`.

## Xác minh đã chạy

- `npm.cmd run build` trong `frontend/`: thành công, TypeScript và Vite tạo bản production.
- Maven main compile: `BUILD SUCCESS`, biên dịch 137 file Java.
- Python `compile()` trên `database/simulation/generate.py`: thành công; chỉ kiểm tra cú pháp, không chạy sinh dữ liệu.
- Rà mapping controller/frontend bằng `rg`: không còn các route Cart, đăng ký khách, đơn của tôi, thanh toán online, giao hàng hoặc xác nhận nhận hàng.
- `git diff --check` trên các file trọng tâm giao diện, đơn hàng và SQL Drill: exit 0; chỉ có cảnh báo chuẩn hóa LF/CRLF.

## Chưa xác minh và lý do

- Maven test hiện chưa chạy được. Lượt mới nhất dừng trong JUnit discovery với `NoClassDefFoundError` cho `RegisterCustomerRequest` từ class `CustomerAccountServiceImplTest` đã bị xóa ở source nhưng còn trong `target/test-classes`; kết quả ghi ở `moc_truoc_khi_go_online/maven-test-pos-only-rerun.txt`. Không xóa `target` vì yêu cầu tránh lệnh phá hủy. Các lượt trước cũng cho thấy bộ test có fixture cũ; do đó chưa tuyên bố test xanh.
- Docker daemon không hoạt động trong môi trường này. Chưa khởi động backend/Drill, chưa gọi API báo cáo, chưa chạy truy vấn Drill và chưa so sánh số liệu POS trước/sau. Không kết luận đối soát PASS.
- Cập nhật 2026-10-28: đã gỡ các method thanh toán/giao hàng online không còn caller và các nhánh online không thể đi tới trong tạo đơn. Schema/entity, job giải phóng reservation và nhánh trả hàng lịch sử vẫn giữ; nghiệp vụ trả POS chưa chốt. Xem [rà soát mới](kiem_tra_pos_2026-10-28.md) để biết các điểm còn lại và giới hạn kiểm tra tĩnh.
- File JSON/Parquet cũ có dữ liệu lịch sử nhiều kênh; được giữ nguyên. Các truy vấn mới phải tiếp tục chịu được dữ liệu cũ.

## Trả hàng POS

Hiện trạng trước khi sửa được ghi tại [tra_hang_hien_trang.md](tra_hang_hien_trang.md). Người dùng đã chốt cho trả đúng số lượng từng món và source đã triển khai theo [thiết kế](tra_hang_thiet_ke.md). Kiểm tra nhẹ và giới hạn xác minh được ghi tại [kết quả trả hàng](tra_hang_ket_qua.md); chưa chạy lại JVM/dịch vụ theo giới hạn RAM.
