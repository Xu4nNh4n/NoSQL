# Báo cáo kết quả nâng cấp Apache Drill — bản khung

> Bản này chỉ mô tả phần đã xác nhận từ source/build. Các phần hiệu năng, số liệu truy vấn trực tiếp và Astra đang chờ môi trường được resume cùng bên phụ trách QA; không được xem là kết quả cuối để nộp.

## Bài toán và giải pháp

Đồ án phân tích hoạt động siêu thị qua nhiều nguồn: MySQL giữ đơn, thanh toán và tồn theo lô; MongoDB giữ catalog sản phẩm; Astra lưu sự kiện; Apache Drill chạy SQL liên nguồn. Spring Boot cung cấp các báo cáo cố định, React giúp quản lý chọn câu hỏi và xem dữ liệu.

## Kết quả triển khai đã xác nhận

- Có endpoint analytics allowlist cho bán hàng theo SKU, hạn dùng, gợi ý nhập hàng, ABC, kênh/giờ và chất lượng dữ liệu.
- Giao diện có bộ chọn nguồn, khoảng ngày UTC, bảng, biểu đồ cột, CSV, định nghĩa chỉ số, SQL thực tế, mã truy vấn và liên kết Drill Profiles.
- Maven compile và frontend production build đạt; không chạy test theo phân công hiện tại.

## Bảng benchmark — chờ đo

| Cỡ dữ liệu | Định dạng | Số dòng đầu vào | Số lượt | Median | Profile/ghi chú |
| --- | --- | ---: | ---: | ---: | --- |
| Chưa đo | JSON | Chưa đo | Chưa đo | Chưa đo | Chờ Drill và bên QA |
| Chưa đo | Parquet ngày | Chưa đo | Chưa đo | Chưa đo | Chờ curation và bên QA |
| Chưa đo | Parquet phân vùng tháng | Chưa đo | Chưa đo | Chưa đo | Chờ curation và bên QA |

## Hạn chế cần công bố

- Astra hiện ngủ đông; kết nối và event export chưa được xác nhận sau khi resume.
- Database mô phỏng riêng chưa được sinh/nạp. Chọn SIMULATED trước khi nạp sẽ báo lỗi datasource/table và không lấy dữ liệu vận hành thay thế.
- Thời gian trả API không phải benchmark. Chưa có số thời gian, lượng bytes đọc, pruning, heap hoặc bằng chứng Profile.
- Lead time nhập hàng là tham số giả định; lợi nhuận lịch sử chưa tính vì thiếu đầy đủ cost allocation theo từng dòng bán.
- Phân công nhóm, ảnh demo, rubric chính thức và tài liệu tham khảo phải được bổ sung bằng thông tin thực tế của nhóm/lớp.

\n