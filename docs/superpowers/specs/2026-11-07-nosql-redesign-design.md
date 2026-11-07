# Thiết kế lại hệ thống siêu thị cho đồ án NoSQL

Ngày: 2026-11-07 · Nhánh: `nosql-migration` · Mốc trước khi chuyển: commit `267e192`

## 1. Mục tiêu và phạm vi

**Chủ dự án đã nói**

- Môn học đổi từ BigData sang NoSQL; bắt buộc dùng 4 CSDL: MongoDB, Cassandra, Neo4j, Redis.
- Bỏ hết Apache Drill và Docker; dùng thẳng dịch vụ cloud.
- Mỗi CSDL phải gánh một nghiệp vụ thật, không chỉ là bản sao.
- Giữ đơn giản, nhưng không chỉ có bán tại quầy: làm đủ vòng nhập hàng → bán → trả → chốt ca → thu hồi lô.
- Có khuyến mãi nhiều loại, không chỉ giảm %: thêm mua X tặng Y, giảm tiền, giảm theo tổng đơn.
- 3 tác nhân. Code cũ không dùng thì xóa.

**Giả định (sửa nếu sai)**

- Dịch vụ cloud: MongoDB Atlas (M0), DataStax Astra DB (Cassandra, đang dùng), Neo4j AuraDB Free, Redis Cloud hoặc Upstash.
- Redis bản free có thể không lưu bền, nên Redis chỉ giữ dữ liệu tạm.
- Một cửa hàng, một múi giờ `Asia/Ho_Chi_Minh`, tiền VND không có số lẻ.

**Tiêu chí hoàn thành**

- Demo được vòng nghiệp vụ đầy đủ bằng 3 tài khoản (ADMIN, WAREHOUSE, STAFF).
- Mỗi CSDL có ít nhất một chức năng trên giao diện mà nếu tắt CSDL đó thì chức năng biến mất.
- Không bán quá tồn khi bán đồng thời; bấm lặp không tạo đơn hay hoàn tiền hai lần.
- Không còn MySQL, JPA, Drill, Docker trong repo.

**Ngoài phạm vi**: bán online, tích điểm, cổng thanh toán, dashboard phân tích kiểu BigData (Drill, Parquet), giới hạn số suất khuyến mãi, nhiều chi nhánh, thông báo đẩy realtime, sao lưu bằng giao diện (sao lưu làm bằng script). Trang thống kê đơn giản bằng aggregation MongoDB vẫn nằm trong phạm vi (mục 19, FR-15), vì mẫu chấm điểm yêu cầu "chức năng hỗ trợ: thống kê, biểu đồ, xuất file".

## 2. Tác nhân

| Vai trò | Ai | Việc chính |
|---|---|---|
| `ADMIN` | Chủ cửa hàng kiêm quản lý | Người dùng, nhóm hàng, sản phẩm, nhà cung cấp, khuyến mãi, thu hồi lô, xem chốt ca mọi thu ngân, top bán chạy, dựng lại Neo4j |
| `WAREHOUSE` | Thủ kho | Phiếu nhập hàng, nhận lô, điều chỉnh giảm tồn, thẻ kho, cảnh báo sắp hết hàng và lô hết hạn |
| `STAFF` | Thu ngân | Bán tại quầy, đơn treo, trả hàng kèm hoàn tiền, chốt ca của mình, khách hàng |

`MANAGER` bị bỏ khỏi code và SRS; quyền của MANAGER gộp vào ADMIN. Vai trò là enum trong code, không cần collection riêng.

## 3. Kiến trúc

```
React (Vite)  ──REST/JSON + JWT──►  Spring Boot 4.1 (Java 21)
                                        │
        ┌───────────────┬───────────────┼────────────────┬────────────────┐
        ▼               ▼               ▼                ▼
   MongoDB Atlas    Astra DB        Neo4j Aura        Redis Cloud
   (nguồn sự thật)  (sổ cái)        (quan hệ)         (tạm, nhanh)
        │
        └─ sync_queue ──(job mỗi 60s + gọi ngay sau commit)──► Cassandra, Neo4j
```

Nguyên tắc:

1. **MongoDB là nguồn sự thật duy nhất** cho tiền, tồn kho và trạng thái đơn. Mọi quyết định nghiệp vụ đọc từ MongoDB.
2. Mỗi thao tác ghi nghiệp vụ là **một transaction MongoDB**, trong đó có luôn một bản ghi `sync_queue` mô tả những gì cần ghi sang Cassandra và Neo4j.
3. Cassandra và Neo4j được ghi **sau** bằng khóa xác định, nên ghi lại bao nhiêu lần cũng không trùng.
4. Redis chỉ giữ dữ liệu tạm. Redis lỗi thì hệ thống vẫn bán được.

Thư viện:

- MongoDB: `spring-boot-starter-data-mongodb`, `MongoTemplate`, `MongoTransactionManager`. Tiền lưu `Decimal128`.
- Cassandra: DataStax `java-driver-core` 4.17 (đang có), viết CQL trực tiếp.
- Neo4j: `neo4j-java-driver`, viết Cypher trực tiếp, không dùng OGM.
- Redis: `spring-boot-starter-data-redis`, `StringRedisTemplate`.
- Bỏ: `spring-boot-starter-data-jpa`, `mysql-connector-j`, `h2`.

Biến môi trường (`.env`, không commit):

| Biến | Ý nghĩa |
|---|---|
| `MONGODB_URI`, `MONGODB_DATABASE` | Chuỗi kết nối Atlas, tên database (`supermarket`, `supermarket_dev`) |
| `CASSANDRA_MODE` | `ASTRA` hoặc `LOCAL` |
| `ASTRA_SECURE_BUNDLE`, `APPLICATION_TOKEN`, `CASSANDRA_KEYSPACE` | Kết nối Astra |
| `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` | Kết nối Aura |
| `REDIS_URL` | `rediss://...` |
| `JWT_SECRET`, `JWT_ACCESS_MINUTES` (15), `JWT_REFRESH_DAYS` (7) | Xác thực |
| `APP_TIMEZONE` (`Asia/Ho_Chi_Minh`), `RETURN_WINDOW_DAYS` (7) | Nghiệp vụ |
| `APP_SEED_DEMO` | `true` thì tạo dữ liệu mẫu lúc khởi động nếu database trống |

