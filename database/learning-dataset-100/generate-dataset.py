"""Sinh và nạp bộ dữ liệu học tập 100 hóa đơn mẫu cho hệ thống POS, Cassandra và Apache Drill.

Tuân thủ nghiêm ngặt theo đặc tả docs/antigravity/YEU_CAU_ANTIGRAVITY_TAO_DU_LIEU_BIGDATA.md:
- Dữ liệu học tập có nguồn gốc rõ ràng, không tiền tố DEMO/TEST/MẪU/SIM.
- Gọi qua API thật của hệ thống: quoteToken, FEFO lô hàng, ReturnPolicy, outbox sync.
- Có chế độ dry-run/preview (mặc định) và --apply để nạp dữ liệu.
- Cơ chế Journaling với lock file và checkpoint chống gián đoạn.
- Đồng bộ outbox sang Cassandra và JSON logs.
- Đối soát Drill và xuất manifest đầy đủ.
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
import sys
import time
import urllib.error
import urllib.request

VN_TZ = dt.timezone(dt.timedelta(hours=7))


def now_vn():
    return dt.datetime.now(VN_TZ)


def json_bytes(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2, default=str).encode('utf-8')


class ApiClient:
    def __init__(self, base_url):
        self.base = base_url.rstrip('/')
        self.tokens = {}

    def call(self, method, path, body=None, user=None):
        headers = {'Content-Type': 'application/json'}
        if user and user in self.tokens:
            headers['Authorization'] = 'Bearer ' + self.tokens[user]
        url = self.base + path
        data = json_bytes(body) if body is not None else None
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


class Journal:
    def __init__(self, path, data):
        self.path = Path(path)
        self.data = data

    def save(self):
        tmp = self.path.with_suffix('.tmp')
        with tmp.open('wb') as f:
            f.write(json_bytes(self.data))
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, self.path)

    def mutate(self, api, step_key, method, path, body, user):
        prev = self.data['steps'].get(step_key)
        if prev:
            if prev.get('state') == 'done':
                return prev.get('response')
            if prev.get('state') == 'inflight':
                raise RuntimeError(f'Step {step_key} was left inflight; verify server status before resuming')

        step_record = {
            'state': 'inflight',
            'method': method,
            'path': path,
            'body': body,
            'user': user,
            'startedAt': now_vn().isoformat()
        }
        self.data['steps'][step_key] = step_record
        self.save()

        try:
            resp = api.call(method, path, body, user)
        except Exception as exc:
            step_record['state'] = 'failed'
            step_record['error'] = str(exc)
            self.save()
            raise

        step_record['state'] = 'done'
        step_record['response'] = resp
        step_record['finishedAt'] = now_vn().isoformat()
        self.save()
        return resp


def get_all_products(api, user='nhanvien'):
    prods = {}
    page = 0
    while True:
        res = api.call('GET', f'/api/products?page={page}&size=100', user=user)
        for p in res['content']:
            prods[p['skuCode']] = p
        page += 1
        if page >= res['totalPages']:
            break
    return prods


def get_all_suppliers(api, user='admin'):
    return {s['supplierName']: s for s in api.call('GET', '/api/suppliers', user=user)}


def get_all_promotions(api, user='admin'):
    return api.call('GET', '/api/promotions', user=user)


def query_drill(sql, drill_url='http://localhost:8048'):
    req = urllib.request.Request(
        drill_url.rstrip('/') + '/query.json',
        headers={'Content-Type': 'application/json'},
        data=json.dumps({'queryType': 'SQL', 'query': sql}).encode('utf-8')
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Thực thi nạp dữ liệu thay vì chỉ lập plan')
    parser.add_argument('--count', type=int, default=100, help='Số lượng hóa đơn POS mẫu cần sinh (mặc định: 100)')
    parser.add_argument('--seed', type=int, default=42, help='Hạt giống sinh ngẫu nhiên tái lập (mặc định: 42)')
    parser.add_argument('--base-url', default='http://127.0.0.1:8080', help='URL Backend API')
    parser.add_argument('--drill-url', default='http://localhost:8048', help='URL Apache Drill')
    parser.add_argument('--password', default=os.environ.get('PREPARE_API_PASSWORD', '123456'))
    parser.add_argument('--output-dir', type=Path, default=Path(__file__).resolve().parent)
    args = parser.parse_args()

    rnd = random.Random(args.seed)
    api = ApiClient(args.base_url)

    print('=== Antigravity BigData POS Dataset Generator ===')
    print(f'Mode: {"APPLY (Ghi dữ liệu)" if args.apply else "PLAN / DRY-RUN (Chỉ kiểm tra)"}')
    print(f'Mục tiêu: {args.count} hóa đơn POS mẫu | Seed: {args.seed}')
    print(f'API: {args.base_url} | Drill: {args.drill_url}')

    # Đăng nhập các tài khoản cần thiết
    api.login('admin', args.password)
    api.login('nhanvien', args.password)

    out_dir = args.output_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    journal_path = out_dir / 'journal.json'
    lock_path = out_dir / 'generator.lock'

    # Thiết lập lock chống chạy đồng thời
    lock_fd = None
    try:
        lock_fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except OSError:
        raise RuntimeError(f'Khóa đang tồn tại tại {lock_path}. Hãy kiểm tra tiến trình trước hoặc xóa khóa nếu đã kết thúc!')

    try:
        journal_data = json.loads(journal_path.read_text(encoding='utf-8')) if journal_path.exists() else None
        if not journal_data:
            journal_data = {
                'baseUrl': args.base_url,
                'targetCount': args.count,
                'seed': args.seed,
                'createdAt': now_vn().isoformat(),
                'steps': {}
            }
        journal = Journal(journal_path, journal_data)

        # ----------------------------------------------------
        # 1. KHẢO SÁT VÀ BỔ SUNG NHÀ CUNG CẤP (SUPPLIERS)
        # ----------------------------------------------------
        existing_suppliers = get_all_suppliers(api, 'admin')
        new_suppliers_def = [
            ('Công ty Nông sản Xanh Việt', 'Lê Hồng Phong', '0903112233', 'Huyện Củ Chi, TP. Hồ Chí Minh'),
            ('Công ty CP Đồ uống Đại Dương', 'Vũ Hoàng Long', '0918223344', 'Huyện Bình Chánh, TP. Hồ Chí Minh'),
            ('Công ty TNHH Hóa mỹ phẩm Đông Á', 'Phạm Thị Bích', '0988334455', 'Quận 12, TP. Hồ Chí Minh'),
            ('Công ty CP Bánh kẹo & Gia vị Tân Bình', 'Đỗ Quốc Huy', '0977445566', 'Quận Tân Bình, TP. Hồ Chí Minh'),
        ]

        if not args.apply:
            print(f'[Plan] Kiểm tra nhà cung cấp: hiện có {len(existing_suppliers)}, dự kiến bổ sung {len(new_suppliers_def)}.')
        else:
            for sname, cname, sphone, saddr in new_suppliers_def:
                if sname not in existing_suppliers:
                    step_key = f'supplier-{hashlib.md5(sname.encode()).hexdigest()[:8]}'
                    res = journal.mutate(api, step_key, 'POST', '/api/suppliers', {
                        'supplierName': sname, 'contactName': cname, 'phone': sphone, 'address': saddr
                    }, 'admin')
                    existing_suppliers[sname] = res
            print(f'Nhà cung cấp sẵn sàng: tổng {len(existing_suppliers)} đơn vị.')

        # ----------------------------------------------------
        # 2. KHẢO SÁT VÀ BỔ SUNG SẢN PHẨM (PRODUCTS)
        # ----------------------------------------------------
        existing_products = get_all_products(api, 'nhanvien')
        new_products_def = [
            # sku, name, category, price, cost, supplier_name, expiry_days
            ('6', 'Trà xanh không độ 450ml', 'Đồ uống', 12000, 7500, 'Công ty CP Đồ uống Đại Dương', 180),
            ('7', 'Cà phê sữa hòa tan 16g*10', 'Đồ uống', 42000, 28000, 'Công ty CP Đồ uống Đại Dương', 365),
            ('8', 'Sữa chua có đường 100g', 'Sữa', 8000, 5500, 'Công ty Thực phẩm An Phú', 45),
            ('9', 'Gạo thơm lài túi 5kg', 'Thực phẩm khô', 125000, 90000, 'Công ty Nông sản Xanh Việt', 365),
            ('10', 'Dầu ăn đậu nành chai 1L', 'Thực phẩm khô', 55000, 38000, 'Công ty Thực phẩm An Phú', 730),
            ('11', 'Nước mắm cá cơm chai 500ml', 'Gia vị', 48000, 32000, 'Công ty CP Bánh kẹo & Gia vị Tân Bình', 540),
            ('12', 'Hạt nêm thịt thăn gói 400g', 'Gia vị', 36000, 24000, 'Công ty CP Bánh kẹo & Gia vị Tân Bình', 365),
            ('13', 'Đường cát trắng túi 1kg', 'Gia vị', 28000, 19000, 'Công ty CP Bánh kẹo & Gia vị Tân Bình', 730),
            ('14', 'Bánh quy bơ hộp 200g', 'Bánh kẹo', 45000, 30000, 'Công ty CP Bánh kẹo & Gia vị Tân Bình', 270),
            ('15', 'Snack khoai tây gói 60g', 'Bánh kẹo', 15000, 9500, 'Công ty CP Bánh kẹo & Gia vị Tân Bình', 180),
            ('16', 'Dầu gội sạch gàu chai 650g', 'Chăm sóc cá nhân', 145000, 105000, 'Công ty TNHH Hóa mỹ phẩm Đông Á', 1095),
            ('17', 'Kem đánh răng tuýp 150g', 'Chăm sóc cá nhân', 38000, 25000, 'Công ty TNHH Hóa mỹ phẩm Đông Á', 1095),
            ('18', 'Nước rửa chén chai 750g', 'Vệ sinh nhà cửa', 32000, 21000, 'Công ty TNHH Hóa mỹ phẩm Đông Á', 730),
            ('19', 'Nước giặt đậm đặc túi 2kg', 'Vệ sinh nhà cửa', 115000, 80000, 'Công ty TNHH Hóa mỹ phẩm Đông Á', 730),
            ('20', 'Chảo chống dính 24cm', 'Gia dụng', 165000, 110000, 'Công ty Gia dụng Minh Phát', None),
        ]

        if not args.apply:
            print(f'[Plan] Kiểm tra sản phẩm: hiện có {len(existing_products)}, dự kiến bổ sung {len(new_products_def)}.')
        else:
            for sku, pname, cat, price, cost, sname, exp_days in new_products_def:
                if sku not in existing_products:
                    step_key = f'product-{sku}'
                    res = journal.mutate(api, step_key, 'POST', '/api/products', {
                        'productName': pname,
                        'skuCode': sku,
                        'price': price,
                        'stockQuantity': 0,
                        'reorderPoint': 15,
                        'categoryName': cat,
                        'isActive': True
                    }, 'admin')
                    existing_products[sku] = res
            existing_products = get_all_products(api, 'nhanvien')
            print(f'Sản phẩm sẵn sàng: tổng {len(existing_products)} SKU.')

        # ----------------------------------------------------
        # 3. KHẢO SÁT VÀ BỔ SUNG LÔ HÀNG (INVENTORY BATCHES)
        # ----------------------------------------------------
        today = now_vn().date()
        existing_batches = {b['batchCode']: b for b in api.call('GET', '/api/inventory/batches', user='admin')}

        batches_to_create = []
        for sku, pname, cat, price, cost, sname, exp_days in new_products_def:
            p = existing_products.get(sku)
            if not p:
                continue
            s = existing_suppliers.get(sname)
            if not s:
                continue
            pid = p['productId']
            sid = s['supplierId']
            # Mỗi sản phẩm tạo 1 lô chính (150 đơn vị)
            code_main = f'LOT-{sku}-01'
            if code_main not in existing_batches:
                mfg = (today - dt.timedelta(days=10)).isoformat()
                exp = (today + dt.timedelta(days=exp_days)).isoformat() if exp_days else None
                batches_to_create.append({
                    'code': code_main, 'productId': pid, 'supplierId': sid,
                    'quantity': 150, 'unitCost': cost,
                    'manufacturingDate': mfg, 'expiryDate': exp
                })
            # Riêng sữa chua (sku 8) và sữa tươi (sku 2): tạo thêm 1 lô nhỏ cận hạn (6 ngày) để báo cáo EXPIRY hiển thị
            if sku == '8':
                code_near = 'LOT-8-NEAR'
                if code_near not in existing_batches:
                    batches_to_create.append({
                        'code': code_near, 'productId': pid, 'supplierId': sid,
                        'quantity': 10, 'unitCost': cost,
                        'manufacturingDate': (today - dt.timedelta(days=30)).isoformat(),
                        'expiryDate': (today + dt.timedelta(days=6)).isoformat()
                    })

        # Bổ sung thêm lô cho 5 sản phẩm đầu nếu cần đảm bảo tồn kho dồi dào
        for sku in ['1', '2', '3', '4', '5']:
            p = existing_products.get(sku)
            if p:
                code_extra = f'LOT-{sku}-REPLENISH'
                if code_extra not in existing_batches:
                    s_id = existing_suppliers['Công ty Gia dụng Minh Phát']['supplierId'] if p['categoryName'] == 'Gia dụng' else existing_suppliers['Công ty Thực phẩm An Phú']['supplierId']
                    exp = (today + dt.timedelta(days=180)).isoformat() if p['categoryName'] != 'Gia dụng' else None
                    batches_to_create.append({
                        'code': code_extra, 'productId': p['productId'], 'supplierId': s_id,
                        'quantity': 100, 'unitCost': int(Decimal(str(p['price'])) * Decimal('0.65')),
                        'manufacturingDate': (today - dt.timedelta(days=5)).isoformat(),
                        'expiryDate': exp
                    })

        if not args.apply:
            print(f'[Plan] Kiểm tra lô kho: dự kiến nhập {len(batches_to_create)} lô mới.')
        else:
            for b in batches_to_create:
                step_key = f'batch-{b["code"]}'
                journal.mutate(api, step_key, 'POST', '/api/inventory/batches', {
                    'productId': b['productId'],
                    'supplierId': b['supplierId'],
                    'batchCode': b['code'],
                    'quantity': b['quantity'],
                    'unitCost': b['unitCost'],
                    'manufacturingDate': b['manufacturingDate'],
                    'expiryDate': b['expiryDate']
                }, 'admin')
            existing_batches = {b['batchCode']: b for b in api.call('GET', '/api/inventory/batches', user='admin')}
            print(f'Lô kho sẵn sàng: tổng {len(existing_batches)} lô hàng.')

        # ----------------------------------------------------
        # 4. KHẢO SÁT VÀ BỔ SUNG KHUYẾN MÃI (PROMOTIONS)
        # ----------------------------------------------------
        existing_promos = {(p['productId'], p['name']): p for p in get_all_promotions(api, 'admin')}
        new_promos_def = [
            ('6', 'Ưu đãi Trà xanh ngày hè', 10),
            ('9', 'Ưu đãi Gạo ngon gia đình', 5),
            ('14', 'Ưu đãi Bánh quy bơ', 15),
            ('18', 'Ưu đãi Nước rửa chén', 10),
            ('20', 'Ưu đãi Đồ dùng nhà bếp', 20),
        ]
        promos_to_create = []
        for sku, title, pct in new_promos_def:
            p = existing_products.get(sku)
            if p and (p['productId'], title) not in existing_promos:
                promos_to_create.append({
                    'productId': p['productId'], 'name': title, 'percent': pct,
                    'startsAt': (now_vn() - dt.timedelta(days=2)).replace(tzinfo=None, microsecond=0).isoformat(),
                    'endsAt': (now_vn() + dt.timedelta(days=28)).replace(tzinfo=None, microsecond=0).isoformat(),
                    'active': True
                })

        if not args.apply:
            print(f'[Plan] Kiểm tra khuyến mãi: dự kiến thêm {len(promos_to_create)} chương trình.')
        else:
            for pr in promos_to_create:
                step_key = f'promo-{pr["productId"]}-{hashlib.md5(pr["name"].encode()).hexdigest()[:6]}'
                journal.mutate(api, step_key, 'POST', '/api/promotions', pr, 'admin')
            print(f'Khuyến mãi sẵn sàng: tổng {len(get_all_promotions(api, "admin"))} chương trình.')

        # ----------------------------------------------------
        # 5. THIẾT KẾ 100 ĐƠN HÀNG POS (ORDER BASKETS)
        # ----------------------------------------------------
        active_products = [p for p in existing_products.values() if p.get('isActive', True)]
        active_products.sort(key=lambda x: int(x['productId']))

        surnames = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương']
        middles = ['Văn', 'Thị', 'Đức', 'Thanh', 'Hữu', 'Ngọc', 'Minh', 'Quang', 'Hồng', 'Mai']
        firstnames = ['Anh', 'Bình', 'Cường', 'Dũng', 'Giang', 'Hà', 'Huy', 'Khánh', 'Lan', 'Mai', 'Nam', 'Phong', 'Quân', 'Sơn', 'Thảo', 'Trang', 'Tuấn', 'Việt']

        orders_plan = []
        run_tag = f'D{today.strftime("%Y%m%d")}'

        for i in range(args.count):
            # Chọn 1 đến 4 sản phẩm ngẫu nhiên có kiểm soát
            basket_size = rnd.choices([1, 2, 3, 4], weights=[25, 45, 20, 10])[0]
            chosen_prods = rnd.sample(active_products, basket_size)
            items = []
            for cp in chosen_prods:
                # Đồ gia dụng thường mua 1 món, đồ tiêu dùng có thể mua 1-3 món
                qty = 1 if cp['categoryName'] == 'Gia dụng' else rnd.randint(1, 3)
                items.append({'productId': cp['productId'], 'quantity': qty})

            # Thanh toán: ~60% tiền mặt, ~40% chuyển khoản
            is_cash = (rnd.random() < 0.6)
            pay_method = 'CASH' if is_cash else 'BANK_TRANSFER'
            pay_ref = f'CK{today.strftime("%Y%m%d")}{run_tag}{i+1:04d}' if not is_cash else None

            # Khách hàng: 70% có tên hư cấu, 30% khách vãng lai không tên
            if rnd.random() < 0.7:
                cust_name = f'{rnd.choice(surnames)} {rnd.choice(middles)} {rnd.choice(firstnames)}'
                cust_phone = f'09{rnd.randint(10000000, 99999999)}' if rnd.random() < 0.5 else None
            else:
                cust_name = None
                cust_phone = None

            order_def = {
                'index': i + 1,
                'orderType': 'IN_STORE',
                'paymentMethod': pay_method,
                'paymentReference': pay_ref,
                'customerName': cust_name,
                'customerPhone': cust_phone,
                'items': items,
                'note': 'Mua hàng tại quầy POS'
            }
            orders_plan.append(order_def)

        if not args.apply:
            print(f'[Plan] Đã lập kế hoạch {len(orders_plan)} đơn hàng POS. Để thực thi, hãy chạy với cờ --apply.')
            print('Mẫu đơn số 1:', json.dumps(orders_plan[0], ensure_ascii=False, indent=2))
            return

        # ----------------------------------------------------
        # 6. THỰC THI NẠP 100 ĐƠN HÀNG QUA API CHÍNH THỨC
        # ----------------------------------------------------
        print(f'\n--- Bắt đầu nạp {len(orders_plan)} đơn hàng POS ---')
        created_orders = []
        for o in orders_plan:
            idx = o['index']
            step_key = f'order-{idx:03d}'
            prev = journal.data['steps'].get(step_key)
            if prev and prev.get('state') == 'done':
                created_orders.append(prev['response'])
                continue

            # BƯỚC BẮT BUỘC THEO CONTRACT: POST /api/orders/quote
            quote = api.call('POST', '/api/orders/quote', {'items': o['items']}, user='nhanvien')
            quote_token = quote['quoteToken']

            order_payload = {
                'orderType': o['orderType'],
                'paymentMethod': o['paymentMethod'],
                'customerName': o['customerName'],
                'customerPhone': o['customerPhone'],
                'quoteToken': quote_token,
                'items': o['items'],
                'note': o['note']
            }
            if o['paymentReference']:
                order_payload['paymentReference'] = o['paymentReference']

            res = journal.mutate(api, step_key, 'POST', '/api/orders', order_payload, user='nhanvien')
            created_orders.append(res)

            if idx % 10 == 0 or idx == len(orders_plan):
                print(f'-> Đã nạp thành công {idx}/{len(orders_plan)} đơn hàng (OrderId: {res["orderId"]})...', flush=True)

        print(f'Hoàn thành nạp {len(created_orders)} đơn hàng POS.')

        # ----------------------------------------------------
        # 7. THỰC THI TRẢ HÀNG VÀ HOÀN TIỀN (RETURNS & REFUNDS)
        # ----------------------------------------------------
        # Chọn 3 đơn trong danh sách để thực hiện trả hàng:
        # 1. Đơn 15: Trả 1 phần, hàng nguyên vẹn (restock=True), đã hoàn tiền
        # 2. Đơn 32: Trả hàng bị móp rách (restock=False), đã hoàn tiền
        # 3. Đơn 55: Trả hàng (restock=True), giữ ở trạng thái REFUND_PENDING
        print('\n--- Bắt đầu xử lý trả hàng và hoàn tiền học tập ---')
        return_scenarios = [
            {'idx': 15, 'restock': True, 'reason': 'Khách mua nhầm quy cách, đổi lại chai khác', 'refund_now': True},
            {'idx': 32, 'restock': False, 'reason': 'Bao bì bị rách khi vận chuyển về nhà', 'refund_now': True},
            {'idx': 55, 'restock': True, 'reason': 'Khách mua thừa số lượng cần dùng', 'refund_now': False},
        ]

        returns_executed = []
        for scen in return_scenarios:
            ord_resp = created_orders[scen['idx'] - 1]
            ord_id = ord_resp['orderId']
            full_ord = api.call('GET', f'/api/orders/{ord_id}', user='admin')
            # Lấy 1 dòng hàng còn nguyên để trả 1 đơn vị
            target_item = next(i for i in full_ord['items'] if i['quantity'] > (i.get('returnedQuantity') or 0))
            order_item_id = target_item['orderItemId']
            allocations = [a for a in target_item['batchAllocations'] if a['quantity'] - a.get('returnedQuantity', 0) > 0]
            batch_id = allocations[0]['batchId'] if allocations else None

            ret_key = f'return-{scen["idx"]}'
            prev_ret = journal.data['steps'].get(ret_key)
            if not prev_ret or prev_ret.get('state') != 'done':
                return_body = {
                    'requestKey': f'TH{run_tag}{scen["idx"]}',
                    'items': [{
                        'orderItemId': order_item_id,
                        'batchId': batch_id,
                        'quantity': 1,
                        'restock': scen['restock'],
                        'reason': scen['reason']
                    }]
                }
                ret_res = journal.mutate(api, ret_key, 'PUT', f'/api/orders/{ord_id}/return', return_body, user='admin')
            else:
                ret_res = prev_ret['response']

            returns_executed.append(ret_res)
            print(f'-> Phiếu trả hàng: ReturnId={ret_res["returnId"]} cho OrderId={ord_id}, RefundAmount={ret_res["refundAmount"]}')

            # Xác nhận hoàn tiền nếu kịch bản yêu cầu
            if scen['refund_now']:
                refund_key = f'refund-{scen["idx"]}'
                prev_ref = journal.data['steps'].get(refund_key)
                if not prev_ref or prev_ref.get('state') != 'done':
                    ref_amount = Decimal(str(ret_res['refundAmount']))
                    if ref_amount > 0:
                        ref_body = {
                            'reference': f'HT{run_tag}{scen["idx"]}',
                            'amount': str(ref_amount)
                        }
                        ref_res = journal.mutate(api, refund_key, 'PUT', f'/api/orders/returns/{ret_res["returnId"]}/refund', ref_body, user='admin')
                        print(f'   -> Đã xác nhận hoàn tiền: Ref={ref_body["reference"]}, Amount={ref_amount}')

        # ----------------------------------------------------
        # 8. ĐỒNG BỘ OUTBOX SANG CASSANDRA & POS PAYMENT LOGS
        # ----------------------------------------------------
        print('\n--- Đồng bộ Outbox sang Cassandra và JSON logs ---')
        deadline = time.monotonic() + 180
        dispatched_total = 0
        while time.monotonic() < deadline:
            st = api.call('GET', '/api/integrations/status', user='admin')
            pending = st.get('astraPendingOutbox', 0) + st.get('posLog', {}).get('pending', 0)
            if pending == 0:
                print('-> Toàn bộ outbox đã được đồng bộ thành công!')
                break
            batch_res = api.call('POST', '/api/integrations/dispatch', user='admin')
            dispatched_total += batch_res.get('sent', 0)
            print(f'-> Dispatching... còn pending={pending}, đã gửi {dispatched_total}', flush=True)
            time.sleep(1)

        # ----------------------------------------------------
        # 9. ĐỐI SOÁT DỮ LIỆU VÀ TRUY VẤN DRILL
        # ----------------------------------------------------
        print('\n--- Thực hiện đối soát dữ liệu qua Drill & API ---')
        today_str = today.isoformat()
        rec_data = api.call('GET', f'/api/analytics/reconciliation?from={today_str}&to={today_str}', user='admin')
        print(f'Reconciliation Status: {rec_data["rows"][0]["quality_status"]} (QueryId: {rec_data["queryId"]})')
        print(f'MySQL Orders: {rec_data["rows"][0]["mysql_order_count"]} | Payments: {rec_data["rows"][0]["mysql_payment_count"]} | Refunds: {rec_data["rows"][0]["mysql_refund_count"]}')
        print(f'Cassandra Payments: {rec_data["rows"][0]["astra_payment_count"]} | Refunds: {rec_data["rows"][0]["astra_refund_count"]}')
        print(f'Collected Delta: {rec_data["rows"][0]["collected_delta"]} | Refunded Delta: {rec_data["rows"][0]["refunded_delta"]}')

        # Chạy các báo cáo vận hành lấy QueryId minh chứng
        reports_provenance = {}
        for r_name in ['SALES', 'EXPIRY', 'REPLENISHMENT', 'PROMOTIONS']:
            try:
                rep_res = api.call('GET', f'/api/analytics/operational?report={r_name}&from={today_str}&to={today_str}', user='admin')
                reports_provenance[r_name] = {
                    'queryId': rep_res.get('queryId'),
                    'rowCount': len(rep_res.get('rows', [])),
                    'sample': rep_res.get('rows', [])[:3]
                }
                print(f'Report {r_name:13}: QueryId={rep_res.get("queryId")}, Rows={len(rep_res.get("rows", []))}')
            except Exception as e:
                print(f'Report {r_name:13}: ERROR {e}')

        # Báo cáo Cash-Flow
        try:
            cf_res = api.call('GET', f'/api/analytics/cash-flow?from={today_str}&to={today_str}', user='admin')
            reports_provenance['CASH_FLOW'] = {
                'queryId': cf_res.get('queryId'),
                'rows': cf_res.get('rows', [])
            }
            print(f'Report CASH_FLOW    : QueryId={cf_res.get("queryId")}, Rows={len(cf_res.get("rows", []))}')
        except Exception as e:
            print(f'Report CASH_FLOW    : ERROR {e}')

        # ----------------------------------------------------
        # 10. XUẤT MANIFEST THEO SECTION 14 CỦA ĐẶC TẢ
        # ----------------------------------------------------
        print('\n--- Xuất Manifest bộ dữ liệu học tập ---')
        # Đếm tổng bảng MySQL
        mysql_counts = {}
        for t in ['roles', 'users', 'suppliers', 'products', 'promotions', 'inventory_batches',
                  'stock_movements', 'sales_orders', 'sales_order_items', 'sales_order_returns',
                  'payment_records', 'data_sync_outbox']:
            res = query_drill(f'SELECT COUNT(*) as cnt FROM mysql_source.supermarket_api.{t}', args.drill_url)
            mysql_counts[t] = res['rows'][0]['cnt']

        # Đếm sự kiện Cassandra
        cas_res = query_drill('SELECT COUNT(*) as cnt FROM cassandra_source.supermarket_analytics.business_events_by_day', args.drill_url)
        cassandra_event_count = cas_res['rows'][0]['cnt']

        # Đếm POS payment JSON logs
        pos_log_dir = Path('analytics/pos-payments') / today_str
        pos_logs_count = len(list(pos_log_dir.glob('*.json'))) if pos_log_dir.exists() else 0

        manifest = {
            'datasetName': 'learning-dataset-100',
            'description': 'Bộ dữ liệu học tập 100 hóa đơn POS và vận hành chuẩn hóa cho siêu thị',
            'generatedAt': now_vn().isoformat(),
            'businessDate': today_str,
            'seed': args.seed,
            'sourceType': 'Học tập do nhóm xây dựng thông qua API nghiệp vụ chính thức',
            'summary': {
                'ordersCreated': len(created_orders),
                'returnsCreated': len(returns_executed),
                'mysqlTableCounts': mysql_counts,
                'cassandraEventCount': cassandra_event_count,
                'posPaymentJsonLogsCount': pos_logs_count,
                'reconciliation': {
                    'qualityStatus': rec_data['rows'][0]['quality_status'],
                    'queryId': rec_data['queryId'],
                    'collectedAmount': rec_data['rows'][0]['mysql_collected_amount'],
                    'refundedAmount': rec_data['rows'][0]['mysql_refunded_amount'],
                    'orderSubtotal': rec_data['rows'][0]['mysql_order_subtotal'],
                    'itemSubtotal': rec_data['rows'][0]['mysql_item_subtotal'],
                },
                'reportsProvenance': reports_provenance
            }
        }

        manifest_path = out_dir / 'manifest.json'
        manifest_path.write_bytes(json_bytes(manifest))
        print(f'-> Đã lưu manifest tại {manifest_path}')

        journal.data['complete'] = True
        journal.data['finishedAt'] = now_vn().isoformat()
        journal.save()
        print('=== Hoàn thành toàn bộ quy trình tạo dữ liệu học tập ===')

    finally:
        if lock_fd is not None:
            os.close(lock_fd)
            try:
                lock_path.unlink()
            except OSError:
                pass


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
