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

**Ngoài phạm vi**: bán online, tích điểm, cổng thanh toán, báo cáo/dashboard phân tích, giới hạn số suất khuyến mãi, nhiều chi nhánh.

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

| Mã | Chức năng | Tác nhân | Mongo | Cassandra | Neo4j | Redis |
|---|---|---|:-:|:-:|:-:|:-:|
| FR-01 | Đăng nhập, làm mới phiên, đăng xuất | Tất cả | ● | | | ● refresh token |
| FR-02 | Hồ sơ cá nhân, đổi mật khẩu | Tất cả | ● | | | ● thu hồi token |
| FR-03 | Quản lý người dùng | ADMIN | ● | | | |
| FR-04 | Quản lý khách hàng | ADMIN, STAFF | ● | | ● | |
| FR-05 | Quản lý sản phẩm (thuộc tính linh hoạt) | ADMIN, WAREHOUSE | ● | | ● | ● cache |
| FR-06 | Nhóm hàng, nhà cung cấp | ADMIN (+WAREHOUSE cho NCC) | ● | | ● | |
| FR-07 | Tra cứu, sắp hết hàng, lô hết hạn | Tất cả | ● | | | ● cache |
| FR-08 | Phiếu nhập hàng → nhận lô | ADMIN, WAREHOUSE | ● | ● | ● | |
| FR-09 | Cấp phát FEFO (nội bộ) | Hệ thống | ● | | | |
| FR-10 | Điều chỉnh giảm tồn theo lô | ADMIN, WAREHOUSE | ● | ● | | |
| FR-11 | Thẻ kho (lịch sử biến động) | Tất cả | | ● | | |
| FR-12 | Bán tại quầy (có khuyến mãi) | ADMIN, STAFF | ● | ● | ● | ● top bán |
| FR-13 | Trả hàng kèm hoàn tiền (1 bước) | ADMIN, STAFF | ● | ● | | |
| FR-14 | Tra cứu đơn, phiếu trả, thanh toán | ADMIN, STAFF | ● | ● | | |
| FR-15 | Khuyến mãi 4 loại | ADMIN | ● | | | ● cache |
| FR-16 | Đơn treo tại quầy | STAFF | | | | ● |
| FR-17 | Chốt ca thu ngân | STAFF (của mình), ADMIN (tất cả) | ● | ● | | |
| FR-18 | Truy vết và thu hồi lô | ADMIN | | | ● | |
| FR-19 | Gợi ý mua kèm | STAFF (quầy), ADMIN (tạo KM) | | | ● | |
| FR-20 | Top bán chạy hôm nay | ADMIN, STAFF | | | | ● |

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
- Loại biến động: `RECEIPT`, `SALE`, `RETURN`, `RETURN_DAMAGED`, `ADJUSTMENT`. Quy ước dấu số lượng giữ như SRS FR-11.

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
- **Truy vết và thu hồi lô (FR-18)**:
  `MATCH (b:Batch {code:$code})<-[t:TOOK_FROM]-(o:Order) OPTIONAL MATCH (c:Customer)-[:PLACED]->(o) RETURN o.code, o.at, t.qty, c.name, c.phone ORDER BY o.at`
  Màn hình hiện danh sách khách cần liên hệ (có số điện thoại) và tổng số lượng đã bán ra.
- **Gợi ý mua kèm (FR-19)**:
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
| `pos:held:{cashierId}:{holdId}` | string JSON giỏ | 30 phút | FR-16 đơn treo |
| `pos:held-index:{cashierId}` | sorted set holdId theo thời gian | 30 phút | Liệt kê đơn treo |
| `top:sales:{yyyyMMdd}` | sorted set productId → số lượng | 3 ngày | FR-20; `ZINCRBY` khi bán (không tính hàng tặng), `ZREVRANGE 0 9` |

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

## 10. Khuyến mãi (FR-15)

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

**Nhập hàng (FR-08)**: WAREHOUSE tạo phiếu `DRAFT` (nhà cung cấp, dòng hàng, giá vốn) → `ORDERED` → khi hàng về, mở phiếu, nhập số thực nhận, ngày sản xuất, hạn dùng từng dòng → bấm "Nhận hàng". Một transaction thực hiện: tạo các lô, cộng `stockQuantity`, đặt phiếu `RECEIVED`, ghi `sync_queue` (`RECEIPT` sang Cassandra, Batch→Supplier sang Neo4j). Vẫn giữ nhập lô lẻ không cần phiếu (FR-08 cũ) cho hàng phát sinh.

**Bán (FR-12)**: quầy → `quote` → thu ngân chọn CASH hoặc BANK_TRANSFER (bắt buộc mã giao dịch) → `POST /api/orders`. Một transaction: kiểm tra `requestKey`, tính lại khuyến mãi, cấp phát FEFO, trừ tồn, tạo đơn có thanh toán nhúng, ghi `sync_queue`. Sau commit: `ZINCRBY` top bán, xóa cache sản phẩm.

**Đơn treo (FR-16)**: nút "Treo đơn" lưu giỏ vào Redis; danh sách "Đơn đang treo" cho phép mở lại hoặc xóa. Mở lại thì giỏ quay về quầy và xóa key. Đơn treo không giữ tồn kho.

**Trả hàng (FR-13)**: một bước, gồm lập phiếu và hoàn tiền. Bỏ endpoint hoàn tiền riêng `/returns/{id}/refund` của code cũ. Quy tắc như SRS FR-13: hạn 7 ngày, nhập lại đúng lô, lô hết hạn thì bắt buộc tính hàng hỏng, tiền hoàn theo mục 10.4.

**Chốt ca (FR-17)**: STAFF mở trang "Chốt ca" → hệ thống xử lý `sync_queue` còn chờ của mình → đọc `payments_by_cashier_day` hôm nay → hiện tiền mặt thu, chuyển khoản thu, tiền hoàn, tiền mặt phải có → thu ngân nhập tiền mặt đếm được và ghi chú → lưu `shift_closings` (mỗi thu ngân mỗi ngày một lần; ADMIN được mở lại). ADMIN xem danh sách chốt ca theo ngày và độ chênh lệch.

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
| Purchase orders | `GET/POST /purchase-orders`, `PUT /purchase-orders/{id}`, `POST /purchase-orders/{id}/order`, `POST /purchase-orders/{id}/receive`, `POST /purchase-orders/{id}/cancel` | Mới |
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
- Mục 3: thêm FR-15 đến FR-20; sửa FR-08 (phiếu nhập), FR-12 (khuyến mãi), FR-13 (một bước, hoàn theo `netAmount`).
- Mục 4: thêm thực thể Khuyến mãi, Phiếu nhập, Chốt ca, Hàng đợi đồng bộ; đổi Refresh token sang Redis; thanh toán nhúng trong đơn và sao sang Cassandra.
- Mục 5.3: thay bằng bảng phân vai mục 4 của tài liệu này.
- Mục 7: thêm dòng ma trận cho FR-15 đến FR-20.

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
