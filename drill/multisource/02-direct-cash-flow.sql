-- Read-only direct report example; NOT executed. Defaults and dates as in 01-source-checks.sql.
WITH events AS (
    SELECT CAST(event_time AS TIMESTAMP) AS event_time,
           CAST(order_id AS BIGINT) AS order_id,
           CAST(event_type AS VARCHAR) AS event_type,
           CAST(amount AS DECIMAL(18,2)) AS amount
    FROM cassandra_source.supermarket_analytics.business_events_by_day
    WHERE event_date >= DATE '2026-10-29' AND event_date <= DATE '2026-10-30'
)
SELECT CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE) AS event_date,
       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN e.amount ELSE 0 END) AS collected_amount,
       SUM(CASE WHEN e.event_type = 'REFUND_CONFIRMED' THEN e.amount ELSE 0 END) AS refunded_amount
FROM events e
JOIN mysql_source.supermarket_api.sales_orders o ON o.order_id = e.order_id
WHERE o.order_type = 'IN_STORE'
  AND e.event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED')
  AND e.event_time >= TIMESTAMP '2026-10-29 17:00:00'
  AND e.event_time < TIMESTAMP '2026-10-30 17:00:00'
GROUP BY CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE)
ORDER BY event_date;
