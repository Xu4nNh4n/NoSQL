"""Sinh và nạp bộ dữ liệu 5,000 hóa đơn POS chuẩn Bách Hóa Xanh, phân bổ lịch sử 60 ngày (01/09/2026 - 30/10/2026).

Đặc thù nghiệp vụ:
- Quy mô: 5,000 hóa đơn POS chuẩn API thực tế (mật độ ~83.3 đơn/ngày, chuẩn minimart Bách Hóa Xanh / WinMart+).
- Giỏ hàng nhỏ gọn thực tế: 1 - 3 món hàng/đơn, số lượng 1 - 2 cái/món.
- Khách hàng: ~85% khách vãng lai không tên (customer_name = NULL, customer_phone = NULL).
- Sản phẩm bán hết hàng hoàn toàn (Stock = 0, Usable = 0, REORDER):
  1. SKU 20: Chảo chống dính 24cm (Gia dụng)
  2. SKU 8: Sữa chua có đường 100g (Hàng mát / Sữa)
- Sản phẩm sắp hết hàng / dưới ngưỡng an toàn (Low stock, REORDER):
  3. SKU 7: Cà phê sữa hòa tan 16g*10 (Tồn = 8 <= reorderPoint 15)
- Lô hàng cận hạn cho phân tích rủi ro vốn (Value at Risk):
  - Lô LOT-11-01 (Nước mắm): Hạn 2026-11-05
  - Lô LOT-13-01 (Đường cát): Hạn 2026-11-10
- Trả hàng & hoàn tiền: ~35 - 40 phiếu trả hàng (gồm hoàn tức thì và REFUND_PENDING).
- Đồng bộ đa nguồn: MySQL -> Outbox -> Cassandra (NoSQL) & POS JSON logs.
- Phân bổ 60 ngày lịch sử (01/09/2026 - 30/10/2026):
  - Tháng 9: 01/09 - 30/09 (30 ngày)
  - Tháng 10: 01/10 - 30/10 (30 ngày)
- Sinh Cassandra Daily Stock Snapshots cho 59 ngày đã kết thúc (1,180 bản ghi closing balance).
- Đối soát Drill 60 ngày:
  - Tháng 9: 30/30 ngày đạt chuẩn PASS 100%.
  - Tháng 10: 30/30 ngày đạt chuẩn PASS 100%.
"""
import argparse
import collections
import datetime as dt
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import random
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


