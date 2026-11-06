-- Storage plugin names: mysql_source, mongo_source, analytics.

-- Catalog from MongoDB joined with the authoritative MySQL inventory balances.
SELECT p.product_id, p.sku_code, m.name, m.categoryName,
       p.price, p.stock_quantity, p.damaged_quantity,
       m.attributes
FROM mysql_source.supermarket_api.products p
JOIN mongo_source.supermarket_catalog.products_catalog m ON p.sku_code = m.sku
ORDER BY p.product_id;

-- Usable quantity excludes expired or quarantined batches.
SELECT p.sku_code, p.name,
       SUM(CASE WHEN b.status = 'ACTIVE'
                     AND (b.expiry_date IS NULL OR b.expiry_date >= CURRENT_DATE)
                THEN b.remaining_quantity ELSE 0 END) AS usable_quantity
FROM mysql_source.supermarket_api.products p
LEFT JOIN mysql_source.supermarket_api.inventory_batches b ON p.product_id = b.product_id
WHERE p.is_active = TRUE
GROUP BY p.sku_code, p.name;

-- Cash received and refunded from actual Astra exports (UTC dates).
-- Sum PAYMENT_RECEIVED and REFUND_CONFIRMED only to avoid counting order/return summaries twice.
SELECT e.event_date,
       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED'
                THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS collected_amount,
       SUM(CASE WHEN e.event_type = 'REFUND_CONFIRMED'
                THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS refunded_amount,
       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2))
                WHEN e.event_type = 'REFUND_CONFIRMED' THEN -CAST(e.amount AS DECIMAL(18, 2))
                ELSE 0 END) AS net_cash_amount
FROM analytics.cassandra.`events-*.json` e
JOIN mysql_source.supermarket_api.sales_orders o
  ON CAST(e.order_id AS BIGINT) = o.order_id AND o.order_type = 'IN_STORE'
WHERE e.event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED')
GROUP BY e.event_date
ORDER BY e.event_date;

-- Cross-source inventory events enriched with MongoDB catalog and MySQL order codes.
-- Exclude the original all-ones UUID example inserted manually in Astra.
SELECT e.event_date, e.event_type, e.product_sku, m.name,
       e.quantity, o.order_code, e.status, e.metadata
FROM analytics.cassandra.`events-*.json` e
LEFT JOIN mongo_source.supermarket_catalog.products_catalog m ON e.product_sku = m.sku
LEFT JOIN mysql_source.supermarket_api.sales_orders o ON CAST(e.order_id AS BIGINT) = o.order_id
WHERE e.product_sku IS NOT NULL
  AND (e.order_id IS NULL OR o.order_type = 'IN_STORE')
  AND e.event_id <> '11111111-1111-1111-1111-111111111111'
ORDER BY e.event_time DESC;
