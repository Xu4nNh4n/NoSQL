# Kế hoạch triển khai liên tục hệ thống phân tích POS đa nguồn

> Cập nhật phạm vi: người dùng đã yêu cầu bỏ báo giá CSV do không có nguồn dữ liệu. Phần CSV/báo giá trong kế hoạch cũ được thay bằng báo cáo giá nhập thực tế theo lô MySQL qua Drill, tại `/purchase-costs` và API `/api/analytics/purchase-costs`. Kho & lô hiển thị `unitCost` từng lô. Giữ phần dưới để truy vết kế hoạch ban đầu; không triển khai lại import báo giá theo các bước cũ.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Theo dõi tiến độ bằng checkbox; tiếp tục bước kế tiếp sau khi điều kiện bước hiện tại đạt, không yêu cầu người dùng gõ continue ở từng chặng.

**Goal:** Nâng cấp project hiện có thành luồng phân tích MySQL, Cassandra trực tiếp, CSV báo giá và JSON nhật ký thanh toán bằng Apache Drill; gỡ MongoDB; chuẩn bị bộ dữ liệu học tập mới có thể nạp lại và giao diện giải thích được từng nguồn.

**Architecture:** MySQL là nguồn chính cho POS, thanh toán, trả hàng và kho theo lô. Outbox đồng bộ sự kiện sang Cassandra và ghi nhật ký thanh toán JSON theo đích riêng; Drill đọc trực tiếp Cassandra sau khi plugin được kiểm chứng, đọc CSV báo giá và JSON nhật ký theo workspace. Mỗi báo cáo chỉ dùng các nguồn cần thiết, không ép JOIN cả bốn nguồn.

**Tech Stack:** Giữ Java 21, Spring Boot 4.1.1, React/TypeScript, MySQL hiện có, Cassandra Docker 4.1 và Drill Docker 1.21.2. Không đổi framework, không tăng RAM mặc định.

**Spec:** `C:/Users/Admin/Downloads/AGENTS.md` là đặc tả định hướng. Kế hoạch này thu gọn và ánh xạ vào source hiện tại, không coi ví dụ schema/SQL trong đặc tả là code đã hoạt động. Đối chiếu với `SQL/schema.sql`, các service integration hiện có và `database/seed-manifest.json`.

**Trạng thái:** Đang triển khai trên nhánh `nang-cap-drill-da-nguon`: đã có code gỡ MongoDB, khuyến mãi/POS/hoàn tiền, nhập báo giá CSV, nhật ký POS_LOG và báo cáo Cassandra trực tiếp. Backend/frontend đã build thành công ở mốc ghép ban đầu; chưa kiểm chứng runtime. Người dùng yêu cầu giữ app dừng và chưa tạo dữ liệu: mọi migration/seed/reset/ghi snapshot được để sau, không chạy tự động. Nhật ký bằng chứng: `docs/ai/nang_cap_da_nguon_tien_do.md`.

**Điều chỉnh phạm vi — khuyến mãi:** Bổ sung bảng chương trình khuyến mãi trong MySQL và liên kết sản phẩm áp dụng khi thiết kế chi tiết; lưu số tiền giảm thực tế trên từng dòng hóa đơn để tính lại hoàn tiền chính xác. Chỉ áp dụng chương trình còn hiệu lực, có quy tắc chọn một ưu đãi khi trùng và giữ lịch sử giá sau khi chương trình thay đổi. Bổ sung giao diện quản lý và báo cáo Drill; CSV chỉ đóng vai trò dữ liệu phân tích bổ trợ, không thay thế giá giảm đã chốt trên hóa đơn. Bảng/cột cụ thể và chính sách trả hàng cần được đối chiếu nghiệp vụ hiện tại trước khi migration.

**Quyết định bổ sung của người dùng:** Cho phép xóa bộ dữ liệu học tập hiện tại của project và tạo bộ dữ liệu mới; không cần giữ các bản ghi cũ có chữ “mẫu” trong dữ liệu đang sử dụng. Dùng tên sản phẩm, tên khách và ghi chú tự nhiên, không thêm tiền tố “Khách mẫu”, “DEMO”, “TEST” vào nội dung hiển thị. Đây là thay bộ dữ liệu, không chỉ đổi nhãn bản cũ. Trước khi xóa vẫn sao lưu, xác định đúng database/keyspace/thư mục và chuẩn bị script nạp lại; không xóa tài nguyên không thuộc project. Nguồn gốc trong README/báo cáo/manifest vẫn ghi “dữ liệu học tập do nhóm tạo”, không gọi là giao dịch thực thu thập từ siêu thị. Việc đồng ý làm mới dữ liệu không tự xác nhận các tính năng mở rộng khác trong kế hoạch.

