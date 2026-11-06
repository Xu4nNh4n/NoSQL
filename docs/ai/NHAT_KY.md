# Nhật ký triển khai

## 14/10/2026

- Tạo nhánh `upgrade/drill-analytics` để cách ly nâng cấp.
- Thêm ignore runtime logs, checksum/build temp và dữ liệu sinh/curated; bước tiếp theo là bỏ các log đang được Git track khỏi index mà giữ nguyên file trên máy.
- Thêm metadata thời gian phản hồi request Drill vào báo cáo. Đây là số phục vụ quan sát giao diện, không phải kết quả benchmark.
- Thêm API cố định `/api/analytics/operational` với các report SALES, EXPIRY, REPLENISHMENT, ABC, CHANNELS, QUALITY; truy vấn giới hạn schema, ngày và số dòng. JOIN Mongo được gom theo SKU để tránh nhân doanh thu.
- Thêm trang phân tích theo câu hỏi, chọn dataset vận hành/mô phỏng, lọc ngày, xem SQL/Profile, biểu đồ, bảng và CSV.
- Sinh bộ mô phỏng mặc định: seed 421014, 200 SKU, 22.222 đơn, 64.395 dòng món, 2.674 phiếu trả, 109.359 event thực tế trong 180 file ngày UTC. Tệp SQL, Mongo JSONL, event JSON và manifest nằm ở thư mục analytics bị ignore khỏi Git; generator chỉ ghi file.
- Khởi động Docker Desktop và Drill container riêng của project ở cổng 8048; cấu hình thành công workspace `analytics.simulation` chỉ đọc tại `/analytics/simulation` cùng các plugin hiện có. Chưa nạp SQL vào MySQL mô phỏng hoặc catalog lên Mongo; chưa chạy truy vấn báo cáo qua Drill.
- Chỉnh SQL phân tích theo trạng thái và tên cột của schema hiện tại; ghi rõ giả định tồn snapshot, thời gian giao hàng và khác biệt giữa hàng bán/tiền/lợi nhuận.
- `mvnw -o -Dmaven.test.skip=true compile`: BUILD SUCCESS. `npm run build`: thành công sau khi bỏ import không dùng. Không chạy test hay benchmark theo yêu cầu người dùng.
- Astra được báo đang ngủ đông; không thử gọi cloud, chưa cập nhật token, chưa xuất lại JSON từ Astra.

## Việc kế tiếp

1. Rà schema mô phỏng, tạo generator + manifest và nạp riêng khi người dùng sẵn sàng.
2. Cấu hình Drill truy cập vùng simulation và kiểm tra query bởi QA được giao; hiện không chạy test.
3. Thêm các script/SQL Parquet ngày và phân vùng tháng; chỉ đo benchmark sau khi người dùng/QA cho chạy.
4. Bổ sung kết quả thật, ảnh Profile, báo cáo và demo; không bịa số liệu.

