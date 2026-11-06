# Yêu cầu Antigravity: tạo dữ liệu hợp lệ cho hệ thống POS và Apache Drill

Bạn đang làm việc trong repository `D:/Code/code/Java/Backend/SuperMarketAPI-BigData`. Hãy đọc source hiện tại, sau đó xây dựng và nạp bộ dữ liệu học tập phù hợp với nghiệp vụ. Làm liên tục theo từng chặng có kiểm chứng; không chỉ đưa ra ý tưởng hoặc báo hoàn thành khi mới viết script.

## 1. Hiểu đúng yêu cầu dữ liệu

“Không ảo” ở đây nghĩa là **không có số liệu vô lý, quan hệ bị đứt hoặc các nguồn được sinh ngẫu nhiên độc lập**. Dữ liệu cần giống hoạt động vận hành thông thường, có thể giải thích và truy vết từng khoản tiền, lượng hàng. Đa dạng có kiểm soát, không thay đổi tên, giá, hành vi quá mức chỉ để tạo nhiều bản ghi.

Nếu dữ liệu do script tạo, phải ghi đúng trong README/manifest là **dữ liệu học tập do nhóm xây dựng**. Không nhận là giao dịch thu thập từ siêu thị thật, Kaggle hoặc nhà cung cấp nếu chưa có nguồn tương ứng. Nếu dùng dữ liệu bên ngoài, ghi URL, giấy phép và cách biến đổi; không thay nguồn gốc dữ liệu đang có bằng một nguồn chưa từng nhập.

Trong dữ liệu hiển thị, dùng tên sản phẩm, khách hàng, chương trình và ghi chú tự nhiên. **Không thêm tiền tố/hậu tố “MẪU”, “DEMO”, “TEST”, “SEED”, “SIM”** vào tên hoặc ghi chú nghiệp vụ. Mã nội bộ vẫn phải duy nhất và có thể truy vết qua manifest ngoài giao diện.

## 2. Mô hình hiện tại phải giữ đúng

| Thành phần | Trách nhiệm |
|---|---|
| MySQL | Nguồn chính cho POS, sản phẩm, nhà cung cấp, lô hàng, khuyến mãi, thu và hoàn tiền |
| Cassandra | Lưu sự kiện; snapshot tồn cuối ngày là dữ liệu dẫn xuất khi lịch sử đủ và đối chiếu được |
| JSON | Nhật ký thu/hoàn do outbox POS_LOG tạo từ khoản tiền đã commit |
| Apache Drill | Đọc MySQL, Cassandra trực tiếp và JSON để lập báo cáo/đối soát |

MongoDB và báo giá CSV đã bị loại khỏi luồng hiện tại. **Không dựng lại MongoDB, không bắt nhập CSV báo giá, không thêm dữ liệu online hoặc nhiều chi nhánh.** Giá nhập thực tế nằm trong lô MySQL, không phải giá chào của nhà cung cấp.

MySQL có 12 bảng trong `SQL/schema.sql`:

| Bảng | Dữ liệu cần nhất quán |
|---|---|
| roles | Vai trò nhân viên |
| users | Tài khoản nội bộ; không tạo khách hàng thành nhân viên |
| suppliers | Nhà cung cấp |
| products | SKU, tên, nhóm hàng, giá hiện tại, trạng thái, tồn tổng hợp |
| promotions | Khuyến mãi phần trăm theo sản phẩm và thời hạn |
| inventory_batches | Lô nhập, giá nhập, lượng nhập/còn/hỏng, hạn dùng, nhà cung cấp |
| stock_movements | Nhập, bán, nhận trả và điều chỉnh kho |
| sales_orders | Hóa đơn POS, tiền trước giảm, giảm giá, số tiền thanh toán |
| sales_order_items | Dòng hàng, giá gốc/net, snapshot khuyến mãi, lượng đã trả, phân bổ lô |
| sales_order_returns | Phiếu nhận trả, các món, tiền cần hoàn và trạng thái |
| payment_records | Các khoản thu/hoàn đã xác nhận |
| data_sync_outbox | Yêu cầu đồng bộ với ID ổn định và khả năng retry |

Đọc schema và entity thực tế trước khi sinh SQL. Không đoán tên trường. Không dùng schema cũ có carts, customers, stores, refresh_tokens hoặc Liquibase. Không làm lại kiến trúc ứng dụng để thuận tiện cho việc nạp dữ liệu.