File `secure-connect-*.zip` chuyển vào thư mục `secrets/` (đã nằm trong `.gitignore`).

## 4. Danh sách chức năng và CSDL dùng

Rút gọn từ 24 xuống 15 FR (07/11/2026): gộp các FR nhỏ theo use case, bỏ thông báo realtime và sao lưu bằng giao diện (sao lưu làm bằng script trong `nosql-scripts/`), phiếu nhập chỉ còn 2 trạng thái.

| Mã | Chức năng | Tác nhân | Mongo | Cassandra | Neo4j | Redis |
|---|---|---|:-:|:-:|:-:|:-:|
| FR-01 | Tài khoản và phiên: đăng nhập, làm mới, đăng xuất, hồ sơ, đổi mật khẩu, lịch sử đăng nhập 90 ngày | Tất cả | ● | ● TTL | | ● refresh token |
| FR-02 | Quản lý người dùng | ADMIN | ● | | | |
| FR-03 | Quản lý khách hàng | ADMIN, STAFF | ● | | ● | |
| FR-04 | Danh mục hàng hóa: sản phẩm (thuộc tính linh hoạt), nhóm hàng, nhà cung cấp, tra cứu, cảnh báo sắp hết hàng và lô hết hạn | Tất cả xem; ADMIN, WAREHOUSE ghi | ● | | ● | ● cache |
| FR-05 | Nhập hàng (phiếu DRAFT → RECEIVED, lô lẻ) và điều chỉnh giảm tồn | ADMIN, WAREHOUSE | ● | ● | ● | |
| FR-06 | Thẻ kho (lịch sử biến động) | Tất cả | | ● | | |
| FR-07 | Bán tại quầy (khuyến mãi, cấp phát FEFO) | ADMIN, STAFF | ● | ● | ● | ● top bán |
| FR-08 | Đơn treo tại quầy | STAFF | | | | ● |
| FR-09 | Trả hàng kèm hoàn tiền (1 bước) | ADMIN, STAFF | ● | ● | | |
| FR-10 | Tra cứu đơn, phiếu trả, thanh toán | ADMIN, STAFF | ● | ● | | |
| FR-11 | Khuyến mãi 4 loại | ADMIN | ● | | | ● cache |
| FR-12 | Chốt ca thu ngân | STAFF (của mình), ADMIN (tất cả) | ● | ● | | |
| FR-13 | Truy vết và thu hồi lô | ADMIN | | | ● | |
| FR-14 | Gợi ý mua kèm | STAFF (quầy), ADMIN (tạo KM) | | | ● | |
| FR-15 | Thống kê bán hàng, biểu đồ, xuất CSV, top bán chạy hôm nay | ADMIN (STAFF xem top bán) | ● aggregation | | | ● sorted set |

Bảng đổi mã từ bản 24 FR: 01, 02, 24 → FR-01 · 03 → FR-02 · 04 → FR-03 · 05, 06, 07 → FR-04 · 08, 10 → FR-05 · 11 → FR-06 · 09, 12 → FR-07 · 16 → FR-08 · 13 → FR-09 · 14 → FR-10 · 15 → FR-11 · 17 → FR-12 · 18 → FR-13 · 19 → FR-14 · 20, 21 → FR-15 · 22, 23 bỏ.

## 5. MongoDB

### 5.1 Collections

Định danh `_id` là `ObjectId`, trả về frontend dạng chuỗi. Mã hiển thị (`orderCode`, `batchCode`, `poCode`, `returnCode`) sinh bằng collection `counters` với `$inc` nguyên tử. Không dùng Redis để đếm, vì Redis có thể mất dữ liệu.

| Collection | Nội dung chính | Index |
|---|---|---|
| `users` | `username, passwordHash, fullName, email, phone, role, active, createdAt` | unique `username`; unique một phần `email`, `phone` |
| `customers` | `fullName, phone, email, emailLower, address, active, createdAt` | unique `phone`; unique một phần `emailLower` |
| `categories` | `name, nameLower, description, active` | unique `nameLower` |
| `suppliers` | `name, phone, email, address, active` | unique một phần `phone`, `email` |
| `products` | `sku, name, price, categoryId, categoryName, supplierId, reorderPoint, imageUrl, active, attributes{}, stockQuantity, damagedQuantity, version` | unique `sku`; text `name, sku`; `categoryId` |
| `batches` | `batchCode, productId, supplierId, purchaseOrderId, receivedQty, remainingQty, damagedQty, unitCost, mfgDate, expiryDate, expirySortKey, receivedAt, receivedBy` | unique `batchCode`; `{productId, expirySortKey, receivedAt, batchCode}` |
| `purchase_orders` | `poCode, supplierId, status, lines[{productId, orderedQty, unitCost, receivedQty, batchId}], createdBy, createdAt, receivedAt` | unique `poCode`; `status` |
| `orders` | Xem 5.2 | unique `orderCode`, `requestKey`; `{orderedAt:-1}`; `{cashierId, orderedAt}` |
| `returns` | `returnCode, orderId, requestKey, items[], refundAmount, refundMethod, refundReference, createdBy, createdAt` | unique `requestKey`; `orderId` |
| `promotions` | Xem mục 8 | `{active, startsAt, endsAt}` |
| `shift_closings` | `cashierId, day, expectedCash, expectedBank, refunds, countedCash, difference, note, closedAt` | unique `{cashierId, day}` |
| `counters` | `_id` (ví dụ `order:20261107`), `seq` | — |
| `sync_queue` | `type, refId, payload, status, attempts, lastError, createdAt, doneAt` | `{status, createdAt}` |

`products.attributes` là map tự do cho thông tin riêng từng ngành hàng, ví dụ `{"dungTich": "1L", "baoHanhThang": 12}`. `products.stockQuantity` là số tổng hợp cập nhật trong cùng transaction với lô, dùng để hiển thị nhanh. `expirySortKey` là `expiryDate`, hoặc `9999-12-31` khi lô không có hạn, để sắp FEFO đúng thứ tự (MongoDB xếp `null` lên đầu).

### 5.2 Document đơn hàng

