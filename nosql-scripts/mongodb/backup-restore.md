# Sao lưu và khôi phục MongoDB Atlas

Cần [MongoDB Database Tools](https://www.mongodb.com/docs/database-tools/) (`mongodump`, `mongorestore`, `mongoexport`). Chuỗi kết nối lấy từ `.env` (`MONGODB_URI`), không ghi vào lệnh trong báo cáo.

## Sao lưu toàn bộ database

```bash
mongodump --uri "$MONGODB_URI" --db supermarket --gzip --archive=backup/supermarket-$(date +%F).gz
```

## Khôi phục (thay toàn bộ dữ liệu hiện có)

```bash
mongorestore --uri "$MONGODB_URI" --nsInclude 'supermarket.*' --drop --gzip --archive=backup/supermarket-2026-11-07.gz
```

`--drop` xóa collection trước khi nạp lại. Index và `$jsonSchema` được khôi phục theo bản sao lưu; backend cũng tự tạo lại lúc khởi động.

## Xuất một collection ra JSON (đọc được bằng mắt)

```bash
mongoexport --uri "$MONGODB_URI" --db supermarket --collection orders --jsonFormat=canonical --out backup/orders.json
mongoimport --uri "$MONGODB_URI" --db supermarket --collection orders --mode=upsert --file backup/orders.json
```

`--jsonFormat=canonical` giữ đúng kiểu `Decimal128`, `Date`, `ObjectId`.

## Sau khi khôi phục MongoDB

MongoDB là nguồn sự thật. Dựng lại đồ thị Neo4j bằng `POST /api/admin/graph/rebuild` (ADMIN). Sổ cái Cassandra khôi phục riêng (xem `../cassandra/backup-restore.md`).

## Kịch bản kiểm thử NFR-10 (< 30 phút)

1. Ghi lại số document: `db.orders.countDocuments()`, `db.batches.countDocuments()`.
2. Sao lưu bằng `mongodump`.
3. Xóa database thử: `db.getSiblingDB('supermarket_dev').dropDatabase()`.
4. Khôi phục bằng `mongorestore --nsFrom 'supermarket.*' --nsTo 'supermarket_dev.*'`.
5. Đếm lại và so sánh; ghi thời gian từng bước vào Chương 7 báo cáo.
