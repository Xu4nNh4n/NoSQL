# Dọn schema POS — kế hoạch thực thi

**Mục tiêu:** bỏ phần online không dùng, giữ POS/FEFO/trả từng món/thu hoàn tiền và phân tích đa nguồn.
**Phạm vi đã được người dùng cho phép:** dọn bảng dư; không khởi chạy hoặc xóa dữ liệu database ở lượt này.
**Nhánh:** go-online-tra-hang. Không thay migration đã có hoặc sửa lịch sử Git.

## Kết quả khảo sát
- 16 bảng đang ánh xạ entity đều có đường sử dụng trong nghiệp vụ hoặc hạ tầng.
- Migration cũ còn tạo carts và cart_items: tổng schema nghiệp vụ/hạ tầng là 18 bảng, không tính Liquibase.
- 12 thuộc tính giao hàng/nhận hàng trong SalesOrder chỉ truyền qua DTO/normalizer, không có màn hình sử dụng.
- Giữ customer.user_id vì AccountProfileService vẫn dùng; giữ payment_expires_at vì scheduler còn sử dụng.
- Giữ delivery_status, order_type và cơ chế tồn dùng chung, tránh phá báo cáo/lịch sử.

## Thiết kế
- Thêm migration mới 017, không sửa 001–016.
- Xóa cart_items chỉ khi bảng tồn tại và trống; xóa carts chỉ khi bảng con đã không còn và bảng cha trống.
- Bảng có dữ liệu được bỏ qua, không đánh dấu migration hoàn tất để lần sau còn kiểm tra lại.
- Gỡ 12 thuộc tính online khỏi entity, response, mapper và frontend normalizer.
- Chỉ dọn 12 cột vật lý khi sales_orders hoàn toàn trống; bảng có hóa đơn giữ nguyên các cột để không mất dữ liệu.
- Không sửa tồn kho, giá, công thức trả hàng, sự kiện, SQL báo cáo hoặc bộ dữ liệu đã xuất.

## Các bước
- [x] Thêm migration có điều kiện và nối vào master.
- [x] Dọn entity/DTO/mapper/types đồng bộ.
- [x] Cập nhật sơ đồ TXT và ghi rõ phạm vi 16 bảng, trạng thái schema thực tế chưa áp dụng.
- [x] Kiểm tra tham chiếu, cấu trúc Java/TypeScript và điều kiện migration bằng lệnh tĩnh; không chạy dịch vụ/build/JUnit.

## Rủi ro và giới hạn
API đơn hàng sẽ không trả các field giao/nhận online bị gỡ; frontend hiện tại không dùng chúng.
Migration chỉ được áp dụng lần khởi động sau, không có thay đổi DB trong lượt này.
Nếu DB còn giỏ hàng hoặc hóa đơn, bước dọn tương ứng bị bỏ qua để bảo toàn dữ liệu.
Không commit khi còn nhiều thay đổi trước đó chưa tách được an toàn.
