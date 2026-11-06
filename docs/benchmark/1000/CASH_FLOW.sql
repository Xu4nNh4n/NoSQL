SELECT CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE) AS event_date,
       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS collected_amount,
       SUM(CASE WHEN e.event_type = 'REFUND_CONFIRMED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS refunded_amount,
       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2))
                WHEN e.event_type = 'REFUND_CONFIRMED' THEN -CAST(e.amount AS DECIMAL(18, 2))
                ELSE 0 END) AS net_cash_amount
 FROM (SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-09-30.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-01.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-02.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-03.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-04.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-05.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-06.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-07.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-08.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-09.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-10.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-11.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-12.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-13.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-14.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-15.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-16.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-17.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-18.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-19.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-20.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-21.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-22.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-23.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-24.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-25.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-26.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-27.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-28.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
 UNION ALL SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount, CAST(order_id AS BIGINT) AS order_id, CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time FROM (
    SELECT event_date, event_type, amount, event_id, order_id, event_time,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM analytics.cassandra.`events-2026-10-29.json`
) raw
WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
) e
 JOIN mysql_source.supermarket_api.sales_orders o
   ON CAST(e.order_id AS BIGINT) = o.order_id AND o.order_type = 'IN_STORE'
 WHERE e.event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED') AND e.event_time >= TIMESTAMP '2026-09-29 17:00:00' AND e.event_time < TIMESTAMP '2026-10-29 17:00:00'
GROUP BY CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE)
ORDER BY event_date