## 1. Quyết định phạm vi và khác biệt với AGENTS

| Nội dung | Đợt nâng cấp này |
|---|---|
| Tên đề tài | Giữ “Phân tích dữ liệu vận hành siêu thị đa nguồn bằng Apache Drill” |
| Phạm vi | Một siêu thị; POS; sản phẩm, nhà cung cấp; kho/lô FEFO; trả từng món; hoàn tiền |
| MySQL | Giữ 11 bảng và tên trường hiện có; không thêm bảng inventory nếu các lô và tổng tồn đã đáp ứng |
| MongoDB | Gỡ code, cấu hình, đồng bộ và JOIN; không cần chuyển catalog sang một hệ thống khác |
| Cassandra | Đọc trực tiếp bằng plugin sau bước xác minh. Giữ sự kiện hiện có; snapshot tồn cuối ngày là dữ liệu dẫn xuất cần bổ sung |
| CSV | Một chức năng: báo giá nhà cung cấp. Có mẫu file, nhập/kiểm tra/xem dữ liệu và báo cáo; chưa triển khai khuyến mãi/chỉ tiêu |
| JSON | Nhật ký khoản thu/hoàn đã commit, có ID ổn định và truy vết. Không giả làm nhật ký cổng thanh toán hoặc thiết bị thật |
| Thanh toán | Giữ phương thức thực sự được source hỗ trợ. Không thêm CARD/QR chỉ để có mẫu lỗi |
| Dữ liệu | Làm bộ mới ban đầu nhỏ, sau đó nâng tới 5.000 hóa đơn trải nhiều ngày; ghi rõ dữ liệu học tập |
| Chưa làm | Nhiều chi nhánh, ca bán, chương trình khuyến mãi tự động, thanh toán thẻ thật, dữ liệu 100k–500k |
| Kiểm chứng | Build, truy vấn nguồn, SQL đối chiếu nhỏ và kiểm tra giao diện tuần tự. Không tự chạy hoặc viết bộ unit/integration test theo yêu cầu trước của người dùng |

Lý do thu gọn: cấu trúc 13 bảng và các mã ST01/SP001 trong AGENTS không khớp hệ thống 11 bảng hiện có; làm nguyên mẫu sẽ mở rộng nghiệp vụ và mất lô/FEFO/trả hàng nếu không ánh xạ. DECIMAL(18,2) hiện tại vẫn là số tiền chính xác, không cần đổi thành DECIMAL(18,0) chỉ để đồng nhất ví dụ.

## 2. Hiện trạng đã xác nhận và bản đồ thay đổi

- Nhánh hiện tại: `go-online-tra-hang`, không phải nhánh chính. Working tree có nhiều thay đổi và thư mục `backend/` chưa được Git theo dõi; không tạo worktree từ HEAD rồi giả định có đầy đủ code mới.
- Schema: `SQL/schema.sql`, 11 bảng. Không có stores, promotions hoặc inventory theo chi nhánh.
- Backend ghi Cassandra trực tiếp: `backend/src/main/java/com/api/supermarket/integration/AstraEventStore.java`.
- Báo cáo Cassandra hiện đọc file: `DrillAnalyticsService.java`, phương thức `eventSource`.
- `OperationalAnalyticsService.java` hiện JOIN `mongo_source`; phải lấy tên/nhóm từ products khi gỡ MongoDB.
- `DataSyncPublisher.java`, `DataSyncDispatcher.java`, `DataSyncOperations.java`, `CatalogSyncBootstrap.java`, `ProductCatalogProjector.java` chứa phụ thuộc catalog và/hoặc đích MONGODB.
- Cassandra configuration: `backend/src/main/java/com/api/supermarket/config/AstraCassandraConfig.java`.
- API hiện tại: `controller/AnalyticsController.java`, `controller/DataIntegrationController.java`; giữ các endpoint còn dùng, cập nhật có kiểm soát khi bỏ xuất Cassandra cho luồng báo cáo chính.
- Giao diện: `frontend/src/pages/OperationalAnalyticsPage.tsx`, `CashFlowReportsPage.tsx`, `ReportsPage.tsx`, `components/analytics/DrillReportExplanation.tsx`, `App.tsx`, `components/layout/Sidebar.tsx`.
- Cấu hình Drill: `drill/configure-storage.ps1`, `drill/Dockerfile`, `docker-compose.yml`.
- Script/dữ liệu: `database/seed-pos-operations.py`, `extend-pos-orders.py`, `spread-pos-history.py`, `export-pos-snapshot.py`, các seed hiện có. Tái sử dụng thuật toán phù hợp thay vì viết bộ sinh thứ hai độc lập.
- Tài liệu cũ `docs/KE_HOACH_TRIEN_KHAI_BIGDATA.md` còn nói online và UTC; phải cập nhật sau khi hành vi mới được kiểm chứng.
- Manifest đã lưu: 5.000 hóa đơn, 12.496 dòng hàng, 56 sản phẩm, 142 lô, 22.679 sự kiện Cassandra. Đây là snapshot, chưa truy vấn live lại trong lượt lập kế hoạch.

