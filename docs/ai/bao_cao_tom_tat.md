# Báo cáo tóm tắt lượt gỡ Shop online

Cập nhật 2026-10-28: đã dọn thêm method online không còn caller, thông tin giao hàng trong giỏ POS, nút Shop ở đăng nhập và quyền truy cập nội bộ. Lượt này chỉ rà tĩnh theo yêu cầu hạn chế RAM; chưa thể kết luận nghiệp vụ hoàn chỉnh. Chi tiết và các điểm chưa xử lý tại [rà soát POS mới nhất](kiem_tra_pos_2026-10-28.md).

Đã làm trên nhánh `go-online-tra-hang`: ẩn Shop khỏi giao diện, gỡ các API/module chỉ dành cho giỏ hàng và mua online, giới hạn đơn mới và báo cáo vận hành ở POS. POS vẫn dùng giỏ React cục bộ cùng logic kho/FEFO, thanh toán, hủy, trả hàng và outbox. Bảng, migration, dữ liệu giao dịch và file JSON/Parquet lịch sử được giữ nguyên. Chi tiết thay đổi và giới hạn xác minh nằm trong [kết quả gỡ online](go_online_ket_qua.md), [khảo sát](online_khao_sat.md), [kế hoạch](online_ke_hoach.md) và [mốc kiểm tra](moc_truoc_khi_go_online/summary.md).

Frontend production build, Maven main compile, kiểm tra cú pháp generator và `git diff --check` trên các file trọng tâm đều đã chạy thành công. Maven test chưa chạy được: JUnit phát hiện class test đăng ký khách hàng cũ còn trong `target/test-classes`, trong khi source và DTO đã bị gỡ; không xóa thư mục build vì yêu cầu tránh lệnh phá hủy. Docker/Drill cũng chưa chạy được nên API, SQL và đối soát chưa được xác minh trên dữ liệu thật.

Trả hàng POS đã được người dùng chốt và sửa source theo [thiết kế](tra_hang_thiet_ke.md): chọn số lượng từng món, giữ phiếu chờ hoàn tiền, bảo vệ tiền/lô và chặn đơn online. Xem [kết quả triển khai](tra_hang_ket_qua.md) để biết kiểm tra nhẹ đã chạy và phần runtime/JUnit còn chưa xác minh. Chưa dựng lại database hoặc dataset.