## 3. Các file bắt buộc đọc trước

- `SQL/schema.sql`, `SQL/migrations/2026-10-30-promotions.sql`.
- `backend/src/main/java/com/api/supermarket/service/Impl/SalesOrderServiceImpl.java`.
- `backend/src/main/java/com/api/supermarket/service/InventoryBatchService.java`.
- `backend/src/main/java/com/api/supermarket/service/PromotionService.java`.
- Các entity, repository, request/response DTO tương ứng.
- `domain/ReturnPolicy.java`, `domain/FefoPolicy.java`, `domain/PromotionSnapshot.java` trong package Java hiện tại.
- `integration/DataSyncPublisher.java`, `DataSyncDispatcher.java`, `AstraEventStore.java`, `PosPaymentLogWriter.java`.
- `integration/StockSnapshotReader.java`, `StockSnapshotService.java` và `database/cassandra/stock-daily-snapshots.cql`.
- `integration/OperationalAnalyticsService.java`, `DrillAnalyticsService.java`, `MultisourceAnalyticsService.java`, `ReportTime.java`.
- `drill/configure-storage.ps1`, `docker-compose.yml`, `.env.example`. Đọc `.env` để kết nối khi cần nhưng không in hoặc đưa bí mật vào báo cáo/Git.
- `database/prepare-pos-discounts.py`, `database/prepare-analytics-operations.py` để tái sử dụng hợp đồng API và cơ chế journal. Không mặc định các script seed cũ khác còn tương thích.

Một số tài liệu/manifest/benchmark cũ mô tả bộ 5.000 hóa đơn và MongoDB. Đó là lịch sử; **phải đếm database đang chạy**, không suy ra hiện trạng từ tài liệu cũ. Lúc chuẩn bị yêu cầu này từng có bộ nhỏ 5 sản phẩm, các lô nhập và hóa đơn do người dùng thao tác; số lượng có thể đã đổi.

## 4. Quy mô và mức đa dạng đề xuất

Đây là mục tiêu cấu hình, không phải tuyên bố dữ liệu hiện đã tồn tại:

| Nội dung | Mục tiêu |
|---|---|
| Hóa đơn | Tổng cộng khoảng 5.000, tính cả hóa đơn đang có; không cộng thêm 5.000 mỗi lần chạy |
| Giai đoạn đầu | Kiểm chứng một tập nhỏ, mở rộng tới 1.000 rồi mới tới 5.000 |
| Khoảng thời gian | 30 ngày theo giờ Việt Nam, kết thúc ở ngày được chốt trong manifest |
| Sản phẩm | Khoảng 100–150 SKU có ý nghĩa, chia 8–12 nhóm hàng |
| Nhà cung cấp | Khoảng 6–10, một đơn vị có thể cung cấp nhiều mặt hàng |
| Nhân viên | Giữ tài khoản hiện có; chỉ thêm ít tài khoản nếu có nhu cầu, không sinh hàng nghìn user |
| Lô hàng | Theo nhu cầu nhập thực tế; mặt hàng bán nhanh có nhiều lô, mặt hàng chậm ít lô |
| Khuyến mãi | Khoảng 8–15 chương trình phần trăm, không phải sản phẩm nào cũng giảm |
| Hàng trả | Tỷ lệ nhỏ, khoảng 1–3% đơn là cấu hình học tập; không tạo trả hàng ở hầu hết đơn |

Nếu người dùng đưa số lượng khác, ưu tiên số đó. Các tỷ lệ trên là lựa chọn thiết kế bộ học tập, **không phải thống kê thị trường đã được chứng minh**.

Đa dạng cần có: số đơn khác nhau giữa các ngày; giờ có bán nhiều/ít; khách vãng lai và khách có tên; tiền mặt/chuyển khoản; mặt hàng bán nhanh/chậm; lô khác giá nhập và hạn dùng; đơn có/không có giảm giá; trả chờ hoàn và đã hoàn.

