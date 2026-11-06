"""Reach 1,000 POS receipts through business APIs. Preview by default; --apply writes.
Run from the repository root. Local progress: .tmp/pos-orders-1000/.
"""
import argparse
import collections
import datetime as dt
import json
import os
import time
from pathlib import Path
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / '.tmp/pos-orders-1000'
MARKER = 'POS-ORDERS-1000'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--target', type=int, choices=[1000,3000,5000,7000], default=1000)
    args = parser.parse_args()
    target=args.target
    DIRECTORY=ROOT/f'.tmp/pos-orders-{target}'
    MARKER=f'POS-ORDERS-{target}'
    tokens = {}
    def api(method, path, body=None, user='admin'):
        if path != '/auth/login' and user not in tokens:
            tokens[user] = api('POST','/auth/login',{'username':user,'password':os.getenv('SEED_ACCOUNT_PASSWORD','123456')})['accessToken']
        headers={'Content-Type':'application/json'}
        if user in tokens: headers['Authorization']='Bearer '+tokens[user]
        request=urllib.request.Request('http://127.0.0.1:8080/api'+path, data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=headers,method=method)
        try:
            with urllib.request.urlopen(request,timeout=90) as response:
                data=response.read()
                return json.loads(data) if data else None
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f'{method} {path}: {exc.code}: {exc.read().decode()}') from None
    total=api('GET','/orders?page=0&size=1')['totalElements']
    print(f'Current receipts: {total}; target: {target}; missing: {max(0,target-total)}',flush=True)
    if not args.apply: return
    DIRECTORY.mkdir(parents=True,exist_ok=True)
    file=DIRECTORY/'progress.json'
    state=json.loads(file.read_text(encoding='utf-8')) if file.exists() else {}
    if '_inflight' in state: raise RuntimeError('Uncertain previous request; reconcile _inflight before resuming, do not blindly retry.')
    def save():
        temporary=file.with_suffix('.tmp')
        temporary.write_text(json.dumps(state,ensure_ascii=False),encoding='utf-8')
        temporary.replace(file)
    def mutate(key,path,body,user='admin'):
        if key in state:return state[key]
        state['_inflight']={'key':key,'path':path,'body':body};save()
        result=api('POST',path,body,user)
        state[key]={'orderId':result['orderId']} if key.startswith('order-') else result;del state['_inflight'];save()
        return result
    if 'plan' not in state:
        if total>=target:
            print('Target already reached; no changes.');return
        products=[]
        for page in range(1000):
            data=api('GET',f'/products?page={page}&size=100')
            products.extend(p for p in data['content'] if p.get('isActive',True))
            if page+1>=data['totalPages']:break
        if not products:raise RuntimeError('No active products')
        products.sort(key=lambda p:p['productId'])
        orders=[];demand=collections.Counter()
        surnames=['Nguyễn','Trần','Lê','Phạm','Hoàng','Vũ','Đặng','Bùi','Đỗ','Hồ']
        names=['Minh Anh','Thanh Bình','Ngọc Hà','Quốc Huy','Thu Lan','Hoài Nam','Phương Thảo','Gia Bảo','Khánh Linh','Đức Thành']
        for i in range(target-total):
            picks=[products[(i*7+j*13)%len(products)] for j in range(2+i%2)]
            quantities=[1+(i+j)%3 for j in range(len(picks))]
            items=[{'productId':p['productId'],'quantity':q} for p,q in zip(picks,quantities)]
            for item in items:demand[item['productId']]+=item['quantity']
            number=total+i+1
            orders.append({'orderType':'IN_STORE','paymentMethod':'BANK_TRANSFER' if i%3==0 else 'CASH','paymentReference':f'{MARKER}-PAY-{number}','customerName':f'Khách mẫu {number:04} - {surnames[i%10]} {names[(i//10)%10]}','note':f'DỮ LIỆU MẪU - {MARKER} - {number}','items':items})
        suppliers=[s for s in api('GET','/suppliers') if s['isActive']]
        if not suppliers:raise RuntimeError('No active supplier')
        today=dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date()
        batches=[]
        for p in products:
            shortage=demand[p['productId']]-p['availableQuantity']
            if shortage>0:
                batches.append({'productId':p['productId'],'supplierId':suppliers[0]['supplierId'],'batchCode':f'{MARKER}-{p["skuCode"]}','quantity':shortage+20,'unitCost':int(p['price']*0.7),'manufacturingDate':str(today-dt.timedelta(days=2)),'expiryDate':str(today+dt.timedelta(days=180))})
        state['plan']={'before':total,'date':str(today),'orders':orders,'batches':batches};save()
    plan=state['plan']
    for i,batch in enumerate(plan['batches']):mutate('lot-'+str(i),'/inventory/batches',batch)
    print(f'Replenishment ready: {len(plan["batches"])} lots.',flush=True)
    for i,order in enumerate(plan['orders']):
        mutate('order-'+str(i),'/orders',order,'nhanvien')
        if (i+1)%100==0:print(f'Created/resumed {i+1}/{len(plan["orders"])} receipts.',flush=True)
    api('POST','/integrations/catalog/sync',{})
    deadline=time.monotonic()+900
    round_number=0
    while time.monotonic()<deadline:
        status=api('GET','/integrations/status')
        if not status['mongodbPendingOutbox'] and not status['astraPendingOutbox']:break
        batch=api('POST','/integrations/dispatch',{})
        if not batch.get('attempted'):time.sleep(.5)
        round_number+=1
        if round_number%40==0:print(f'Sync pending: MongoDB={status["mongodbPendingOutbox"]}, Cassandra={status["astraPendingOutbox"]}',flush=True)
    status=api('GET','/integrations/status')
    if status['mongodbPendingOutbox'] or status['astraPendingOutbox']:raise RuntimeError('Outbox not empty; stop before benchmarking incomplete data.')
    exported=api('POST','/integrations/astra/events/export?date='+plan['date']+'&timeZone=Asia/Ho_Chi_Minh',{})
    result={'before':plan['before'],'addedOrders':len(plan['orders']),'addedLots':len(plan['batches']),'totalOrders':api('GET','/orders?page=0&size=1')['totalElements'],'integration':api('GET','/integrations/status'),'export':exported}
    (DIRECTORY/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False),flush=True)

if __name__=='__main__':main()