## Global Constraints

- Không xóa dữ liệu cũ trước khi có bản sao lưu và bộ mới vượt các kiểm tra nhất quán.
- Người dùng đã cho phép làm mới dữ liệu học tập; chỉ tác động database/keyspace/thư mục được nhận diện thuộc project này, không xóa toàn server hoặc toàn Docker volume.
- Không commit `.env`, mật khẩu, token, dữ liệu nhạy cảm hoặc backup chứa password_hash. Không `git add .`.
- Giữ các thay đổi chưa commit của người dùng. Không reset/clean/viết lại lịch sử Git.
- Chuẩn bị dữ liệu mới ở namespace riêng; chỉ chuyển cấu hình sau khi chạy được. Bản cũ được ngắt khỏi ứng dụng, không buộc phải xóa vật lý để hoàn thành nâng cấp.
- ID số, SKU và ID sự kiện nhất quán mọi nguồn; không dùng SP001/ORD001 nếu nguồn thật là BIGINT.
- Giờ giao diện: Asia/Ho_Chi_Minh. Thời điểm và khóa phân vùng có hợp đồng UTC rõ ràng.
- Tồn chính thức chỉ ở MySQL. Snapshot Cassandra không thay thế kiểm tra khả năng bán tại POS.
- Giá bán lịch sử lấy dòng hóa đơn; báo giá CSV không tính lại hóa đơn hoặc giá vốn lô cũ.
- Trả hàng và chi tiền hoàn là hai bước khác nhau; phiếu chờ hoàn không được tính thành tiền đã chi.
- Không phát sinh giao dịch học tập trong database cũ chỉ để lấy ảnh. Giữ benchmark/ảnh cũ, ghi rõ lịch sử; mọi đo mới dùng thư mục kết quả mới.
- Không chạy toàn bộ dịch vụ/test/benchmark cùng lúc. Với máy RAM hạn chế, build rồi mới chạy các thành phần cần thiết theo chặng.

## Review Focus

1. Ghi sự kiện lại sau lỗi: không nhân đôi thu/hoàn hoặc snapshot; ID và khóa Cassandra phải ổn định.
2. CSV sai SKU, giá, đơn vị hoặc thời hạn: báo lỗi theo dòng, không thay file đang có hiệu lực bằng dữ liệu nhập dở.
3. Một ngày Việt Nam cắt hai phân vùng UTC: truy vấn đủ phạm vi, không cộng thêm 7 giờ lần thứ hai.
4. Trả hàng làm thay đổi kho nhưng tiền chưa hoàn: báo cáo tách số lượng ròng, tiền hàng và dòng tiền.
5. Cassandra/Drill không truy cập được hoặc đồng bộ chậm: hiện lỗi/độ mới dữ liệu; không trả số 0 và không ép đối soát PASS.

## 3. Thứ tự triển khai liên tục

### Chặng 0 — Bảo toàn code, dữ liệu và đo mốc ban đầu

**Files:** tạo manifest và nhật ký trong `.tmp/multisource-v2-backup/` đã kiểm tra được ignore; tạo `docs/ai/nang_cap_da_nguon_tien_do.md` không chứa bí mật.