Đa dạng không có nghĩa mỗi SKU bán đúng số lượng bằng nhau, mỗi ngày đúng cùng doanh số, mọi sản phẩm được giảm cùng mức, giá nhảy hàng chục lần hoặc mọi đơn đều chứa tất cả sản phẩm. Có một vài SKU dẫn đầu nhưng không ép thứ hạng để biểu đồ đẹp. Giới hạn số lượng theo nhóm: hàng gia dụng thường ít đơn vị mỗi đơn, hàng tiêu dùng có thể nhiều hơn.

Không bịa dữ liệu cá nhân: ưu tiên khách vãng lai không có thông tin; khi cần tên dùng tên hư cấu thông thường. Điện thoại tùy chọn có thể NULL; không tạo số điện thoại/địa chỉ chi tiết rồi nhận là thông tin liên hệ thật. Tên sản phẩm nên thể hiện quy cách như chai 500ml, túi 1kg, hộp 180ml. Model quantity là số nguyên nên không sinh 0,5 sản phẩm hoặc cân lẻ nếu chưa có nghiệp vụ hỗ trợ.

## 5. Quy tắc bắt buộc về thời gian và hàng tồn

### Thời gian

- Chốt một `business_end_date` và seed ngẫu nhiên trong manifest để chạy lại không tự đổi bộ dữ liệu theo ngày máy tính.
- Mọi giao dịch có thứ tự hợp lý: nhận lô trước bán; trả sau bán; xác nhận hoàn sau nhận trả.
- Phân biệt datetime nghiệp vụ Việt Nam với timestamp UTC và event_date phân vùng UTC. Không cộng 7 giờ nhiều lần.
- Sự kiện Cassandra và JSON dùng thời điểm gốc của nghiệp vụ, không dùng thời điểm chạy script cho toàn bộ lịch sử.
- Không gán mọi hóa đơn vào một ngày rồi chỉ sửa timestamp hóa đơn để giả thành nhiều ngày.

### Nhập lô và FEFO

- Một SKU có thể có nhiều lô. Lô cùng sản phẩm có thể khác giá nhập/hạn dùng; thay đổi giá phải vừa phải và giải thích được.
- Bán từ lô ACTIVE còn hạn và còn hàng, ưu tiên FEFO đúng quy tắc hiện có. Không phân bổ tùy ý vào lô mới nhất.
- Số lượng xuất phải khớp JSON batch_allocations của dòng hóa đơn và stock_movements.
- Đồ gia dụng có thể không có hạn dùng; thực phẩm có ngày sản xuất/hạn dùng hợp lý.
- Một vài lô gần hết hạn để báo cáo có dữ liệu; không tạo hàng hết hạn nhưng vẫn bán được.
- Có một số sản phẩm tồn 0 hoặc thấp để dashboard thể hiện được, nhưng không tạo giao dịch bán vượt khả dụng để đạt số đơn mục tiêu.
- Không cộng số lượng nhập, tồn còn và hỏng tùy tiện. Đọc đúng ý nghĩa remaining_quantity/damaged_quantity và cách điều chỉnh của service.
- Không lấy giá nhập lô cũ làm báo giá tương lai. Giá trị tồn theo giá nhập không phải lợi nhuận hay khoản thất thoát đã xảy ra.

## 6. Hóa đơn, khuyến mãi và tiền

Chỉ dùng các phương thức/trạng thái thực sự được code hỗ trợ. Hiện POS có CASH và BANK_TRANSFER thủ công. Không tự thêm thanh toán thẻ, cổng thanh toán, phí giao hàng hoặc trạng thái giao đơn online.

### Hợp đồng giá đã triển khai

| Trường | Ý nghĩa |
|---|---|
| sales_order_items.unit_price | Giá gốc tại lúc bán |
| sales_order_items.net_unit_price | Giá thu thực tế cho một đơn vị; dữ liệu mới luôn điền, kể cả không giảm hoặc giá bằng 0 |
| sales_order_items.subtotal | net_unit_price × quantity |
| sales_order_items.promotion_snapshot | Chương trình thực sự áp dụng lúc bán; NULL nếu không áp dụng |
| sales_orders.subtotal | Tổng unit_price × quantity, trước giảm |
| sales_orders.discount_amount | Tiền gốc trừ tiền sau giảm |
| sales_orders.total_amount | Tổng tiền sau giảm; dùng ghi khoản thu |

