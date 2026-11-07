-- 04-queries-advanced.cql – truy vấn nâng cao: khoảng trên clustering key, TTL, BATCH nhiều bảng, LWT,
-- tổng hợp trong một partition, consistency level.
USE supermarket;

-- [NC-C1] Khoảng thời gian trên clustering key trong MỘT partition: biến động buổi chiều ngày 7/11
SELECT created_at, movement_type, quantity FROM stock_movements_by_product
WHERE product_id = 'p-milk' AND month = '2026-11'
  AND created_at >= '2026-11-07 12:00:00+0700' AND created_at < '2026-11-08 00:00:00+0700';

-- [NC-C2] AP-16: lịch sử đăng nhập tự xóa sau 90 ngày (default_time_to_live của bảng) và TTL riêng từng dòng
INSERT INTO login_history_by_user (user_id, month, login_at, event_id, success, ip, user_agent)
VALUES ('u-lan', '2026-11', toTimestamp(now()), uuid(), true, '113.161.1.1', 'Chrome');
INSERT INTO login_history_by_user (user_id, month, login_at, event_id, success, ip, user_agent)
VALUES ('u-lan', '2026-11', toTimestamp(now()), uuid(), false, '113.161.1.1', 'Chrome') USING TTL 3600;
SELECT login_at, success, TTL(success) AS con_lai_giay FROM login_history_by_user WHERE user_id = 'u-lan' AND month = '2026-11';

-- [NC-C3] BATCH logged: một khoản thanh toán ghi đồng thời vào HAI bảng (mỗi bảng một câu truy vấn), không lệch nhau
BEGIN BATCH
  INSERT INTO payments_by_order (order_id, created_at, payment_id, type, method, amount, reference, cashier_id)
  VALUES ('o-2', '2026-11-07 18:00:00+0700', 7a000000-0000-3000-8000-000000000003, 'PAYMENT', 'BANK_TRANSFER', 96000, 'FT2611070001', 'c-hung');
  INSERT INTO payments_by_cashier_day (cashier_id, day, created_at, payment_id, order_code, type, method, amount)
  VALUES ('c-hung', '2026-11-07', '2026-11-07 18:00:00+0700', 7a000000-0000-3000-8000-000000000003, 'HD261107-0002', 'PAYMENT', 'BANK_TRANSFER', 96000);
APPLY BATCH;

-- [NC-C4] LWT (Paxos): chỉ ghi nếu dòng chưa tồn tại; chạy lần 2 trả [applied] = False
INSERT INTO payments_by_order (order_id, created_at, payment_id, type, method, amount, cashier_id)
VALUES ('o-3', '2026-11-07 19:00:00+0700', 7a000000-0000-3000-8000-000000000004, 'PAYMENT', 'CASH', 50000, 'c-lan') IF NOT EXISTS;

-- [NC-C5] Tổng hợp trong một partition: biến động ròng của sản phẩm trong tháng (đối chiếu NFR-06) và tổng của ca.
-- Cassandra chỉ GROUP BY theo cột khóa, nên tổng theo loại biến động làm ở backend hoặc bằng bảng riêng.
SELECT SUM(quantity) AS bien_dong_rong, COUNT(*) AS so_dong FROM stock_movements_by_product
WHERE product_id = 'p-milk' AND month = '2026-11';
SELECT SUM(amount) AS tong_thu, COUNT(*) AS so_khoan FROM payments_by_cashier_day WHERE cashier_id = 'c-lan' AND day = '2026-11-07';

-- [NC-C6] Mức nhất quán: backend đọc/ghi LOCAL_QUORUM (R + W > RF = 3 trên Astra). Trong cqlsh:
CONSISTENCY LOCAL_QUORUM;
SELECT * FROM payments_by_cashier_day WHERE cashier_id = 'c-lan' AND day = '2026-11-07';