- [ ] Tạo nhánh riêng `nang-cap-drill-da-nguon` từ working tree hiện tại nếu chưa tồn tại; giữ nguyên thay đổi chưa commit. Không chuyển sang worktree thiếu backend.
- [ ] Lưu danh sách file thay đổi, diff tracked và bản sao các file untracked liên quan. Kiểm tra không chép node_modules/target/backup lồng nhau.
- [ ] Kiểm tra tài nguyên đang chạy trước khi dừng; chỉ dừng đúng tiến trình project khi thực sự cần.
- [ ] Đếm live các bảng và sự kiện, xuất MySQL/schema, catalog MongoDB và các sự kiện Cassandra; ghi số lượng/checksum và hướng khôi phục. Backup không đưa vào Git.
- [ ] Thu mốc API báo cáo và SQL hiện có vào thư mục có timestamp; nguồn lỗi ghi nguyên trạng, không nhận là đã có mốc thành công.

**Điều kiện qua chặng:** xác định đúng đích backup, có số lượng và có thể đọc lại bản xuất; chưa xóa dữ liệu nào.

### Chặng 1 — Xác minh Drill đọc trực tiếp Cassandra

**Modify:** `drill/configure-storage.ps1`, `docker-compose.yml` nếu cần kết nối mạng; tạo `drill/multisource/01-source-checks.sql`.

- [ ] Xác minh image Drill 1.21.2 có Cassandra storage plugin thực tế; cấu hình thử `cassandra_source` với host trong mạng Docker, cổng 9042. Không dùng localhost trong container để trỏ nhầm máy.
- [ ] Chạy truy vấn có lọc một event_date trên `supermarket_analytics.business_events_by_day`, chọn ID, thời gian, loại, quantity, amount. So với CQL ở cùng phân vùng.
- [ ] Chạy JOIN MySQL/Cassandra tối thiểu theo order_id và lọc ngày; kiểm tra kiểu BIGINT, DECIMAL, UTC và pushdown bằng Profile.
- [ ] Ghi SQL, query ID, kết quả và lỗi nếu có. Không gỡ JSON cũ khi plugin chưa qua bước này.

**Điều kiện qua chặng:** thực sự đọc được Cassandra và JOIN có kết quả đối chiếu đúng. Nếu plugin không tương thích, dừng thay thế nguồn này để báo nguyên nhân và lựa chọn; không tự đổi sang đọc file rồi gọi là kết nối trực tiếp.

### Chặng 2 — Bỏ MongoDB, giữ POS hoạt động

**Modify:** `OperationalAnalyticsService.java`, `DataSyncPublisher.java`, `DataSyncDispatcher.java`, `DataSyncOperations.java`, `LocalSourceBootstrap.java`, `backend/pom.xml`, cấu hình application, `.env.example`, `drill/configure-storage.ps1`, controller integration và frontend có lời gọi catalog.

**Remove sau khi tìm hết caller:** `backend/src/main/java/com/api/supermarket/mongodb/`, `ProductCatalogProjector.java`, `CatalogSyncBootstrap.java` và các script chỉ phục vụ catalog. Không xóa dữ liệu MongoDB vật lý ở bước này.

- [ ] Lấy product_name/category_name từ products cho SALES, ABC, EXPIRY, REPLENISHMENT; bỏ catalog_count khỏi DTO/UI khi không còn ý nghĩa.
- [ ] Bỏ các quality check SKU MySQL–Mongo; giữ kiểm tra trả vượt mua, số dư lô và bổ sung kiểm tra sự kiện nếu đã có nguồn trực tiếp.
- [ ] Bỏ enqueue MONGODB, dependency Mongo và API catalog/sync sau khi frontend không còn gọi; không để dispatcher gửi nhầm destination không hỗ trợ sang Cassandra.
- [ ] Tách xử lý đích đồng bộ rõ ràng thay vì else mặc định mọi đích đều là ASTRA.
- [ ] Build backend bằng `./mvnw.cmd '-Dmaven.test.skip=true' compile` trong backend; `npm.cmd run build` trong frontend. Không chạy test suite.

**Điều kiện qua chặng:** build đạt; POS, nhập lô, trả, hoàn không phụ thuộc Mongo; kết quả bán hàng không đổi chỉ vì bỏ tên/nhóm từ catalog.