Tính tiền bằng Decimal/BigDecimal, không dùng float để cộng/trừ/phân bổ. Làm tròn đúng service: HALF_UP 2 chữ số ở giá net mỗi đơn vị, sau đó nhân số lượng. Không nhân thêm một lần tỷ lệ giảm cấp đơn vào dòng đã có net_unit_price.

Khuyến mãi hiện chỉ là phần trăm theo sản phẩm, trong khoảng `starts_at <= thời điểm bán < ends_at`. Chọn mức giảm cao nhất, bằng nhau ưu tiên ID nhỏ nhất, không cộng dồn. Dữ liệu chính nên dùng mức giảm quen thuộc 5%, 10%, 15%, 20%; các mức lớn hơn cần tình huống rõ. Không làm mọi đơn đều giảm giá. Chương trình tương lai, hết hạn hoặc đã tắt không được áp dụng.

**Chưa có mua 2 tặng 1**: không sinh hàng tặng, loại khuyến mãi hoặc trường gift_quantity như thể đã được hỗ trợ. Không cố nhét hàng tặng bằng cách đổi tổng số lượng và đặt giá sai.

Snapshot phải khớp chính sách có hiệu lực tại thời điểm bán. Dữ liệu cũ không được thay giá vì catalog/chương trình hiện tại đã đổi. Đọc cách Hibernate lưu snapshot, không đoán ngày trong JSON luôn là chuỗi ISO: dữ liệu hiện có có thể chứa mảng thành phần ngày.

Khi gọi API tạo đơn, bắt buộc gọi `POST /api/orders/quote`, lấy `quoteToken`, rồi gửi token và dòng hàng tới `POST /api/orders` bằng tài khoản STAFF. Giá thay đổi thì lấy quote mới và xử lý theo hợp đồng; không tự gửi lại một giao dịch đã có kết quả chưa rõ. Chuyển khoản cần paymentReference duy nhất. Không nhầm quoteToken là khóa chống trùng giao dịch.

## 7. Trả hàng và hoàn tiền

- Tạo phiếu trả từ hóa đơn đã bán và dòng hàng thực có. Tổng lượng trả không vượt lượng mua; nhiều lần trả phải cộng dồn đúng.
- Khi cần, tham chiếu đúng lô đã xuất; không trả vào một lô bất kỳ cùng SKU.
- Có tình huống trả một phần, trả toàn bộ; một phần phiếu đã hoàn, một phần còn chờ. Không sinh tỷ lệ cực đoan chỉ để có nhiều trạng thái.
- Hàng bán lại được nhập lại lô đúng quy tắc. Hàng hỏng không tăng tồn khả dụng; ghi đúng trạng thái và lượng hỏng qua nghiệp vụ hiện tại.
- Giá hoàn của dòng mới dựa trên giá net đã lưu, không phải giá catalog hoặc giá sau khuyến mãi hôm nay. Dòng lịch sử net NULL giữ công thức ReturnPolicy cũ.
- Không thay tổng tiền hóa đơn gốc khi khách trả; ghi riêng phiếu trả và khoản hoàn.
- Phiếu REFUND_PENDING chưa phải tiền đã chi. Chỉ sau xác nhận mới có payment_records REFUND và sự kiện/JSON tương ứng.
- Mỗi phiếu trả chỉ có tối đa một khoản hoàn được xác nhận. Tổng hoàn và nghĩa vụ hoàn không vượt ngân sách thanh toán của đơn theo service.

API hiện có cần đối chiếu controller trước khi gọi:

```text
PUT /api/orders/{id}/return
body: {requestKey, items:[{orderItemId, batchId?, quantity, restock, reason}]}

PUT /api/orders/returns/{returnId}/refund
body: {reference, amount}
```

## 8. Liên kết MySQL – Cassandra – JSON

Không sinh ba bộ dữ liệu độc lập. Nghiệp vụ MySQL và outbox phải cùng transaction; dispatcher chuyển sự kiện sang Cassandra và đích POS_LOG theo code.