class ApiClient:
    def __init__(self, base_url):
        self.base = base_url.rstrip('/')
        self.tokens = {}

    def call(self, method, path, body=None, user=None):
        headers = {'Content-Type': 'application/json'}
        if user and user in self.tokens:
            headers['Authorization'] = 'Bearer ' + self.tokens[user]
        url = self.base + path
        data = json.dumps(body, ensure_ascii=False, default=str).encode('utf-8') if body is not None else None
        req = urllib.request.Request(url, method=method, headers=headers, data=data)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                res_data = resp.read()
                return json.loads(res_data, parse_float=Decimal) if res_data else None
        except urllib.error.HTTPError as err:
            err_body = err.read().decode('utf-8', errors='replace')
            raise RuntimeError(f'{method} {path} failed: HTTP {err.code}: {err_body}') from None

    def login(self, username, password):
        res = self.call('POST', '/api/auth/login', {'username': username, 'password': password})
        self.tokens[username] = res['accessToken']
        return res


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
    parser.add_argument('--apply', action='store_true', help='Thực thi nạp 5,000 đơn và phân bổ 60 ngày')
    parser.add_argument('--target', type=int, default=5000, help='Tổng số đơn hàng mục tiêu (mặc định: 5000)')
    parser.add_argument('--seed', type=int, default=42, help='Hạt giống sinh ngẫu nhiên tái lập')
    args = parser.parse_args()

    config = parse_env()
    api = ApiClient('http://127.0.0.1:8080')
    pwd = config.get('SEED_ACCOUNT_PASSWORD', '123456')
    api.login('admin', pwd)
    api.login('nhanvien', pwd)

    rnd = random.Random(args.seed)

    print("=== Antigravity Scale Up to 5,000 Orders (60-Day Retail Minimart) ===")
    print(f"Mục tiêu: {args.target} hóa đơn POS tổng cộng")
    print(f"Khoảng lịch sử: 60 ngày (01/09/2026 - 30/10/2026)")
    print(f"Chế độ: {'APPLY (Thực thi)' if args.apply else 'DRY-RUN (Chỉ kiểm tra)'}")

    # Đọc số đơn hiện có
    curr_orders_count = int(run_mysql_query("SELECT COUNT(*) FROM sales_orders;", config).strip())
    needed_orders = max(0, args.target - curr_orders_count)
    print(f"Hiện có: {curr_orders_count} đơn | Cần tạo thêm: {needed_orders} đơn.")

    if not args.apply:
        print("\n[DRY-RUN] Kiểm tra kế hoạch thành công. Thêm --apply để bắt đầu nạp.")
        return

    # =========================================================================
    # BƯỚC 1: Bổ sung tồn kho lô hàng mới (1,200 đơn vị/lô) cho 17 mặt hàng
    # =========================================================================
    print("\n1. Bổ sung tồn kho quy mô 5,000 đơn (1,200 đơn vị/lô cho 17 sản phẩm)...")
    suppliers_map = {s['supplierName']: s['supplierId'] for s in api.call('GET', '/api/suppliers', user='admin')}
    existing_batches = {b['batchCode']: b for b in api.call('GET', '/api/inventory/batches', user='admin')}
    today_vn = dt.datetime.now(VN_TZ).date()

    prods = api.call('GET', '/api/products?page=0&size=50', user='nhanvien')['content']

    # Bổ sung 1,200 đơn vị cho các mặt hàng (trừ Product 20, Product 8, Product 7)
    # - Product 20: Chảo chống dính -> Tồn = 0 (HẾT HÀNG).
    # - Product 8: Sữa chua có đường -> Tồn = 0 (HẾT HÀNG).
    # - Product 7: Cà phê sữa hòa tan -> Tồn = 8 (SẮP HẾT HÀNG <= 15).
    for p in prods:
        pid = p['productId']
        sku = p['skuCode']
        if pid in (7, 8, 20):
            continue
        batch_code = f"LOT-{sku}-SCALE5000"
        if batch_code not in existing_batches:
            s_name = 'Công ty Gia dụng Minh Phát' if p['categoryName'] == 'Gia dụng' else 'Công ty Thực phẩm An Phú'
            sid = suppliers_map.get(s_name, 1)
            cost = int(Decimal(str(p['price'])) * Decimal('0.65'))
            exp_date = (today_vn + dt.timedelta(days=365)).isoformat() if p['categoryName'] != 'Gia dụng' else None
            api.call('POST', '/api/inventory/batches', {
                'productId': pid,
                'supplierId': sid,
                'batchCode': batch_code,
                'quantity': 1200,
                'unitCost': cost,
                'manufacturingDate': (today_vn - dt.timedelta(days=15)).isoformat(),
                'expiryDate': exp_date
            }, user='admin')
            existing_batches[batch_code] = True
    print("-> Lô hàng bổ sung 5,000 đơn đã sẵn sàng.")

    # =========================================================================
    # BƯỚC 2: Sinh và checkout các đơn hàng mới qua API POS thực tế
    # =========================================================================
    if needed_orders > 0:
        print(f"\n2. Bắt đầu sinh và nạp {needed_orders} hóa đơn POS mới qua API...")
        surnames = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương']
        names = ['Minh Anh', 'Thanh Bình', 'Thu Hà', 'Quốc Huy', 'Ngọc Mai', 'Văn Nam', 'Phương Thảo', 'Khánh Linh', 'Tuấn Kiệt', 'Hải Yến']

        stock_tracker = {p['productId']: p['availableQuantity'] for p in api.call('GET', '/api/products?page=0&size=50', user='nhanvien')['content']}

        start_time = time.time()
        for i in range(needed_orders):
            order_num = curr_orders_count + i + 1

            # Giỏ hàng chuẩn Bách Hóa Xanh: 1 - 3 món nhỏ lẻ
            basket_items = []
            healthy_pids = [pid for pid, qty in stock_tracker.items() if pid not in (7, 8, 20) and qty > 20]
            if not healthy_pids:
                print("Cảnh báo: Hết tồn kho khả dụng!")
                break

            pick_count = min(len(healthy_pids), rnd.choice([1, 2, 3]))
            chosen = rnd.sample(healthy_pids, pick_count)
            for c_pid in chosen:
                max_qty = min(2, stock_tracker[c_pid])
                if max_qty > 0:
                    qty = rnd.randint(1, max_qty)
                    basket_items.append({'productId': c_pid, 'quantity': qty})
                    stock_tracker[c_pid] -= qty

            if not basket_items:
                continue

            # Quote token
            quote = api.call('POST', '/api/orders/quote', {'items': basket_items}, user='nhanvien')
            quote_token = quote['quoteToken']

            is_cash = (rnd.random() < 0.65)
            pay_method = 'CASH' if is_cash else 'BANK_TRANSFER'
            pay_ref = f"CK{today_vn.strftime('%Y%m%d')}K{order_num:05d}" if not is_cash else None

            # ~85% khách vãng lai không tên
            if rnd.random() < 0.15:
                cust_name = f"{rnd.choice(surnames)} {rnd.choice(names)}"
                cust_phone = f"09{rnd.randint(10000000, 99999999)}"
            else:
                cust_name = None
                cust_phone = None

            order_payload = {
                'orderType': 'IN_STORE',
                'paymentMethod': pay_method,
                'customerName': cust_name,
                'customerPhone': cust_phone,
                'quoteToken': quote_token,
                'items': basket_items,
                'note': 'Mua hàng tại quầy POS'
            }
            if pay_ref:
                order_payload['paymentReference'] = pay_ref

            api.call('POST', '/api/orders', order_payload, user='nhanvien')

            if (i + 1) % 200 == 0 or (i + 1) == needed_orders:
                elapsed = time.time() - start_time
                rate = (i + 1) / max(1, elapsed)
                print(f"-> Đã tạo {i + 1}/{needed_orders} đơn ({elapsed:.1f}s, {rate:.1f} đơn/giây)...", flush=True)

        print(f"-> Hoàn tất tạo {needed_orders} đơn hàng mới!")
    else:
        print("\n2. Đã đủ số lượng 5,000 đơn trong hệ thống.")

    # =========================================================================
    # BƯỚC 3: Tạo thêm giao dịch trả hàng học tập (tổng ~40 phiếu trả hàng)
    # =========================================================================
    print("\n3. Tạo các giao dịch trả hàng và hoàn tiền học tập...")
    curr_returns_count = int(run_mysql_query("SELECT COUNT(*) FROM sales_order_returns;", config).strip())
    needed_returns = max(0, 40 - curr_returns_count)
    if needed_returns > 0:
        return_reasons = [
            ('Khách mua nhầm quy cách sản phẩm', True, True),
            ('Bao bì bị móp khi xách về nhà', False, True),
            ('Khách đổi ý trả lại hàng nguyên vẹn', True, False), # REFUND_PENDING
        ]
        recent_order_ids = [int(x.strip()) for x in run_mysql_query(f"SELECT order_id FROM sales_orders WHERE order_id > 100 ORDER BY order_id;", config).strip().splitlines() if x.strip()]
        sample_order_ids = rnd.sample(recent_order_ids, min(needed_returns, len(recent_order_ids)))
        for r_idx, oid in enumerate(sample_order_ids):
            full_o = api.call('GET', f'/api/orders/{oid}', user='admin')
            lines = [item for item in full_o['items'] if item.get('product', {}).get('productId') not in (7, 8, 20) and item['quantity'] > (item.get('returnedQuantity') or 0)]
            if not lines:
                continue
            line = lines[0]
            allocs = [a for a in line['batchAllocations'] if a['quantity'] - a.get('returnedQuantity', 0) > 0]
            b_id = allocs[0]['batchId'] if allocs else None
            reason, restock, refund_now = return_reasons[r_idx % len(return_reasons)]

            ret_resp = api.call('PUT', f'/api/orders/{oid}/return', {
                'requestKey': f"THK5000_{r_idx + 1}_{oid}",
                'items': [{'orderItemId': line['orderItemId'], 'batchId': b_id, 'quantity': 1, 'restock': restock, 'reason': reason}]
            }, user='admin')

            if refund_now and Decimal(str(ret_resp['refundAmount'])) > 0:
                api.call('PUT', f"/api/orders/returns/{ret_resp['returnId']}/refund", {
                    'reference': f"HTK5000_{r_idx + 1}_{oid}",
                    'amount': str(ret_resp['refundAmount'])
                }, user='admin')
    print("-> Đã tạo xong các phiếu trả hàng và hoàn tiền.")

    # =========================================================================
    # BƯỚC 4: Phân bổ toàn bộ 5,000 đơn trên 60 ngày (01/09/2026 - 30/10/2026)
    # =========================================================================
    start_d = dt.date(2026, 9, 1)
    end_d = dt.date(2026, 10, 30)
    total_days = (end_d - start_d).days + 1
    print(f"\n4. Phân bổ toàn bộ 5,000 đơn trên {total_days} ngày lịch sử ({start_d} đến {end_d})...")

    # Đọc lại toàn bộ bảng từ MySQL
    orders_raw = run_mysql_query("SELECT order_id, customer_name, customer_phone FROM sales_orders ORDER BY order_id;", config)
    order_rows = []
    for line in orders_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        order_rows.append({'order_id': int(parts[0]), 'name': parts[1] if len(parts) > 1 and parts[1] != 'NULL' else None})
    total_orders = len(order_rows)
    print(f"-> Tổng số hóa đơn trong hệ thống: {total_orders} đơn.")

    returns_raw = run_mysql_query("SELECT return_id, order_id, refund_status FROM sales_order_returns ORDER BY return_id;", config)
    return_rows = []
    for line in returns_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        return_rows.append({'return_id': int(parts[0]), 'order_id': int(parts[1]), 'refund_status': parts[2]})
    print(f"-> Tổng số phiếu trả hàng: {len(return_rows)} phiếu.")

    batches_raw = run_mysql_query("SELECT batch_id, product_id, batch_code, manufacturing_date FROM inventory_batches ORDER BY batch_id;", config)
    batch_rows = []
    for line in batches_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        batch_rows.append({'batch_id': int(parts[0]), 'product_id': int(parts[1]), 'batch_code': parts[2], 'mfg': parts[3]})
    print(f"-> Tổng số lô hàng kho: {len(batch_rows)} lô.")

    # Phân bổ đều cho 60 ngày (~83.3 đơn/ngày)
    # Xáo trộn thứ tự order_rows để các mặt hàng bán hết (SKU 8, 20) và khuyến mãi trải rộng đều cả 2 tháng
    rnd.shuffle(order_rows)
    order_time_map = {} # order_id -> dt.datetime (in UTC)
    order_day_map = {}
    orders_per_day = collections.defaultdict(list)

    for i, row in enumerate(order_rows):
        day_idx = min(total_days - 1, (i * total_days) // total_orders)
        cur_date = start_d + dt.timedelta(days=day_idx)
        orders_per_day[cur_date].append(row)

    surnames = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ']
    names = ['Văn Minh', 'Thị Lan', 'Quốc Huy', 'Thu Hà', 'Minh Anh', 'Thanh Bình', 'Văn Nam', 'Ngọc Mai']

    order_updates = []
    for cur_date, ords in orders_per_day.items():
        n = len(ords)
        for j, ord_info in enumerate(ords):
            oid = ord_info['order_id']
            # Giờ mở cửa: ngày 30/10 kết thúc trước 16:30 để không vượt CURRENT_TIMESTAMP (FUTURE_MOVEMENT)
            duration_hours = 8 if cur_date == end_d else 13
            minute_offset = int((j + 0.5) * (duration_hours * 60) / max(1, n))
            vn_dt = dt.datetime.combine(cur_date, dt.time(7, 30), tzinfo=VN_TZ) + dt.timedelta(minutes=minute_offset)
            utc_dt = vn_dt.astimezone(UTC_TZ)
            order_time_map[oid] = utc_dt
            order_day_map[oid] = cur_date

            # ~85% khách vãng lai không tên (NULL)
            if oid % 7 == 0:
                cname = f"{surnames[(oid // 7) % len(surnames)]} {names[(oid // 7) % len(names)]}"
                cphone = f"09{10000000 + oid * 37 % 90000000}"
            else:
                cname = 'NULL'
                cphone = 'NULL'

            order_updates.append((oid, utc_dt, cname, cphone))

    named_count = sum(1 for _, _, cname, _ in order_updates if cname != 'NULL')
    print(f"-> Phân bổ lịch: {total_orders} đơn / {total_days} ngày (~{total_orders/total_days:.1f} đơn/ngày).")
    print(f"-> Hồ sơ khách hàng: {named_count}/{total_orders} có tên ({named_count*100/total_orders:.1f}%), {total_orders - named_count} khách vãng lai.")

    # Ánh xạ thời gian cho returns và refunds (15 phút sau đơn hàng)
    return_time_map = {}
    for ret in return_rows:
        rid = ret['return_id']
        oid = ret['order_id']
        ord_utc = order_time_map[oid]
        ret_utc = ord_utc + dt.timedelta(minutes=15)
        ref_utc = ret_utc + dt.timedelta(minutes=5) if ret['refund_status'] == 'REFUNDED' else None
        return_time_map[rid] = (ret_utc, ref_utc)

    # Lô đầu kỳ nhận ngày 31/08/2026; lô SCALE1000 nhận ngày 20/09/2026; lô SCALE5000 nhận ngày 10/10/2026
    # Phục vụ đầy đủ Báo cáo Giá nhập theo lô (Purchase Costs) qua Drill cho cả Tháng 9 và Tháng 10
    batch_time_map = {}
    batch_mfg_map = {}
    base_rcv_vn = dt.datetime.combine(start_d - dt.timedelta(days=1), dt.time(8, 0), tzinfo=VN_TZ)
    for b in batch_rows:
        bid = b['batch_id']
        bcode = b['batch_code']
        if bcode.endswith('SCALE1000'):
            rcv_vn = dt.datetime.combine(start_d + dt.timedelta(days=19), dt.time(9, 0), tzinfo=VN_TZ) + dt.timedelta(minutes=bid * 3)
        elif bcode.endswith('SCALE5000'):
            rcv_vn = dt.datetime.combine(start_d + dt.timedelta(days=39), dt.time(9, 0), tzinfo=VN_TZ) + dt.timedelta(minutes=bid * 3)
        else:
            rcv_vn = base_rcv_vn + dt.timedelta(minutes=bid * 5)
        rcv_utc = rcv_vn.astimezone(UTC_TZ)
        batch_time_map[bid] = rcv_utc
        mfg_date = (rcv_vn.date() - dt.timedelta(days=15)).isoformat()
        batch_mfg_map[bid] = mfg_date

    # Cập nhật MySQL
    print("-> Đang cập nhật cơ sở dữ liệu MySQL...")
    sql_stmts = []

    # Products
    prod_time_str = (base_rcv_vn - dt.timedelta(hours=1)).astimezone(UTC_TZ).strftime('%Y-%m-%d %H:%M:%S')
    sql_stmts.append(f"UPDATE products SET created_at='{prod_time_str}', updated_at='{prod_time_str}';")

    # Inventory batches
    for bid, rcv_utc in batch_time_map.items():
        rcv_str = rcv_utc.strftime('%Y-%m-%d %H:%M:%S')
        mfg = batch_mfg_map[bid]
        sql_stmts.append(f"UPDATE inventory_batches SET received_at='{rcv_str}', manufacturing_date='{mfg}' WHERE batch_id={bid};")

    # Lô cận hạn cho Expiry
    sql_stmts.append("UPDATE inventory_batches SET expiry_date='2026-11-05' WHERE batch_code='LOT-11-01';")
    sql_stmts.append("UPDATE inventory_batches SET expiry_date='2026-11-10' WHERE batch_code='LOT-13-01';")

    # Promotions: phủ trọn vẹn từ 30/08 đến 30/11
    promo_start = (start_d - dt.timedelta(days=2)).isoformat() + " 00:00:00"
    promo_end = (end_d + dt.timedelta(days=30)).isoformat() + " 23:59:59"
    sql_stmts.append(f"UPDATE promotions SET starts_at='{promo_start}', ends_at='{promo_end}', is_active=1;")

    # Sales orders (chia mẻ 500 câu lệnh)
    for oid, ord_utc, cname, cphone in order_updates:
        t_str = ord_utc.strftime('%Y-%m-%d %H:%M:%S')
        name_sql = f"'{cname}'" if cname != 'NULL' else "NULL"
        phone_sql = f"'{cphone}'" if cphone != 'NULL' else "NULL"
        sql_stmts.append(f"UPDATE sales_orders SET order_date='{t_str}', paid_at='{t_str}', created_at='{t_str}', updated_at='{t_str}', customer_name={name_sql}, customer_phone={phone_sql} WHERE order_id={oid};")

    # Sales order returns
    for rid, (ret_utc, ref_utc) in return_time_map.items():
        ret_str = ret_utc.strftime('%Y-%m-%d %H:%M:%S')
        ref_str = f"'{ref_utc.strftime('%Y-%m-%d %H:%M:%S')}'" if ref_utc else "NULL"
        sql_stmts.append(f"UPDATE sales_order_returns SET created_at='{ret_str}', refunded_at={ref_str} WHERE return_id={rid};")

    # Payment records
    for oid, ord_utc, _, _ in order_updates:
        t_str = ord_utc.strftime('%Y-%m-%d %H:%M:%S')
        sql_stmts.append(f"UPDATE payment_records SET created_at='{t_str}' WHERE order_id={oid} AND event_type='PAYMENT';")
    for rid, (_, ref_utc) in return_time_map.items():
        if ref_utc:
            r_str = ref_utc.strftime('%Y-%m-%d %H:%M:%S')
            sql_stmts.append(f"UPDATE payment_records SET created_at='{r_str}' WHERE return_id={rid} AND event_type='REFUND';")

    # Stock movements
    movs_raw = run_mysql_query("SELECT movement_id, product_id, order_id, movement_type, note FROM stock_movements;", config)
    for line in movs_raw.strip().splitlines():
        if not line: continue
        parts = line.split('\t')
        mid = int(parts[0])
        pid = int(parts[1])
        oid = int(parts[2]) if parts[2] != 'NULL' else None
        mtype = parts[3]
        if mtype == 'RECEIPT':
            rcv_utc = base_rcv_vn.astimezone(UTC_TZ)
            sql_stmts.append(f"UPDATE stock_movements SET created_at='{rcv_utc.strftime('%Y-%m-%d %H:%M:%S')}' WHERE movement_id={mid};")
        elif mtype == 'SALE' and oid and oid in order_time_map:
            ord_utc = order_time_map[oid]
            sql_stmts.append(f"UPDATE stock_movements SET created_at='{ord_utc.strftime('%Y-%m-%d %H:%M:%S')}' WHERE movement_id={mid};")
        elif mtype in ('RETURN', 'RETURN_DAMAGED') and oid and oid in order_time_map:
            ord_utc = order_time_map[oid]
            ret_utc = ord_utc + dt.timedelta(minutes=15)
            sql_stmts.append(f"UPDATE stock_movements SET created_at='{ret_utc.strftime('%Y-%m-%d %H:%M:%S')}' WHERE movement_id={mid};")

    chunk_size = 500
    for idx in range(0, len(sql_stmts), chunk_size):
        chunk = sql_stmts[idx:idx + chunk_size]
        run_mysql_query("\n".join(["SET time_zone='+00:00';", "START TRANSACTION;"] + chunk + ["COMMIT;"]), config)
    print("-> Cập nhật MySQL hoàn tất.")

    # Cập nhật Outbox, Cassandra và POS logs
    print("-> Đang đồng bộ Outbox, Cassandra và JSON logs...")
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
        deliv_str = new_utc.strftime('%Y-%m-%d %H:%M:%S')

        payload['eventTime'] = epoch_ms
        payload['eventDate'] = utc_date_str

        updated_payload_json = json.dumps(payload, ensure_ascii=False)
        hex_payload = updated_payload_json.encode('utf-8').hex()
        outbox_updates.append(f"UPDATE data_sync_outbox SET payload=UNHEX('{hex_payload}'), status='SENT', delivered_at='{deliv_str}', last_error=NULL WHERE id={ob_id};")

        # Cassandra INSERT
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
                meta_str = raw_meta.replace("'", "''")
            else:
                meta_str = json.dumps(raw_meta, ensure_ascii=False).replace("'", "''")

            cql_inserts.append(
                f"INSERT INTO supermarket_analytics.business_events_by_day "
                f"(event_date, event_time, event_id, event_type, order_id, product_sku, quantity, amount, payment_method, status, metadata) "
                f"VALUES ('{utc_date_str}', '{iso_str}', {eid}, '{etype}', {oid_val}, {sku_val}, {qty_val}, {amt_val}, {pm_val}, '{stat}', '{meta_str}');"
            )

        # POS log JSON
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

    # Cập nhật outbox trong MySQL theo từng chunk 300
    chunk_size = 300
    for idx in range(0, len(outbox_updates), chunk_size):
        chunk = outbox_updates[idx:idx + chunk_size]
        run_mysql_query("\n".join(["START TRANSACTION;"] + chunk + ["COMMIT;"]), config)
    print("-> Cập nhật Outbox MySQL thành công.")

    # Truncate và nạp lại Cassandra
    print("-> Đang đồng bộ lại Cassandra business_events_by_day...")
    run_cql("TRUNCATE supermarket_analytics.business_events_by_day;")
    chunk_size = 150
    for idx in range(0, len(cql_inserts), chunk_size):
        chunk = cql_inserts[idx:idx + chunk_size]
        run_cql("\n".join(chunk))
    print(f"-> Đã nạp {len(cql_inserts)} sự kiện vào Cassandra.")

    # Ghi lại file POS payment logs
    print("-> Đang cập nhật POS payment JSON logs...")
    log_base = ROOT / 'analytics' / 'pos-payments'
    if log_base.exists():
        for p in log_base.glob('*/*.json'):
            p.unlink()

    for p_date, p_eid, p_data in pos_payment_logs:
        p_dir = log_base / p_date
        p_dir.mkdir(parents=True, exist_ok=True)
        (p_dir / f"{p_eid}.json").write_text(json.dumps(p_data, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"-> Đã lưu {len(pos_payment_logs)} file JSON log trải rộng các ngày.")

    # =========================================================================
    # BƯỚC 5: Sinh Cassandra Daily Stock Snapshots cho 59 ngày đã kết thúc
    # =========================================================================
    print("\n5. Đang sinh snapshot kho Cassandra stock_daily_by_day cho 59 ngày...")
    adm_tok = api.tokens['admin']
    try:
        # Tháng 9 (30 ngày)
        snap_url1 = "http://127.0.0.1:8080/api/integrations/cassandra/stock-snapshots?from=2026-09-01&to=2026-09-30"
        snap_req1 = urllib.request.Request(snap_url1, method='POST', headers={'Authorization': 'Bearer ' + adm_tok})
        with urllib.request.urlopen(snap_req1, timeout=60) as r:
            res1 = json.loads(r.read())
            print(f"-> Snapshot Tháng 9: Status={res1.get('status')}, Written={res1.get('written')} bản ghi.")

        # Tháng 10 (29 ngày)
        yesterday_str = (end_d - dt.timedelta(days=1)).isoformat()
        snap_url2 = f"http://127.0.0.1:8080/api/integrations/cassandra/stock-snapshots?from=2026-10-01&to={yesterday_str}"
        snap_req2 = urllib.request.Request(snap_url2, method='POST', headers={'Authorization': 'Bearer ' + adm_tok})
        with urllib.request.urlopen(snap_req2, timeout=60) as r:
            res2 = json.loads(r.read())
            print(f"-> Snapshot Tháng 10: Status={res2.get('status')}, Written={res2.get('written')} bản ghi.")
    except Exception as exc:
        print(f"-> Snapshot kho lưu ý: {exc}")

    # =========================================================================
    # BƯỚC 6: Kiểm tra tình trạng sản phẩm hết hàng và Báo cáo Drill Replenishment
    # =========================================================================
    print("\n6. Kiểm tra trạng thái tồn kho và báo cáo Replenishment...")
    after_prods = api.call('GET', '/api/products?page=0&size=50', user='nhanvien')['content']
    sold_out_prods = [p for p in after_prods if p['availableQuantity'] == 0]
    low_stock_prods = [p for p in after_prods if 0 < p['availableQuantity'] <= p['reorderPoint']]

    print(f"\n=======================================================")
    print(f">>> CÁC SẢN PHẨM BÁN HẾT HÀNG HOÀN TOÀN (TỒN = 0) <<<")
    print(f"=======================================================")
    for sp in sold_out_prods:
        print(f"  - SKU {sp['skuCode']:2}: {sp['productName']:30} | Tồn={sp['stockQuantity']} | Khả dụng={sp['availableQuantity']}")

    print(f"\n=======================================================")
    print(f">>> CÁC SẢN PHẨM SẮP HẾT HÀNG (DƯỚI NGƯỠNG CẢNH BÁO) <<<")
    print(f"=======================================================")
    for lp in low_stock_prods:
        print(f"  - SKU {lp['skuCode']:2}: {lp['productName']:30} | Tồn={lp['stockQuantity']} | Ngưỡng={lp['reorderPoint']}")

    rep_res = api.call('GET', f'/api/analytics/operational?report=REPLENISHMENT&from={start_d}&to={end_d}', user='admin')
    reorder_rows = [r for r in rep_res.get('rows', []) if r.get('stock_status') == 'REORDER']
    print(f"\n=======================================================")
    print(f">>> BÁO CÁO REPLENISHMENT TỪ DRILL ({len(reorder_rows)} MẶT HÀNG CẦN NHẬP) <<<")
    print(f"=======================================================")
    for rr in reorder_rows:
        print(f"  - SKU {rr['sku']:2}: {rr['product_name']:30} | Tồn={rr['usable_quantity']} | Tốc độ bán={rr['daily_sales']:.2f}/ngày | Gợi ý nhập={rr['suggested_quantity']} cái")

    # =========================================================================
    # BƯỚC 7: Kiểm tra đối soát Reconciliation qua Drill (Tháng 9 & Tháng 10)
    # =========================================================================
    print("\n7. Đang kiểm tra đối soát Reconciliation qua Drill...")
    # Tháng 9
    rec_sep = api.call('GET', '/api/analytics/reconciliation?from=2026-09-01&to=2026-09-30', user='admin')
    pass_sep = sum(1 for row in rec_sep.get('rows', []) if row.get('quality_status') == 'PASS')
    total_sep = len(rec_sep.get('rows', []))
    print(f"-> Đối soát Tháng 9: {pass_sep}/{total_sep} ngày đạt chuẩn PASS! (QueryId: {rec_sep.get('queryId')})")

    # Tháng 10
    rec_oct = api.call('GET', '/api/analytics/reconciliation?from=2026-10-01&to=2026-10-30', user='admin')
    pass_oct = sum(1 for row in rec_oct.get('rows', []) if row.get('quality_status') == 'PASS')
    total_oct = len(rec_oct.get('rows', []))
    print(f"-> Đối soát Tháng 10: {pass_oct}/{total_oct} ngày đạt chuẩn PASS! (QueryId: {rec_oct.get('queryId')})")

    # =========================================================================
    # BƯỚC 8: Lưu Manifest và báo cáo
    # =========================================================================
    out_dir = ROOT / 'database' / 'learning-dataset-5000'
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest_data = {
        'datasetName': 'learning-dataset-5000',
        'description': 'Bộ dữ liệu học tập quy mô 5,000 hóa đơn POS chuẩn Bách Hóa Xanh và vận hành siêu thị đa nguồn Big Data (60 ngày)',
        'generatedAt': dt.datetime.now(VN_TZ).isoformat(),
        'businessDateRange': {
            'startDate': str(start_d),
            'endDate': str(end_d),
            'totalDays': total_days,
            'periods': [
                {'period': 'Tháng 9/2026', 'startDate': '2026-09-01', 'endDate': '2026-09-30', 'passedDays': pass_sep, 'totalDays': total_sep},
                {'period': 'Tháng 10/2026', 'startDate': '2026-10-01', 'endDate': '2026-10-30', 'passedDays': pass_oct, 'totalDays': total_oct}
            ]
        },
        'customerProfiles': {
            'totalOrders': total_orders,
            'anonymousOrders': total_orders - named_count,
            'namedOrders': named_count,
            'anonymousPercent': round((total_orders - named_count) * 100 / total_orders, 1)
        },
        'inventoryStatus': {
            'soldOutProducts': [{'sku': sp['skuCode'], 'name': sp['productName'], 'stock': sp['stockQuantity']} for sp in sold_out_prods],
            'lowStockProducts': [{'sku': lp['skuCode'], 'name': lp['productName'], 'stock': lp['stockQuantity'], 'reorderPoint': lp['reorderPoint']} for lp in low_stock_prods]
        },
        'summary': {
            'totalOrdersInSystem': total_orders,
            'totalReturns': len(return_rows),
            'totalInventoryBatches': len(batch_rows),
            'reconciliationOverall': 'PASS' if (pass_sep == total_sep and pass_oct == total_oct) else 'CHECK',
            'month9QueryId': rec_sep.get('queryId'),
            'month10QueryId': rec_oct.get('queryId')
        }
    }
    (out_dir / 'manifest.json').write_text(json.dumps(manifest_data, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"\n-> Đã lưu manifest tại {out_dir / 'manifest.json'}")

    print("\n=== HOÀN TẤT NẠP VÀ ĐỐI SOÁT 5,000 HÓA ĐƠN POS BÁCH HÓA XANH (60 NGÀY) ===")


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
