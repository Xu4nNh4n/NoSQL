-- 01-schema.cql – bảng Cassandra của hệ thống (SAO Y backend/src/main/resources/cassandra/schema.cql).
-- Astra: chọn keyspace trên giao diện CQL Console rồi chạy. Cassandra cài máy: tạo keyspace trước:
--   CREATE KEYSPACE IF NOT EXISTS supermarket WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
USE supermarket;

-- Sổ cái chỉ thêm. Mỗi bảng phục vụ đúng một câu truy vấn (spec mục 6, 19.2).
-- Keyspace tạo trên giao diện Astra (Astra không cho tạo keyspace bằng CQL).

CREATE TABLE IF NOT EXISTS stock_movements_by_product (
  product_id text, month text,
  created_at timestamp, movement_id uuid,
  movement_type text, quantity int, batch_code text,
  ref_type text, ref_code text, note text, performed_by text,
  PRIMARY KEY ((product_id, month), created_at, movement_id)
) WITH CLUSTERING ORDER BY (created_at DESC, movement_id ASC);

CREATE TABLE IF NOT EXISTS payments_by_order (
  order_id text, created_at timestamp, payment_id uuid,
  type text, method text, amount decimal, reference text, return_code text, cashier_id text,
  PRIMARY KEY ((order_id), created_at, payment_id)
);

CREATE TABLE IF NOT EXISTS payments_by_cashier_day (
  cashier_id text, day date, created_at timestamp, payment_id uuid,
  order_code text, type text, method text, amount decimal,
  PRIMARY KEY ((cashier_id, day), created_at, payment_id)
);

CREATE TABLE IF NOT EXISTS login_history_by_user (
  user_id text, month text, login_at timestamp, event_id uuid,
  success boolean, ip text, user_agent text,
  PRIMARY KEY ((user_id, month), login_at, event_id)
) WITH CLUSTERING ORDER BY (login_at DESC, event_id ASC)
  AND default_time_to_live = 7776000;