- Giữ order_id, payment_record_id, SKU và mã sự kiện tương ứng. Không tạo ID mới không liên quan ở phía phân tích.
- Reuse event_id từ payload nguồn hoặc đúng thuật toán của DataSyncPublisher; không thay bằng UUID ngẫu nhiên mỗi lần chạy lại.
- Mã sự kiện giống nhau nhưng ngày/thời điểm khác có thể thành khóa Cassandra khác. Không đổi event_time mà bỏ lại khóa cũ.
- JSON thu/hoàn ở `analytics/pos-payments/<ngày UTC>/<eventId>.json`; do writer của project tạo với fingerprint và xử lý retry. Không append log trùng hoặc đưa JSON xuất Cassandra cũ vào giả làm log thanh toán mới.
- Nhật ký này là dẫn xuất từ ứng dụng, không phải bằng chứng độc lập của ngân hàng. Không tạo PAYMENT_FAILED/terminal_id/latency khi source chưa ghi nhận.
- Không cộng amount của tất cả loại sự kiện. Dòng tiền chỉ dùng đúng PAYMENT_RECEIVED và REFUND_CONFIRMED; MySQL dùng đúng các khoản PAYMENT/REFUND đã ghi nhận.
- Sau nạp, chờ/dispatch outbox có kiểm soát, đọc lỗi và số chờ. Hàng đợi 0 không tự chứng minh mọi đối soát đều đúng.
- Không sửa số tiền Cassandra/JSON hoặc câu SQL chỉ để ra PASS. Sai lệch phải truy nguyên về giao dịch, event, timestamp hoặc bộ lọc.

## 9. Dữ liệu nhiều ngày: không được làm tắt

API hiện tại tạo hóa đơn ở thời điểm chạy, không cung cấp một trường createdAt tùy ý cho client. Muốn có lịch sử 30 ngày phải xử lý rõ giới hạn này.

Ưu tiên dữ liệu hiện tại đi qua API thật. Với phần lịch sử bổ sung, xây dựng một timeline xác định gồm nhận hàng → bán → trả → hoàn. Dùng importer dành riêng cho dữ liệu học tập, chạy trên bản sao/namespace chuẩn bị trước; tái sử dụng quy tắc FEFO, giá theo thời điểm, ReturnPolicy và hợp đồng outbox. Nếu cần bổ sung helper nhập lịch sử, tách khỏi API POS bình thường, không mở endpoint cho thu ngân tùy ý lùi ngày.

Không chỉ backdate orders rồi quên order_items, payment_records, stock_movements, outbox, các khóa/sự kiện Cassandra, JSON và khuyến mãi tại thời điểm gốc. Không đổi clock máy hoặc clock production để tạo lịch sử. Không sửa thời gian giao dịch người dùng đã thao tác.

Nếu chưa thực hiện được importer lịch sử an toàn và nhất quán, hoàn thành bộ nhỏ ngày hiện tại, báo rõ phần lịch sử còn thiếu. Không nhận đã có dữ liệu 30 ngày khi chỉ sửa ngày hiển thị. Không thay nghiệp vụ ứng dụng để “hợp thức hóa” dữ liệu sai.

## 10. Đáp ứng báo cáo đang dùng

Các báo cáo hoạt động hiện tại là SALES, EXPIRY, REPLENISHMENT và PROMOTIONS. ABC, CHANNELS và QUALITY đã bị bỏ khỏi nhóm báo cáo này; không dựng lại để lấp danh sách yêu cầu cũ. Vẫn giữ dòng tiền, đối soát, lịch sử kho, giá nhập theo lô.

| Báo cáo | Bộ dữ liệu cần thể hiện |
|---|---|
| Bán chạy | Có SKU dẫn đầu, SKU chậm, lượng bán ròng tính sau trả; không đồng hạng gần hết danh sách |
| Sắp hết hạn | Một số lô ACTIVE còn hàng gần hạn 7/14/30 ngày; lô hết hạn được xử lý đúng điều kiện |
| Gợi ý nhập | Có mặt hàng đủ hàng, thấp tồn và chưa có nhu cầu; không làm tất cả đều cần nhập |
| Thống kê khuyến mãi | Có chương trình thực sự áp dụng, số đơn, lượng mua/trả, tiền giảm và tiền bán hàng; không lấy chương trình chưa dùng làm giao dịch |
| Giá nhập theo lô | Nhiều lần nhập cùng SKU với giá hợp lý có tăng/giảm/giữ nguyên; ngày nhập và nhà cung cấp hợp lệ |
| Dòng tiền | Có tiền mặt/chuyển khoản, thu, hoàn đã xác nhận và phiếu còn chờ hoàn |
| Đối soát | Có thể đối chiếu số khoản, số tiền, tổng đơn/dòng và truy vết từng ID giữa các nguồn |
| Lịch sử kho | Chỉ dựng snapshot từ lịch sử đủ, chốt ngày đã kết thúc, khớp sổ kho cùng thời điểm |

