-- 02-seed.cql – vài dòng mẫu để chạy thử 03/04. Dữ liệu thật do backend ghi qua sync_queue.
USE supermarket;

-- Thẻ kho sản phẩm p-milk tháng 2026-11: nhập 30, bán 2, trả 1, điều chỉnh -1
INSERT INTO stock_movements_by_product (product_id, month, created_at, movement_id, movement_type, quantity, batch_code, ref_type, ref_code, note, performed_by)
VALUES ('p-milk', '2026-11', '2026-11-07 08:00:00+0700', 6f1c0b5e-0000-3000-8000-000000000001, 'RECEIPT', 30, 'L261107-0001', 'PURCHASE_ORDER', 'PN261107-0001', null, 'thukho01');
INSERT INTO stock_movements_by_product (product_id, month, created_at, movement_id, movement_type, quantity, batch_code, ref_type, ref_code, note, performed_by)
VALUES ('p-milk', '2026-11', '2026-11-07 10:15:00+0700', 6f1c0b5e-0000-3000-8000-000000000002, 'SALE', -2, 'L261107-0001', 'ORDER', 'HD261107-0001', null, 'thungan01');
INSERT INTO stock_movements_by_product (product_id, month, created_at, movement_id, movement_type, quantity, batch_code, ref_type, ref_code, note, performed_by)
VALUES ('p-milk', '2026-11', '2026-11-07 15:40:00+0700', 6f1c0b5e-0000-3000-8000-000000000003, 'RETURN', 1, 'L261107-0001', 'RETURN', 'PT261107-0001', 'Khách đổi ý', 'thungan01');
INSERT INTO stock_movements_by_product (product_id, month, created_at, movement_id, movement_type, quantity, batch_code, ref_type, ref_code, note, performed_by)
VALUES ('p-milk', '2026-11', '2026-11-07 17:00:00+0700', 6f1c0b5e-0000-3000-8000-000000000004, 'ADJUSTMENT', -1, 'L261107-0001', 'ADJUSTMENT', 'L261107-0001', 'Lô L261107-0001: Vỡ hộp', 'thukho01');

-- Một khoản thu và một khoản hoàn của thu ngân c-lan ngày 2026-11-07 (ghi cả hai bảng như backend)
BEGIN BATCH
  INSERT INTO payments_by_order (order_id, created_at, payment_id, type, method, amount, reference, return_code, cashier_id)
  VALUES ('o-1', '2026-11-07 10:15:00+0700', 7a000000-0000-3000-8000-000000000001, 'PAYMENT', 'CASH', 64000, 'POS-HD261107-0001', null, 'c-lan');
  INSERT INTO payments_by_cashier_day (cashier_id, day, created_at, payment_id, order_code, type, method, amount)
  VALUES ('c-lan', '2026-11-07', '2026-11-07 10:15:00+0700', 7a000000-0000-3000-8000-000000000001, 'HD261107-0001', 'PAYMENT', 'CASH', 64000);
APPLY BATCH;
BEGIN BATCH
  INSERT INTO payments_by_order (order_id, created_at, payment_id, type, method, amount, reference, return_code, cashier_id)
  VALUES ('o-1', '2026-11-07 15:40:00+0700', 7a000000-0000-3000-8000-000000000002, 'REFUND', 'CASH', 32000, 'REFUND-PT261107-0001', 'PT261107-0001', 'c-lan');
  INSERT INTO payments_by_cashier_day (cashier_id, day, created_at, payment_id, order_code, type, method, amount)
  VALUES ('c-lan', '2026-11-07', '2026-11-07 15:40:00+0700', 7a000000-0000-3000-8000-000000000002, 'HD261107-0001', 'REFUND', 'CASH', 32000);
APPLY BATCH;
