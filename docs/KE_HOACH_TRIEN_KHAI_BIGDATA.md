# Thực trạng và kế hoạch triển khai Big Data

Cập nhật 2026-10-30 theo mã nguồn đang được các owner chỉnh sửa. Source hiện đang dừng; chưa xác nhận runtime cho kiến trúc mới. Không có yêu cầu seed/reset, khởi động dịch vụ hoặc ghi database trong đợt cập nhật này. Không xem code đã có, dependency đã khai báo hay build cũ là bằng chứng nghiệp vụ đã chạy thành công.

| Hạng mục | Đã thấy trong source | Chưa xác minh |
|---|---|---|
| POS và khuyến mãi | Kho lô/FEFO, trả món/hoàn tiền; bảng `promotions`, đơn giá `net_unit_price`, `promotion_snapshot`; UI `/promotions` cho ADMIN/MANAGER | Migration, tính tiền/hoàn tiền, UI và runtime |
| Quote | `POST /api/orders/quote`; tạo đơn phải gửi `quoteToken`; backend khóa và tính lại giá trước khi ghi | Luồng client end-to-end, thay đổi giá và token cũ |
| Outbox | Cùng giao dịch MySQL; `ASTRA` và bản thanh toán `POS_LOG` có khóa độc lập `POSLOG-<key>`, cùng UUID; MongoDB đã gỡ khỏi luồng | Delivery, backlog và retry thực tế |
| JSON POS | `PosPaymentLogWriter` ghi `<UTC-date>/<eventId>.json`, temp + atomic move, khóa, replay idempotent và conflict theo payload | Chưa thực hiện ghi, replay, conflict hay failure injection; không có số attempts runtime để báo cáo |
| SQL MySQL + Cassandra | Drill đọc trực tiếp `cassandra_source.<keyspace>.business_events_by_day`, thay đường export cho báo cáo hiện tại; OPERATIONS | Kết nối/plugin, kiểu dữ liệu, partition predicate và kết quả SQL |
| Giá nhập thực tế qua Drill | Hợp đồng mới: UI `/purchase-costs`, API `GET /api/analytics/purchase-costs`, JOIN MySQL `inventory_batches` + `products` + `suppliers`, lấy `unit_cost` của lô | Parent đang thay query/controller; owner frontend cập nhật route. Chưa xác nhận hoàn tất hay kết quả SQL/UI runtime |
| SQL JSON + MySQL | `MultisourceAnalyticsService.paymentLog` đối chiếu thanh toán theo ID/đơn/loại/phương thức/số tiền | Log thực, thiếu partition và kết quả SQL; MATCH không đảm bảo đầy đủ log hoặc timestamp bằng nhau |

Luồng trong code: POS → giao dịch MySQL + Outbox → dispatcher → Cassandra (`ASTRA`) và JSON thanh toán (`POS_LOG`); giá nhập thực tế lấy từ lô MySQL qua Drill, không cần CSV; Drill truy vấn các nguồn và backend trả báo cáo. MongoDB không còn là nguồn của kiến trúc hiện tại. Các owner vẫn đang làm việc; chỉ xác nhận các thành phần đọc được, không tuyên bố đã hoàn tất snapshot, seed, dữ liệu ban đầu hoặc triển khai toàn hệ thống.

CSV báo giá đã bị loại bỏ khỏi phạm vi: gỡ service/controller/DTO, template `database/multisource-v2/supplier-prices-template.csv` và dependency Commons CSV; cấu hình Drill bỏ riêng workspace `business` và format `csvh`, giữ các workspace JSON/Cassandra. Không cần import CSV để xem giá nhập thực tế. CSV hoạt động và archive của người dùng được giữ nguyên, không dùng trong luồng mới; parent phụ trách bỏ trường/query CSV trong MultisourceAnalyticsService/Controller. Giá lô đã nhập không phải báo giá mua tương lai. Không thay đổi database hoặc frontend trong phần dọn dẹp này.

## Điều kiện trước khi chạy lại

Schema mới `SQL/schema.sql` có 12 bảng: roles, users, suppliers, products, promotions, inventory_batches, stock_movements, sales_orders, sales_order_items, sales_order_returns, payment_records, data_sync_outbox.

Database POS cũ phải được sao lưu rồi chạy thủ công `SQL/migrations/2026-10-30-promotions.sql` một lần trên database được chọn trước khi khởi động backend. File thêm `promotions` và hai cột `sales_order_items.net_unit_price`/`promotion_snapshot`, giữ đơn giá net của hóa đơn cũ NULL để giữ cách hoàn tiền cũ. Migration chưa được chạy trong đợt này, không tự được ứng dụng áp dụng; DDL MySQL tự commit.

Với database mới, trống, dùng `SQL/schema.sql` thay thế. Không chạy cả schema mới và migration, không chạy lại schema lên dữ liệu hiện có. Không seed/reset hoặc backfill snapshot khi chưa có yêu cầu riêng.

Chỉ sau khi người vận hành hoàn tất schema thủ công mới dùng các lệnh khởi động trong [README](../README.md). Kiểm tra `.env`, keyspace Cassandra và storage Drill theo cấu hình hiện tại; không suy diễn source đang dừng thành việc các dịch vụ đã được kiểm tra khỏe.

## Việc còn lại cần bàn giao

1. Chờ owner hoàn thiện thay đổi và rà soát source cuối cùng, không ghi đè công việc đang diễn ra.
2. Người vận hành chuẩn bị schema theo đúng một nhánh ở trên; ghi nhận kết quả thực tế thay vì đánh dấu migration đã chạy trước.
3. Khi được yêu cầu chạy, xác minh ba nhóm báo cáo Drill MySQL+Cassandra, giá nhập theo lô MySQL, JSON+MySQL trên nguồn thật; lưu câu SQL, phạm vi ngày, lỗi/kết quả và giới hạn dữ liệu. Chưa có lần chạy nào được chứng nhận ở đây.
4. Khi được yêu cầu kiểm thử, xác minh quoteToken bắt buộc cho mọi client, khuyến mãi, tiền hoàn và giá nhập theo lô; xác minh atomic write/replay/conflict/retry POS_LOG. Code có retry không đồng nghĩa đã thử lỗi thành công.
5. Chỉ lập benchmark mới sau khi có dataset và quyền chạy rõ ràng, rồi thu Drill Profiles/ảnh/kết quả đối soát. Không đặt số liệu hoặc ảnh snapshot giả để hoàn thiện báo cáo.

## Tài liệu lịch sử và benchmark

Các benchmark, seed và demo JOIN MySQL/MongoDB/export JSON/Parquet trong tài liệu cũ thuộc kiến trúc trước. Có thể giữ làm tham khảo lịch sử, không dùng làm bằng chứng hiệu năng hay đối soát cho đường Cassandra trực tiếp, giá nhập MySQL và JSON mới. Code và script export/curate còn tồn tại không chứng minh đã xuất hay hoàn tất snapshot mới.

Không chạy lại seed cũ hoặc quy trình tái tạo benchmark cũ: hợp đồng tạo đơn hiện bắt buộc `quoteToken`, script thiếu token phải được sửa và rà soát schema/nguồn trước; mọi nạp dữ liệu cần yêu cầu riêng. Không backdate giao dịch thật hay nhân bản event để tạo PASS. Tiền thu ròng không phải lợi nhuận; đối soát phải nêu độ trễ Outbox và nguồn bị thiếu.
