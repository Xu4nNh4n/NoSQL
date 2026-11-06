-- Applied promotion history, Vietnam invoice date 2026-10-30
WITH promoted_lines AS (
    SELECT o.order_id, i.product_id, p.sku_code AS sku, p.name AS product_name,
           i.unit_price AS original_unit_price,
           COALESCE(i.net_unit_price, i.unit_price) AS net_unit_price,
           i.quantity, COALESCE(i.returned_quantity, 0) AS returned_quantity,
           CONVERT_FROM(CONVERT_TO(CAST(i.promotion_snapshot AS VARCHAR(65535)), 'UTF8'), 'JSON') AS snapshot
    FROM mysql_source.`supermarket_api`.sales_order_items i
    JOIN mysql_source.`supermarket_api`.sales_orders o ON o.order_id = i.order_id
    JOIN mysql_source.`supermarket_api`.products p ON p.product_id = i.product_id
    WHERE o.created_at >= TIMESTAMP '2026-10-29 17:00:00' AND o.created_at < TIMESTAMP '2026-10-30 17:00:00'
      AND o.order_type = 'IN_STORE' AND o.status IN ('PAID','PARTIALLY_RETURNED','RETURNED')
      AND i.promotion_snapshot IS NOT NULL
), promotions AS (
    SELECT CAST(l.snapshot.promotionId AS BIGINT) AS promotion_id,
           CAST(l.snapshot.name AS VARCHAR) AS promotion_name,
           CAST(l.snapshot.percent AS DECIMAL(5,2)) AS discount_percent,
           l.order_id, l.product_id, l.sku, l.product_name,
           l.original_unit_price, l.net_unit_price, l.quantity, l.returned_quantity
    FROM promoted_lines l
)
SELECT promotion_id, promotion_name, discount_percent, product_id, sku, product_name,
       COUNT(DISTINCT order_id) AS order_count, SUM(quantity) AS sold_quantity,
       SUM(returned_quantity) AS returned_quantity,
       SUM(quantity - returned_quantity) AS net_quantity,
       SUM((original_unit_price - net_unit_price) * quantity) AS discount_amount,
       SUM(net_unit_price * (quantity - returned_quantity)) AS net_sales_amount
FROM promotions
GROUP BY promotion_id, promotion_name, discount_percent, product_id, sku, product_name
ORDER BY discount_amount DESC, promotion_id, sku, promotion_name, discount_percent
LIMIT 20
;
