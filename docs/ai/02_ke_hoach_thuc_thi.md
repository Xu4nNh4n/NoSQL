# Kế hoạch thực thi nâng cấp Drill

Trạng thái cập nhật 14/10/2026. Các mốc dưới đây phân biệt phần đã có trong source với phần còn cần dữ liệu thật, thao tác môi trường hoặc QA.

| Giai đoạn | Nội dung | Trạng thái | Điều kiện hoàn tất |
| --- | --- | --- | --- |
| 0. Nền tảng | Nhánh riêng, bỏ log/build artifact khỏi Git, rà credential, làm rõ hợp đồng dữ liệu | Đang làm | Không còn log runtime trong Git; kiểm tra lịch sử; quyền Astra được resume; yêu cầu rubric được đối chiếu với giảng viên |
| 1. Bộ dữ liệu mô phỏng | Generator có seed, database MySQL/Mongo tách riêng, JSON event và manifest; quy tắc nghiệp vụ nhất quán | Chưa hoàn tất | Nạp được tập mặc định, số lượng thực sinh ghi manifest, DBA/QA bên ngoài xác nhận dữ liệu và Drill đọc đúng |
| 2. Phân tích vận hành | Bán hàng JOIN MySQL–Mongo, hạn dùng, gợi ý nhập, ABC, kênh/giờ, chất lượng dữ liệu | Đã triển khai mã API; cần xác nhận trên Drill có nguồn | Mỗi báo cáo trả SQL/nguồn/kết quả; người dùng phân biệt hàng bán, tiền và lợi nhuận; các nhóm kiểm tra nghiệp vụ được QA xác nhận |
| 3. Parquet và benchmark | JSON ngày so Parquet ngày/phân vùng tháng; EXPLAIN/Profile; median và raw results | Chưa thực hiện | Chạy lại tối thiểu 5 lượt theo cỡ dữ liệu; lưu các lần chạy, cấu hình và giới hạn; chưa đưa số giả lập vào báo cáo |
| 4. Giao diện phân tích | Tab theo câu hỏi, bộ lọc, bảng/biểu đồ, CSV, SQL/Profiles, nguồn và thời gian trả lời | Đã triển khai; frontend build đạt | Chạy thực tế với Drill; xác nhận các giá trị trên UI khớp SQL và quyền ADMIN/MANAGER |
| 5. Báo cáo và demo | Báo cáo, slide, demo, đóng góp nhóm | Khung cần hoàn thiện sau khi có số liệu | Chỉ điền kết quả đo, ảnh, thành viên và đóng góp có bằng chứng thực tế |

## Quyết định thiết kế

- Một endpoint `/api/analytics/operational` nhận một `report` cố định và dataset `OPERATIONS` hoặc `SIMULATED`; không nhận SQL tùy ý từ trình duyệt.
- Các truy vấn chỉ `SELECT`, giới hạn dòng/ngày và enum report allowlist. `SIMULATED` yêu cầu nạp riêng schema `supermarket_sim` và catalog `supermarket_sim_catalog`; lựa chọn này không tự tạo hay nạp database.
- Ngày bán dùng UTC. Tồn lô là ảnh chụp hiện tại (mô phỏng dùng ngày snapshot trong cấu hình), không giả vờ tái dựng lịch sử.
- Báo cáo bán tính `(quantity - returned_quantity) * unit_price` trên đơn hợp lệ. Không gọi đây là tiền thu hoặc lợi nhuận.
- Báo cáo nhập hàng lấy 30 ngày lịch, chia đủ 30 kể cả ngày không bán; lead time và coverage là tham số người dùng, không phải dữ liệu nhà cung cấp đã đo.
- Astra cần khởi động lại và xác nhận trạng thái ACTIVE trước khi kiểm chứng export/benchmark cloud. Không cần Astra để chạy báo cáo MySQL–Mongo nếu Drill và hai nguồn kia khả dụng.

## Không được xem là hoàn thành

Code compile/build không chứng minh SQL đã chạy trên các nguồn. Thời gian `requestElapsedMs` chỉ là thời gian API đợi Drill, không có kiểm soát cache hay tải máy. Không gắn nhãn PASS/CHECK cho dữ liệu chưa chạy qua rule. Không tự đặt tên/đóng góp thành viên nhóm hay số liệu hiệu năng.

\n