```json
{
  "_id": "...", "orderCode": "HD20261107-0042", "requestKey": "uuid-từ-client",
  "cashier": {"userId": "...", "name": "Thu ngân A"},
  "customer": {"customerId": "...", "name": "...", "phone": "..."},
  "orderedAt": "2026-11-07T10:15:00+07:00",
  "status": "PAID",
  "paymentMethod": "CASH", "transactionRef": null,
  "grossTotal": 120000, "discountTotal": 20000, "total": 100000,
  "orderPromotion": {"promotionId": "...", "name": "Đơn từ 100k giảm 10k", "type": "ORDER_THRESHOLD", "discount": 10000},
  "items": [{
    "lineNo": 1, "productId": "...", "sku": "SUA01", "name": "Sữa tươi 1L",
    "quantity": 3, "unitPrice": 30000, "grossAmount": 90000, "netAmount": 60000,
    "isGift": false,
    "promotion": {"promotionId": "...", "name": "Mua 2 tặng 1", "type": "BUY_X_GET_Y", "freeUnits": 1},
    "returnedQuantity": 0,
    "allocations": [{"batchId": "...", "batchCode": "L261101-0003", "expiryDate": "2026-11-20", "quantity": 3, "returnedQuantity": 0}]
  }],
  "payments": [{"type": "PAYMENT", "method": "CASH", "amount": 100000, "reference": "POS-HD20261107-0042", "returnId": null, "at": "...", "by": "..."}]
}
```

`netAmount` của dòng là số tiền khách thực trả cho dòng đó, sau khi đã chia khuyến mãi dòng và khuyến mãi đơn. Tổng `netAmount` của các dòng đúng bằng `total`. Thanh toán được nhúng trong đơn, nên kiểm tra "hoàn không vượt số đã thu" chỉ cần đọc một document.

### 5.3 Transaction và chống bán quá tồn

- Mọi thao tác ghi dùng `@Transactional` với `MongoTransactionManager`. Gặp lỗi `TransientTransactionError` hoặc `WriteConflict` thì thử lại tối đa 3 lần.
- Cấp phát FEFO: đọc các lô hợp lệ đã sắp xếp, rồi trừ từng lô bằng `updateOne({_id, remainingQty: {$gte: take}}, {$inc: {remainingQty: -take}})`. Nếu không sửa được bản ghi nào thì hủy transaction và thử lại. Hết lần thử thì báo thiếu tồn.
- Sản phẩm được cập nhật theo thứ tự `productId` tăng dần để giảm xung đột giữa hai đơn.
- Chống gửi lặp: index unique trên `requestKey`. Gặp trùng thì so nội dung: giống thì trả đơn cũ, khác thì báo lỗi 409.
- Không dùng khóa Redis cho bán hàng. Transaction và cập nhật có điều kiện của MongoDB đã đủ, lại không phụ thuộc Redis.

## 6. Cassandra (keyspace `supermarket`)

```sql
CREATE TABLE stock_movements_by_product (
  product_id text, month text,               -- '2026-11'
  created_at timestamp, movement_id uuid,
  movement_type text, quantity int, batch_code text,
  ref_type text, ref_code text, note text, performed_by text,
  PRIMARY KEY ((product_id, month), created_at, movement_id)
) WITH CLUSTERING ORDER BY (created_at DESC, movement_id ASC);

CREATE TABLE payments_by_order (
  order_id text, created_at timestamp, payment_id uuid,
  type text, method text, amount decimal, reference text, return_code text, cashier_id text,
  PRIMARY KEY ((order_id), created_at, payment_id)
);

CREATE TABLE payments_by_cashier_day (
  cashier_id text, day date, created_at timestamp, payment_id uuid,
  order_code text, type text, method text, amount decimal,
  PRIMARY KEY ((cashier_id, day), created_at, payment_id)
);
```

- Mỗi bảng phục vụ đúng một câu truy vấn: thẻ kho theo sản phẩm và tháng, thanh toán theo đơn, thanh toán theo thu ngân và ngày.
- `movement_id`, `payment_id` là UUID tạo từ nội dung, ví dụ `UUID.nameUUIDFromBytes("SALE|orderId|lineNo|batchId")`. Ghi lại cùng dữ liệu chỉ ghi đè đúng dòng cũ, nên không cần LWT.
- Thẻ kho mặc định xem tháng hiện tại; frontend có ô chọn tháng.
- Loại biến động: `RECEIPT`, `SALE`, `RETURN`, `RETURN_DAMAGED`, `ADJUSTMENT`. Quy ước dấu số lượng giữ như SRS FR-06.

## 7. Neo4j

Mô hình:

```
(:Customer {id, name, phone})-[:PLACED]->(:Order {id, code, at})
(:Order)-[:CONTAINS {qty}]->(:Product {id, sku, name})
(:Order)-[:TOOK_FROM {qty}]->(:Batch {id, code, expiry})
(:Batch)-[:OF_PRODUCT]->(:Product)
(:Batch)-[:SUPPLIED_BY]->(:Supplier {id, name})
```

- Ghi bằng `MERGE` theo `id`, nên ghi lặp an toàn. Khách lẻ không có node Customer.
- **Truy vết và thu hồi lô (FR-13)**:
  `MATCH (b:Batch {code:$code})<-[t:TOOK_FROM]-(o:Order) OPTIONAL MATCH (c:Customer)-[:PLACED]->(o) RETURN o.code, o.at, t.qty, c.name, c.phone ORDER BY o.at`
  Màn hình hiện danh sách khách cần liên hệ (có số điện thoại) và tổng số lượng đã bán ra.
- **Gợi ý mua kèm (FR-14)**:
  `MATCH (p:Product {id:$id})<-[:CONTAINS]-(o:Order)-[:CONTAINS]->(x:Product) WHERE x <> p RETURN x.id, x.name, count(o) AS times ORDER BY times DESC LIMIT 5`
  Ở quầy, khi thêm sản phẩm vào giỏ thì hiện 5 gợi ý. Ở trang khuyến mãi, ADMIN xem gợi ý để chọn cặp tạo khuyến mãi mua X tặng Y.
- **Dựng lại**: `POST /api/admin/graph/rebuild` (ADMIN) xóa graph, đọc toàn bộ đơn, lô, khách, nhà cung cấp từ MongoDB rồi `MERGE` lại theo từng lô 500 bản ghi.
- Graph không quan tâm hàng trả. Truy vết là "đã bán ra cho ai", bao gồm cả đơn đã trả.

