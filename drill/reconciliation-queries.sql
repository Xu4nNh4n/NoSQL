-- Đối soát ngày UTC 2026-10-08; JDBC mysql_source phải dùng session UTC.
-- Đổi ngày ở JSON và các mốc thời gian nếu dùng ngày khác.
-- Chạy configure-storage.ps1 để cố định session UTC trước khi dùng SQL này.
-- API chọn JSON/Parquet còn cập nhật theo manifest.

WITH report_day AS (
    SELECT DATE '2026-10-08' AS event_date,
           TIMESTAMP '2026-10-08 00:00:00' AS utc_start,
           TIMESTAMP '2026-10-09 00:00:00' AS utc_end
), astra_raw AS (
    SELECT event_date, event_type, amount, event_id, order_id,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-08.json`
), astra_daily AS (
    SELECT CAST(a.event_date AS DATE) AS event_date,
           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED' THEN 1 ELSE 0 END) AS astra_payment_count,
           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED' THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_collected_amount,
           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED' THEN 1 ELSE 0 END) AS astra_refund_count,
           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED' THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_refunded_amount
    FROM astra_raw a
    JOIN mysql_source.supermarket_api.sales_orders source_order
      ON CAST(a.order_id AS BIGINT) = source_order.order_id AND source_order.order_type = 'IN_STORE'
    WHERE event_rank = 1 AND event_id IS NOT NULL
      AND event_id <> '11111111-1111-1111-1111-111111111111'
      AND event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED')
    GROUP BY CAST(event_date AS DATE)
), mysql_payments AS (
    SELECT d.event_date,
           SUM(CASE WHEN event_type = 'PAYMENT' THEN 1 ELSE 0 END) AS mysql_payment_count,
           SUM(CASE WHEN event_type = 'PAYMENT' THEN amount ELSE 0 END) AS mysql_collected_amount,
           SUM(CASE WHEN event_type = 'REFUND' THEN 1 ELSE 0 END) AS mysql_refund_count,
           SUM(CASE WHEN event_type = 'REFUND' THEN amount ELSE 0 END) AS mysql_refunded_amount
    FROM mysql_source.supermarket_api.payment_records p
    JOIN mysql_source.supermarket_api.sales_orders o ON o.order_id = p.order_id
    JOIN report_day d ON p.created_at >= d.utc_start AND p.created_at < d.utc_end
    WHERE o.order_type = 'IN_STORE'
    GROUP BY d.event_date
), mysql_orders AS (
    -- Tổng tiền đơn được cộng trước khi JOIN dòng hàng; mỗi đơn chỉ tính một lần.
    SELECT d.event_date, SUM(COALESCE(o.subtotal, 0)) AS mysql_order_subtotal
    FROM mysql_source.supermarket_api.sales_orders o
    JOIN report_day d ON o.order_date >= d.utc_start AND o.order_date < d.utc_end
    WHERE o.order_type = 'IN_STORE'
    GROUP BY d.event_date
), mysql_items AS (
    SELECT d.event_date,
           SUM(COALESCE(i.subtotal, 0)) AS mysql_item_subtotal
    FROM mysql_source.supermarket_api.sales_orders o
    JOIN mysql_source.supermarket_api.sales_order_items i ON i.order_id = o.order_id
    JOIN report_day d ON o.order_date >= d.utc_start AND o.order_date < d.utc_end
    WHERE o.order_type = 'IN_STORE'
    GROUP BY d.event_date
)
SELECT d.event_date,
       COALESCE(p.mysql_payment_count, 0) AS mysql_payment_count,
       COALESCE(a.astra_payment_count, 0) AS astra_payment_count,
       COALESCE(p.mysql_collected_amount, 0) AS mysql_collected_amount,
       COALESCE(a.astra_collected_amount, 0) AS astra_collected_amount,
       COALESCE(p.mysql_collected_amount, 0) - COALESCE(a.astra_collected_amount, 0) AS collected_delta,
       COALESCE(p.mysql_refund_count, 0) AS mysql_refund_count,
       COALESCE(a.astra_refund_count, 0) AS astra_refund_count,
       COALESCE(p.mysql_refunded_amount, 0) AS mysql_refunded_amount,
       COALESCE(a.astra_refunded_amount, 0) AS astra_refunded_amount,
       COALESCE(p.mysql_refunded_amount, 0) - COALESCE(a.astra_refunded_amount, 0) AS refunded_delta,
       COALESCE(i.mysql_item_subtotal, 0) AS mysql_item_subtotal,
       COALESCE(o.mysql_order_subtotal, 0) AS mysql_order_subtotal,
       COALESCE(o.mysql_order_subtotal, 0) - COALESCE(i.mysql_item_subtotal, 0) AS item_subtotal_delta
FROM report_day d
LEFT JOIN mysql_payments p ON p.event_date = d.event_date
LEFT JOIN mysql_items i ON i.event_date = d.event_date
LEFT JOIN mysql_orders o ON o.event_date = d.event_date
LEFT JOIN astra_daily a ON a.event_date = d.event_date
ORDER BY event_date;
