-- Verified via running backend; Vietnam business date 2026-10-30
WITH report_days AS (SELECT * FROM (VALUES (DATE '2026-10-30', TIMESTAMP '2026-10-29 17:00:00', TIMESTAMP '2026-10-30 17:00:00')) AS d(event_date, utc_start, utc_end)), astra_raw AS (
    SELECT CAST(TIMESTAMPADD(HOUR, 7, event_time) AS DATE) AS event_date, event_type, amount, order_id
    FROM (SELECT CAST(TO_TIMESTAMP(CAST(event_date AS BIGINT) * 86400) AS DATE) AS event_date,
       CAST(event_type AS VARCHAR) AS event_type,
       CAST(amount AS DECIMAL(18, 2)) AS amount,
       CAST(order_id AS BIGINT) AS order_id,
       TO_TIMESTAMP(CAST(event_time AS DOUBLE) / 1000.0) AS event_time
FROM cassandra_source.`supermarket_analytics`.business_events_by_day
WHERE CAST(event_date AS BIGINT) >= 20755 AND CAST(event_date AS BIGINT) <= 20756
) raw WHERE event_time >= TIMESTAMP '2026-10-29 17:00:00' AND event_time < TIMESTAMP '2026-10-30 17:00:00'
),
astra_daily AS (
    SELECT a.event_date,
           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED' THEN 1 ELSE 0 END) AS astra_payment_count,
           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED'
                    THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_collected_amount,
           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED' THEN 1 ELSE 0 END) AS astra_refund_count,
           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED'
                    THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_refunded_amount
    FROM astra_raw a
    JOIN mysql_source.`supermarket_api`.sales_orders source_order
      ON CAST(a.order_id AS BIGINT) = source_order.order_id
     AND source_order.order_type = 'IN_STORE'
    WHERE event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED')
    GROUP BY a.event_date
),
mysql_payments AS (
    SELECT d.event_date,
           SUM(CASE WHEN event_type = 'PAYMENT' THEN 1 ELSE 0 END) AS mysql_payment_count,
           SUM(CASE WHEN event_type = 'PAYMENT' THEN amount ELSE 0 END) AS mysql_collected_amount,
           SUM(CASE WHEN event_type = 'REFUND' THEN 1 ELSE 0 END) AS mysql_refund_count,
           SUM(CASE WHEN event_type = 'REFUND' THEN amount ELSE 0 END) AS mysql_refunded_amount
    FROM mysql_source.`supermarket_api`.payment_records p
    JOIN mysql_source.`supermarket_api`.sales_orders o ON o.order_id = p.order_id
    JOIN report_days d ON p.created_at >= d.utc_start AND p.created_at < d.utc_end
    WHERE o.order_type = 'IN_STORE'
    GROUP BY d.event_date
),
mysql_orders AS (
    SELECT d.event_date,
           COUNT(*) AS mysql_order_count,
           SUM(COALESCE(subtotal, 0)) AS mysql_order_subtotal,
           SUM(COALESCE(total_amount, 0)) AS mysql_order_total
    FROM mysql_source.`supermarket_api`.sales_orders o
    JOIN report_days d ON o.created_at >= d.utc_start AND o.created_at < d.utc_end
    WHERE o.order_type = 'IN_STORE'
    GROUP BY d.event_date
),
item_totals AS (
    SELECT i.order_id, SUM(i.unit_price * i.quantity) AS gross_subtotal,
           SUM(i.subtotal) AS net_subtotal,
           COUNT(*) AS line_count, COUNT(i.net_unit_price) AS net_line_count
    FROM mysql_source.`supermarket_api`.sales_order_items i
    GROUP BY i.order_id
),
mysql_items AS (
    SELECT d.event_date,
           SUM(COALESCE(i.net_subtotal, 0)) AS mysql_item_subtotal,
           SUM(COALESCE(i.gross_subtotal, 0)) AS mysql_item_gross_subtotal,
           SUM(ABS(COALESCE(o.subtotal, 0) - COALESCE(i.gross_subtotal, 0))) AS gross_mismatch_amount,
           SUM(CASE WHEN i.line_count > 0 AND i.line_count = i.net_line_count THEN 1 ELSE 0 END) AS net_checked_order_count,
           SUM(CASE WHEN i.line_count > 0 AND i.line_count = i.net_line_count
                    THEN o.total_amount - i.net_subtotal ELSE 0 END) AS net_subtotal_delta,
           SUM(CASE WHEN i.line_count > 0 AND i.line_count = i.net_line_count
                    THEN ABS(o.total_amount - i.net_subtotal) ELSE 0 END) AS net_mismatch_amount
    FROM mysql_source.`supermarket_api`.sales_orders o
    LEFT JOIN item_totals i ON i.order_id = o.order_id
    JOIN report_days d ON o.created_at >= d.utc_start AND o.created_at < d.utc_end
    WHERE o.order_type = 'IN_STORE'
    GROUP BY d.event_date
),
days AS (
    SELECT event_date FROM report_days
)
SELECT d.event_date,
       COALESCE(a.astra_payment_count, 0) + COALESCE(a.astra_refund_count, 0) > 0 AS export_available,
       COALESCE(o.mysql_order_count, 0) AS mysql_order_count,
       COALESCE(o.mysql_order_subtotal, 0) AS mysql_order_subtotal,
       COALESCE(i.mysql_item_subtotal, 0) AS mysql_item_subtotal,
       COALESCE(i.mysql_item_gross_subtotal, 0) AS mysql_item_gross_subtotal,
       COALESCE(o.mysql_order_subtotal, 0) - COALESCE(i.mysql_item_gross_subtotal, 0)
           AS item_subtotal_delta,
       COALESCE(i.net_checked_order_count, 0) AS net_checked_order_count,
       COALESCE(o.mysql_order_count, 0) - COALESCE(i.net_checked_order_count, 0) AS net_skipped_order_count,
       COALESCE(i.net_subtotal_delta, 0) AS net_subtotal_delta,
       COALESCE(i.gross_mismatch_amount, 0) AS gross_mismatch_amount,
       COALESCE(i.net_mismatch_amount, 0) AS net_mismatch_amount,
       COALESCE(p.mysql_payment_count, 0) AS mysql_payment_count,
       COALESCE(a.astra_payment_count, 0) AS astra_payment_count,
       COALESCE(p.mysql_collected_amount, 0) AS mysql_collected_amount,
       COALESCE(a.astra_collected_amount, 0) AS astra_collected_amount,
       COALESCE(p.mysql_collected_amount, 0) - COALESCE(a.astra_collected_amount, 0)
           AS collected_delta,
       COALESCE(p.mysql_refund_count, 0) AS mysql_refund_count,
       COALESCE(a.astra_refund_count, 0) AS astra_refund_count,
       COALESCE(p.mysql_refunded_amount, 0) AS mysql_refunded_amount,
       COALESCE(a.astra_refunded_amount, 0) AS astra_refunded_amount,
       COALESCE(p.mysql_refunded_amount, 0) - COALESCE(a.astra_refunded_amount, 0)
           AS refunded_delta,
       CASE WHEN (COALESCE(a.astra_payment_count, 0) + COALESCE(a.astra_refund_count, 0) > 0) AND COALESCE(i.gross_mismatch_amount, 0) <= 0.01
                  AND COALESCE(i.net_mismatch_amount, 0) <= 0.01
                  AND COALESCE(p.mysql_payment_count, 0) = COALESCE(a.astra_payment_count, 0)
                  AND ABS(COALESCE(p.mysql_collected_amount, 0) - COALESCE(a.astra_collected_amount, 0)) <= 0.01
                  AND COALESCE(p.mysql_refund_count, 0) = COALESCE(a.astra_refund_count, 0)
                  AND ABS(COALESCE(p.mysql_refunded_amount, 0) - COALESCE(a.astra_refunded_amount, 0)) <= 0.01
            THEN 'PASS' ELSE 'CHECK' END AS quality_status
FROM days d
LEFT JOIN mysql_orders o ON o.event_date = d.event_date
LEFT JOIN mysql_items i ON i.event_date = d.event_date
LEFT JOIN mysql_payments p ON p.event_date = d.event_date
LEFT JOIN astra_daily a ON a.event_date = d.event_date
ORDER BY d.event_date
;
