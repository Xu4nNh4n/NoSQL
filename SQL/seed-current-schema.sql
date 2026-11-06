-- Mẫu dữ liệu POS cho 11 bảng sau khi chạy SQL/schema.sql.
-- Không chạy trên database cần giữ dữ liệu hiện có.
USE supermarket_api;
START TRANSACTION;

INSERT INTO roles (role_name, description)
VALUES ('ADMIN', 'Quản trị hệ thống'), ('STAFF', 'Thu ngân'), ('WAREHOUSE', 'Quản lý kho')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO users (username, password_hash, full_name, email, phone, role_id, is_active)
SELECT 'seed_admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       'Seed Administrator', 'seed.admin@supermarket.local', '0900000001', r.role_id, TRUE
FROM roles r WHERE r.role_name = 'ADMIN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), is_active = TRUE;

INSERT INTO users (username, password_hash, full_name, email, phone, role_id, is_active)
SELECT 'seed_staff', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       'Seed Sales Staff', 'seed.staff@supermarket.local', '0900000002', r.role_id, TRUE
FROM roles r WHERE r.role_name = 'STAFF'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), is_active = TRUE;

INSERT INTO suppliers (name) VALUES ('Fresh Farm'), ('Daily Food'), ('Home Goods')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO products (name, sku_code, price, stock_quantity, damaged_quantity, version,
                      reorder_point, category_name, image_url, is_active)
VALUES
 ('Nước suối 500ml', '1', 5000, 0, 0, 0, 20, 'Đồ uống', NULL, TRUE),
 ('Sữa tươi không đường 1L', '2', 32000, 0, 0, 0, 10, 'Sữa và chế phẩm', NULL, TRUE),
 ('Mì gói vị bò hầm', '3', 6500, 0, 0, 0, 30, 'Thực phẩm khô', NULL, TRUE),
 ('Nồi inox 18cm', '4', 185000, 0, 0, 0, 5, 'Đồ gia dụng', NULL, TRUE),
 ('Táo đỏ nhập khẩu', '5', 45000, 0, 0, 0, 15, 'Rau củ quả', NULL, TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price), category_name = VALUES(category_name), is_active = TRUE;

INSERT INTO inventory_batches
  (product_id, supplier_name, batch_code, received_quantity, remaining_quantity, damaged_quantity,
   unit_cost, manufacturing_date, expiry_date, received_by, status)
SELECT p.product_id, 'Fresh Farm', '1', 100, 100, 0, 2500, '2026-09-01', '2027-09-01', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '1'
ON DUPLICATE KEY UPDATE batch_code = VALUES(batch_code);

INSERT INTO inventory_batches
  (product_id, supplier_name, batch_code, received_quantity, remaining_quantity, damaged_quantity,
   unit_cost, manufacturing_date, expiry_date, received_by, status)
SELECT p.product_id, 'Fresh Farm', '2', 40, 40, 0, 2300, '2026-08-01', '2026-12-01', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '1'
ON DUPLICATE KEY UPDATE batch_code = VALUES(batch_code);

INSERT INTO inventory_batches
  (product_id, supplier_name, batch_code, received_quantity, remaining_quantity, damaged_quantity,
   unit_cost, manufacturing_date, expiry_date, received_by, status)
SELECT p.product_id, 'Fresh Farm', '3', 60, 60, 0, 21000, '2026-09-20', '2026-11-20', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '2'
ON DUPLICATE KEY UPDATE batch_code = VALUES(batch_code);

INSERT INTO inventory_batches
  (product_id, supplier_name, batch_code, received_quantity, remaining_quantity, damaged_quantity,
   unit_cost, manufacturing_date, expiry_date, received_by, status)
SELECT p.product_id, 'Daily Food', '4', 120, 120, 0, 4200, '2026-09-15', '2027-09-15', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '3'
ON DUPLICATE KEY UPDATE batch_code = VALUES(batch_code);

INSERT INTO inventory_batches
  (product_id, supplier_name, batch_code, received_quantity, remaining_quantity, damaged_quantity,
   unit_cost, manufacturing_date, expiry_date, received_by, status)
SELECT p.product_id, 'Home Goods', '5', 25, 25, 0, 120000, NULL, NULL, 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '4'
ON DUPLICATE KEY UPDATE batch_code = VALUES(batch_code);

INSERT INTO inventory_batches
  (product_id, supplier_name, batch_code, received_quantity, remaining_quantity, damaged_quantity,
   unit_cost, manufacturing_date, expiry_date, received_by, status)
SELECT p.product_id, 'Fresh Farm', '6', 50, 50, 0, 28000, '2026-09-25', '2026-11-05', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '5'
ON DUPLICATE KEY UPDATE batch_code = VALUES(batch_code);

UPDATE inventory_batches b JOIN suppliers s ON s.name = b.supplier_name
SET b.supplier_id = s.supplier_id WHERE b.supplier_id IS NULL;

UPDATE products p
SET p.stock_quantity = (SELECT COALESCE(SUM(b.remaining_quantity), 0)
                        FROM inventory_batches b WHERE b.product_id = p.product_id)
WHERE p.sku_code IN ('1', '2', '3', '4', '5');

INSERT INTO stock_movements (product_id, movement_type, quantity, note, performed_by)
SELECT p.product_id, 'OPENING', SUM(b.received_quantity), 'Lô hàng mẫu ban đầu', 'seed_admin'
FROM products p JOIN inventory_batches b ON b.product_id = p.product_id
WHERE p.sku_code IN ('1', '2', '3', '4', '5')
GROUP BY p.product_id;

COMMIT;
