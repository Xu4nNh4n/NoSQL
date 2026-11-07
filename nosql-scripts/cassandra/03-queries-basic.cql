-- 03-queries-basic.cql – truy vấn cơ bản. Mọi SELECT đều chỉ định đủ partition key (không ALLOW FILTERING).
USE supermarket;

-- [CB-C1] AP-13: thẻ kho của sản phẩm trong tháng, mới nhất trước (clustering order created_at DESC) – StockLedger.read
SELECT created_at, movement_type, quantity, batch_code, ref_code, performed_by
FROM stock_movements_by_product WHERE product_id = 'p-milk' AND month = '2026-11' LIMIT 500;

-- [CB-C2] AP-14: lịch sử thanh toán và hoàn tiền của một đơn – PaymentLedger.byOrder
SELECT created_at, type, method, amount, reference, return_code FROM payments_by_order WHERE order_id = 'o-1';

-- [CB-C3] AP-15: mọi khoản của thu ngân trong ngày (đầu vào chốt ca) – PaymentLedger.byCashierDay
SELECT created_at, order_code, type, method, amount FROM payments_by_cashier_day WHERE cashier_id = 'c-lan' AND day = '2026-11-07';

-- [CB-C4] Ghi một dòng thẻ kho (chỉ thêm; movement_id tạo từ nội dung nên ghi lại chỉ ghi đè đúng dòng cũ)
INSERT INTO stock_movements_by_product (product_id, month, created_at, movement_id, movement_type, quantity, batch_code, ref_type, ref_code, performed_by)
VALUES ('p-milk', '2026-11', '2026-11-07 18:00:00+0700', 6f1c0b5e-0000-3000-8000-000000000005, 'SALE', -3, 'L261107-0001', 'ORDER', 'HD261107-0002', 'thungan02');
