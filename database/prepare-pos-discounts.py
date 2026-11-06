"""Prepare a small, repeatable learning dataset through the application's real APIs.

Run after schema/account setup: python database/prepare-pos-discounts.py --apply
No orders are created. Display names contain no testing labels.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get('SEED_API_URL', 'http://127.0.0.1:8080/api')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--near-expiry', action='store_true', help='Add two small near-expiry batches through the inventory API')
    args = parser.parse_args()
    if not args.apply:
        print('Plan: two suppliers, five products, six batches and four percentage promotions; no orders.')
        return
    token = None
    def api(method, path, body=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(BASE + path, method=method, headers=headers,
            data=None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8'))
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                data = response.read()
                return json.loads(data) if data else None
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'{method} {path}: HTTP {error.code}: {error.read().decode("utf-8")}') from None
    token = api('POST', '/auth/login', {'username': 'admin',
        'password': os.environ.get('SEED_ACCOUNT_PASSWORD', '123456')})['accessToken']
    suppliers = {s['supplierName']: s['supplierId'] for s in api('GET', '/suppliers')}
    for name, contact, address in [
        ('Công ty Thực phẩm An Phú', 'Nguyễn Minh Anh', 'Quận Bình Tân, TP. Hồ Chí Minh'),
        ('Công ty Gia dụng Minh Phát', 'Trần Hoàng Nam', 'Quận Tân Phú, TP. Hồ Chí Minh')]:
        if name not in suppliers:
            suppliers[name] = api('POST', '/suppliers', {'supplierName': name,
                'contactName': contact, 'address': address})['supplierId']
    products = {}
    page = 0
    while True:
        result = api('GET', f'/products?page={page}&size=100')
        products.update({p['skuCode']: p for p in result['content']})
        page += 1
        if page >= result['totalPages']:
            break
    # Vietnam is UTC+7 for the contemporary dates used by this dataset.
    now = dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).replace(tzinfo=None, microsecond=0)
    batches = {b['batchCode'] for b in api('GET', '/inventory/batches')}
    promotions = {(p['productId'], p['name']) for p in api('GET', '/promotions')}
    records = [
        ('1', 'Nước suối 500ml', 'Đồ uống', 10000, 6000, 10, 365),
        ('2', 'Sữa tươi không đường 1L', 'Sữa', 32000, 23000, 15, 60),
        ('3', 'Mì gói vị bò', 'Thực phẩm khô', 6500, 4000, None, 180),
        ('4', 'Nồi inox 18cm', 'Gia dụng', 185000, 125000, 20, None),
        ('5', 'Táo đỏ', 'Trái cây', 45000, 30000, None, 14),
    ]
    summary = []
    for sku, name, category, price, cost, percent, lifetime in records:
        if sku not in products:
            products[sku] = api('POST', '/products', {'productName': name, 'skuCode': sku,
                'price': price, 'stockQuantity': 0, 'reorderPoint': 10,
                'categoryName': category, 'isActive': True})
        product = products[sku]
        # Do not silently reuse a different catalog entry or overwrite its price.
        if product['productName'] != name or product['price'] != price:
            raise RuntimeError('SKU already belongs to a different product/price: ' + sku)
        supplier = suppliers['Công ty Gia dụng Minh Phát' if category == 'Gia dụng' else 'Công ty Thực phẩm An Phú']
        for offset in range(2 if sku == '1' else 1):
            code = str(100 + int(sku) * 10 + offset)
            if code not in batches:
                api('POST', '/inventory/batches', {'productId': product['productId'],
                    'supplierId': supplier, 'batchCode': code, 'quantity': 30 if offset else 50,
                    'unitCost': cost, 'manufacturingDate': (now.date() - dt.timedelta(days=7)).isoformat(),
                    'expiryDate': None if lifetime is None else
                        (now.date() + dt.timedelta(days=lifetime + offset * 30)).isoformat()})
                batches.add(code)
        if percent:
            title = 'Ưu đãi ' + name
            if (product['productId'], title) not in promotions:
                api('POST', '/promotions', {'productId': product['productId'], 'name': title,
                    'percent': percent, 'startsAt': (now - dt.timedelta(days=1)).isoformat(),
                    'endsAt': (now + dt.timedelta(days=30)).isoformat(), 'active': True})
        if sku == '5':
            title = 'Ưu đãi trái cây cuối tuần'
            if (product['productId'], title) not in promotions:
                api('POST', '/promotions', {'productId': product['productId'], 'name': title,
                    'percent': 10, 'startsAt': (now + dt.timedelta(days=3)).isoformat(),
                    'endsAt': (now + dt.timedelta(days=10)).isoformat(), 'active': True})
        summary.append({'sku': sku, 'name': name, 'price': price, 'currentDiscountPercent': percent or 0})
    if args.near_expiry:
        for sku, code, quantity, cost, days in [('2', '201', 5, 24000, 5), ('5', '202', 3, 29000, 3)]:
            if code not in batches:
                api('POST', '/inventory/batches', {'productId': products[sku]['productId'],
                    'supplierId': suppliers['Công ty Thực phẩm An Phú'], 'batchCode': code,
                    'quantity': quantity, 'unitCost': cost,
                    'manufacturingDate': (now.date() - dt.timedelta(days=7)).isoformat(),
                    'expiryDate': (now.date() + dt.timedelta(days=days)).isoformat()})
    print(json.dumps({'products': summary, 'ordersCreated': 0, 'nearExpiryRequested': args.near_expiry}, ensure_ascii=True, indent=2))


if __name__ == '__main__':
    main()
