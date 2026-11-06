-- Read-only examples, NOT executed or runtime-validated.
-- Defaults below must match DRILL_MYSQL_SCHEMA / DRILL_CASSANDRA_KEYSPACE.
-- Change the literal dates together: Vietnam 2026-10-30 is UTC [Oct 29 17:00, Oct 30 17:00).
SELECT CAST(event_date AS DATE) AS event_date,
       CAST(event_time AS TIMESTAMP) AS event_time,
       CAST(order_id AS BIGINT) AS order_id,
       CAST(event_type AS VARCHAR) AS event_type,
       CAST(amount AS DECIMAL(18,2)) AS amount
FROM cassandra_source.supermarket_analytics.business_events_by_day
WHERE event_date >= DATE '2026-10-29' AND event_date <= DATE '2026-10-30'
LIMIT 20;

SELECT CAST(o.order_id AS BIGINT) AS order_id, o.order_code,
       CAST(p.payment_record_id AS BIGINT) AS payment_record_id,
       p.event_type, CAST(p.amount AS DECIMAL(18,2)) AS amount, p.created_at
FROM mysql_source.supermarket_api.sales_orders o
JOIN mysql_source.supermarket_api.payment_records p ON p.order_id = o.order_id
WHERE o.order_type = 'IN_STORE'
  AND p.created_at >= TIMESTAMP '2026-10-29 17:00:00'
  AND p.created_at < TIMESTAMP '2026-10-30 17:00:00'
LIMIT 20;

SELECT CAST(product_id AS BIGINT) AS product_id, sku_code, name
FROM mysql_source.supermarket_api.products LIMIT 20;

-- File workspaces are optional; no source files are created by configure-storage.ps1.
-- pos_payments uses JSON. Use BIGINT product_id / order_id and actual VARCHAR sku_code values.
SHOW FILES IN analytics.pos_payments;
