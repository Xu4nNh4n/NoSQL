"""Prepare eight (or ten) small, current-day POS sales through the running API.

Default is read-only planning. Pass --apply to mutate. No SQL, resets, backdating,
stock refills, or master-data writes. A completed journal is read back, never reseeded.
An inflight/failed mutation blocks further writes: inspect the API and journal manually;
do not delete the journal and retry an ambiguous checkout. Credentials are never saved.
Example: python database/prepare-analytics-operations.py --apply --password 123456
"""
import argparse
import collections
import datetime as dt
from decimal import Decimal
import json
import os
from pathlib import Path
import sys
import tempfile
import urllib.error
import urllib.request
import uuid

VN = dt.timezone(dt.timedelta(hours=7))


def now():
    return dt.datetime.now(VN)


def encoded(value):
    return json.dumps(value, ensure_ascii=False, indent=2, default=str).encode('utf-8')


class Api:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.tokens = {}

    def call(self, method, path, body=None, account=None):
        headers = {'Content-Type': 'application/json'}
        if account:
            headers['Authorization'] = 'Bearer ' + self.tokens[account]
        request = urllib.request.Request(self.base + path, method=method, headers=headers,
                                         data=encoded(body) if body is not None else None)
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read(), parse_float=Decimal)

    def login(self, username, password, role):
        response = self.call('POST', '/api/auth/login', {'username': username, 'password': password})
        if response.get('role') != role:
            raise RuntimeError('Unexpected role for ' + username)
        self.tokens[username] = response['accessToken']


class Journal:
    def __init__(self, path, data):
        self.path, self.data = path, data

    def save(self):
        temporary = self.path.with_suffix('.tmp')
        with temporary.open('wb') as stream:
            stream.write(encoded(self.data))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, self.path)

    def mutate(self, api, key, method, path, body, account):
        previous = self.data['steps'].get(key)
        if previous:
            if previous['state'] != 'done':
                raise RuntimeError('Blocked by unresolved mutation ' + key + '; inspect journal/API, no retry')
            return previous['response']
        step = {'state': 'inflight', 'method': method, 'path': path, 'body': body,
                'account': account, 'startedAt': now().isoformat()}
        self.data['steps'][key] = step
        self.save()  # Persist the intent before sending anything to the server.
        try:
            response = api.call(method, path, body, account)
        except urllib.error.HTTPError as error:
            step.update(state='rejected' if 400 <= error.code < 500 else 'ambiguous',
                        httpStatus=error.code, error=error.read().decode('utf-8', errors='replace'))
            self.save()
            raise RuntimeError('Mutation stopped at ' + key + '; HTTP ' + str(error.code)) from error
        except Exception as error:
            step.update(state='ambiguous', error=type(error).__name__ + ': ' + str(error))
            self.save()
            raise RuntimeError('Ambiguous mutation ' + key + '; no automatic retry') from error
        step.update(state='done', response=response, finishedAt=now().isoformat())
        self.save()
        print('Recorded', key, response.get('orderId', response.get('returnId')), flush=True)
        return response