### Chặng 3 — Dữ liệu mới và lịch sử kho Cassandra

**Modify:** `database/cassandra/local-schema.cql`, `AstraEventStore.java`, `DataSyncPublisher.java`, `AstraCassandraConfig.java`; tạo `database/multisource-v2/` chứa hợp đồng dữ liệu, fixture và seed phiên bản mới.

- [ ] Chuẩn bị namespace mới `supermarket_api_v2` và `supermarket_analytics_v2`, có cấu hình schema dùng chung cho SQL Drill; không để từng query hardcode database cũ. Giữ 11 bảng, lô FEFO, roles/users/nhà cung cấp và các kiểu tiền hiện có.
- [ ] Giữ cấu trúc business_events_by_day; thêm bảng dẫn xuất `stock_daily_by_day` với partition snapshot_date, clustering product_id. Chưa thêm store_id khi chỉ có một cửa hàng.
- [ ] Snapshot gồm opening_quantity, received_quantity, sold_quantity, returned_quantity, adjustment_delta, closing_quantity và generated_at; quy ước quantity là hàng vật lý còn lại, không gọi là tồn khả dụng. Ghi tách dữ liệu hỏng khi cần đối chiếu bán được.
- [ ] Tính snapshot từ lịch sử đã có số dư đầu kỳ, xử lý trả/hỏng/điều chỉnh đúng dấu; kiểm tra công thức đóng kỳ và ranh giới giờ Việt Nam. Không tạo snapshot bằng số ngẫu nhiên độc lập.
- [ ] Upsert snapshot theo cùng khóa ngày/sản phẩm, có mốc nguồn để biết độ mới. Replay không cộng tổng lần nữa.
- [ ] Fixture nhỏ phải có nhập, bán, nhận trả bán lại được, hàng trả hỏng và hoàn chờ/xác nhận; có bảng kết quả tính tay để SQL đối chiếu.

**Điều kiện qua chặng:** số dư kỳ khớp ledger MySQL cùng mốc; tồn chốt ngày và tồn hiện tại được đặt tên khác nhau trên báo cáo.

### Chặng 4 — Nhật ký thanh toán JSON có nguồn thật từ ứng dụng

**Modify:** `DataSyncPublisher.java`, `DataSyncDispatcher.java`, controller/status integration; tạo `integration/PosPaymentLogWriter.java`.

- [ ] Một khoản thu/hoàn đã commit sinh một yêu cầu POS_LOG với event_id ổn định, payment_record_id, order_id, loại, amount, method và event_time UTC.
- [ ] Worker ghi mỗi sự kiện thành một file JSON riêng trong `analytics/pos-payments/YYYY-MM-DD/`, tên theo ID ổn định; ghi file tạm rồi đổi tên trong cùng volume. Retry kiểm tra ID/nội dung, không append mù gây trùng.
- [ ] Tạo đúng schema cho sự kiện thu và hoàn thực sự có. Chưa dựng PAYMENT_FAILED, terminal_id hoặc latency khi ứng dụng chưa thu thập được.
- [ ] Không dùng file sự kiện Cassandra cũ để giả làm nguồn log thanh toán mới. Ghi rõ đây là nhật ký dẫn xuất từ nghiệp vụ đã commit, không phải bằng chứng độc lập của ngân hàng.
- [ ] Kiểm tra số khoản và tổng tiền theo loại trên fixture, gửi lại cùng event không tăng số bản ghi logic.

**Điều kiện qua chặng:** Drill đọc JSON trực tiếp, đối chiếu ID/tổng tiền với payment_records được; hai đích Cassandra/POS_LOG có retry độc lập.

### Chặng 5 — CSV báo giá có thao tác trên giao diện

**Create:** `integration/SupplierPriceFileService.java`, `controller/SupplierPriceController.java`, `frontend/src/pages/SupplierPricesPage.tsx`, `database/multisource-v2/supplier-prices-template.csv`.
**Modify:** `App.tsx`, `Sidebar.tsx`, `SecurityConfig.java`, cấu hình file workspace và lời giải thích báo cáo.

**Hợp đồng CSV:** `supplier_id,sku,unit,purchase_price,valid_from,valid_to`. UTF-8, tiền VND, ISO date; mỗi sản phẩm dùng một đơn vị đã chuẩn hóa trong bộ dữ liệu. Một dòng là một giá chào, không phải giá vốn đã mua.

