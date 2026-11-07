# Sao lưu và khôi phục Cassandra (Astra DB)

Sổ cái Cassandra chỉ thêm, ghi từ MongoDB qua `sync_queue`. Nếu mất sổ cái, có thể ghi lại từ payload của `sync_queue` (đặt lại các bản ghi về `PENDING`); cách dưới đây là sao lưu trực tiếp.

## Astra DB

Gói Free không có snapshot tự động cho người dùng. Dùng [DSBulk](https://docs.datastax.com/en/dsbulk/) với secure bundle:

```bash
dsbulk unload -b secrets/secure-connect-supermarket-bigdata.zip -u token -p "$APPLICATION_TOKEN" \
  -k supermarket -t stock_movements_by_product -url backup/stock_movements
dsbulk unload -b secrets/secure-connect-supermarket-bigdata.zip -u token -p "$APPLICATION_TOKEN" \
  -k supermarket -t payments_by_order -url backup/payments_by_order
dsbulk unload -b secrets/secure-connect-supermarket-bigdata.zip -u token -p "$APPLICATION_TOKEN" \
  -k supermarket -t payments_by_cashier_day -url backup/payments_by_cashier_day
```

Khôi phục: đổi `unload` thành `load`. Vì khóa chính xác định (`movement_id`, `payment_id` tạo từ nội dung), nạp lại nhiều lần không sinh dòng trùng.

## cqlsh (Cassandra cài máy hoặc Astra CQL Console)

```sql
COPY supermarket.payments_by_order TO 'backup/payments_by_order.csv' WITH HEADER = TRUE;
COPY supermarket.payments_by_order FROM 'backup/payments_by_order.csv' WITH HEADER = TRUE;
```

`login_history_by_user` có TTL 90 ngày nên thường không cần sao lưu.