## 8. Redis

| Key | Kiểu | TTL | Dùng cho |
|---|---|---|---|
| `auth:refresh:{sha256(token)}` | string → userId | 7 ngày | FR-01, xoay vòng refresh token |
| `auth:user-tokens:{userId}` | set các hash | 7 ngày | Thu hồi hết token khi đổi mật khẩu hoặc khóa tài khoản |
| `cache:products:active` | string JSON | 5 phút | Danh sách sản phẩm ở quầy; xóa khi sản phẩm hoặc lô đổi |
| `cache:products:low-stock` | string JSON | 1 phút | Cảnh báo sắp hết hàng |
| `cache:promotions:active` | string JSON | 5 phút | Tính giá ở quầy; xóa khi sửa khuyến mãi |
| `pos:held:{cashierId}:{holdId}` | string JSON giỏ | 30 phút | FR-08 đơn treo |
| `pos:held-index:{cashierId}` | sorted set holdId theo thời gian | 30 phút | Liệt kê đơn treo |
| `top:sales:{yyyyMMdd}` | sorted set productId → số lượng | 3 ngày | FR-15; `ZINCRBY` khi bán (không tính hàng tặng), `ZREVRANGE 0 9` |

Khi Redis lỗi:

- Cache: đọc thẳng MongoDB.
- Làm mới phiên: trả 401, người dùng đăng nhập lại.
- Đơn treo, top bán: báo "tạm thời không dùng được", không chặn việc bán.

Mọi lệnh Redis đi qua một lớp `RedisSafe` có `try/catch` và log.

## 9. Đồng bộ sang Cassandra và Neo4j

- Trong cùng transaction nghiệp vụ, ghi một document `sync_queue`, ví dụ `{type: "ORDER_CREATED", refId, payload: {movements[], payments[], graph{...}}, status: "PENDING"}`. Payload chứa sẵn dữ liệu cần ghi, nên job không phải tính lại.
- Sau khi commit, `SyncService.process(id)` chạy ngay (bất đồng bộ). Ghi Cassandra và Neo4j xong thì đặt `DONE`; lỗi thì tăng `attempts`, lưu `lastError`.
- Job `@Scheduled` mỗi 60 giây xử lý các bản ghi `PENDING` cũ hơn 30 giây, với `attempts < 10`. Đến lần thứ 10 vẫn lỗi thì đặt `FAILED` và chờ ADMIN bấm thử lại.
- Các loại sự kiện: `BATCH_RECEIVED`, `STOCK_ADJUSTED`, `ORDER_CREATED`, `RETURN_CREATED`, `MASTER_UPSERTED` (khách, sản phẩm, nhà cung cấp sang Neo4j).
- Chốt ca gọi xử lý các bản ghi `PENDING` của thu ngân đó trước khi tính tổng, để số liệu không thiếu.
- `GET /api/admin/sync/status` (ADMIN) trả số bản ghi PENDING/FAILED, kèm nút "Thử lại".

## 10. Khuyến mãi (FR-11)

### 10.1 Bốn loại, cùng một collection `promotions`

```json
{ "name": "...", "type": "PERCENT|FIXED|BUY_X_GET_Y|ORDER_THRESHOLD",
  "active": true, "startsAt": "...", "endsAt": "...",
  "scope": {"productIds": [], "categoryIds": []},
  "rule": { ... tùy loại ... } }
```

| `type` | `rule` | Ý nghĩa |
|---|---|---|
| `PERCENT` | `{percent: 20}` | Giảm 20% mỗi đơn vị trong phạm vi |
| `FIXED` | `{amount: 2000}` | Giảm 2.000đ mỗi đơn vị, không thấp hơn 0 |
| `BUY_X_GET_Y` | `{buyQty: 2, getQty: 1, giftProductId: null}` | `giftProductId = null`: tặng cùng loại; cứ đủ `buyQty + getQty` thì `getQty` cái 0đ. Có `giftProductId`: cứ mua `buyQty` thì tự thêm `getQty` quà khác loại |
| `ORDER_THRESHOLD` | `{minTotal: 500000, amount: 50000}` hoặc `{minTotal, percent, maxDiscount}` | Giảm trên tổng đơn sau khuyến mãi dòng |

`scope` chỉ dùng cho 3 loại đầu. `BUY_X_GET_Y` chỉ nhận `productIds`.

### 10.2 Cách tính (lớp `PromotionEngine`, Java thuần, không gọi CSDL)

1. Gộp giỏ theo sản phẩm.
2. Với mỗi dòng, tính số tiền giảm của từng khuyến mãi dòng đang hiệu lực, rồi chọn **đúng một** cái giảm nhiều nhất. Bằng nhau thì chọn cái `startsAt` mới hơn.
3. `BUY_X_GET_Y` khác loại: thêm dòng quà (`isGift = true`). Nếu quà không đủ tồn thì không áp khuyến mãi đó và trả cảnh báo cho quầy.
4. Tính tổng sau bước 2–3, chọn **một** khuyến mãi đơn có lợi nhất mà đạt `minTotal`.
5. Chia tiền:
   - Gói mua X tặng Y khác loại: tổng tiền gói chia cho dòng mua và dòng quà theo tỉ lệ giá niêm yết.
   - Khuyến mãi đơn: chia cho các dòng theo tỉ lệ tiền của từng dòng.
   - Làm tròn xuống đến đồng; phần dư cộng vào dòng có `lineNo` lớn nhất, để tổng luôn khớp.

Kết quả là `netAmount` của từng dòng (mục 5.2).

### 10.3 Tính giá trước và chốt giá

- `POST /api/orders/quote` trả giỏ đã tính giá cùng `quoteHash` (hash của giá và khuyến mãi). Giữ ý tưởng của code cũ.
- `POST /api/orders` gửi kèm `quoteHash`. Server tính lại; nếu khác thì trả 409 `PRICE_CHANGED`, quầy tính lại và báo thu ngân.

### 10.4 Trả hàng có khuyến mãi