**API mới dự kiến:** GET `/api/supplier-prices/template`; GET `/api/supplier-prices`; POST `/api/supplier-prices/import?validateOnly=true|false`. ADMIN và WAREHOUSE được quản lý; kiểm tra quyền cả backend và UI.

- [ ] Màn hình có tải mẫu, nhập file, xem trước lỗi theo dòng, xác nhận thay phiên bản báo giá và danh sách giá đang đọc được.
- [ ] Kiểm tra mã nhà cung cấp và SKU tồn tại, đơn vị thống nhất, giá không âm, ngày hợp lệ, không trùng/chồng thời gian cùng nhà cung cấp–SKU. Giới hạn import 2 MiB/5.000 dòng cho MVP và trả thông báo rõ nếu vượt.
- [ ] Khai báo giá trong bộ mẫu là giá chào cùng mặt bằng so sánh, chưa gồm thuế/phí; không xếp giá thấp nhất thành nhà cung cấp tốt nhất.
- [ ] Dùng thư viện CSV đã có nếu phù hợp; nếu chưa có, chọn parser CSV chuẩn, không split dấu phẩy thủ công. Cho phép trường có dấu phẩy/quote và bỏ UTF-8 BOM đầu header.
- [ ] Chỉ thay `analytics/business/supplier_prices.csv` sau khi toàn file hợp lệ bằng ghi tạm rồi đổi tên; lưu bản trước ngoài workspace active để Drill không đọc cả hai phiên bản. Không lưu path do người dùng cung cấp.
- [ ] Chỉ mục trên giao diện: sản phẩm, nhà cung cấp, đơn vị, giá chào, thời hạn, trạng thái. Không sửa giá hóa đơn hoặc tạo nhập kho khi import báo giá.

**Điều kiện qua chặng:** người dùng biết dữ liệu đến từ file nào, xem được giá trên UI; import lỗi không làm mất dữ liệu đang dùng.

### Chặng 6 — Báo cáo Drill và đối soát đa nguồn

**Modify:** `DrillAnalyticsService.java`, `OperationalAnalyticsService.java`, `AnalyticsController.java`, `ReportTime.java`, `drill/analytics-queries.sql`, `drill/reconciliation-queries.sql`; tạo SQL riêng trong `drill/multisource/`.

- [ ] MySQL-only: bán chạy theo lượng ròng, tiền hàng sau trả, ABC, tồn và hạn dùng; giữ ý nghĩa và quy tắc hoàn hiện có.
- [ ] MySQL + Cassandra: báo cáo lượng bán kèm tồn chốt ngày; đối soát biến động/kho cùng ngày, cùng đại lượng. Tổng hợp từng phía trước JOIN để tránh nhân dòng.
- [ ] MySQL + CSV: báo cáo mặt hàng cần xem xét nhập, các báo giá có hiệu lực, dự toán theo số lượng nhập do người dùng chọn hoặc công thức hiện có; không tự mua/nhập kho.
- [ ] MySQL + JSON: dòng tiền và đối chiếu số khoản, tổng thu/hoàn theo ngày; lọc đúng loại và dedup event_id khi truy vấn.
- [ ] API báo cáo mới dự kiến: GET `/api/analytics/stock-history?from=&to=`; GET `/api/analytics/purchase-prices?asOf=&quantity=`. API cash-flow/reconciliation giữ URL khi có thể, cập nhật metadata nguồn.
- [ ] Tất cả báo cáo trả về nguồn, thời gian cập nhật, SQL/query ID, định nghĩa chỉ tiêu và lỗi có ý nghĩa. Không ép PASS khi thiếu nguồn hoặc độ trễ chưa xử lý.
- [ ] Bỏ vai trò bắt buộc của export JSON Cassandra sau khi query trực tiếp vượt đối chiếu; giữ bản xuất cũ làm bằng chứng/backup, không xóa lịch sử benchmark.

**Điều kiện qua chặng:** ba JOIN liên nguồn thực sự chạy trên fixture, so được kết quả tính tay; không đánh dấu chỉ vì đã viết SQL.

### Chặng 7 — Giao diện giải thích được nguồn và kết quả

**Modify:** các trang Reports/OperationalAnalytics/CashFlowReports và DrillReportExplanation đã liệt kê; trang giá mới; route/menu/types liên quan sau khi đọc caller.

