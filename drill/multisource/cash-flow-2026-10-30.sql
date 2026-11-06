-- Verified via running backend; Vietnam business date 2026-10-30
SELECT CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE) AS event_date,
       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS collected_amount,
       SUM(CASE WHEN e.event_type = 'REFUND_CONFIRMED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS refunded_amount,
       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2))
                WHEN e.event_type = 'REFUND_CONFIRMED' THEN -CAST(e.amount AS DECIMAL(18, 2))
                ELSE 0 END) AS net_cash_amount
 FROM (SELECT CAST(TO_TIMESTAMP(CAST(event_date AS BIGINT) * 86400) AS DATE) AS event_date,
       CAST(event_type AS VARCHAR) AS event_type,
       CAST(amount AS DECIMAL(18, 2)) AS amount,
       CAST(order_id AS BIGINT) AS order_id,
       TO_TIMESTAMP(CAST(event_time AS DOUBLE) / 1000.0) AS event_time
FROM cassandra_source.`supermarket_analytics`.business_events_by_day
WHERE CAST(event_date AS BIGINT) >= 20755 AND CAST(event_date AS BIGINT) <= 20756
) e
 JOIN mysql_source.`supermarket_api`.sales_orders o
   ON CAST(e.order_id AS BIGINT) = o.order_id AND o.order_type = 'IN_STORE'
 WHERE e.event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED') AND e.event_time >= TIMESTAMP '2026-10-29 17:00:00' AND e.event_time < TIMESTAMP '2026-10-30 17:00:00'
GROUP BY CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE)
ORDER BY event_date
;