- Tiền hoàn của một lần trả `k` đơn vị trên một dòng, khi đã trả trước đó `r` đơn vị:
  `floor(netAmount × (r + k) / quantity) − floor(netAmount × r / quantity)`.
  Công thức này dùng lại ý tưởng ranh giới cộng dồn trong `ReturnPolicy` cũ, nên trả hết luôn hoàn đúng bằng `netAmount`.
- Không truy thu khi trả bớt làm đơn không còn đạt `minTotal`.
- Dòng quà trả lại thì hoàn theo `netAmount` đã được chia cho nó.

## 11. Luồng nghiệp vụ chính

**Nhập hàng (FR-05)**: WAREHOUSE tạo phiếu `DRAFT` (nhà cung cấp, dòng hàng, giá vốn); còn `DRAFT` thì sửa hoặc xóa được. Khi hàng về, mở phiếu, nhập số thực nhận, ngày sản xuất, hạn dùng từng dòng → bấm "Nhận hàng". Một transaction thực hiện: tạo các lô, cộng `stockQuantity`, đặt phiếu `RECEIVED`, ghi `sync_queue` (`RECEIPT` sang Cassandra, Batch→Supplier sang Neo4j). Vẫn giữ nhập lô lẻ không cần phiếu cho hàng phát sinh. Điều chỉnh giảm tồn theo lô cũng thuộc FR-05.

**Bán (FR-07)**: quầy → `quote` → thu ngân chọn CASH hoặc BANK_TRANSFER (bắt buộc mã giao dịch) → `POST /api/orders`. Một transaction: kiểm tra `requestKey`, tính lại khuyến mãi, cấp phát FEFO, trừ tồn, tạo đơn có thanh toán nhúng, ghi `sync_queue`. Sau commit: `ZINCRBY` top bán, xóa cache sản phẩm.

**Đơn treo (FR-08)**: nút "Treo đơn" lưu giỏ vào Redis; danh sách "Đơn đang treo" cho phép mở lại hoặc xóa. Mở lại thì giỏ quay về quầy và xóa key. Đơn treo không giữ tồn kho.

**Trả hàng (FR-09)**: một bước, gồm lập phiếu và hoàn tiền. Bỏ endpoint hoàn tiền riêng `/returns/{id}/refund` của code cũ. Quy tắc như SRS FR-09: hạn 7 ngày, nhập lại đúng lô, lô hết hạn thì bắt buộc tính hàng hỏng, tiền hoàn theo mục 10.4.

**Chốt ca (FR-12)**: STAFF mở trang "Chốt ca" → hệ thống xử lý `sync_queue` còn chờ của mình → đọc `payments_by_cashier_day` hôm nay → hiện tiền mặt thu, chuyển khoản thu, tiền hoàn, tiền mặt phải có → thu ngân nhập tiền mặt đếm được và ghi chú → lưu `shift_closings` (mỗi thu ngân mỗi ngày một lần; ADMIN được mở lại). ADMIN xem danh sách chốt ca theo ngày và độ chênh lệch.

## 12. API

Giữ tiền tố `/api`. Thay đổi so với code cũ:

