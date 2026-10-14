# Khảo sát hiện trạng đồ án Apache Drill

Ngày khảo sát: 14/10/2026. Phạm vi được đối chiếu trực tiếp với source code, Liquibase, cấu hình Drill và giao diện trong repository. Các kết luận về dịch vụ cloud được đánh dấu riêng vì Astra hiện ngủ đông.

## Luồng dữ liệu hiện có

| Nguồn | Dữ liệu chính | Đường Drill đọc | Vai trò |
| --- | --- | --- | --- |
| MySQL `supermarket_api` | Đơn, món hàng, thanh toán, tồn theo lô và trả hàng | JDBC storage `mysql_source` | Nguồn giao dịch và số lượng kho chuẩn |
| MongoDB `supermarket_catalog` | Tên, SKU, nhóm hàng và thuộc tính catalog | Mongo storage `mongo_source` | Bổ sung mô tả/catalog cho báo cáo; không phải sổ giao dịch |
| Astra keyspace `supermarket_analytics` | Sự kiện nghiệp vụ theo ngày | Backend xuất từng ngày thành JSON trong `analytics/cassandra`; Drill có thể đọc Parquet đã chuẩn hóa trong `analytics/curated` | Bản sao lịch sử phục vụ dòng tiền và đối soát |
| Apache Drill | SQL liên kết MySQL, MongoDB và file JSON/Parquet | REST `/query.json` | Thực thi truy vấn liên nguồn, trả kết quả và Profile |
| Spring Boot / React | Endpoint analytics và trang báo cáo | `/api/analytics/*` | Giới hạn truy vấn được phép, hiển thị nguồn, SQL và kết quả |

MySQL vẫn là nguồn chuẩn cho tiền và tồn. MongoDB bổ sung catalog; Astra là event store; Drill truy vấn liên nguồn. Hiện báo cáo dòng tiền/đối soát đọc MySQL và file xuất Astra; các báo cáo bán hàng mới nối MySQL với MongoDB. Chưa có truy vấn ba nguồn duy nhất trong luồng màn hình.

## Đối chiếu schema và logic

- Bảng bán là `sales_orders`, `sales_order_items`; thời gian có `created_at`; món có `quantity`, `returned_quantity`, `unit_price`, `subtotal`. Không có trạng thái `COMPLETED`: POS thành công là `PAID`/trạng thái trả một phần hoặc trả hết; online cần đã giao và trạng thái `DELIVERED`/trả hàng. Ví dụ SQL cũ trong kế hoạch dùng sai tên `order_date`, `products.product_id` phù hợp nhưng lấy `quantity * unit_price` mà chưa trừ số đã trả.
- Kho theo lô nằm trong `inventory_batches`; số khả dụng là `remaining_quantity - reserved_quantity` cho lô `ACTIVE`, chưa hết hạn. Giá vốn lịch sử là `unit_cost` của lô. Các lô nền từ migration có giá vốn 0 vì không biết giá lịch sử.
- Catalog Mongo dùng `products_catalog`, khóa nối `sku` ↔ `products.sku_code`, tên `name`, nhóm `categoryName`. Trước khi nối cần gom về một dòng/SKU; nếu không, SKU trùng làm nhân doanh thu.
- Không có thời gian giao hàng thực tế/lead time trong supplier schema. Gợi ý nhập hàng phải nhận lead time và số ngày dự trữ làm tham số giả định, đồng thời ghi rõ trong kết quả.
- Doanh thu hàng bán ròng không phải tiền thu ròng hay lợi nhuận. Lợi nhuận cần giá vốn của đúng lô đã xuất; hiện chi tiết bán chưa mang đủ phân bổ giá vốn để tính lợi nhuận lịch sử chính xác.
- Astra đang ngủ đông theo thông tin người dùng. Lần gọi trước nhận 401 khi database chưa khả dụng; chưa kết luận token sai và chưa kiểm chứng kết nối sau khi resume.

## Đánh giá mức phù hợp với rubric

Hướng đề tài phù hợp: Drill có vai trò cụ thể trong truy vấn liên nguồn và giao diện quản lý. Điểm yếu ban đầu là demo nghiêng về tiền, chưa cho thấy JOIN MySQL–Mongo hay câu hỏi kinh doanh đủ cụ thể. Báo cáo sản phẩm, hạn dùng, bổ sung hàng, ABC, kênh bán và chất lượng dữ liệu giúp đề tài dễ giải thích hơn. Tuy nhiên rubric không thể chốt điểm từ code: dung lượng, Profile, so sánh JSON/Parquet, báo cáo nộp và bằng chứng đóng góp nhóm vẫn cần kết quả thực đo/thực tế.

## Rủi ro dữ liệu và demo

1. Không gộp dữ liệu mô phỏng vào database vận hành; dùng database MySQL và Mongo riêng, gắn `SIMULATED` vào tên nguồn/manifest.
2. Bản mô phỏng có thể phục vụ demo quy mô, nhưng không chứng minh Astra cloud hoặc hiệu năng Astra.
3. Đối soát sạch và lỗi chủ động chỉ có giá trị khi chạy thật trên hai bộ dữ liệu tách biệt; bản ghi trùng ID có thể bị deduplicate nên không nhất thiết làm lệch tổng tiền. Chỉ đánh dấu `CHECK` nếu rule quality phát hiện lỗi thực sự.
4. Thời gian phản hồi API không được gọi là benchmark. Benchmark phải đo theo quy trình cố định, giữ raw results và Drill Profiles; hiện chưa chạy.

\n