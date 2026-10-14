#!/usr/bin/env python3
"""Create a deterministic, isolated supermarket dataset for Drill demonstrations.

This generator writes files only. It never connects to MySQL, MongoDB or Astra.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import random
import uuid
from collections import defaultdict
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
from pathlib import Path

AS_OF = date(2026, 10, 14)
START = AS_OF - timedelta(days=179)
SEED = 421014
NAMES = ["Gạo thơm", "Nước suối", "Sữa tươi", "Mì ăn liền", "Dầu ăn", "Nước rửa chén", "Táo đỏ", "Bánh quy", "Cà phê", "Giấy vệ sinh"]


def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float, Decimal)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "\\'") + "'"


def insert(table, columns, rows):
    if not rows:
        return ""
    lines = []
    names = ", ".join(f"`{name}`" for name in columns)
    for start in range(0, len(rows), 500):
        chunk = rows[start:start + 500]
        values = ",\n".join("(" + ", ".join(sql(v) for v in row) + ")" for row in chunk)
        lines.append(f"INSERT INTO `{table}` ({names}) VALUES\n{values};")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--events", type=int, default=100_000, help="Mục tiêu xấp xỉ; manifest ghi số thực tế.")
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--output", type=Path, default=Path("analytics/simulation"))
    args = parser.parse_args()
    if args.events < 10_000 or args.events > 5_000_000:
        parser.error("--events phải trong khoảng 10.000 đến 5.000.000")

    rng = random.Random(args.seed)
    dataset_id = f"sim-{args.seed}-{args.events}-{AS_OF.isoformat()}"
    output = args.output.resolve() / dataset_id
    output.mkdir(parents=True, exist_ok=True)
    event_dir = output / "events"
    event_dir.mkdir(exist_ok=True)

    # Approximately one order, 2.5 product lines and one payment event per 4.5 events.
    order_count = max(2_500, round(args.events / 4.5))
    products = []
    for product_id in range(1, 201):
        group_id = (product_id - 1) % len(NAMES) + 1
        price = rng.choice([6500, 12000, 18000, 25000, 32000, 45000, 69000, 99000, 145000, 220000])
        products.append((product_id, str(product_id), f"{NAMES[group_id - 1]} mô phỏng {product_id:03}", price, group_id, rng.randint(8, 30)))

    # Popular products follow a stable long-tail distribution; weekends and evening hours are busier.
    weights = [1 / (rank ** 0.82) for rank in range(1, len(products) + 1)]
    population = list(range(len(products)))
    orders, items, payment_rows, return_rows, return_item_rows = [], [], [], [], []
    returns_by_product = defaultdict(int)
    events = defaultdict(list)
    next_item_id = next_return_id = 1
    payment_id = 1

    def add_event(at, event_type, order_id=None, sku=None, quantity=0, amount=0, method=None, status="COMPLETED", metadata=None):
        event_uuid = uuid.uuid5(uuid.NAMESPACE_URL, f"{dataset_id}:{len(events[at.date()])}:{event_type}:{order_id}:{sku}:{at.isoformat()}")
        events[at.date()].append({
            "event_date": at.date().isoformat(), "event_time": at.isoformat(timespec="seconds"),
            "event_id": str(event_uuid), "event_type": event_type, "order_id": order_id,
            "product_sku": sku, "quantity": quantity, "amount": str(Decimal(str(amount)).quantize(Decimal("0.01"))),
            "payment_method": method, "status": status, "metadata": json.dumps(metadata or {"source": "SIMULATED", "dataset": dataset_id}, ensure_ascii=False, separators=(",", ":"))
        })

    for order_id in range(1, order_count + 1):
        offset = rng.randrange(179)
        day = START + timedelta(days=offset)
        if day.weekday() >= 5 and rng.random() < 0.70:
            day = min(AS_OF - timedelta(days=1), day + timedelta(days=1))
        hour = rng.choices([9, 12, 17, 19, 21], weights=[1, 2, 2, 4, 2])[0]
        created = datetime.combine(day, time(hour, rng.randrange(60), rng.randrange(60)), tzinfo=timezone.utc)
        order_type = "IN_STORE" if rng.random() < 0.42 else "ONLINE"
        method = "CASH" if order_type == "IN_STORE" else ("COD" if rng.random() < 0.58 else "BANK_TRANSFER")
        cancelled = order_type == "ONLINE" and rng.random() < 0.05
        lines = []
        for _ in range(rng.choices([1, 2, 3, 4, 5], weights=[10, 28, 34, 20, 8])[0]):
            index = rng.choices(population, weights=weights)[0]
            product = products[index]
            quantity = rng.choices([1, 2, 3, 4, 5], weights=[48, 26, 14, 8, 4])[0]
            returned = 0 if cancelled or rng.random() >= 0.045 else rng.randint(1, quantity)
            lines.append((product, quantity, returned))
        subtotal = sum(product[3] * qty for product, qty, _ in lines)
        returned_total = sum(line[2] for line in lines)
        if cancelled:
            status, payment_status = "CANCELLED", "CANCELLED"
        elif returned_total >= sum(line[1] for line in lines):
            status, payment_status = "RETURNED", "REFUNDED"
        elif returned_total:
            status, payment_status = "PARTIALLY_RETURNED", "PAID"
        else:
            status, payment_status = ("PAID" if order_type == "IN_STORE" else "DELIVERED"), "PAID"
        delivery_status = "CANCELLED" if cancelled else ("NOT_REQUIRED" if order_type == "IN_STORE" else "DELIVERED")
        orders.append((order_id, str(order_id), created.strftime("%Y-%m-%d %H:%M:%S"), order_type, method, status, payment_status, delivery_status, subtotal, subtotal))
        add_event(created, "ORDER_PLACED", order_id, status=status, method=method, amount=subtotal)
        order_line_ids = []
        for product, quantity, returned in lines:
            item_id = next_item_id
            next_item_id += 1
            items.append((item_id, order_id, product[0], quantity, returned, product[3], product[3] * quantity))
            order_line_ids.append((product, quantity, returned, item_id))
            if not cancelled:
                add_event(created, "ITEM_SOLD", order_id, product[1], quantity - returned, product[3] * (quantity - returned), method, status,
                          {"source": "SIMULATED", "dataset": dataset_id})
                if returned:
                    returns_by_product[product[0]] += returned
        if not cancelled:
            payment_at = created + (timedelta(minutes=20) if method == "COD" else timedelta(minutes=2))
            reference = f"SIM-PAY-{order_id}"
            payment_rows.append((payment_id, order_id, "PAYMENT", reference, subtotal, method, payment_at.strftime("%Y-%m-%d %H:%M:%S")))
            payment_id += 1
            add_event(payment_at, "PAYMENT_RECEIVED", order_id, quantity=0, amount=subtotal, method=method, status="COMPLETED")
            returned_lines = [(product, qty, returned, item_id) for product, qty, returned, item_id in order_line_ids if returned]
            if returned_lines:
                refund = sum(product[3] * returned for product, _, returned, _ in returned_lines)
                return_id = next_return_id
                next_return_id += 1
                refund_at = payment_at + timedelta(days=1)
                return_rows.append((return_id, order_id, f"SIM-RETURN-{order_id}", refund, "REFUNDED", payment_at.strftime("%Y-%m-%d %H:%M:%S"), refund_at.strftime("%Y-%m-%d %H:%M:%S")))
                for product, quantity, returned, item_id in returned_lines:
                    return_item_rows.append((len(return_item_rows) + 1, return_id, item_id, product[0], returned, 1, "Mô phỏng: trả hàng bán được"))
                payment_rows.append((payment_id, order_id, "REFUND", f"SIM-REFUND-{order_id}", refund, method, refund_at.strftime("%Y-%m-%d %H:%M:%S")))
                payment_id += 1
                add_event(payment_at + timedelta(days=1), "REFUND_CONFIRMED", order_id, amount=refund, method=method, status="COMPLETED")

    # Two lots per SKU: one FEFO lot near expiry plus a newer reserve lot. All completed lines
    # allocate to the earliest-expiry batch; canceled orders keep no allocation or stock debit.
    batch_rows, allocation_rows = [], []
    batch_id = 1
    units_sold = defaultdict(int)
    for row in items:
        if orders[row[1] - 1][5] != "CANCELLED":
            units_sold[row[2]] += row[3]
    return_per_product = returns_by_product
    near_days = [3, 7, 14, 30]
    product_by_id = {p[0]: p for p in products}
    near_batch_by_product = {}
    for product_id in range(1, 201):
        product = product_by_id[product_id]
        near_remaining = rng.randint(12, 45)
        expiry = AS_OF + timedelta(days=rng.choice(near_days))
        received = units_sold[product_id] + near_remaining - return_per_product[product_id]
        unit_cost = round(product[3] * rng.uniform(0.55, 0.82), 2)
        batch_rows.append((batch_id, product_id, 1, str(batch_id), received, near_remaining, 0, 0, unit_cost,
                           (START + timedelta(days=5)).isoformat(), expiry.isoformat(), (START + timedelta(days=5)).strftime("%Y-%m-%d %H:%M:%S"), "SIMULATED", "ACTIVE"))
        received_at = datetime.combine(START + timedelta(days=5), time(9, 0), tzinfo=timezone.utc)
        add_event(received_at, "INVENTORY_RECEIVED", sku=product[1], quantity=received, amount=unit_cost * received,
                  metadata={"source": "SIMULATED", "dataset": dataset_id, "batchCode": str(batch_id)})
        near_batch_by_product[product_id] = batch_id
        batch_id += 1
        batch_rows.append((batch_id, product_id, 1, str(batch_id), 100, 100, 0, 0, unit_cost,
                           (START + timedelta(days=95)).isoformat(), (AS_OF + timedelta(days=180)).isoformat(), (START + timedelta(days=95)).strftime("%Y-%m-%d %H:%M:%S"), "SIMULATED", "ACTIVE"))
        received_at = datetime.combine(START + timedelta(days=95), time(10, 0), tzinfo=timezone.utc)
        add_event(received_at, "INVENTORY_RECEIVED", sku=product[1], quantity=100, amount=unit_cost * 100,
                  metadata={"source": "SIMULATED", "dataset": dataset_id, "batchCode": str(batch_id)})
        batch_id += 1

    for row in items:
        item_id, order_id, product_id, quantity, returned, unit_price, subtotal = row
        if orders[order_id - 1][5] == "CANCELLED":
            continue
        allocation_rows.append((len(allocation_rows) + 1, item_id, near_batch_by_product[product_id], quantity, returned, "CONSUMED"))

    return_item_rows = [(return_item_id, return_id, order_item_id, near_batch_by_product[product_id], quantity, restock, reason)
                        for return_item_id, return_id, order_item_id, product_id, quantity, restock, reason in return_item_rows]

    category_rows = [(i, name) for i, name in enumerate(NAMES, 1)]
    supplier_rows = [(1, "Nhà cung cấp mô phỏng")]
    catalog_path = output / "products_catalog.jsonl"
    with catalog_path.open("w", encoding="utf-8", newline="\n") as f:
        for product in products:
            product_id, sku, name, price, group_id, reorder = product
            doc = {"_id": sku, "sku": sku, "name": name, "price": {"$numberDecimal": f"{price:.2f}"},
                   "categoryName": NAMES[group_id - 1], "supplierName": "Nhà cung cấp mô phỏng", "active": True,
                   "attributes": {"brand": "Nhãn mô phỏng", "categoryCode": str(group_id), "source": "SIMULATED"}}
            f.write(json.dumps(doc, ensure_ascii=False, separators=(",", ":")) + "\n")

    for event_day, day_events in events.items():
        with (event_dir / f"events-{event_day.isoformat()}.json").open("w", encoding="utf-8", newline="\n") as f:
            json.dump(day_events, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")

    sql_parts = ["""-- Generated simulation only; never points at supermarket_api.