| Nhóm | Endpoint | Ghi chú |
|---|---|---|
| Auth | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET/PUT /auth/me`, `PUT /auth/password` | Thêm refresh/logout |
| Users | `GET/POST /users`, `GET/PUT/DELETE /users/{id}`, `GET /roles` | |
| Customers | `GET/POST /customers`, `GET/PUT/DELETE /customers/{id}` | Mới |
| Categories | `GET/POST /categories`, `PUT/DELETE /categories/{id}` | Mới |
| Suppliers | Giữ như cũ | |
| Products | Giữ như cũ, thêm `attributes`, `categoryId`, `supplierId` | |
| Stock card | `GET /products/{id}/stock-movements?month=2026-11` | Đọc Cassandra |
| Batches | `GET /inventory/batches`, `POST /inventory/batches`, `POST /inventory/batches/{id}/adjustments`, `GET /inventory/batches/expired` | |
| Purchase orders | `GET/POST /purchase-orders`, `PUT/DELETE /purchase-orders/{id}` (chỉ khi `DRAFT`), `POST /purchase-orders/{id}/receive` | Mới |
| Orders | `POST /orders/quote`, `POST /orders`, `GET /orders`, `GET /orders/{id}`, `GET /orders/code/{code}`, `POST /orders/{id}/returns`, `GET /orders/{id}/returns`, `GET /orders/{id}/payments` | Trả hàng 1 bước; payments đọc Cassandra |
| Held carts | `GET/POST /pos/held`, `GET/DELETE /pos/held/{holdId}` | Mới, Redis |
| Promotions | `GET/POST /promotions`, `PUT /promotions/{id}`, `PUT /promotions/{id}/active` | Mở rộng 4 loại |
| Shifts | `GET /shifts/me/today`, `POST /shifts/me/close`, `GET /shifts?day=` | Mới |
| Graph | `GET /batches/{code}/trace`, `GET /products/{id}/also-bought`, `POST /admin/graph/rebuild` | Mới, Neo4j |
| Top sales | `GET /stats/top-sales/today` | Mới, Redis |
| Ops | `GET /health/datastores`, `GET /admin/sync/status`, `POST /admin/sync/retry` | Trang trạng thái 4 CSDL, dùng khi demo |

Lỗi trả theo `ApiErrorResponse` hiện có; mã 400/401/403/404/409 như SRS.

## 13. Cấu trúc backend mới

Package `com.api.supermarket`, chia theo module nghiệp vụ, mỗi module có `*Controller`, `*Service`, document và DTO riêng:

```
config/        MongoConfig, CassandraConfig, Neo4jConfig, RedisConfig, SecurityConfig, OpenApiConfig, SchedulingConfig
common/        ApiErrorResponse, exceptions, PageResponse, PaginationValidator, CodeGenerator(counters), Clock/AppTime, RedisSafe
auth/          AuthController, AuthService, JwtService, RefreshTokenStore(Redis), filters
user/  customer/  catalog/(category, supplier, product)
inventory/     BatchService, FefoPolicy, PurchaseOrderService, StockCardReader(Cassandra)
sales/         OrderService, ReturnService, ReturnPolicy, QuoteService, HeldCartService(Redis), TopSalesService(Redis)
promotion/     PromotionService, PromotionEngine (thuần Java)
shift/         ShiftService
graph/         GraphWriter, GraphQueryService, GraphRebuildService (Neo4j)
sync/          SyncQueue document, SyncService, SyncJob, CassandraLedgerWriter
seed/          DemoDataSeeder (APP_SEED_DEMO)
```

`SalesOrderServiceImpl` hiện dài 657 dòng; khi viết lại tách thành `QuoteService`, `OrderService`, `ReturnService` để mỗi file một việc. Thư mục `service/Impl` và interface một-cài-đặt bị bỏ.

## 14. Frontend

| Trang | Tác nhân | Thay đổi |
|---|---|---|
| Đăng nhập | Tất cả | Thêm tự làm mới token |
| Bán tại quầy | STAFF, ADMIN | Khuyến mãi tự áp, dòng quà, đơn treo, gợi ý mua kèm, top bán chạy |
| Đơn hàng | STAFF, ADMIN | Trả hàng 1 bước, lịch sử thanh toán |
| Chốt ca | STAFF, ADMIN | Mới |
| Khách hàng | STAFF, ADMIN | Mới |
| Sản phẩm | Tất cả xem | Thuộc tính linh hoạt, tab "Thẻ kho" (gộp `StockHistoryPage`) |
| Nhóm hàng | ADMIN | Mới |
| Nhà cung cấp | ADMIN, WAREHOUSE | Giữ |
| Phiếu nhập hàng | ADMIN, WAREHOUSE | Mới |
| Tồn kho theo lô | ADMIN, WAREHOUSE | Thêm "Truy vết / Thu hồi" (ADMIN) |
| Khuyến mãi | ADMIN | Form theo 4 loại, khung gợi ý mua kèm |
| Người dùng | ADMIN | Bỏ MANAGER |
| Trạng thái hệ thống | ADMIN | Mới: tình trạng 4 CSDL, sync queue, nút dựng lại Neo4j |

ID chuyển từ `number` sang `string` trong `types/index.ts`. Trang chủ sau đăng nhập: ADMIN và STAFF vào Bán tại quầy, WAREHOUSE vào Tồn kho.

## 15. Xóa code và file cũ

| Đường dẫn | Lý do |
|---|---|
| `drill/`, `analytics/`, `docker-compose.yml`, `.dockerignore` | Drill, Docker |
| `SQL/`, `database/` (sau khi chuyển `secure-connect-*.zip` sang `secrets/`) | MySQL, seed và benchmark cũ |
| `.tmp/` (348 MB: Maven tạm, backup MySQL) | Rác của bản cũ |
| `docs/benchmark/`, `docs/word/`, `docs/ai/`, `docs/antigravity/`, `docs/01_lo_trinh_va_kien_truc/`, `docs/KE_HOACH_*.md`, `docs/NOSQL_BIGDATA_PROJECT_PLAN.txt`, `docs/promotions-contract.md`, `docs/superpowers/plans/2026-10-*` | Tài liệu Drill/BigData; vẫn còn trong commit `267e192` |
| `CONTEXT.md`, `HELP.md` | Mô tả bản cũ; nội dung cần thiết đưa vào README mới |
| Backend: toàn bộ `integration/`, `entity/`, `repository/`, `service/`, `controller/AnalyticsController`, `DataIntegrationController`, `StockSnapshotController`, `exception/AnalyticsUnavailableException`, `application-test.properties` (H2) | Viết lại theo mục 13 |
| Backend `pom.xml`: JPA, MySQL, H2, `spring-boot-starter-data-jpa-test` | Không còn dùng |
| Frontend: `DashboardPage`, `ReportsPage`, `CashFlowReportsPage`, `OperationalAnalyticsPage`, `PurchaseCostsPage`, `StockHistoryPage` (gộp vào Sản phẩm), `components/analytics/`, `components/chart/`, `components/shop/` | Trang phân tích và phần shop cũ |

Giữ lại và chuyển sang chỗ mới: `FefoPolicy`, `ReturnPolicy`, logic JWT, `GlobalExceptionHandler`, `PaginationValidator`, cấu hình kết nối Astra, `Bruno/` (cập nhật request), `img/`, SRS `.docx`.

## 16. Kiểm thử

Không có Docker nên không dùng Testcontainers.

- **Unit test (JUnit, chạy mọi lúc)**: `PromotionEngine` (đủ 4 loại, chọn khuyến mãi tốt nhất, chia tiền khớp tổng), `FefoPolicy`, công thức hoàn tiền, kiểm tra ngày lô, sinh mã. Đây là phần nhiều logic nhất nên phủ kỹ nhất.
- **Service test (Mockito)**: luồng bán, trả, nhận phiếu nhập với repository giả lập.
- **Test trên cloud (tag `cloud`, chạy tay với database `_dev`)**:
  - Hai luồng cùng mua đơn vị hàng cuối: đúng một luồng thành công, tồn không âm (NFR-03).
  - Gửi lặp `requestKey`: không tạo đơn thứ hai.
  - Tắt Redis (URL sai): vẫn bán được.
  - Gọi lại `SyncService` hai lần: Cassandra không có dòng trùng.
- **Kiểm tra tay bằng Bruno** cho các API mới; checklist demo theo 3 tài khoản.

## 17. Sửa SRS (bản v2.0)

- Mục 1.2 và 8.2: bỏ "khuyến mãi và giảm giá" khỏi *ngoài phạm vi*.
- Mục 2.3: còn 3 vai trò, bỏ MANAGER.
- Mục 2.5 và 2.6: thay "khóa dòng" bằng transaction MongoDB kèm cập nhật có điều kiện; thêm `sync_queue`; Redis không cần bật AOF.
- Mục 3: 15 FR theo bảng mục 4 (đã làm trong SRS v2.0).
- Mục 4: thêm thực thể Khuyến mãi, Phiếu nhập, Chốt ca, Hàng đợi đồng bộ; đổi Refresh token sang Redis; thanh toán nhúng trong đơn và sao sang Cassandra.
- Mục 5.3: thay bằng bảng phân vai mục 4 của tài liệu này.
- Mục 8: ma trận truy vết theo 15 FR.

## 18. Thứ tự triển khai

1. **Dọn dẹp**: xóa theo mục 15, đổi `pom.xml`, chuyển file bundle. Backend còn khung chạy được.
2. **Nền tảng**: cấu hình 4 CSDL, `/health/datastores`, `counters`, `RedisSafe`, `sync_queue` và job.
3. **Tài khoản**: người dùng, đăng nhập và refresh token qua Redis, hồ sơ.
4. **Danh mục**: nhóm hàng, nhà cung cấp, sản phẩm, khách hàng, cache.
5. **Kho**: lô, FEFO, điều chỉnh, phiếu nhập, thẻ kho trên Cassandra.
6. **Khuyến mãi**: `PromotionEngine` (TDD), API.
7. **Bán**: quote, đơn hàng, đơn treo, top bán.
8. **Trả hàng và chốt ca**.
9. **Neo4j**: ghi graph, truy vết và thu hồi, gợi ý mua kèm, dựng lại.
10. **Frontend** theo mục 14, chạy song song từ bước 4.
11. **Dữ liệu mẫu, README, SRS v2.0, checklist demo**.

## 19. Bổ sung theo mẫu chấm điểm của trường

Nguồn: `REFS_NoSQL_SRS&SDS_Template.docx` và `BaoCaoMinhChung_DoAn_DuLieuNoSQL.docx`. Các mục dưới đây là bắt buộc hoặc được chấm điểm, nên được thêm vào thiết kế.

### 19.1 Bảng mẫu truy cập dữ liệu (Access Patterns)

| Mã | Mẫu truy cập | Loại | Tham số | Tần suất | Độ trễ mục tiêu | Nhất quán | CSDL / cấu trúc |
|---|---|---|---|---|---|---|---|
| AP-01 | Tìm tài khoản để đăng nhập | Đọc | username | Cao | < 50 ms | Strong | Mongo `users` unique `username` |
| AP-02 | Lưu, kiểm tra, xoay vòng refresh token | Đọc/Ghi | tokenHash | Cao | < 10 ms | Strong (1 key) | Redis `auth:refresh:*` |
| AP-03 | Danh sách sản phẩm đang bán ở quầy | Đọc | — | Rất cao | < 50 ms | Eventual (5 phút) | Redis `cache:products:active` → Mongo `products` |
| AP-04 | Tìm sản phẩm theo tên hoặc SKU | Đọc | từ khóa | Rất cao | < 100 ms | Strong | Mongo text index, unique `sku` |
| AP-05 | Lọc sản phẩm theo nhóm, giá, sắp xếp, phân trang | Đọc | categoryId, khoảng giá, trang | Cao | < 100 ms | Strong | Mongo `{categoryId:1, price:1}` |
| AP-06 | Sản phẩm sắp hết hàng | Đọc | — | Trung bình | < 100 ms | Eventual (1 phút) | Redis cache → Mongo `$expr` |
| AP-07 | Lô còn hạn của sản phẩm theo FEFO | Đọc | productId, hôm nay | Rất cao | < 20 ms | Strong | Mongo `batches {productId, expirySortKey, receivedAt, batchCode}` |
| AP-08 | Lô hết hạn còn tồn | Đọc | hôm nay | Thấp | < 200 ms | Strong | Mongo `batches {expirySortKey, remainingQty}` |
| AP-09 | Tạo đơn bán: trừ lô, tạo đơn, thanh toán | Ghi | giỏ hàng, requestKey | Cao | < 300 ms | Strong | Mongo transaction |
| AP-10 | Chi tiết đơn theo mã | Đọc | orderCode | Cao | < 50 ms | Strong | Mongo unique `orderCode` |
| AP-11 | Danh sách đơn theo thời gian, trạng thái | Đọc | khoảng ngày, status, trang | Trung bình | < 100 ms | Strong | Mongo `{status:1, orderedAt:-1}` |
| AP-12 | Lập phiếu trả kèm hoàn tiền | Ghi | orderId, dòng trả | Trung bình | < 300 ms | Strong | Mongo transaction |
| AP-13 | Thẻ kho của sản phẩm theo tháng | Đọc | productId, tháng | Trung bình | < 100 ms | Eventual | Cassandra `stock_movements_by_product` |
| AP-14 | Lịch sử thanh toán của đơn | Đọc | orderId | Trung bình | < 50 ms | Eventual | Cassandra `payments_by_order` |
| AP-15 | Tổng thu của thu ngân trong ngày (chốt ca) | Đọc | cashierId, ngày | Thấp | < 100 ms | Eventual, đồng bộ trước khi đọc | Cassandra `payments_by_cashier_day` |
| AP-16 | Lịch sử đăng nhập 90 ngày | Đọc/Ghi | userId, tháng | Thấp | < 100 ms | Eventual | Cassandra `login_history_by_user` (TTL) |
| AP-17 | Khuyến mãi đang hiệu lực | Đọc | thời điểm | Rất cao | < 20 ms | Eventual (5 phút) | Redis `cache:promotions:active` → Mongo `promotions` |
| AP-18 | Lưu, liệt kê, mở đơn treo | Đọc/Ghi | cashierId, holdId | Cao | < 10 ms | Strong (1 key) | Redis + MULTI và Lua |
| AP-19 | Top 10 bán chạy hôm nay | Đọc/Ghi | ngày | Cao | < 10 ms | Eventual | Redis sorted set `top:sales:*` |
| AP-20 | Truy vết lô → đơn → khách | Đọc | batchCode | Thấp | < 200 ms | Eventual | Neo4j `TOOK_FROM`, `PLACED` |
| AP-21 | Gợi ý mua kèm | Đọc | productId | Cao | < 200 ms | Eventual | Neo4j `CONTAINS` 2 bước |
| AP-22 | Phiếu nhập theo trạng thái; nhận hàng | Đọc/Ghi | status, poId | Trung bình | < 300 ms | Strong | Mongo `purchase_orders` + transaction |
| AP-23 | Thống kê doanh thu theo ngày, nhóm hàng | Đọc | khoảng ngày | Thấp | < 1 s | Strong | Mongo aggregation trên `orders {orderedAt}` |
| AP-24 | Sinh mã chứng từ | Ghi | loại, ngày | Cao | < 20 ms | Strong | Mongo `counters` `$inc` |

### 19.2 Chức năng thêm

- **FR-15 Thống kê**: trang ADMIN gồm doanh thu và số đơn theo ngày (7/30 ngày), doanh thu theo nhóm hàng, tổng tiền khuyến mãi đã giảm, top sản phẩm theo kỳ. Dùng aggregation `$match → $unwind items → $group → $sort`, kèm `$facet` để lấy nhiều số liệu trong một lần gọi. Có nút **Xuất CSV**. Khung top 10 bán chạy hôm nay (Redis) hiện ở trang này và ở quầy.
- **Lịch sử đăng nhập (thuộc FR-01)**: mỗi lần đăng nhập ghi vào Cassandra, dữ liệu tự xóa sau 90 ngày:

```sql
CREATE TABLE login_history_by_user (
  user_id text, month text, login_at timestamp, event_id uuid,
  success boolean, ip text, user_agent text,
  PRIMARY KEY ((user_id, month), login_at, event_id)
) WITH CLUSTERING ORDER BY (login_at DESC, event_id ASC)
  AND default_time_to_live = 7776000;
