"""Append the first POS data batch through existing business APIs; never delete data.

Run from repository root: python database/seed-pos-operations.py --apply
Progress and pre-load snapshots are kept locally in .tmp/pos-first-1000/.
Receipts use today's date: the POS API does not support historical timestamps.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / '.tmp' / 'pos-first-1000'
MARKER = 'POS1000-01'
GROUPS = [
 ('Đồ uống', [('Trà xanh chai 455ml',10000),('Nước cam hộp 1L',35000),('Nước ngọt cola lon 330ml',11000),('Nước khoáng có ga 500ml',12000),('Cà phê đen lon 180ml',15000)]),
 ('Sữa', [('Sữa tươi có đường hộp 180ml',8000),('Sữa tươi ít đường hộp 1L',34000),('Sữa chua có đường lốc 4 hộp',28000),('Sữa đặc lon 380g',27000),('Sữa đậu nành hộp 1L',24000)]),
 ('Thực phẩm khô', [('Gạo thơm túi 5kg',125000),('Mì tôm chua cay gói 75g',5000),('Bún khô gói 400g',24000),('Nui xoắn gói 400g',22000),('Miến dong gói 200g',19000)]),
 ('Bánh kẹo', [('Bánh quy bơ hộp 200g',39000),('Bánh gạo gói 150g',22000),('Kẹo sữa gói 120g',18000),('Sô cô la thanh 40g',25000),('Bánh bông lan gói 200g',32000)]),
 ('Gia vị', [('Nước mắm chai 500ml',38000),('Dầu đậu nành chai 1L',55000),('Muối i-ốt gói 500g',7000),('Đường trắng túi 1kg',26000),('Hạt nêm gói 400g',39000)]),
 ('Đồ hộp', [('Cá ngừ ngâm dầu hộp 185g',32000),('Cá sốt cà hộp 155g',19000),('Thịt hộp 170g',37000),('Bắp ngọt hộp 340g',26000),('Đậu đỏ hộp 400g',28000)]),
 ('Chăm sóc cá nhân', [('Kem đánh răng tuýp 180g',39000),('Bàn chải mềm chiếc',18000),('Dầu gội chai 350ml',79000),('Sữa tắm chai 500ml',89000),('Xà phòng bánh 90g',15000)]),
 ('Vệ sinh nhà cửa', [('Nước rửa chén chai 750ml',35000),('Nước lau sàn chai 1L',42000),('Bột giặt túi 800g',46000),('Giấy vệ sinh lốc 6 cuộn',39000),('Túi rác cuộn 500g',26000)]),
 ('Gia dụng', [('Chén sứ trắng chiếc',18000),('Ly thủy tinh 300ml chiếc',22000),('Hộp thực phẩm nhựa 1L',29000),('Khăn lau bếp bộ 3 chiếc',25000),('Muỗng inox bộ 6 chiếc',45000)]),
 ('Trái cây đóng gói', [('Cam vàng túi 1kg',45000),('Chuối túi 1kg',28000),('Lê xanh túi 1kg',49000),('Nho xanh hộp 500g',69000),('Ổi túi 1kg',25000)]),
]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    print('Plan: 50 products, 5 suppliers, 75 batches, 110 sales, 275 lines, 10 returns, 6 refunds; about 1,001 business rows.', flush=True)
    if not args.apply:
        return
    STATE.mkdir(parents=True, exist_ok=True)
    journal = STATE / 'progress.json'
    state = json.loads(journal.read_text(encoding='utf-8')) if journal.exists() else {}
    if '_inflight' in state:
        raise RuntimeError('Previous request outcome uncertain. Reconcile _inflight in local progress before resuming; no blind retry.')
    tokens = {}

    def save():
        temp = journal.with_suffix('.tmp')
        temp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
        temp.replace(journal)

    def api(method, path, body=None, role='admin'):
        if role not in tokens and path != '/auth/login':
            tokens[role] = api('POST', '/auth/login', {'username':role,'password':os.environ.get('SEED_ACCOUNT_PASSWORD','123456')})['accessToken']
        headers = {'Content-Type':'application/json'}
        if role in tokens:
            headers['Authorization'] = 'Bearer ' + tokens[role]
        data = json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
        request = urllib.request.Request('http://127.0.0.1:8080/api'+path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f'{method} {path}: HTTP {exc.code}: {exc.read().decode("utf-8")}') from None

    def mutate(key, method, path, body, role='admin'):
        if key in state:
            return state[key]
        state['_inflight'] = {'key':key,'method':method,'path':path,'body':body}
        save()
        response = api(method,path,body,role)
        state[key] = response
        del state['_inflight']
        save()
        return response

    def all_products():
        result=[]
        page=0
        while True:
            batch=api('GET',f'/products?page={page}&size=100')
            result.extend(batch['content'])
            page+=1
            if page >= batch['totalPages']:
                return result

    if '_before' not in state:
        existing=all_products()
        snapshot={'products':existing,'suppliers':api('GET','/suppliers'),'batches':api('GET','/inventory/batches'),'orders':api('GET','/orders?page=0&size=100'),'integration':api('GET','/integrations/status')}
        (STATE/'before.json').write_text(json.dumps(snapshot,ensure_ascii=False,indent=2),encoding='utf-8')
        state['_before']=True
        state['_sku_start']=max([int(p['skuCode']) for p in existing if p['skuCode'].isdigit()]+[0])+1
        if {p['productName'] for p in existing} & {n for _,items in GROUPS for n,_ in items}:
            raise RuntimeError('Some planned product names already exist. Adjust the catalog before appending.')
        save()
    today=dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).date()
    suppliers=[]
    for i,name in enumerate(['Phân phối Hưng Thịnh','Thực phẩm Tân Phú','Tiêu dùng Bình Minh','Gia dụng An Khang','Nông sản Phú An']):
        suppliers.append(mutate(f'supplier-{i}','POST','/suppliers',{'supplierName':name,'contactName':['Nguyễn Minh Hùng','Trần Thu Hà','Lê Quốc Bình','Phạm Thanh An','Võ Ngọc Lan'][i],'phone':f'09000010{i:02}','address':f'{20+i*10} Đường số {i+1}, TP. Hồ Chí Minh (địa chỉ mẫu)'}))
    products=[]
    for i,(category,(name,price)) in enumerate((g,item) for g,items in GROUPS for item in items):
        product=mutate(f'product-{i}','POST','/products',{'productName':name,'skuCode':str(state['_sku_start']+i),'price':price,'stockQuantity':0,'reorderPoint':10,'imageUrl':'','categoryName':category,'isActive':True})
        products.append(product)
        supplier=suppliers[4 if i>=45 else 3 if i>=40 else 2 if i>=30 else i%2]
        for lot in range(2 if i<25 else 1):
            expiry=None if 35<=i<45 else (today+dt.timedelta(days=(6 if i>=45 else 40)+lot*90)).isoformat()
            mutate(f'batch-{i}-{lot}','POST','/inventory/batches',{'productId':product['productId'],'supplierId':supplier['supplierId'],'batchCode':f'{MARKER}-{i+1}-{lot+1}','quantity':20 if lot==0 and i<25 else 45,'unitCost':price*7//10,'manufacturingDate':(today-dt.timedelta(days=15+lot*3)).isoformat(),'expiryDate':expiry})
    print('Catalog and 75 lots loaded.',flush=True)
    orders=[]
    for i in range(110):
        size=2 if i<55 else 3
        picks=[products[(i*7+j*13)%50] for j in range(size)]
        order=mutate(f'order-{i}','POST','/orders',{'orderType':'IN_STORE','paymentMethod':'CASH' if i%3 else 'BANK_TRANSFER','paymentReference':f'{MARKER}-PAY-{i+1}','customerName':None,'note':f'DỮ LIỆU MẪU - {MARKER} - Hóa đơn {i+1}','items':[{'productId':p['productId'],'quantity':1+(i+j)%3} for j,p in enumerate(picks)]},'nhanvien')
        orders.append(order)
        if (i+1)%25==0:
            print(f'Loaded {i+1}/110 receipts.',flush=True)
    for i in range(10):
        order=orders[i]
        item=order['items'][0]
        # Early receipts consume a single original lot; return its complete line.
        returned=mutate(f'return-{i}','PUT',f'/orders/{order["orderId"]}/return',{'requestKey':f'{MARKER}-RET-{i+1}','items':[{'orderItemId':item['orderItemId'],'quantity':item['quantity'],'restock':i%3!=0,'reason':f'DỮ LIỆU MẪU - {"Bao bì hỏng, không bán lại" if i%3==0 else "Khách mua nhầm, bao bì nguyên vẹn"}'}]})
        if i<6:
            mutate(f'refund-{i}','PUT',f'/orders/returns/{returned["returnId"]}/refund',{'reference':f'{MARKER}-REF-{i+1}','amount':returned['refundAmount']})
    api('POST','/integrations/catalog/sync',{})
    for _ in range(30):
        status=api('GET','/integrations/status')
        if not status['mongodbPendingOutbox'] and not status['astraPendingOutbox']:
            break
        api('POST','/integrations/dispatch',{})
    status=api('GET','/integrations/status')
    export=api('POST',f'/integrations/astra/events/export?date={today}&timeZone=Asia/Ho_Chi_Minh',{})
    summary={'date':str(today),'products':50,'suppliers':5,'batches':75,'orders':110,'orderLines':275,'returns':10,'refunds':6,'integration':status,'export':export}
    (STATE/'result.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False),flush=True)

if __name__=='__main__':
    main()