- [ ] Trang Big Data giới thiệu bốn nguồn, trạng thái và thời điểm cập nhật; bỏ đồng bộ catalog Mongo và bước “xuất Cassandra để xem báo cáo” nếu đã thay bằng direct plugin.
- [ ] Chia nhóm: Bán hàng; Kho và lịch sử; Báo giá nhập; Dòng tiền và đối soát. Giữ bố cục xanh dương lạnh, filter ngày Việt Nam, pagination và thông báo trạng thái rỗng/lỗi.
- [ ] Mỗi báo cáo có “đọc dữ liệu gì”, “cách tính”, bảng/biểu đồ và SQL/Profile khi cần; không bắt người dùng đọc file raw để hiểu.
- [ ] Tránh nhãn doanh thu/lợi nhuận cho tiền thu ròng; không ghi tồn chốt ngày thành tồn hiện tại; không hiển thị tỷ lệ thanh toán lỗi khi chưa có log attempt/failure.
- [ ] Build frontend và xem một luồng đầy đủ: xem báo giá → chạy phân tích → xem dòng tiền/đối soát, với tài khoản có quyền phù hợp.

### Chặng 8 — Tạo dữ liệu mới, chuyển cấu hình và ngắt bộ cũ

**Files:** `database/multisource-v2/README.md`, schema/seed MySQL, CQL, mẫu và dữ liệu CSV, JSON fixture, manifest; điều chỉnh script sinh dữ liệu hiện có để dùng cùng hợp đồng.

- [ ] Viết script và hướng dẫn trước khi nạp. Cùng seed/đầu vào tạo bộ liên kết có thể tái lập; lưu manifest, số lượng, khoảng ngày và tổng tiền đối chiếu.
- [ ] Nạp fixture kiểm tra riêng trước, sau đó tạo bộ vận hành mới tới 5.000 hóa đơn trên 30 ngày trong namespace mới. Không trộn các dòng cố tình lỗi của fixture vào bộ sử dụng. Sản phẩm/lô/nhà cung cấp đa dạng, có dữ liệu nhập trước bán, đủ tồn, có trả/hoàn và ngày không bán. Dùng tên và ghi chú tự nhiên, không gắn chữ “mẫu”/DEMO/TEST trên bản ghi hiển thị; vẫn lưu nguồn gốc do nhóm tạo trong manifest/tài liệu ngoài luồng giao diện.
- [ ] Dùng API nghiệp vụ cho các hành vi phát sinh hoặc script có cùng kiểm tra, không nạp số dư và sự kiện ngẫu nhiên riêng rẽ. Nếu trải lại thời gian, đồng bộ thời điểm MySQL, outbox, khóa Cassandra, nhật ký JSON và snapshot cùng một bộ quy tắc.
- [ ] Xuất file nạp từng nguồn; hướng dẫn rõ thứ tự và hành vi khi chạy lại. Không gọi JSON/CSV là SQL. CSV báo giá mẫu được ghi nhãn nguồn học tập.
- [ ] So số đơn/dòng/tiền/thu/hoàn/kho và snapshot; xác minh outbox không còn lỗi, nhưng không suy ra điều đó đồng nghĩa mọi báo cáo PASS.
- [ ] Chuyển backend và plugin Drill sang namespace mới; khởi chạy lại đúng thành phần để người dùng xem. Ngắt bộ cũ khỏi app.
- [ ] Chỉ xóa vật lý database/keyspace/collection cũ của project khi xác minh tên đích chính xác và backup khôi phục được. Có thể giữ bản cũ tạm thời; không cần xóa để chứng minh app đã dùng bộ mới. Không dùng docker compose down -v hoặc xóa cả volume để dọn một keyspace.

### Chặng 9 — Dọn code, cập nhật tài liệu và bàn giao

**Modify:** `README.md`, `.env.example`, `docs/KE_HOACH_TRIEN_KHAI_BIGDATA.md`, từ điển dữ liệu, sơ đồ và `docs/word/TONG_HOP_DE_TAI_GUI_CHATGPT_VIET_WORD.md`. Cập nhật bản TXT trong Downloads khi schema/luồng mới đã ổn định.