```

### 19.3 Kỹ thuật nâng cao (mục 4.13 của mẫu)

| Kỹ thuật | Áp dụng |
|---|---|
| Đa luồng (`CompletableFuture`) | `GET /api/pos/products/{id}/panel` gọi song song MongoDB (chi tiết, lô), Neo4j (gợi ý) và Redis (top bán), có đo thời gian tuần tự và song song |
| `@Async` | `SyncService.process` chạy sau commit, không chặn API |
| `@Scheduled` | Job đồng bộ mỗi 60 giây; job 07:00 hằng ngày làm nóng cache danh sách lô sắp hết hạn trong 3 ngày |
| Tối ưu CSDL | Index ESR, cache-aside với Redis, bucket theo tháng ở Cassandra, projection, `explain()` chứng minh dùng IXSCAN |
| Xử lý tranh chấp | Transaction MongoDB và `updateOne` có điều kiện `$gte`; unique `requestKey`; Lua script lấy và xóa đơn treo nguyên tử |

### 19.4 Script cho từng CSDL (hồ sơ nộp số 2)

Thư mục mới `nosql-scripts/`:

```
nosql-scripts/
  mongodb/   01-create-collections.js ($jsonSchema validation + index), 02-seed.js, 03-queries-basic.js, 04-queries-advanced.js, backup-restore.md
  cassandra/ 01-schema.cql, 02-seed.cql, 03-queries-basic.cql, 04-queries-advanced.cql, backup-restore.md
  neo4j/     01-constraints.cypher, 02-seed.cypher, 03-queries-basic.cypher, 04-queries-advanced.cypher, backup-restore.md
  redis/     01-key-design.md, 02-commands-basic.redis, 03-commands-advanced.redis (ZSET, MULTI/EXEC, Lua, Pub/Sub)
