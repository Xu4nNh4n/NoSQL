# Phân tích dữ liệu siêu thị đa nguồn bằng Apache Drill

Ứng dụng POS gồm bán tại quầy, kho theo lô/FEFO, trả từng món, hoàn tiền và khuyến mãi. Shop online đã ra khỏi phạm vi. Backend Spring Boot chạy trên host, frontend React/Vite chạy riêng; Docker Compose phục vụ Drill và Cassandra local.

Tình trạng bàn giao 2026-10-30: source hiện đang dừng, các owner vẫn đang hoàn thiện thay đổi. Nội dung dưới đây mô tả mã nguồn đã đọc, không chứng nhận triển khai thành công. Chưa kiểm chứng runtime ba nhóm SQL đa nguồn, kết nối nguồn, migration, báo cáo giá nhập thực tế, khuyến mãi hoặc ghi POS_LOG. Không yêu cầu seed/reset dữ liệu; đợt cập nhật tài liệu này không khởi động dịch vụ, chạy SQL hay ghi database.

| Nguồn/thành phần | Vai trò trong source hiện tại |
|---|---|
| MySQL `supermarket_api` | Giao dịch POS, giá/khuyến mãi, lô hàng, thanh toán và Outbox; schema mới có 12 bảng |
| Cassandra `supermarket_analytics.business_events_by_day` | Sự kiện chia partition theo ngày UTC; dispatcher ghi qua đích `ASTRA`; SQL Drill đọc trực tiếp qua `cassandra_source` |
| Giá nhập thực tế theo lô (MySQL) | Drill JOIN `inventory_batches` + `products` + `suppliers`, dùng `unit_cost` đã lưu khi nhập hàng; không cần CSV báo giá |
| JSON `analytics/pos-payments/<UTC-date>/<eventId>.json` | Log thanh toán/hoàn tiền do `POS_LOG` ghi; đối chiếu với sổ MySQL |
| Apache Drill | SQL qua JDBC MySQL, plugin Cassandra và workspace JSON; backend gọi Drill REST |

MongoDB đã được gỡ khỏi luồng tích hợp hiện tại. Không cần Mongo URI trong `.env.example`. Các tài liệu, seed, SQL demo và benchmark cũ có thể vẫn mô tả MongoDB hoặc đường export Cassandra → JSON → Parquet; đó là kiến trúc lịch sử, không phải bằng chứng cho source mới.

## Lịch sử tồn kho chốt ngày

`database/cassandra/stock-daily-snapshots.cql` là DDL thủ công cho bảng snapshot, chưa được chạy. Dữ liệu chỉ được dựng khi ADMIN gọi `POST /api/integrations/cassandra/stock-snapshots?from=YYYY-MM-DD&to=YYYY-MM-DD`; không có scheduler tự ghi. API từ chối kỳ chưa kết thúc hoặc lịch sử không giải thích được số dư lô. Trang **Lịch sử kho** chỉ đọc snapshot qua Drill và không tự tạo dữ liệu. Snapshot phản ánh lượng còn lại vật lý theo sản phẩm, không đồng nghĩa tồn khả dụng sau khi xét hạn dùng/trạng thái lô.

## Schema và migration bắt buộc trước khi khởi động

`SQL/schema.sql` định nghĩa 12 bảng: `roles`, `users`, `suppliers`, `products`, `promotions`, `inventory_batches`, `stock_movements`, `sales_orders`, `sales_order_items`, `sales_order_returns`, `payment_records`, `data_sync_outbox`.

Bảng mới `promotions` lưu khuyến mãi theo sản phẩm và khoảng hiệu lực. `sales_order_items.net_unit_price` lưu đơn giá thực thu, làm tròn HALF_UP trước khi nhân số lượng; `promotion_snapshot` lưu thông tin khuyến mãi tại lúc bán. Các dòng hóa đơn cũ được giữ `net_unit_price = NULL` theo migration để bảo toàn cách phân bổ hoàn tiền cũ. Thời gian hiệu lực khuyến mãi dùng giờ Việt Nam `Asia/Ho_Chi_Minh`.

Chọn đúng một đường chuẩn bị schema, thực hiện thủ công trước khi khởi động backend:

- Database POS cũ: sao lưu, chọn database `supermarket_api` trong MySQL client rồi chạy một lần [SQL/migrations/2026-10-30-promotions.sql](SQL/migrations/2026-10-30-promotions.sql). Migration này chưa được thực thi trong đợt công việc này; MySQL DDL tự commit.
- Database mới, trống: dùng [SQL/schema.sql](SQL/schema.sql) thay cho migration. Không chạy lại schema trên database đang dùng và không chạy migration sau schema mới.

Backend không tự áp dụng migration. Không có yêu cầu seed, reset hay nạp lại dữ liệu. Không chạy lại script seed/benchmark cũ: client tạo đơn hiện phải gửi `quoteToken`, còn script cũ có thể thiếu token và giả định schema/kiến trúc đã bỏ.

## Khởi động sau khi hoàn tất schema thủ công

Cần Java 21, MySQL, Node.js và Docker. Các lệnh sau chỉ là hướng dẫn cho lần vận hành sau khi người vận hành hoàn tất migration hoặc tạo schema mới; chưa được chạy để xác minh source hiện tại.

Sao chép `.env.example` thành `.env` nếu chưa có, điền `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` và cấu hình Cassandra/Drill. Backend đọc `.env` ở thư mục cha qua `spring.config.import`. Giữ timezone JVM/MySQL nhất quán với dữ liệu đã lưu. Chạy Maven từ `backend/`.

```powershell
# Tại thư mục gốc, chỉ sau bước schema thủ công:
docker compose up -d cassandra
docker compose up -d --build drill
powershell -ExecutionPolicy Bypass -File .\drill\configure-storage.ps1

# Terminal backend:
cd backend
.\mvnw.cmd '-Dmaven.test.skip=true' spring-boot:run

# Terminal riêng, bắt đầu tại thư mục gốc:
cd frontend
npm run dev
```

Cassandra phải có keyspace/bảng phù hợp trước khi dùng chức năng sự kiện; file tham chiếu là `database/cassandra/local-schema.cql`, không phải lệnh seed tự động. Chế độ local dùng `CASSANDRA_MODE=LOCAL`, host `127.0.0.1`, cổng `9042`, datacenter `datacenter1`; token/bundle chỉ dành cho Astra. Backend mặc định cổng 8080, OpenAPI `/v3/api-docs`; Drill dùng `DRILL_HTTP_PORT` (8048 trong `.env.example`). Địa chỉ Cassandra trong container Drill cấu hình riêng bằng `DRILL_CASSANDRA_HOST`.

Compose giới hạn Cassandra 2 GiB (heap 768 MiB, young generation 128 MiB), Drill 3 GiB (heap 1 GiB, direct memory 1 GiB, code cache 128 MiB). Đây là trần cấu hình, không phải số đo runtime; chưa tính Docker VM, MySQL, backend và frontend. Cassandra dùng profile `local-cassandra`, không tự restart; có thể dừng khi không dùng bằng `docker compose stop cassandra`. Volume `cassandra-data` và `drill-data` giữ dữ liệu/cấu hình; không xóa volume để cập nhật source.

## UI và hợp đồng client

- `/promotions`: ADMIN/MANAGER quản lý khuyến mãi; route đã có trong `frontend/src/App.tsx`, chưa xác minh UI runtime.
- `/purchase-costs`: trang giá nhập thực tế qua `GET /api/analytics/purchase-costs`, dùng Drill đọc MySQL `inventory_batches` + `products` + `suppliers`. Giá là `inventory_batches.unit_cost` của lô đã nhập, không phải báo giá tương lai hoặc giá bán. Không cần nhập CSV. Route/API đã được triển khai. Báo cáo đã chạy thành công qua Drill trên dữ liệu hiện có; kết quả gồm giá từng lô và chênh lệch so với lô trước. Backend/frontend build thành công, chưa chạy bộ test.
- `/pos`: client gọi `POST /api/orders/quote`, hiển thị báo giá, rồi gửi `quoteToken` nhận được cùng request `POST /api/orders`. Token là bắt buộc cho mọi client tạo đơn, kể cả công cụ/script. Backend khóa sản phẩm và tính lại giá; token thiếu hoặc khác báo giá hiện tại sẽ bị từ chối, cần lấy lại báo giá và xác nhận.