Thống kê khuyến mãi hiện nhóm theo chương trình/tên/mức giảm đã lưu trên dòng bán và sản phẩm. Tổng tiền giảm là phần giảm ban đầu trên số lượng gốc; tiền bán hàng dùng giá net nhân lượng sau trả. Số đơn trong từng dòng là DISTINCT theo dòng báo cáo; không cộng các dòng thành số đơn duy nhất toàn bộ nếu một đơn dùng nhiều chương trình.

## 11. Snapshot kho

Đọc schema `stock_daily_by_day` thật. Hiện có các trường snapshot_date, product_id, remaining_quantity, opening, received, sold, returned, adjustment_delta, closing, generated_at. Không thay thành tên trường khác chỉ vì ví dụ tài liệu.

Snapshot lưu lượng còn lại vật lý theo sản phẩm, bao gồm hàng hết hạn/ngừng bán còn trong lô; không gọi đó là tồn khả dụng. Hàng nhận trả hỏng không được cộng vào lượng bán được. Công thức và dấu của biến động phải theo StockSnapshotReader/InventoryBatchService.

Chỉ tạo qua tác vụ đã có khi lịch sử đủ, kỳ đã kết thúc và đối chiếu được. Nếu bị REJECTED phải đọc lý do, bổ sung dữ liệu nguồn thiếu một cách đúng nghĩa; không sửa status hoặc chèn closing bằng số hiện tại cho mọi ngày. Sản phẩm chưa từng nhập và tồn 0 có thể bị bỏ qua có giải thích, không buộc tạo lịch sử giả.

## 12. An toàn và chạy lại

1. Khảo sát live trước: tên database/keyspace, bảng/cột, số lượng và khoảng ngày hiện có. Lưu baseline không chứa bí mật.
2. Bảo toàn dữ liệu và giao dịch người dùng đã tạo. Việc cho phép xóa trong hội thoại cũ không phải giấy phép reset mọi nguồn ở lượt này. Nếu thay toàn bộ bộ dữ liệu, chuẩn bị bản sao lưu/namespace mới và xác định phạm vi rõ trước khi chuyển.
3. Script có chế độ plan/dry-run và apply, seed cố định, manifest, journal, checkpoint và khóa chống chạy hai bản đồng thời.
4. Tổng mục tiêu là tổng sau nạp. Chạy lại tiếp tục phần thiếu, không nhân đôi dữ liệu hoặc sửa giá/tồn cũ mỗi lần.
5. Ghi trạng thái trước mutation; nếu timeout khiến kết quả không rõ, đối chiếu hóa đơn và tham chiếu trước khi retry. Không xóa journal để né kiểm tra.
6. Nạp tuần tự theo lô nhỏ, có transaction và kiểm tra ràng buộc. Không tắt khóa ngoại/validation để nạp cho xong.
7. Không tăng RAM, chạy benchmark lớn hoặc bật toàn bộ dịch vụ không cần thiết. Máy hiện giới hạn Drill 3 GiB, Cassandra 2 GiB; vẫn cần RAM cho MySQL/backend/frontend/Windows.
8. Không commit `.env`, token, URI có password, password_hash của tài khoản thực hoặc backup chứa thông tin nhạy cảm. Không `git add .`, không revert thay đổi ngoài phạm vi.

## 13. Điều kiện đối chiếu bắt buộc

Đây là yêu cầu đối chiếu dữ liệu bằng SQL/API sau khi nạp, không yêu cầu tự tạo/chạy bộ unit/integration test. Không tuyên bố kiểm thử toàn diện.

