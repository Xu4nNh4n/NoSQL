# Bỏ refresh token — 29/10/2026

## Đã sửa
- Login chỉ trả accessToken; giữ mật khẩu BCrypt, JWT, kiểm tra tài khoản hoạt động và quyền.
- Bỏ POST /api/auth/refresh và POST /api/auth/logout, các route không còn được permitAll.
- Xóa 7 file chuyên biệt: entity, repository, service/interface, implementation, request/response DTO và test riêng của refresh token.
- Bỏ phụ thuộc refresh token trong AccountProfileService và cấu hình jwt.refresh-expiration.
- Frontend bỏ queue, gọi refresh và retry; 401 API bảo vệ xóa phiên và phát auth:unauthorized. Lỗi login vẫn giữ thông báo từ API, không coi là phiên hết hạn.
- Logout xóa access token và hồ sơ local; các khóa refresh_token còn từ bản cũ được dọn khi login/logout/401.
- Điều chỉnh test đăng nhập cũ để hợp đồng không còn refreshToken; không chạy JUnit.
- Migration mới 018 chỉ xóa bảng refresh_tokens khi tồn tại và trống; có dữ liệu thì giữ lại. Không sửa migration đã có, không chạy SQL.
- File sơ đồ mục tiêu mới: Downloads/supermarket_pos_10_bang_de_xuat.txt. Bản 11 bảng giữ lại như tài liệu trước.

## Bằng chứng và giới hạn
Kiểm tra tĩnh 147 file Java source/test: import nội bộ và dấu ngoặc đạt; không còn phụ thuộc class refresh đã xóa. TypeScript parser cho client/context/types đạt. git diff --check trên file sửa đạt. Kiểm tra nội dung migration: chỉ drop bảng trống, không DELETE/TRUNCATE/UPDATE. TXT có 10 bảng và các tham chiếu tồn tại.

Không chạy build, JUnit, dịch vụ, database hoặc query Drill. Kiểm tra tĩnh không đảm bảo toàn bộ ứng dụng biên dịch/chạy thành công. Source thực tế từ 16 còn 15 entity; 10 bảng vẫn là thiết kế đề xuất, chưa gộp 5 bảng còn lại. Database chưa bị thay đổi trong lượt này.

JWT đã phát vẫn có hiệu lực đến khi hết hạn nếu bị sao chép ra ngoài; logout chỉ xóa token trong trình duyệt. Đổi mật khẩu không tự thu hồi JWT cũ. Tài khoản bị khóa vẫn bị từ chối ở JwtAuthenticationFilter trên mỗi yêu cầu.