CREATE DATABASE IF NOT EXISTS supermarket_sim CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE supermarket_sim;
SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS batch_allocations, sales_order_return_items, sales_order_returns, payment_records, sales_order_items, sales_orders, inventory_batches, products, categories, suppliers;
CREATE TABLE categories (category_id BIGINT PRIMARY KEY, category_name VARCHAR(100) NOT NULL);
CREATE TABLE suppliers (supplier_id BIGINT PRIMARY KEY, supplier_name VARCHAR(150) NOT NULL);
CREATE TABLE products (product_id BIGINT PRIMARY KEY, sku_code VARCHAR(50) NOT NULL, name VARCHAR(150) NOT NULL, price DECIMAL(18,2) NOT NULL, category_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL, stock_quantity INT NOT NULL DEFAULT 0, reserved_quantity INT NOT NULL DEFAULT 0, damaged_quantity INT NOT NULL DEFAULT 0, reorder_point INT NOT NULL, is_active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE sales_orders (order_id BIGINT PRIMARY KEY, order_code VARCHAR(50) NOT NULL, created_at TIMESTAMP NOT NULL, order_type VARCHAR(20) NOT NULL, payment_method VARCHAR(50) NOT NULL, status VARCHAR(50) NOT NULL, payment_status VARCHAR(30) NOT NULL, delivery_status VARCHAR(30) NOT NULL, subtotal DECIMAL(18,2) NOT NULL, total_amount DECIMAL(18,2) NOT NULL, discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0, inventory_released BOOLEAN NOT NULL DEFAULT FALSE, inventory_deducted BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE sales_order_items (order_item_id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL, returned_quantity INT NOT NULL DEFAULT 0, unit_price DECIMAL(18,2) NOT NULL, subtotal DECIMAL(18,2) NOT NULL);
CREATE TABLE inventory_batches (batch_id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL, supplier_id BIGINT, batch_code VARCHAR(100) NOT NULL, received_quantity INT NOT NULL, remaining_quantity INT NOT NULL, reserved_quantity INT NOT NULL, damaged_quantity INT NOT NULL, unit_cost DECIMAL(18,2) NOT NULL, manufacturing_date DATE, expiry_date DATE, received_at TIMESTAMP NOT NULL, received_by VARCHAR(100) NOT NULL, status VARCHAR(30) NOT NULL);
CREATE TABLE batch_allocations (allocation_id BIGINT PRIMARY KEY, order_item_id BIGINT NOT NULL, batch_id BIGINT NOT NULL, quantity INT NOT NULL, returned_quantity INT NOT NULL, status VARCHAR(20) NOT NULL);
CREATE TABLE payment_records (payment_record_id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, event_type VARCHAR(20) NOT NULL, reference VARCHAR(100) NOT NULL, amount DECIMAL(18,2) NOT NULL, payment_method VARCHAR(50) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE sales_order_returns (return_id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, request_key VARCHAR(100) NOT NULL, refund_amount DECIMAL(18,2) NOT NULL, refund_status VARCHAR(30) NOT NULL, created_at TIMESTAMP NOT NULL, refunded_at TIMESTAMP);
CREATE TABLE sales_order_return_items (return_item_id BIGINT PRIMARY KEY, return_id BIGINT NOT NULL, order_item_id BIGINT NOT NULL, batch_id BIGINT, quantity INT NOT NULL, restock BOOLEAN NOT NULL, reason VARCHAR(255) NOT NULL);
"""]
    sql_parts += [insert("categories", ["category_id", "category_name"], category_rows),
                  insert("suppliers", ["supplier_id", "supplier_name"], supplier_rows),
                  insert("products", ["product_id", "sku_code", "name", "price", "category_id", "supplier_id", "stock_quantity", "reserved_quantity", "damaged_quantity", "reorder_point", "is_active"],
                         [(p[0], p[1], p[2], p[3], p[4], 1, 220, 0, 0, p[5], True) for p in products]),
                  insert("sales_orders", ["order_id", "order_code", "created_at", "order_type", "payment_method", "status", "payment_status", "delivery_status", "subtotal", "total_amount", "discount_amount", "inventory_released", "inventory_deducted"],
                         [r + (0, r[5] == "CANCELLED", r[5] != "CANCELLED") for r in orders]),
                  insert("sales_order_items", ["order_item_id", "order_id", "product_id", "quantity", "returned_quantity", "unit_price", "subtotal"], items),
                  insert("inventory_batches", ["batch_id", "product_id", "supplier_id", "batch_code", "received_quantity", "remaining_quantity", "reserved_quantity", "damaged_quantity", "unit_cost", "manufacturing_date", "expiry_date", "received_at", "received_by", "status"], batch_rows),
                  insert("batch_allocations", ["allocation_id", "order_item_id", "batch_id", "quantity", "returned_quantity", "status"], allocation_rows),
                  insert("payment_records", ["payment_record_id", "order_id", "event_type", "reference", "amount", "payment_method", "created_at"], payment_rows),
                  insert("sales_order_returns", ["return_id", "order_id", "request_key", "refund_amount", "refund_status", "created_at", "refunded_at"], return_rows),
                  insert("sales_order_return_items", ["return_item_id", "return_id", "order_item_id", "batch_id", "quantity", "restock", "reason"], return_item_rows),
                  "SET FOREIGN_KEY_CHECKS=1;"]
    sql_text = "\n\n".join(part for part in sql_parts if part)
    sql_path = output / "mysql-load.sql"
    sql_path.write_text(sql_text + "\n", encoding="utf-8")

    files = [catalog_path, sql_path, *event_dir.glob("*.json")]
    manifest = {
        "source": "SIMULATED", "datasetId": dataset_id, "seed": args.seed, "asOfUtc": AS_OF.isoformat(),
        "periodStartUtc": START.isoformat(), "transactionEndUtc": (AS_OF - timedelta(days=1)).isoformat(),
        "eventEndUtc": AS_OF.isoformat(),
        "requestedEventsApprox": args.events, "actualEvents": sum(map(len, events.values())),
        "orders": len(orders), "orderLines": len(items), "products": len(products), "categories": len(category_rows),
        "cancelledOrders": sum(1 for row in orders if row[5] == "CANCELLED"), "returns": len(return_rows),
        "eventFiles": len(events), "outputs": {str(path.relative_to(output)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
        "notes": ["Synthetic records; not live customer or sales data.", "SQL recreates only supermarket_sim and contains DROP TABLE for that schema.",
                  "Mongo JSON uses string _id; it does not write to Atlas.", "Events are local JSON and are not uploaded to Astra.",
                  "Price stored on order lines is the sale-time price; promotions are not modeled."]
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"dataset": dataset_id, "directory": str(output), "orders": len(orders), "items": len(items),
                      "actualEvents": manifest["actualEvents"], "eventFiles": len(events), "manifest": str(output / "manifest.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
