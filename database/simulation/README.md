# Bộ dữ liệu mô phỏng cho Apache Drill

`generate.py` sinh dữ liệu cục bộ có seed mặc định `421014`, khoảng 6 tháng, 200 SKU, 10 nhóm hàng, đơn POS, thanh toán tiền mặt/chuyển khoản tại quầy, hủy, trả, thanh toán, hoàn tiền, tồn theo hai lô và sự kiện theo ngày. Tập mặc định nhắm khoảng 100.000 event; con số thực tế được ghi vào `manifest.json` vì số event phụ thuộc số dòng món và tỷ lệ trả. Script chỉ ghi file, không kết nối MySQL/MongoDB/Astra. Bộ mô phỏng đã sinh trước đây có thể vẫn chứa giao dịch ONLINE; giữ nguyên bộ cũ và sinh dataset ID/thư mục mới cho dữ liệu POS-only.

Từ thư mục gốc project, sinh bộ mặc định:

```powershell
python database/simulation/generate.py --events 100000
```

Muốn thay đổi seed hoặc quy mô, dùng `--seed 421014 --events 1000000` hoặc `--events 5000000`. Tập lớn có thể tạo file SQL/JSON lớn; không cần sinh 1M/5M trước khi đã xác nhận dung lượng đĩa và cấu hình Drill. Kết quả nằm trong `analytics/simulation/sim-<seed>-<mục-tiêu>-2026-10-14/`, thư mục này được Git ignore.

## Tệp đầu ra và nạp dữ liệu

- `mysql-load.sql`: tạo riêng schema `supermarket_sim`, tạo các bảng báo cáo và nạp dữ liệu. Script có `DROP TABLE` nhưng chỉ sau `USE supermarket_sim`; chỉ chạy khi đã xem kỹ đúng database mô phỏng. Mở file bằng MySQL Workbench và xác nhận schema đích trước khi chạy.
- `products_catalog.jsonl`: catalog Mongo với `_id` dạng chuỗi để tránh tình huống BSON ObjectId của collection Atlas thật. Nạp vào database/collection riêng `supermarket_sim_catalog.products_catalog` bằng MongoDB Compass Import hoặc `mongoimport` với URI riêng. Không dùng URI của collection vận hành nếu chưa đổi database đích.
- `events/events-YYYY-MM-DD.json`: event JSON theo UTC ngày; không tự gửi lên Astra.
- `manifest.json`: seed, thời gian, số dòng thực sinh, số file và SHA-256 các đầu ra.

Trong MongoDB Compass, chọn `supermarket_sim_catalog.products_catalog` rồi Import JSON/JSON Lines. Hoặc dùng `mongoimport` với biến môi trường URI đã trỏ đúng cluster/database riêng, `--db supermarket_sim_catalog --collection products_catalog --mode=upsert --upsertFields=sku`.

Drill storage file có workspace `analytics.simulation` trỏ tới `/analytics/simulation`. Backend report `SIMULATED` đọc MySQL schema `supermarket_sim` và Mongo database `supermarket_sim_catalog`; chọn report mô phỏng trước khi nạp hai nguồn sẽ trả lỗi rõ từ Drill, không tự rơi về dữ liệu live. Ngày snapshot kho là `analytics.simulation.snapshot-date` trong `application.properties` (mặc định `2026-10-14`).

## Giới hạn mô phỏng

Đây là dữ liệu tổng hợp để minh họa pipeline và quy mô, không phải dữ liệu bán hàng thật. Kho được tạo nhất quán ở snapshot và đơn hủy không xuất kho; các dòng hàng thành công có phân bổ lô, FEFO theo lô sắp hết hạn trước; trả hàng khôi phục về lô đã xuất. Giá khuyến mãi cấp đơn không được mô phỏng. Bộ này chưa kết nối Astra và không chứng minh hiệu năng thật. Chạy benchmark hoặc QA cần được người phụ trách thực hiện riêng.

