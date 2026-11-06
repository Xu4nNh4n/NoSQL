# Bỏ refresh token

Mục tiêu đã được người dùng chốt: chỉ đăng nhập bằng access JWT; hết hạn thì đăng nhập lại. Giữ kiểm tra mật khẩu băm, khóa tài khoản, phân quyền và giới hạn JWT hiện tại.

- [x] Dọn API refresh/logout, service/entity/repository/DTO refresh; login chỉ trả accessToken.
- [x] Bỏ phụ thuộc refresh khi đổi mật khẩu; không thay kiểm tra tài khoản trong JWT filter.
- [x] Frontend không gia hạn hoặc gọi logout server; 401 API bảo vệ xóa phiên, đăng xuất xóa phiên tại trình duyệt.
- [x] Điều chỉnh các test hiện có bị ảnh hưởng; không chạy build/JUnit/dịch vụ theo phạm vi đã thống nhất.
- [x] Migration mới chỉ dọn bảng refresh_tokens nếu trống; không sửa migration cũ hoặc chạy SQL.
- [x] Cập nhật TXT mục tiêu 10 bảng và ghi kết quả kiểm tra tĩnh.

Giới hạn: JWT đã phát không tự mất hiệu lực khi đăng xuất/đổi mật khẩu; tiếp tục kiểm tra tài khoản bị khóa trên từng yêu cầu. 10 bảng là thiết kế mục tiêu; phần gộp 5 bảng khác chưa triển khai, source sau lượt này còn 15 entity.
