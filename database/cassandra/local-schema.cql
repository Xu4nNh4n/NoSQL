-- Một node Docker: dữ liệu vận hành và mô phỏng ở hai keyspace riêng.
CREATE KEYSPACE IF NOT EXISTS supermarket_analytics
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
CREATE KEYSPACE IF NOT EXISTS supermarket_sim
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS supermarket_analytics.business_events_by_day (
    event_date date, event_time timestamp, event_id uuid, event_type text,
    order_id bigint, product_sku text, quantity int, amount decimal,
    payment_method text, status text, metadata text,
    PRIMARY KEY ((event_date), event_time, event_id)
) WITH CLUSTERING ORDER BY (event_time DESC);

CREATE TABLE IF NOT EXISTS supermarket_sim.business_events_by_day (
    event_date date, event_time timestamp, event_id uuid, event_type text,
    order_id bigint, product_sku text, quantity int, amount decimal,
    payment_method text, status text, metadata text,
    PRIMARY KEY ((event_date), event_time, event_id)
) WITH CLUSTERING ORDER BY (event_time DESC);
