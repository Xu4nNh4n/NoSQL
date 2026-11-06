# Nạp bộ dữ liệu POS để học và xem báo cáo

## Mở rộng lên 1.000 hóa đơn

Để nâng **tổng số hóa đơn** đang có lên 1.000, dùng script mở rộng sau. Đây là 1.000 hóa đơn, khác với đợt đầu khoảng 1.000 bản ghi ở nhiều bảng.

```powershell
python database/extend-pos-orders.py
python database/extend-pos-orders.py --apply
```

Lệnh đầu xem trước số hóa đơn còn thiếu; lệnh thứ hai bổ sung qua API POS. Script tạo tên khách mẫu riêng trên từng hóa đơn mới, xen kẽ tiền mặt/chuyển khoản, nhập thêm lô còn thiếu tồn và gửi dữ liệu qua outbox. Không tạo bảng khách hàng hoặc tài khoản khách. Thông tin của các hóa đơn cũ được giữ nguyên. Đợt mở rộng được đánh dấu `POS-ORDERS-1000` và lưu tiến độ tại `.tmp/pos-orders-1000/`. Chạy lại sau khi đủ 1.000 hóa đơn không tạo thêm đơn trùng. Nếu bị gián đoạn ở một yêu cầu chưa rõ kết quả, script sẽ dừng để đối chiếu trước khi tiếp tục.

Các tên là dữ liệu mẫu, không phải khách hàng thật. Phiên bản hiện tại phân trang danh sách trên frontend và tải các trang API; 1.000 hóa đơn vẫn được lấy đầy đủ để tìm kiếm/lọc.

Script `seed-pos-operations.py` tạo dữ liệu qua API của backend, vì vậy nhập kho, FEFO, bán hàng, trả hàng, hoàn tiền và outbox đồng bộ chạy theo nghiệp vụ đang có. Không chạy trực tiếp các lệnh INSERT vào bảng đơn hàng: sẽ thiếu phân bổ lô và sự kiện đồng bộ.

## Trước khi chạy

- Khởi chạy MySQL, MongoDB, Cassandra và Apache Drill; backend phải đọc được cấu hình ở `.env`.
- Mở PowerShell tại thư mục gốc repo. Python 3 có sẵn, không cần cài thư viện ngoài.
- Bộ nạp **chỉ thêm** dữ liệu; không xóa dữ liệu hiện tại. Tên sản phẩm, SKU, nhà cung cấp và mã lô được kiểm tra để tránh nạp đè.

## Xem trước và nạp

Chạy thử kế hoạch, không ghi database:

```powershell
python database/seed-pos-operations.py
```

Nạp khoảng 1.000 bản ghi nghiệp vụ:

```powershell
python database/seed-pos-operations.py --apply
```

Kế hoạch gồm 50 sản phẩm, 5 nhà cung cấp, 75 lô, 110 hóa đơn, 275 dòng hàng, 10 phiếu trả và 6 lần hoàn tiền. Hóa đơn dùng ngày chạy script theo giờ Việt Nam vì API POS không cho đặt ngày quá khứ. Chứng từ có ghi `DỮ LIỆU MẪU - POS1000-01`; đây là dữ liệu mẫu để học và trình diễn, không phải lịch sử giao dịch thật của cửa hàng.

Script cần backend hoạt động tại `http://127.0.0.1:8080`. Mặc định đăng nhập bằng tài khoản mẫu `admin` và `nhanvien`, mật khẩu `123456`. Nếu đã đổi mật khẩu, đặt tạm trong phiên PowerShell, không ghi vào file:

```powershell
$env:SEED_ACCOUNT_PASSWORD = Read-Host 'Mật khẩu tài khoản mẫu'
python database/seed-pos-operations.py --apply
Remove-Item Env:SEED_ACCOUNT_PASSWORD
```

Script lưu bản chụp dữ liệu trước và tiến độ cục bộ tại `.tmp/pos-first-1000/`; MySQL backup `.sql` nằm trong cùng thư mục. Không đưa thư mục này lên Git. Nếu script dừng giữa một yêu cầu đang gửi, nó sẽ dừng lần chạy tiếp theo để tránh tạo trùng; kiểm tra hóa đơn/phiếu liên quan trong giao diện và xử lý mục `_inflight` ở `progress.json` trước khi tiếp tục.

## Xem kết quả

- Mở frontend, vào **Sản phẩm**, **Kho & Lô hàng FEFO**, **Đơn hàng & Trả hàng** để xem dữ liệu.
- Báo cáo lấy ngày hiện tại theo giờ Việt Nam. Dòng tiền và đối soát dùng MySQL cùng bản xuất sự kiện Cassandra; catalog sản phẩm được đồng bộ sang MongoDB.
- Kết quả nạp và trạng thái outbox lưu tại `.tmp/pos-first-1000/result.json`. Bản xuất Cassandra nằm trong `analytics/cassandra/`.

Script cố ý dùng dịch vụ thật đang cấu hình trong `.env`, không tạo database `SIMULATED`. Chỉ chạy `--apply` trên database bạn định bổ sung dữ liệu.

## Phân bố lịch sử để xem xu hướng

Bộ snapshot hiện tại đã trải 1.000 hóa đơn trên 30 ngày từ 30/09 đến 29/10/2026. Script tạo đơn qua API vốn ghi ngày hiện tại. Công cụ `database/spread-pos-history.py` là bước chuyển đổi lịch sử mẫu một lần: cần dừng backend và sao lưu trước khi chạy `--apply`; nó đổi cùng lúc các mốc MySQL, payload outbox và khóa thời gian Cassandra theo thứ tự gốc. Dữ liệu hoàn tất được ghi tại `.tmp/spread-pos-history/completed.json`, chạy lại không tiếp tục dịch ngày. Sau đó bật backend và xuất lại từng ngày cho Drill. Không áp dụng công cụ này lên giao dịch thực tế của cửa hàng.
