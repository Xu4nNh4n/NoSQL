"""Phân bổ lịch sử 30 ngày (2026-10-01 đến 2026-10-30) cho bộ dữ liệu 108 hóa đơn POS.

Tuân thủ nghiêm ngặt:
- 108 đơn được phân bổ đều trên 30 ngày Việt Nam (giờ mở cửa 08:30 - 21:00).
- Phần lớn đơn hàng (~85%) là khách vãng lai không tên (customer_name = NULL, customer_phone = NULL)
  theo đúng đặc thù siêu thị bán lẻ và yêu cầu của người dùng.
- Thứ tự thời gian: Lô hàng nhập -> Bán hàng -> Trả hàng -> Hoàn tiền.
- Đồng bộ nhất quán MySQL - Cassandra - POS Payment JSON logs.
- Sinh snapshot kho Cassandra stock_daily_by_day cho 29 ngày đã kết thúc.
- Kiểm tra đối soát Reconciliation qua Drill đạt chuẩn PASS.
"""
import argparse
import collections
import datetime as dt
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
VN_TZ = dt.timezone(dt.timedelta(hours=7))
UTC_TZ = dt.timezone.utc


def parse_env():
    config = {}
    with open(ROOT / '.env', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if '=' in line and not line.startswith('#'):
                k, v = line.split('=', 1)
                config[k.strip()] = v.strip().strip('"').strip("'")
    return config


def run_mysql_query(sql, config):
    env = os.environ.copy()
    env['MYSQL_PWD'] = config.get('DB_PASSWORD', '')
    user = config.get('DB_USERNAME', 'root')
    cmd = [
        r'D:\MySQL\Server84\bin\mysql.exe',
        '--host=127.0.0.1',
        f'--user={user}',
        '--default-character-set=utf8mb4',
        '--batch',
        '--raw',
        '--skip-column-names',
        'supermarket_api'
    ]
    proc = subprocess.run(cmd, input="SET time_zone='+00:00';\n" + sql, capture_output=True, text=True, encoding='utf-8', env=env)
    if proc.returncode != 0:
        raise RuntimeError(f"MySQL error: {proc.stderr}")
    return proc.stdout


def run_cql(cql_text):
    proc = subprocess.run(
        ['docker', 'exec', '-i', 'supermarket-cassandra', 'cqlsh'],
        input=cql_text,
        capture_output=True,
        text=True,
        encoding='utf-8'
    )
    if proc.returncode != 0:
        raise RuntimeError(f"Cassandra error: {proc.stderr}")
    return proc.stdout


def canonical_json(obj):
    if isinstance(obj, dict):
        return {k: canonical_json(obj[k]) for k in sorted(obj.keys())}
    if isinstance(obj, list):
        return [canonical_json(x) for x in obj]
    return obj


def compute_sha256(payload_dict):
    canonical = canonical_json(payload_dict)
    raw = json.dumps(canonical, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    return hashlib.sha256(raw).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Thực hiện cập nhật dữ liệu')
    parser.add_argument('--start-date', default='2026-10-01', help='Ngày bắt đầu lịch sử (YYYY-MM-DD)')
    parser.add_argument('--end-date', default='2026-10-30', help='Ngày kết thúc lịch sử (YYYY-MM-DD)')
    args = parser.parse_args()

    config = parse_env()
    start_d = dt.date.fromisoformat(args.start_date)
    end_d = dt.date.fromisoformat(args.end_date)
    total_days = (end_d - start_d).days + 1

    print(f"=== Antigravity 30-Day POS History Redistribution ===")
    print(f"Khoảng ngày: {start_d} đến {end_d} ({total_days} ngày)")
    print(f"Chế độ: {'APPLY (Thực thi)' if args.apply else 'DRY-RUN (Chỉ kiểm tra)'}")

    # 1. Đọc toàn bộ bảng từ MySQL
    print("\n1. Đang đọc dữ liệu hiện có từ MySQL...")
    orders_raw = run_mysql_query("SELECT order_id, customer_name, customer_phone FROM sales_orders ORDER BY order_id;", config)
    order_rows = []
    for line in orders_raw.strip().splitlines():
        parts = line.split('\t')
        order_rows.append({'order_id': int(parts[0]), 'name': parts[1] if len(parts) > 1 and parts[1] != 'NULL' else None})
    print(f"-> Tìm thấy {len(order_rows)} hóa đơn POS.")

    returns_raw = run_mysql_query("SELECT return_id, order_id, refund_status FROM sales_order_returns ORDER BY return_id;", config)
    return_rows = []
    for line in returns_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        return_rows.append({'return_id': int(parts[0]), 'order_id': int(parts[1]), 'refund_status': parts[2]})
    print(f"-> Tìm thấy {len(return_rows)} phiếu trả hàng.")

    batches_raw = run_mysql_query("SELECT batch_id, product_id, batch_code, manufacturing_date FROM inventory_batches ORDER BY batch_id;", config)
    batch_rows = []
    for line in batches_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        batch_rows.append({'batch_id': int(parts[0]), 'product_id': int(parts[1]), 'batch_code': parts[2], 'mfg': parts[3]})
    print(f"-> Tìm thấy {len(batch_rows)} lô kho.")

    # 2. Xây dựng ánh xạ thời gian (Time Mapping)
    # 108 đơn phân bổ đều vào 30 ngày (ngày 0 đến 29)
    order_time_map = {} # order_id -> dt.datetime (in UTC)
    order_day_map = {}
    orders_per_day = collections.defaultdict(list)

    for i, row in enumerate(order_rows):
        day_idx = min(total_days - 1, (i * total_days) // len(order_rows))
        cur_date = start_d + dt.timedelta(days=day_idx)
        orders_per_day[cur_date].append(row)

    surnames = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi']
    names = ['Văn Minh', 'Thị Lan', 'Quốc Huy', 'Thu Hà', 'Minh Anh', 'Thanh Bình']

    order_updates = []
    for cur_date, ords in orders_per_day.items():
        n = len(ords)
        for j, ord_info in enumerate(ords):
            oid = ord_info['order_id']
            # Giờ mở cửa: ngày hôm nay kết thúc trước 16:30 để không vượt CURRENT_TIMESTAMP (FUTURE_MOVEMENT)
            duration_hours = 8 if cur_date == end_d else 12
            minute_offset = int((j + 0.5) * (duration_hours * 60) / max(1, n))
            vn_dt = dt.datetime.combine(cur_date, dt.time(8, 30), tzinfo=VN_TZ) + dt.timedelta(minutes=minute_offset)
            utc_dt = vn_dt.astimezone(UTC_TZ)
            order_time_map[oid] = utc_dt
            order_day_map[oid] = cur_date

            # Quy định tên khách hàng: ~85% khách vãng lai không tên (NULL)
            # Chỉ khoảng 15% khách có tên
            if oid % 7 == 0:
                cname = f"{surnames[(oid // 7) % len(surnames)]} {names[(oid // 7) % len(names)]}"
                cphone = f"09{10000000 + oid * 37 % 90000000}"
            else:
                cname = 'NULL'
                cphone = 'NULL'

            order_updates.append((oid, utc_dt, cname, cphone))

    print(f"-> Đã lập lịch 108 đơn hàng từ {start_d} đến {end_d} (trung bình {len(order_rows)/total_days:.1f} đơn/ngày).")
    named_count = sum(1 for _, _, cname, _ in order_updates if cname != 'NULL')
    print(f"-> Tỷ lệ khách hàng: {named_count}/{len(order_rows)} có tên ({named_count*100/len(order_rows):.1f}%), còn lại là khách vãng lai.")

    # Ánh xạ thời gian cho returns và refunds
    return_time_map = {} # return_id -> (created_utc, refunded_utc or None)
    for ret in return_rows:
        rid = ret['return_id']
        oid = ret['order_id']
        ord_utc = order_time_map[oid]
        # Return xảy ra sau khi mua hàng 15-30 phút (đặc biệt trong ngày hiện tại không vượt CURRENT_TIMESTAMP)
        ret_utc = ord_utc + dt.timedelta(minutes=15)
        ref_utc = ret_utc + dt.timedelta(minutes=5) if ret['refund_status'] == 'REFUNDED' else None
        return_time_map[rid] = (ret_utc, ref_utc)

    # Ánh xạ thời gian cho lô hàng: nhập kho trước đơn đầu tiên (2026-09-30)
    batch_time_map = {} # batch_id -> received_utc
    batch_mfg_map = {}
    base_rcv_vn = dt.datetime.combine(start_d - dt.timedelta(days=1), dt.time(8, 0), tzinfo=VN_TZ)
    for b in batch_rows:
        bid = b['batch_id']
        # Lô bổ sung (batch_id 25..30) nhập ngày 2026-10-15
        if bid >= 25:
            rcv_vn = dt.datetime.combine(start_d + dt.timedelta(days=14), dt.time(8, 0), tzinfo=VN_TZ)
        else:
            rcv_vn = base_rcv_vn + dt.timedelta(minutes=bid * 5)
        rcv_utc = rcv_vn.astimezone(UTC_TZ)
        batch_time_map[bid] = rcv_utc
        # Manufacturing date: 15 ngày trước khi nhận
        mfg_date = (rcv_vn.date() - dt.timedelta(days=15)).isoformat()
        batch_mfg_map[bid] = mfg_date

    if not args.apply:
        print("\n[DRY-RUN] Kiểm tra kế hoạch thành công. Thêm --apply để áp dụng cập nhật.")
        return

    # Lấy admin token trước
    login_req = urllib.request.Request(
        'http://127.0.0.1:8080/api/auth/login',
        headers={'Content-Type': 'application/json'},
        data=json.dumps({'username': 'admin', 'password': config.get('SEED_ACCOUNT_PASSWORD', '123456')}).encode('utf-8')
    )
    with urllib.request.urlopen(login_req, timeout=10) as r:
        adm_tok = json.loads(r.read())['accessToken']

    # 3. Áp dụng cập nhật MySQL
    print("\n3. Đang cập nhật cơ sở dữ liệu MySQL...")
    sql_stmts = ["SET time_zone='+00:00';", "START TRANSACTION;"]

    # Cập nhật products created_at
    prod_time_str = (base_rcv_vn - dt.timedelta(hours=1)).astimezone(UTC_TZ).strftime('%Y-%m-%d %H:%M:%S')
    sql_stmts.append(f"UPDATE products SET created_at='{prod_time_str}', updated_at='{prod_time_str}';")

    # Cập nhật inventory_batches
    for bid, rcv_utc in batch_time_map.items():
        rcv_str = rcv_utc.strftime('%Y-%m-%d %H:%M:%S')
        mfg = batch_mfg_map[bid]
        sql_stmts.append(f"UPDATE inventory_batches SET received_at='{rcv_str}', manufacturing_date='{mfg}' WHERE batch_id={bid};")

    # Cập nhật promotions starts_at & ends_at bao trùm 30 ngày
    promo_start = (start_d - dt.timedelta(days=1)).isoformat() + " 00:00:00"
    promo_end = (end_d + dt.timedelta(days=30)).isoformat() + " 23:59:59"
    sql_stmts.append(f"UPDATE promotions SET starts_at='{promo_start}', ends_at='{promo_end}', is_active=1;")

    # Cập nhật sales_orders
    for oid, ord_utc, cname, cphone in order_updates:
        t_str = ord_utc.strftime('%Y-%m-%d %H:%M:%S')
        name_sql = f"'{cname}'" if cname != 'NULL' else "NULL"
        phone_sql = f"'{cphone}'" if cphone != 'NULL' else "NULL"
        sql_stmts.append(f"UPDATE sales_orders SET order_date='{t_str}', paid_at='{t_str}', created_at='{t_str}', updated_at='{t_str}', customer_name={name_sql}, customer_phone={phone_sql} WHERE order_id={oid};")

    # Cập nhật sales_order_returns
    for rid, (ret_utc, ref_utc) in return_time_map.items():
        ret_str = ret_utc.strftime('%Y-%m-%d %H:%M:%S')
        ref_str = f"'{ref_utc.strftime('%Y-%m-%d %H:%M:%S')}'" if ref_utc else "NULL"
        sql_stmts.append(f"UPDATE sales_order_returns SET created_at='{ret_str}', refunded_at={ref_str} WHERE return_id={rid};")

    # Cập nhật payment_records
    for oid, ord_utc, _, _ in order_updates:
        t_str = ord_utc.strftime('%Y-%m-%d %H:%M:%S')
        sql_stmts.append(f"UPDATE payment_records SET created_at='{t_str}' WHERE order_id={oid} AND event_type='PAYMENT';")
    for rid, (_, ref_utc) in return_time_map.items():
        if ref_utc:
            r_str = ref_utc.strftime('%Y-%m-%d %H:%M:%S')
            sql_stmts.append(f"UPDATE payment_records SET created_at='{r_str}' WHERE return_id={rid} AND event_type='REFUND';")

    # Cập nhật stock_movements
    # Lấy danh sách movements để map theo type
    movs_raw = run_mysql_query("SELECT movement_id, product_id, order_id, movement_type, note FROM stock_movements;", config)
    for line in movs_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        mid = int(parts[0])
        pid = int(parts[1])
        oid = int(parts[2]) if parts[2] != 'NULL' else None
        mtype = parts[3]
        if mtype == 'RECEIPT':
            # Nhập lô: gán theo thời gian nhận lô đầu tiên của sản phẩm
            rcv_utc = base_rcv_vn.astimezone(UTC_TZ)
            sql_stmts.append(f"UPDATE stock_movements SET created_at='{rcv_utc.strftime('%Y-%m-%d %H:%M:%S')}' WHERE movement_id={mid};")
        elif mtype == 'SALE' and oid:
            ord_utc = order_time_map[oid]
            sql_stmts.append(f"UPDATE stock_movements SET created_at='{ord_utc.strftime('%Y-%m-%d %H:%M:%S')}' WHERE movement_id={mid};")
        elif mtype in ('RETURN', 'RETURN_DAMAGED') and oid:
            ord_utc = order_time_map[oid]
            ret_utc = ord_utc + dt.timedelta(minutes=15)
            sql_stmts.append(f"UPDATE stock_movements SET created_at='{ret_utc.strftime('%Y-%m-%d %H:%M:%S')}' WHERE movement_id={mid};")

    sql_stmts.append("COMMIT;")
    run_mysql_query("\n".join(sql_stmts), config)
    print("-> Cập nhật MySQL thành công.")

    # 4. Cập nhật data_sync_outbox, Cassandra & POS Payment JSON logs
    print("\n4. Đang cập nhật Outbox, Cassandra và JSON Payment logs...")
    outbox_raw = run_mysql_query("SELECT id, event_key, destination, HEX(payload) FROM data_sync_outbox;", config)
    cql_inserts = []
    pos_payment_logs = []

    def parse_payload(raw_str):
        try:
            return json.loads(raw_str)
        except Exception:
            idx = raw_str.find('"metadata": "')
            if idx != -1:
                main_part = raw_str[:idx].rstrip(', ') + '}'
                obj = json.loads(main_part)
                meta_str = raw_str[idx + len('"metadata": "'):-2]
                obj['metadata'] = meta_str
                return obj
            raise

    outbox_updates = []
    for line in outbox_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        ob_id = int(parts[0])
        key = parts[1]
        dest = parts[2]
        raw_bytes = bytes.fromhex(parts[3])
        payload = parse_payload(raw_bytes.decode('utf-8'))

        # Xác định mốc thời gian mới cho sự kiện này
        new_utc = None
        if key.startswith('ORDER_CREATED-'):
            oid = payload.get('orderId')
            new_utc = order_time_map.get(oid)
        elif key.startswith('PAYMENT-') or key.startswith('POSLOG-PAYMENT-'):
            raw_m = payload.get('metadata', '{}')
            meta = json.loads(raw_m) if isinstance(raw_m, str) else raw_m
            ret_id = meta.get('returnId')
            oid = payload.get('orderId')
            if ret_id and ret_id in return_time_map and return_time_map[ret_id][1]:
                new_utc = return_time_map[ret_id][1]
            elif oid and oid in order_time_map:
                new_utc = order_time_map[oid]
        elif key.startswith('MOVEMENT-'):
            raw_m = payload.get('metadata', '{}')
            meta = json.loads(raw_m) if isinstance(raw_m, str) else raw_m
            mtype = meta.get('movementType')
            oid = payload.get('orderId')
            if mtype == 'RECEIPT':
                new_utc = base_rcv_vn.astimezone(UTC_TZ)
            elif mtype == 'SALE' and oid in order_time_map:
                new_utc = order_time_map[oid]
            elif oid in order_time_map:
                new_utc = order_time_map[oid] + dt.timedelta(minutes=15)
        elif key.startswith('RETURN_RECEIVED-'):
            oid = payload.get('orderId')
            if oid in order_time_map:
                new_utc = order_time_map[oid] + dt.timedelta(minutes=15)
        elif key.startswith('RETURN_REFUND_CONFIRMED-'):
            raw_m = payload.get('metadata', '{}')
            meta = json.loads(raw_m) if isinstance(raw_m, str) else raw_m
            ret_id = meta.get('returnId')
            if ret_id and ret_id in return_time_map and return_time_map[ret_id][1]:
                new_utc = return_time_map[ret_id][1]

        if not new_utc:
            new_utc = base_rcv_vn.astimezone(UTC_TZ)

        epoch_ms = int(new_utc.timestamp() * 1000)
        utc_date_str = new_utc.strftime('%Y-%m-%d')
        iso_str = new_utc.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z'

        payload['eventTime'] = epoch_ms
        payload['eventDate'] = utc_date_str

        updated_payload_json = json.dumps(payload, ensure_ascii=False)
        hex_payload = updated_payload_json.encode('utf-8').hex()
        outbox_updates.append(f"UPDATE data_sync_outbox SET payload=UNHEX('{hex_payload}') WHERE id={ob_id};")

        # Chuẩn bị bản ghi Cassandra
        if dest == 'ASTRA':
            eid = payload['eventId']
            etype = payload['eventType']
            oid = payload.get('orderId')
            oid_val = str(oid) if oid is not None else 'null'
            sku = payload.get('productSku')
            sku_val = f"'{sku}'" if sku else 'null'
            qty = payload.get('quantity')
            qty_val = str(qty) if qty is not None else 'null'
            amt = payload.get('amount')
            amt_val = str(amt) if amt is not None else 'null'
            pm = payload.get('paymentMethod')
            pm_val = f"'{pm}'" if pm else 'null'
            stat = payload.get('status', 'COMPLETED')
            raw_meta = payload.get('metadata', {})
            if isinstance(raw_meta, str):
                meta_dict = json.loads(raw_meta)
                meta_str = raw_meta.replace("'", "''")
            else:
                meta_dict = raw_meta
                meta_str = json.dumps(raw_meta, ensure_ascii=False).replace("'", "''")

            cql_inserts.append(
                f"INSERT INTO supermarket_analytics.business_events_by_day "
                f"(event_date, event_time, event_id, event_type, order_id, product_sku, quantity, amount, payment_method, status, metadata) "
                f"VALUES ('{utc_date_str}', '{iso_str}', {eid}, '{etype}', {oid_val}, {sku_val}, {qty_val}, {amt_val}, {pm_val}, '{stat}', '{meta_str}');"
            )

        # Chuẩn bị POS log JSON
        if dest == 'POS_LOG':
            raw_meta = payload.get('metadata', {})
            meta_dict = json.loads(raw_meta) if isinstance(raw_meta, str) else raw_meta
            pr_id = meta_dict.get('paymentRecordId')
            canonical_payload = dict(payload)
            canonical_payload['metadata'] = meta_dict
            fprint = compute_sha256(canonical_payload)

            log_entry = {
                'event_id': payload['eventId'],
                'event_time': iso_str,
                'payment_record_id': pr_id,
                'order_id': payload.get('orderId'),
                'event_type': payload.get('eventType'),
                'amount': payload.get('amount'),
                'payment_method': payload.get('paymentMethod'),
                'status': payload.get('status'),
                'payload_sha256': fprint
            }
            pos_payment_logs.append((utc_date_str, payload['eventId'], log_entry))

    # Cập nhật outbox trong MySQL
    run_mysql_query("\n".join(["START TRANSACTION;"] + outbox_updates + ["COMMIT;"]), config)
    print("-> Cập nhật Outbox payload hoàn tất.")

    # Truncate và nạp lại Cassandra
    print("-> Đang đồng bộ lại Cassandra business_events_by_day...")
    run_cql("TRUNCATE supermarket_analytics.business_events_by_day;")
    # Nạp theo từng mẻ 100 câu lệnh
    chunk_size = 100
    for idx in range(0, len(cql_inserts), chunk_size):
        chunk = cql_inserts[idx:idx + chunk_size]
        run_cql("\n".join(chunk))
    print(f"-> Đã nạp {len(cql_inserts)} sự kiện vào Cassandra.")

    # Ghi lại file POS payment logs
    print("-> Đang cập nhật POS payment JSON logs...")
    log_base = ROOT / 'analytics' / 'pos-payments'
    # Xóa file cũ
    if log_base.exists():
        for p in log_base.glob('*/*.json'):
            p.unlink()

    for p_date, p_eid, p_data in pos_payment_logs:
        p_dir = log_base / p_date
        p_dir.mkdir(parents=True, exist_ok=True)
        (p_dir / f"{p_eid}.json").write_text(json.dumps(p_data, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"-> Đã lưu {len(pos_payment_logs)} file JSON log trải rộng các ngày.")

    # 5. Sinh Cassandra Daily Stock Snapshots cho các ngày đã kết thúc
    print("\n5. Đang sinh snapshot kho Cassandra stock_daily_by_day...")
    yesterday_str = (end_d - dt.timedelta(days=1)).isoformat()
    try:
        # Gọi API sinh snapshot từ start_date đến ngày hôm qua
        snap_url = f"http://127.0.0.1:8080/api/integrations/cassandra/stock-snapshots?from={args.start_date}&to={yesterday_str}"
        snap_req = urllib.request.Request(snap_url, method='POST', headers={'Authorization': 'Bearer ' + adm_tok})
        with urllib.request.urlopen(snap_req, timeout=30) as r:
            snap_res = json.loads(r.read())
            print(f"-> Kết quả snapshot kho: Status={snap_res.get('status')}, Written={snap_res.get('written')} bản ghi.")
    except Exception as exc:
        print(f"-> Snapshot kho lưu ý: {exc}")

    # 6. Kiểm tra đối soát Reconciliation qua Drill
    print("\n6. Đang kiểm tra đối soát Reconciliation qua Drill...")
    try:
        rec_url = f"http://127.0.0.1:8080/api/analytics/reconciliation?from={args.start_date}&to={args.end_date}"
        rec_req = urllib.request.Request(rec_url, headers={'Authorization': 'Bearer ' + adm_tok})
        with urllib.request.urlopen(rec_req, timeout=30) as r:
            rec_res = json.loads(r.read())
            pass_days = sum(1 for row in rec_res['rows'] if row.get('quality_status') == 'PASS')
            print(f"-> Đối soát 30 ngày: {pass_days}/{len(rec_res['rows'])} ngày đạt chuẩn PASS! (QueryId: {rec_res.get('queryId')})")

            manifest_file = ROOT / 'database' / 'learning-dataset-100' / 'manifest.json'
            if manifest_file.exists():
                man_data = json.loads(manifest_file.read_text(encoding='utf-8'))
                man_data['businessDateRange'] = {
                    'startDate': args.start_date,
                    'endDate': args.end_date,
                    'totalDays': total_days
                }
                man_data['customerProfiles'] = {
                    'totalOrders': len(order_rows),
                    'anonymousOrders': len(order_rows) - named_count,
                    'namedOrders': named_count,
                    'anonymousPercent': round((len(order_rows) - named_count) * 100 / len(order_rows), 1)
                }
                man_data['summary']['reconciliation'] = {
                    'startDate': args.start_date,
                    'endDate': args.end_date,
                    'totalDaysChecked': len(rec_res.get('rows', [])),
                    'passedDays': pass_days,
                    'overallQuality': 'PASS' if pass_days == len(rec_res.get('rows', [])) else 'CHECK',
                    'queryId': rec_res.get('queryId')
                }
                manifest_file.write_text(json.dumps(man_data, ensure_ascii=False, indent=2), encoding='utf-8')
                print("-> Đã cập nhật manifest.json với thông tin lịch sử 30 ngày.")
    except Exception as exc:
        print(f"-> Lỗi đối soát: {exc}")

    print("\n=== HOÀN TẤT PHÂN BỔ LỊCH SỬ 30 NGÀY ===")


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