Tính năng CSV báo giá đã bị loại bỏ: gỡ backend import/template và dependency Commons CSV, bỏ workspace `business` cùng format `csvh` trong cấu hình Drill. CSV và archive do người dùng tạo được giữ nguyên trên đĩa, không còn là nguồn của luồng thay thế; không xóa hay nạp lại chúng. Query/controller CSV đã được thay bằng báo cáo giá nhập lô MySQL. Hợp đồng `quoteToken` của POS vẫn giữ nguyên.

## Ba nhóm báo cáo Drill và log thanh toán

| Nhóm trong source | Nguồn và giới hạn xác minh |
|---|---|
| Báo cáo sự kiện/dòng tiền/đối soát | MySQL + Cassandra trực tiếp qua `cassandra_source`; `DrillAnalyticsService` không dùng export JSON/Parquet cho nguồn sự kiện hiện tại. Chưa thử kết nối, kiểu dữ liệu, predicate partition hay kết quả runtime; đường trực tiếp chỉ cấu hình cho OPERATIONS |
| Giá nhập thực tế | `/purchase-costs` → `GET /api/analytics/purchase-costs` → Drill JOIN MySQL `inventory_batches` + `products` + `suppliers`; đã chạy thành công qua Drill trên dữ liệu hiện có |
| Đối chiếu log POS | JSON + MySQL trong `MultisourceAnalyticsService.paymentLog`; cần log thực do dispatcher ghi, chưa chạy SQL. MATCH theo dòng không chứng nhận log đầy đủ hoặc timestamp bằng nhau |

`DataSyncPublisher` xếp sự kiện vào `ASTRA`; riêng `PAYMENT_RECEIVED`/`REFUND_CONFIRMED` còn có bản `POS_LOG` với event key độc lập ổn định `POSLOG-<key>` và cùng event UUID. Dispatcher xử lý `POS_LOG` kể cả khi Astra tắt. `PosPaymentLogWriter.write(JsonNode)` chỉ ghi khi được gọi, không tạo dữ liệu ban đầu; thư mục cấu hình bằng `integration.pos-payment-log-directory`, mặc định `../analytics/pos-payments` khi chạy từ backend.

Writer kiểm tra UUID, epoch milliseconds, ngày UTC, ID và metadata JSON; ghi một file cho mỗi UUID trong partition UTC bằng temp file rồi atomic move. Replay giống nhau không ghi lại; payload khác (kể cả metadata, qua `payload_sha256`) hoặc UUID ở partition khác gây lỗi. Có khóa giữa các writer cùng phối hợp. Atomic move không được hỗ trợ thì thất bại, không hạ xuống ghi không atomic. Lỗi được đưa về dispatcher để retry; không nuốt lỗi. Chưa có lần ghi log, replay, xung đột hoặc thử lỗi được thực hiện để xác minh runtime trong đợt này.

Tiền thu ròng = PAYMENT − REFUND, không đồng nghĩa lợi nhuận hay doanh thu kế toán. Partition sự kiện/log là UTC; các khoảng báo cáo hiện dùng ngày nghiệp vụ Việt Nam và chuyển sang UTC trong SQL. Khi thiếu nguồn, không suy diễn báo cáo rỗng thành kiểm chứng thành công.

## Tài liệu và mã nguồn

- `backend/`: Spring Boot, Maven wrapper; `frontend/`: React/Vite.
- `SQL/`: schema/migration thủ công; `database/`: công cụ và bộ dữ liệu lịch sử, cần rà soát trước khi dùng.
- `analytics/`, `drill/`: file phân tích và cấu hình Drill. Sự tồn tại của code export/Parquet không chứng nhận đã tạo snapshot mới.
- [Kế hoạch triển khai](docs/KE_HOACH_TRIEN_KHAI_BIGDATA.md): tình trạng code và các bước xác minh còn lại.
- [Chỉ mục tài liệu](docs/README.md), `docs/ai/`, `docs/benchmark/`: tài liệu lịch sử; benchmark theo kiến trúc cũ không đại diện cho ba nhóm SQL hiện tại. Không chạy lại seed để tái tạo benchmark trước khi sửa hợp đồng quoteToken và có yêu cầu nạp dữ liệu riêng.

Đăng nhập dùng access JWT, không refresh token. Frontend gặp 401 ở API bảo vệ thì xóa phiên và yêu cầu đăng nhập lại; logout xử lý tại trình duyệt. JWT đã cấp không tự mất hiệu lực ngay khi logout/đổi mật khẩu.