- Khóa ngoại và SKU/ID hợp lệ; không dòng hàng mồ côi.
- Giá, đơn vị, số lượng, thời gian và tiền đều đúng hợp đồng hiện tại.
- Không có lượng bán/nhận trả vượt hàng có thể bán/đã mua.
- Giá gốc đơn = tổng giá gốc dòng; giảm giá = gốc − net; tiền đơn = tổng net dòng, trước khi xét khoản hoàn riêng.
- Tiền thu ròng = tiền đã thu − tiền hoàn đã xác nhận. Phiếu chờ hoàn chỉ thể hiện nghĩa vụ chưa chi.
- Số tồn tổng hợp, số dư lô, phân bổ, hàng hỏng và stock_movements khớp theo đúng đại lượng.
- Sự kiện thu/hoàn Cassandra và JSON khớp payment_records cả ID, loại, số tiền và thời điểm; không đếm lại sự kiện retry.
- Các ngày có dữ liệu được đối soát trên cùng ranh giới giờ Việt Nam. Ngày thiếu dữ liệu phải thể hiện thiếu, không tự nhận PASS vì hai phía cùng 0.
- Đọc báo cáo trên UI/API và lưu SQL + query ID thật của Drill. Không chỉ đếm INSERT thành công.
- Nếu gặp lỗi SQL, mở Profile, tìm nguyên nhân trước; không đổi dữ liệu hợp lệ để né lỗi truy vấn.

Lưu ý kỹ thuật đã gặp: Drill/Calcite đẩy literal tiếng Việt dạng `u&'...'` xuống MySQL có thể gây Unknown column 'u'. Adapter Cassandra hiện trả DATE dạng epoch-day INT, TIMESTAMP dạng epoch-ms BIGINT; đọc cách chuyển đã sửa trong service, không dùng lại bộ lọc DATE sai hoặc quên múi giờ.

## 14. Các file bàn giao phải có

Tạo thư mục mới theo quy ước repo cho bộ dữ liệu, không ghi đè seed/benchmark cũ như thể chúng cùng một bộ:

1. `README.md`: nguồn gốc, quy mô, thứ tự nạp, tham số, chạy lại, cách khôi phục, điều kiện lịch sử và tài khoản cần dùng.
2. Script sinh/nạp có plan/apply, seed cố định, journal và manifest; ưu tiên sửa/tái sử dụng công cụ hiện có khi phù hợp.
3. File schema/DDL cần thiết nếu có, tách khỏi file dữ liệu. Không tự thêm bảng ngoài mô hình chỉ vì bộ sinh thuận tiện.
4. File dữ liệu MySQL có hướng dẫn rõ database mới hay có sẵn; CQL và JSON xuất từ đúng nguồn MySQL/outbox. Không tái tạo độc lập các con số ở mỗi file.
5. Manifest: ngày đầu/cuối, ngày có giao dịch, số từng bảng, số loại event, số log, tổng thu/hoàn/giảm, lượng kho và checksum của các file xuất.
6. SQL đối chiếu, kết quả thực tế và query ID cho mỗi báo cáo đã chạy; phần chưa chạy ghi rõ lý do.
7. Hướng dẫn xem trên giao diện: chọn khoảng ngày nào, dự kiến thấy mặt hàng nào, phiếu nào còn chờ hoàn và đối chiếu số nào.

Nếu cung cấp cả SQL/CQL/JSON nạp trực tiếp và outbox replay, phải quy định rõ phương thức nào được dùng để tránh nạp hai lần. Giữ khóa sự kiện và trạng thái outbox phù hợp với bản xuất đã chuẩn bị. Không gọi file JSON hay CQL là “file SQL”.

## 15. Cách bắt đầu và kết thúc công việc

Hãy bắt đầu bằng việc đọc code, báo ngắn hiện trạng và ghi kế hoạch chạy vào file. Sau đó thực hiện các bước đã được yêu cầu, ưu tiên dữ liệu ít nhưng đúng rồi tăng dần. Chỉ hỏi khi thiếu quyết định quan trọng về thay bộ dữ liệu, quyền truy cập hoặc khi phải thay nghiệp vụ; không hỏi lại từng thao tác đọc/script thông thường.

Kết thúc bằng báo cáo ngắn gồm: đã nạp bao nhiêu sản phẩm/lô/đơn/phiếu trả/khoản thu-hoàn; dữ liệu trải bao nhiêu ngày; các nguồn có khớp không; SQL/query ID minh chứng; phần chưa làm hoặc chưa xác minh; cách người dùng mở giao diện để kiểm tra. **Không báo hoàn thành toàn bộ nếu còn lỗi hoặc thiếu lịch sử, và không tự tạo dữ liệu bất hợp lý để làm đầy biểu đồ.**
