-- MANUAL ONLY. Select the existing operational keyspace matching astra.keyspace before running.
-- This file neither creates a keyspace nor touches simulation data. The application never runs DDL.
-- Admin trigger: POST /api/integrations/cassandra/stock-snapshots?from=YYYY-MM-DD&to=YYYY-MM-DD
-- Inclusive interval: at most 31 completed dates in Asia/Ho_Chi_Minh (UTC+07).
-- No scheduler. MySQL source reads are read-only in a repeatable-read transaction.
-- remaining_quantity is historical end-of-day SUM(batch.remaining_quantity), reconstructed
-- from the ledger, not usable stock: expired/inactive batches are included; damaged is excluded.
-- opening = start-of-day balance; received includes OPENING and RECEIPT movements.
-- SALE quantity is already negative: sold stores its positive magnitude. RETURN adds remaining.
-- ADJUSTMENT preserves its signed quantity. RETURN_DAMAGED contributes zero to remaining stock.
-- closing = opening + received - sold + returned + adjustment_delta = remaining_quantity.
-- All history is reconciled to current physical batch remaining before any writes.
-- Missing opening/receipt evidence, unknown types (including legacy RESERVE/RELEASE),
-- invalid signs, negative daily balances, future movements or unexplained balances reject
-- the entire request (HTTP 412 with per-product reasons and written=0). Dates before first known opening/receipt are omitted, never invented.
-- Products with zero remaining and no movements are omitted (omittedUnstockedProducts).
-- Damaged-return-only history still needs a known opening/receipt; no zero baseline is invented.
-- MySQL stock_movements.created_at must be TIMESTAMP (as in SQL/schema.sql).
-- UNIX_TIMESTAMP + 25200 uses UTC epoch bounds for Vietnam days, independent of session zone.
-- Reconciliation cannot prove a missing/edited ledger whose errors cancel out, nor intraday
-- provenance: this is a daily aggregate, not a historical lot/expiry/FEFO reconstruction.
-- Cassandra upserts are idempotent by date/product, but a write failure may leave partial rows.
-- Retry the same interval after resolving failure. No deletes or cross-database transaction.
-- generated_at identifies the MySQL read cut, not the Cassandra completion time.
-- Concurrent requests are serialized only inside one app process; coordinate multiple instances.
CREATE TABLE IF NOT EXISTS stock_daily_by_day (
    snapshot_date date,
    product_id bigint,
    remaining_quantity bigint,
    opening bigint,
    received bigint,
    sold bigint,
    returned bigint,
    adjustment_delta bigint,
    closing bigint,
    generated_at timestamp,
    PRIMARY KEY ((snapshot_date), product_id)
) WITH CLUSTERING ORDER BY (product_id ASC);