- [ ] Tìm toàn bộ caller MONGODB/mongo_source, luồng xuất bắt buộc cũ, SIMULATED và script chỉ dùng dữ liệu cũ. Chỉ xóa phần không còn caller; giữ archive ngoài đường chạy nếu là bằng chứng lịch sử.
- [ ] Chốt sơ đồ thật: Drill đọc MySQL, Cassandra, CSV và JSON; JSON thanh toán mới không phải bước bắt buộc để đọc Cassandra.
- [ ] Giữ benchmark/ảnh lịch sử 1k/3k/5k/7k và ghi rõ kiến trúc cũ, không lấy số đo cũ làm hiệu năng direct plugin mới.
- [ ] Nếu đo kiến trúc mới, tạo thư mục kết quả mới, chạy tuần tự ít lượt phù hợp RAM, lưu cả lỗi; không đo 100k–500k tự động.
- [ ] Commit nhỏ chỉ file thuộc từng thay đổi sau khi kiểm tra staged diff, loại bí mật và dữ liệu lớn. Không đưa toàn bộ thay đổi kế thừa vào một commit.
- [ ] Ghi báo cáo: đã làm, câu lệnh và đầu ra, phần chưa kiểm chứng, hạn chế, hướng chạy lại và hướng rollback.

## 4. Bảng theo dõi và nguyên tắc tiếp tục

| Chặng | Sản phẩm bàn giao | Trạng thái |
|---|---|---|
| 0 | Backup, mốc đối chiếu, nhánh làm việc | Chưa làm |
| 1 | Drill đọc và JOIN Cassandra trực tiếp | Chưa làm |
| 2 | POS/báo cáo bỏ phụ thuộc MongoDB | Chưa làm |
| 3 | Namespace mới, sự kiện và lịch sử kho | Chưa làm |
| 4 | JSON nhật ký thu/hoàn có thể replay | Chưa làm |
| 5 | Import và xem bảng giá CSV | Chưa làm |
| 6 | Truy vấn và đối soát đa nguồn | Chưa làm |
| 7 | Giao diện rõ nguồn/chỉ tiêu | Chưa làm |
| 8 | Bộ mới 5.000 hóa đơn, chuyển cấu hình | Chưa làm |
| 9 | Dọn code, tài liệu, bằng chứng và bàn giao | Chưa làm |

Triển khai liên tục nghĩa là tự chuyển chặng sau khi điều kiện đạt, sửa lỗi có nguyên nhân rõ và cập nhật checklist; không cần người dùng gõ continue. Chỉ hỏi khi phải đổi phạm vi đã chốt, thiếu quyền/thông tin bên ngoài hoặc không thể thực hiện thay đổi dữ liệu an toàn. Nếu gặp lỗi, dùng systematic-debugging để tìm nguyên nhân trước khi sửa. Không chạy các bước phụ thuộc qua một chặng đang thất bại.

## 5. Tự rà soát kế hoạch và giới hạn

- Đã ánh xạ đường dẫn theo source backend/frontend hiện tại; file mới và API mới được ghi rõ là đề xuất, không phải tên đã tồn tại.
- Đã bao gồm độ trễ đồng bộ, retry/file atomic, thời gian, hoàn tiền, dữ liệu thiếu và nguồn lỗi trong các chặng tương ứng.
- AGENTS có Q04 khuyến mãi, Q06 chỉ tiêu và ba chi nhánh: chưa đưa vào MVP này. CSV chọn báo giá để người dùng có thể xem/nhập trên UI và không sửa quy tắc checkout.
- Snapshot theo sản phẩm, chưa theo lô/chi nhánh; FEFO và tồn khả dụng vẫn do dữ liệu lô MySQL quyết định.
- Cách cô lập namespace mới là đề xuất bảo toàn dữ liệu khi thay toàn bộ; tên phải được kiểm tra chưa xung đột trước khi tạo.
- Quyền làm mới dữ liệu không có nghĩa toàn bộ dữ liệu máy đều được xóa. Backup và mốc trước vẫn cần thiết để khôi phục và giải thích khác biệt.
- Việc đọc source/tài liệu chính thức chưa chứng minh plugin Cassandra trong image thực tế hoạt động. Chặng 1 là điều kiện kỹ thuật đầu tiên, trước khi bỏ đường cũ.
- Kế hoạch này chưa phải bằng chứng hoàn thành hay kết quả kiểm thử. Không đánh dấu chặng nào đã xong ngoài việc lập kế hoạch.