```

Tối thiểu cho toàn đồ án là 12 truy vấn cơ bản và 18 truy vấn nâng cao, hệ nào cũng có cả hai loại:

- **MongoDB**: lọc + sắp xếp + phân trang; thêm, sửa, xóa mềm; aggregation doanh thu theo ngày; `$facet`; `$lookup` lô → nhà cung cấp; `findOneAndUpdate` trừ tồn có điều kiện; text search; cập nhật mảng lồng nhau bằng `arrayFilters`.
- **Cassandra**: truy vấn theo partition; truy vấn khoảng trên clustering key; `INSERT ... USING TTL` và `SELECT TTL()`; `BATCH` ghi cùng lúc hai bảng thanh toán; LWT `IF NOT EXISTS`.
- **Neo4j**: `MATCH` theo SKU; `MERGE`; truy vết lô nhiều bước; gợi ý mua kèm; `shortestPath` nhà cung cấp → khách; khách hàng mua giống nhau.
- **Redis**: `SET EX`/`GET`/`TTL`; `ZINCRBY`/`ZREVRANGE WITHSCORES`; `MULTI/EXEC`; Lua script; `PUBLISH`/`SUBSCRIBE`.

Schema validation `$jsonSchema` và index cũng được backend tạo lúc khởi động nếu còn thiếu, nên script và app luôn khớp nhau.

### 19.5 Phân tán và CAP khi dùng cloud (mục 4.8 của mẫu)

Không dùng Docker, nên phần cụm mô tả theo dịch vụ cloud:

| CSDL | Cụm | Sao chép | CAP | Ghi chú cấu hình |
|---|---|---|---|---|
| MongoDB Atlas M0 | Replica set 3 node do Atlas quản lý | primary–secondary | CP | `writeConcern: majority`, `readConcern: majority` cho transaction |
| Astra DB | Serverless, nhiều vùng sẵn sàng | RF 3 (do Astra) | AP, tunable | Ghi `LOCAL_QUORUM`, đọc `LOCAL_QUORUM` (R + W > RF) |
| Neo4j Aura Free | 1 instance | — | CP | Dựng lại được từ MongoDB |
| Redis Cloud/Upstash | 1 node | — | AP | Chỉ dữ liệu tạm, `maxmemory-policy allkeys-lru` |

Kiểm thử chịu lỗi (mục khuyến khích) thay bằng: tắt Redis và Neo4j (đổi URL sai) → hệ thống vẫn bán được, sync queue giữ PENDING rồi tự đồng bộ lại khi bật.

### 19.6 Tài liệu nộp

| Hồ sơ | File |
|---|---|
| SRS + SDS (Chương 1–4), khung Chương 5–8 | `docs/nop-bai/BaoCao_DoAn_NoSQL_SieuThi.docx` |
| Bảng minh chứng và tự đánh giá | `docs/nop-bai/BaoCaoMinhChung_DoAn_DuLieuNoSQL.docx` |
| Script từng CSDL | `nosql-scripts/` |
| Kịch bản kiểm thử | Chương 7 của báo cáo + `Bruno/` |
| SRS cũ v1.1 | Thay bằng Chương 3 của báo cáo |

API thêm: `GET /api/stats/sales?from=&to=`, `GET /api/stats/sales.csv`, `GET /api/pos/products/{id}/panel`, `GET /api/users/{id}/logins?month=`, `GET /api/auth/me/logins?month=`. Trang frontend thêm: **Thống kê**.
