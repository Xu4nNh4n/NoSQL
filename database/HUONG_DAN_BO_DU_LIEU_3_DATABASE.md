# Nhập snapshot vận hành có 5.000 hóa đơn vào ba database

Ba file này là snapshot toàn bộ dữ liệu vận hành sau khi mở rộng lên 5.000 hóa đơn ngày 29/10/2026. Gồm dữ liệu trước đó và 4.885 hóa đơn mẫu qua các đợt mở rộng, nhập thêm 60 lô phục vụ các giao dịch mới. Toàn bộ 5.000 hóa đơn đã được trải trên 30 ngày Việt Nam từ 30/09 đến 29/10/2026; thứ tự thời gian gốc của nhập hàng, bán, nhận trả và hoàn tiền được giữ nguyên. Các liên kết sản phẩm, lô, hóa đơn, phiếu trả và sự kiện được giữ nguyên.

| Database | File | Nội dung |
|---|---|---|
| MySQL | `database/seed-mysql.sql` | 35.373 bản ghi: 8 nhà cung cấp, 56 sản phẩm, 142 lô, 5.000 hóa đơn, 12.496 dòng hàng, 12 phiếu trả, 5.008 khoản thu/hoàn, 12.651 biến động kho |
| MongoDB | `database/seed-mongodb.js` | 56 document catalog lấy từ MongoDB đang dùng; cùng productId và SKU với MySQL |
| Cassandra | `database/seed-cassandra.cql` | 22.679 sự kiện nhập, bán, nhận trả, thanh toán và hoàn tiền |

Số lượng đối chiếu được ghi trong `database/seed-manifest.json`. Số 35.373 là tổng bản ghi nghiệp vụ MySQL, trong đó có 5.000 hóa đơn; không cộng bản sao catalog/sự kiện hoặc outbox để tăng số lượng.

## Điều kiện trước khi nhập

Đây là **snapshot giữ nguyên ID**, dùng để dựng lại dữ liệu trên môi trường học tập tương thích. Database hiện tại đã có đợt dữ liệu này, nên không chạy lại MySQL seed trên đó. MySQL sẽ báo lỗi khóa trùng thay vì âm thầm bỏ qua hoặc ghi đè dữ liệu khác.

1. Tạo schema MySQL theo `SQL/schema.sql` trên database học tập mới. Chỉ tạo roles/tài khoản nền; phải có `admin` và `nhanvien` trước khi nhập. Không chạy phần seed sản phẩm/lô/hóa đơn nền vì snapshot đã chứa những bản ghi đó. Bộ snapshot không chứa mật khẩu hoặc hash của người dùng. Các cột người tạo/xác nhận được ánh xạ theo username.
2. Đảm bảo database đích chưa dùng các ID/SKU/mã lô trong file MySQL. Các file giữ nguyên phân bổ lô JSON và quan hệ khóa ngoại; không tự sửa riêng một ID trong một bảng.
3. Chuẩn bị collection MongoDB `supermarket_catalog.products_catalog` và bảng Cassandra `supermarket_analytics.business_events_by_day` theo schema source. Không dùng keyspace mô phỏng.
4. Chỉ bắt đầu backend sau khi nhập xong cả ba nguồn để tác vụ đồng bộ nền không xen vào quá trình nhập.

## Thứ tự nhập

**MySQL:** mở `database/seed-mysql.sql` bằng MySQL Workbench hoặc Database Client, kết nối database đích và chạy toàn bộ file. Bật dừng khi gặp lỗi; không chọn tiếp tục bỏ qua lỗi. File dùng transaction, giữ khóa ngoại và múi giờ UTC. Nếu lỗi trước COMMIT, chạy ROLLBACK trên cùng kết nối và xử lý lỗi trước khi thử lại.

**MongoDB:** mở file `.js` trong công cụ hỗ trợ mongosh, hoặc chạy:

```powershell
mongosh --file database/seed-mongodb.js
```

Script chọn database bằng `getSiblingDB`, dùng Extended JSON để giữ kiểu Decimal128 và ngày tháng. Nó chỉ chèn document chưa có cùng `_id`. SKU bị trùng với document mang `_id` khác sẽ báo lỗi; cần kiểm tra thay vì ghi đè. Chạy mongosh trên URI của database đích nếu không dùng localhost.

**Cassandra:** chạy bằng cqlsh trên máy, hoặc qua Docker từ PowerShell:

```powershell
Get-Content -Raw -Encoding UTF8 database/seed-cassandra.cql | docker exec -i supermarket-cassandra cqlsh
```

CQL giữ nguyên UUID, partition ngày UTC và thời gian sự kiện; không đổi UUID khi chạy lại vì sẽ nhân đôi sự kiện. Chạy lại CQL cùng khóa chỉ cập nhật bản ghi cùng khóa.

## Sau khi nhập

Khởi chạy backend từ `backend/`. Outbox là hàng đợi lúc chạy, không nằm trong snapshot nghiệp vụ; backend dùng cơ chế bootstrap hiện có để đồng bộ catalog và lịch sử kho/biên nhận. MongoDB giữ SKU để Drill JOIN với MySQL; Cassandra chứa cả các sự kiện chuyển trạng thái đã xuất ở thời điểm nạp.

Mở Đối soát & Big Data, xuất các ngày cần xem trong khoảng **30/09–29/10/2026** rồi xem báo cáo. File JSON/Parquet phục vụ Drill không tự xuất chỉ bằng cách chạy CQL; dùng chức năng xuất ngày trong ứng dụng. Các sự kiện cùng UUID được chuyển sang partition UTC ứng với thời gian mới; không giữ thêm bản sao ở ngày cũ.

Dữ liệu mẫu ban đầu dùng nhãn `DỮ LIỆU MẪU - POS1000-01`; Các đợt mở rộng dùng nhãn `DỮ LIỆU MẪU - POS-ORDERS-1000/3000/5000` và tên khách mẫu khác nhau. Thông tin hóa đơn cũ được giữ nguyên. Đây là bộ học tập thực hiện qua nghiệp vụ thật của project, không phải lịch sử thu thập từ một cửa hàng.

Ba file import được xuất chỉ đọc; chưa chạy lại trên database. Bộ nạp Python qua API vẫn được giữ tại `database/seed-pos-operations.py` cho trường hợp muốn tạo dữ liệu qua luồng ứng dụng.

## Trạng thái sau khi dọn dữ liệu

Đợt 2.000 hóa đơn `POS-ORDERS-7000` đã được gỡ ngày 30/10/2026 theo yêu cầu, khôi phục tồn và lịch sử snapshot 5k. Các file seed hiện tại chứa bộ 5.000 hóa đơn. Ảnh/Profile/Word benchmark ở mốc 7k được giữ làm bằng chứng lịch sử; không mô tả số hóa đơn đang có trong database hiện tại. Backup và tiến độ đợt đã gỡ nằm trong `.tmp/keep-5000-orders/`.
