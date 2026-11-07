# Script cho 4 CSDL NoSQL (hồ sơ nộp số 2)

Mỗi thư mục có: tạo cấu trúc → dữ liệu mẫu → truy vấn cơ bản → truy vấn nâng cao → sao lưu/khôi phục. Mỗi truy vấn có mã (`CB-…` cơ bản, `NC-…` nâng cao), mẫu truy cập (AP) trong SRS và lớp backend dùng nó.

| CSDL | Vai trò | Cơ bản | Nâng cao |
|---|---|---:|---:|
| [MongoDB](mongodb/) | Nguồn sự thật: danh mục, lô, phiếu nhập, đơn (nhúng dòng, lô, thanh toán), khuyến mãi | 6 | 9 |
| [Cassandra](cassandra/) | Sổ cái chỉ thêm: thẻ kho, thanh toán theo đơn / theo thu ngân-ngày, lịch sử đăng nhập (TTL) | 4 | 6 |
| [Neo4j](neo4j/) | Đồ thị đơn–sản phẩm–lô–NCC: truy vết thu hồi, gợi ý mua kèm | 3 | 7 |
| [Redis](redis/) | Dữ liệu tạm: phiên, cache, đơn treo, top bán | 3 | 5 |
| **Tổng** | | **16** | **27** |

Mẫu chấm yêu cầu tối thiểu 12 truy vấn cơ bản và 18 nâng cao, mỗi CSDL có cả hai loại.

## Thứ tự chạy

```bash
mongosh "$MONGODB_URI" --file nosql-scripts/mongodb/01-create-collections.js
mongosh "$MONGODB_URI" --file nosql-scripts/mongodb/02-seed.js
```

- Cassandra: mở Astra CQL Console, dán lần lượt `cassandra/01` → `04`.
- Neo4j: mở Neo4j Browser của Aura, chạy `neo4j/01` → `04`.
- Redis: `redis-cli -u "$REDIS_URL" --tls`, dán `redis/02`, `redis/03`.

Backend tự tạo cấu trúc giống hệt lúc khởi động (`SCHEMA_INIT=true`): `$jsonSchema` + index MongoDB đọc từ `backend/src/main/resources/mongodb/validators.json`, bảng Cassandra từ `cassandra/schema.cql`, constraint Neo4j từ `neo4j/constraints.cypher`. Dữ liệu demo đầy đủ tạo bằng `APP_SEED_DEMO=true`.