def observe(api, journal, path):
    orders, payments, returns = [], [], []
    for key, step in journal.data['steps'].items():
        if key.startswith('order-') and step['state'] == 'done':
            order_id = step['response']['orderId']
            orders.append(api.call('GET', '/api/orders/' + str(order_id), account='admin'))
            payments.extend(api.call('GET', f'/api/orders/{order_id}/payments', account='admin'))
            returns.extend(api.call('GET', f'/api/orders/{order_id}/returns', account='admin'))
    collected = sum((Decimal(str(p['amount'])) for p in payments if p['eventType'] == 'PAYMENT'), Decimal(0))
    refunded = sum((Decimal(str(p['amount'])) for p in payments if p['eventType'] == 'REFUND'), Decimal(0))
    pending = sum((Decimal(str(r['refundAmount'])) for r in returns if r['refundStatus'] == 'REFUND_PENDING'), Decimal(0))
    returned = sum((Decimal(str(r['refundAmount'])) for r in returns), Decimal(0))
    summary = {'source': 'Actual order/payment/return GET responses; not a Drill PASS assertion',
               'dateVN': journal.data['dateVN'], 'journal': str(journal.path),
               'orders': orders, 'payments': payments, 'returns': returns,
               'observed': {'orderCount': len(orders), 'paymentCount': sum(p['eventType'] == 'PAYMENT' for p in payments),
                            'refundPaymentCount': sum(p['eventType'] == 'REFUND' for p in payments),
                            'grossSubtotal': sum((Decimal(str(o['subtotal'])) for o in orders), Decimal(0)),
                            'discountAmount': sum((Decimal(str(o['discountAmount'])) for o in orders), Decimal(0)),
                            'collected': collected, 'confirmedRefunds': refunded,
                            'netCash': collected - refunded, 'pendingRefunds': pending,
                            'returnedValue': returned, 'retainedSalesValue': collected - returned,
                            'returnReceiptCount': len(returns)},
               'stockAfter': api.call('GET', '/api/products/active', account='nhanvien'),
               'limitations': ['No optional price-cost receipts added.', 'No backdating or historical stock snapshots.',
                              'Drill/Cassandra delivery and reconciliation are not asserted by this script.']}
    path.write_bytes(encoded(summary))
    print(json.dumps({'summary': str(path), 'journal': str(journal.path),
                      'orderIds': [o['orderId'] for o in orders], 'observed': summary['observed'],
                      'payments': payments, 'returns': returns}, ensure_ascii=False, indent=2, default=str))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--password', default=os.environ.get('PREPARE_API_PASSWORD'))
    parser.add_argument('--count', type=int, choices=(8, 10), default=8)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--journal', type=Path, default=Path(tempfile.gettempdir()) /
                        'supermarket-analytics-operations' / ('operations-' + now().date().isoformat() + '.json'))
    args = parser.parse_args()
    if not args.password:
        parser.error('Supply --password or PREPARE_API_PASSWORD')
    api = Api(args.base_url)
    contracts = api.call('GET', '/v3/api-docs')
    required = {'/api/orders': 'post', '/api/orders/quote': 'post', '/api/orders/{id}/return': 'put',
                '/api/orders/returns/{returnId}/refund': 'put', '/api/orders/{id}/payments': 'get',
                '/api/orders/{id}/returns': 'get', '/api/products/active': 'get'}
    for route, method in required.items():
        if method not in contracts.get('paths', {}).get(route, {}):
            raise RuntimeError('Missing API contract ' + method + ' ' + route)
    properties = contracts.get('components', {}).get('schemas', {}).get('SalesOrderRequest', {}).get('properties', {})
    if 'quoteToken' not in properties:
        raise RuntimeError('Running API does not expose the quoteToken checkout contract')
    api.login('admin', args.password, 'ADMIN')
    api.login('nhanvien', args.password, 'STAFF')
    journal_path = args.journal.expanduser().resolve()
    journal_path.parent.mkdir(parents=True, exist_ok=True)
    lock_path = journal_path.with_suffix('.lock')
    # A leftover lock is deliberate: require manual investigation after a process crash.
    descriptor = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    os.close(descriptor)
    try:
        data = json.loads(journal_path.read_text(encoding='utf-8')) if journal_path.exists() else None
        if data:
            if data['baseUrl'] != api.base or data['count'] != args.count:
                raise RuntimeError('Journal belongs to another API/count; refusing mutations')
            unresolved = [k for k, s in data['steps'].items() if s['state'] != 'done']
            if unresolved:
                raise RuntimeError('Unresolved mutation(s), no retries: ' + ', '.join(unresolved))
        else:
            products = sorted(api.call('GET', '/api/products/active', account='nhanvien'), key=lambda p: p['productId'])
            if len(products) < 5:
                raise RuntimeError('Need five existing active products; no products will be created')
            products = products[:5]
            names = ['Nguyễn Thị Lan', 'Trần Văn Minh', 'Lê Thu Hà', 'Phạm Quốc Huy', 'Hoàng Ngọc Mai',
                     'Vũ Đức Anh', 'Đặng Thanh Bình', 'Đỗ Thị Hương', 'Bùi Hải Nam', 'Đinh Ngọc Ánh']
            baskets = [[(0, 2), (1, 1)], [(1, 2), (2, 1)], [(2, 2), (4, 1)], [(3, 1), (0, 1)],
                       [(4, 2), (1, 1)], [(0, 1), (2, 2)], [(1, 1), (3, 1)], [(2, 1), (4, 2)],
                       [(0, 2), (4, 1)], [(1, 1), (2, 1)]]
            needed = collections.Counter()
            plan = []
            run_id = uuid.uuid4().hex[:12]
            for index, basket in enumerate(baskets[:args.count]):
                items = [{'productId': products[p]['productId'], 'quantity': q} for p, q in basket]
                for item in items:
                    needed[item['productId']] += item['quantity']
                quote = api.call('POST', '/api/orders/quote', {'items': items}, 'nhanvien')
                plan.append({'customerName': names[index], 'orderType': 'IN_STORE',
                             'paymentMethod': 'CASH' if index % 2 == 0 else 'BANK_TRANSFER',
                             'paymentReference': 'CK' + now().strftime('%Y%m%d') + run_id + str(index + 1),
                             'items': items, 'quoteToken': quote['quoteToken'], 'quote': quote})
            for product in products:
                if product['availableQuantity'] < needed[product['productId']]:
                    raise RuntimeError('Insufficient stock for product ' + str(product['productId']) + '; no refill or sale attempted')
            for order in plan[:2]:
                if not any(i['quantity'] == 2 and Decimal(str(i['netUnitPrice'])) > 0 for i in order['quote']['items']):
                    raise RuntimeError('The first two baskets need a positive-price two-unit line for partial returns')
            data = {'baseUrl': api.base, 'dateVN': now().date().isoformat(), 'count': args.count,
                    'runId': run_id, 'stockBefore': products, 'plan': plan, 'steps': {}}
        journal = Journal(journal_path, data)
        if not args.apply:
            print(encoded({'mode': 'read-only plan', 'orderCount': data['count'], 'journal': str(journal_path),
                           'stockBefore': data['stockBefore'], 'plan': data['plan']}).decode('utf-8'))
            return
        if data.get('complete'):
            # Completed runs only re-read their own IDs, even if those orders later change.
            observe(api, journal, journal_path.with_name(journal_path.stem + '-summary.json'))
            return
        if data['dateVN'] != now().date().isoformat() and not data.get('complete'):
            raise RuntimeError('Journal is from another Vietnam date; refusing new writes')
        journal.save()
        for index, order in enumerate(data['plan']):
            key = 'order-' + str(index + 1)
            if key in data['steps']:
                continue
            latest = api.call('POST', '/api/orders/quote', {'items': order['items']}, 'nhanvien')
            if latest['quoteToken'] != order['quoteToken']:
                raise RuntimeError('Prices changed after preflight; no automatic price acceptance')
            current = {p['productId']: p for p in api.call('GET', '/api/products/active', account='nhanvien')}
            if any(current.get(i['productId'], {}).get('availableQuantity', 0) < i['quantity'] for i in order['items']):
                raise RuntimeError('Stock changed; stopped without refill')
            body = {k: v for k, v in order.items() if k != 'quote'}
            journal.mutate(api, key, 'POST', '/api/orders', body, 'nhanvien')
        for index in (1, 2):
            order_id = data['steps']['order-' + str(index)]['response']['orderId']
            order = api.call('GET', '/api/orders/' + str(order_id), account='admin')
            line = next(i for i in order['items'] if i['quantity'] == 2 and Decimal(str(i['netUnitPrice'])) > 0)
            allocations = [a for a in line['batchAllocations'] if a['quantity'] - a.get('returnedQuantity', 0) > 0]
            if not allocations:
                raise RuntimeError('Missing return batch allocation')
            body = {'requestKey': 'TH' + data['runId'] + str(index), 'items': [
                {'orderItemId': line['orderItemId'], 'batchId': allocations[0]['batchId'], 'quantity': 1,
                 'restock': True, 'reason': 'Khách đổi nhu cầu, hàng còn nguyên vẹn'}]}
            receipt = journal.mutate(api, 'return-' + str(index), 'PUT', f'/api/orders/{order_id}/return', body, 'admin')
            if index == 1:
                amount = Decimal(str(receipt['refundAmount']))
                if amount <= 0:
                    raise RuntimeError('No positive refund to confirm; stopped')
                journal.mutate(api, 'refund-1', 'PUT', f"/api/orders/returns/{receipt['returnId']}/refund",
                               {'reference': 'HT' + data['runId'] + '1', 'amount': str(amount)}, 'admin')
        data['complete'] = True
        journal.save()
        observe(api, journal, journal_path.with_name(journal_path.stem + '-summary.json'))
    finally:
        lock_path.unlink()


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    try:
        main()
    except Exception as error:
        print('STOPPED:', type(error).__name__ + ':', str(error), file=sys.stderr)
        sys.exit(1)
