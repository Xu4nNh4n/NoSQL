# Lỗi JDBC pushdown chuỗi Unicode trong báo cáo bán hàng

Profile gốc: `151b9f2a-cf22-1dac-4f28-e3a8f6732f90`.

Lỗi thực tế: `Unknown column 'u' in 'field list'`. Profile cho thấy Drill/Calcite đẩy biểu thức dự phòng tên nhóm xuống MySQL dưới dạng `u&'Ch\01b0a c\00f3 nh\00f3m h\00e0ng'`. MySQL không xử lý cú pháp literal này như mong đợi và hiểu `u` là cột. Đây không phải lỗi quyền truy cập hoặc mất kết nối nguồn.

Sau khi bỏ MongoDB, SQL cũ còn JOIN lại catalog tổng hợp từ chính bảng products. Đã gỡ JOIN/CTE dư thừa, lấy trực tiếp tên và nhóm từ products (các cột không NULL trong schema), loại bỏ literal dự phòng gây lỗi khỏi các nhánh SALES/ABC, EXPIRY và REPLENISHMENT. Giữ nguyên cách dùng giá net đã lưu và trừ lượng nhận trả.

Kiểm tra đọc riêng SQL mới trên Drill: SALES, ABC, EXPIRY và REPLENISHMENT đều COMPLETED; Profile SALES mới không còn literal `u&`. Mốc này chưa có đơn bán nên kết quả rỗng không đồng nghĩa đã xác nhận số tiền nghiệp vụ. Cần đối chiếu lại API sau build và sau khi dữ liệu có hóa đơn thật trong bộ học tập.

Các chuỗi tiếng Việt trong dữ liệu MySQL vẫn được giữ. Không chuyển tên sản phẩm/nhóm thành không dấu để né lỗi; sửa biểu thức SQL và đường pushdown gây lỗi.

Kiểm chứng sau khi nạp dữ liệu: API SALES trả COMPLETED với 5 sản phẩm, query ID `151b9bbe-8457-5b6c-c48d-261495827fe1`; các API ABC, EXPIRY, REPLENISHMENT, CHANNELS và QUALITY cũng COMPLETED. Cách đọc DATE/TIMESTAMP Cassandra được sửa riêng để đối soát không bỏ sót các khoản thu/hoàn đã đồng bộ; xem `huong_dan_thu_doi_soat_2026-10-30.md`.
