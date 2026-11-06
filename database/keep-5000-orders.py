"""Remove only the 2,000 benchmark receipts marked POS-ORDERS-7000.
Preview by default. --apply requires backend stopped; backs up MySQL and CQL first.
Restores consumed lot quantities and the previously saved 5k history timestamps.
"""
import argparse
import collections
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess

ROOT=Path(__file__).resolve().parents[1]
DIR=ROOT/'.tmp/keep-5000-orders'

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    DIR.mkdir(parents=True,exist_ok=True)
    if (DIR/'completed.json').exists():print('Cleanup already completed; no changes.');return
    config={}
    for line in (ROOT/'.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key,value=line.split('=',1);config[key.strip()]=value.strip().strip('\"').strip("'")
    password=config.get('DB_PASSWORD')
    if password is None:password=re.search(r'\$\{DB_PASSWORD:([^}]*)\}',(ROOT/'backend/src/main/resources/application.properties').read_text(encoding='utf-8')).group(1)
    env=os.environ.copy();env['MYSQL_PWD']=password
    exe=Path(os.getenv('MYSQL_CLIENT',r'D:\MySQL\Server84\bin\mysql.exe'))
    flags=['--host=127.0.0.1','--user='+config.get('DB_USERNAME','root'),'--default-character-set=utf8mb4']
    def query(sql):
        result=subprocess.run([str(exe),*flags,'--batch','--raw','--skip-column-names','supermarket_api'],input="SET time_zone='+00:00';\n"+sql,encoding='utf-8',capture_output=True,env=env)
        if result.returncode:raise RuntimeError(result.stderr)
        return result.stdout.splitlines()
    def rows(table,condition='1=1'):
        cols=query("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' ORDER BY ORDINAL_POSITION;")
        expr=','.join("'"+c+"',`"+c+'`' for c in cols)
        return [json.loads(line) for line in query('SELECT JSON_OBJECT('+expr+') FROM `'+table+'` WHERE '+condition+';')]
    def check(ok,message):
        if not ok:raise RuntimeError(message)
    nums=lambda values:','.join(map(str,values))
    q=lambda value:"'"+str(value).replace('\\','\\\\').replace("'","''")+"'"
    plan=DIR/'prepared.json'
    if not plan.exists():
        orders=rows('sales_orders');selected=[o for o in orders if 'POS-ORDERS-7000' in (o['note'] or '')]
        expected=json.loads((ROOT/'.tmp/pos-orders-7000/progress.json').read_text(encoding='utf-8'))
        expected_ids={v['orderId'] for k,v in expected.items() if k.startswith('order-')}
        ids={o['order_id'] for o in selected}
        check(len(orders)==7000 and len(ids)==2000 and ids==expected_ids,'Expected exactly the original 2,000 benchmark receipts inside 7,000 orders.')
        check(not rows('sales_order_returns','order_id IN ('+nums(ids)+')'),'Selected orders now have returns; automatic cleanup stopped.')
        items=rows('sales_order_items');restore=collections.Counter()
        for item in items:
            if item['order_id'] not in ids:continue
            allocations=item['batch_allocations']
            if isinstance(allocations,str):allocations=json.loads(allocations)
            check(item['returned_quantity']==0 and sum(a['quantity'] for a in allocations)==item['quantity'],'Unexpected return/allocation balance.')
            for allocation in allocations:
                check(allocation['status']=='CONSUMED' and allocation['returnedQuantity']==0,'Unexpected allocation status.')
                restore[allocation['batchId']]+=allocation['quantity']
        batches=rows('inventory_batches');new_batches=[b for b in batches if b['batch_code'].startswith('POS-ORDERS-7000-')]
        new_ids={b['batch_id'] for b in new_batches}
        check(len(new_ids)==20,'Expected 20 replenishment lots from the 7k batch.')
        for item in items:
            if item['order_id'] in ids:continue
            allocations=item['batch_allocations'];allocations=json.loads(allocations) if isinstance(allocations,str) else allocations
            check(not any(a['batchId'] in new_ids for a in allocations),'Retained order refers to a replenishment lot being removed.')
        for batch in new_batches:
            check(batch['damaged_quantity']==0 and batch['remaining_quantity']+restore[batch['batch_id']]==batch['received_quantity'],'Replenishment lot changed outside benchmark; stopped.')
        notes=[q('Nhập lô '+b['batch_code']) for b in new_batches]
        movement_condition='order_id IN ('+nums(ids)+") OR (movement_type='RECEIPT' AND note IN ("+','.join(notes)+'))'
        movements=rows('stock_movements',movement_condition);movement_ids={m['movement_id'] for m in movements}
        check(sum(m['movement_type']=='RECEIPT' for m in movements)==20,'Receipt movement mismatch.')
        outbox=rows('data_sync_outbox',"destination='ASTRA'");remove_events=[]
        for row in outbox:
            payload=json.loads(row['payload']);metadata=json.loads(payload.get('metadata') or '{}')
            if payload.get('orderId') in ids or metadata.get('movementId') in movement_ids:remove_events.append(row['id'])
        check(len(remove_events)==9020,'Expected 9,020 events for the removed batch; stopped on mismatch.')
        sql=['START TRANSACTION;']
        for batch_id,quantity in restore.items():sql.append(f'UPDATE inventory_batches SET remaining_quantity=remaining_quantity+{quantity} WHERE batch_id={batch_id};')
        sql += ['DELETE FROM data_sync_outbox WHERE id IN ('+nums(remove_events)+');','DELETE FROM payment_records WHERE order_id IN ('+nums(ids)+');','DELETE FROM stock_movements WHERE movement_id IN ('+nums(movement_ids)+');','DELETE FROM sales_order_items WHERE order_id IN ('+nums(ids)+');','DELETE FROM sales_orders WHERE order_id IN ('+nums(ids)+');','DELETE FROM inventory_batches WHERE batch_id IN ('+nums(new_ids)+');']
        products={b['product_id'] for b in batches if b['batch_id'] in restore}
        sql.append('UPDATE products p SET stock_quantity=(SELECT COALESCE(SUM(remaining_quantity),0) FROM inventory_batches b WHERE b.product_id=p.product_id), version=version+1 WHERE product_id IN ('+nums(products)+');')
        # Force existing catalog snapshot requests to refresh all remaining quantities.
        sql.append("UPDATE data_sync_outbox SET status='PENDING',attempts=0,next_attempt_at=NOW(3),delivered_at=NULL,last_error=NULL WHERE destination='MONGODB' AND event_key LIKE 'CATALOG-SNAPSHOT-%';")
        sql.append('COMMIT;')
        (DIR/'remove-mysql.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
        current=(ROOT/'.tmp/spread-pos-history-7000/cassandra.cql').read_text(encoding='utf-8').splitlines()
        cql=[]
        for line in current:
            if not line.startswith('INSERT INTO '):continue
            match=re.search(r"VALUES \(('[^']*'),('[^']*'),([0-9a-f-]+),",line)
            check(match is not None,'Cannot parse current Cassandra key.')
            cql.append('DELETE FROM supermarket_analytics.business_events_by_day WHERE event_date='+match[1]+' AND event_time='+match[2]+' AND event_id='+match[3]+';')
        kept=[line for line in (ROOT/'.tmp/spread-pos-history-5000/cassandra.cql').read_text(encoding='utf-8').splitlines() if line.startswith('INSERT INTO ')]
        check(len(cql)==31699 and len(kept)==22679,'Cassandra source snapshot count mismatch.')
        (DIR/'restore-cassandra.cql').write_text('\n'.join(cql+kept)+'\n',encoding='utf-8')
        plan.write_text(json.dumps({'before':7000,'removedOrders':2000,'remainingOrders':5000,'removedLots':20,'remainingEvents':22679}),encoding='utf-8')
    print(plan.read_text(),flush=True)
    if not args.apply:return
    with socket.socket() as probe:
        probe.settimeout(.5)
        check(probe.connect_ex(('127.0.0.1',8080))!=0,'Stop backend before applying cleanup.')
    count=int(query('SELECT COUNT(*) FROM sales_orders;')[0])
    selected_count=int(query("SELECT COUNT(*) FROM sales_orders WHERE note LIKE '%POS-ORDERS-7000%';")[0])
    check((count,selected_count) in [(7000,2000),(5000,0)],'Database changed since preview; cleanup stopped.')
    backup=DIR/('before-'+dt.datetime.now().strftime('%Y%m%d-%H%M%S')+'.sql')
    result=subprocess.run([str(exe.with_name('mysqldump.exe')),*flags,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--result-file='+str(backup),'supermarket_api'],env=env,capture_output=True)
    check(result.returncode==0,'MySQL backup failed; cleanup stopped.')
    shutil.copy2(ROOT/'database/seed-cassandra.cql',DIR/'cassandra-before.cql')
    if count==7000:query((DIR/'remove-mysql.sql').read_text(encoding='utf-8'))
    query((ROOT/'.tmp/spread-pos-history-5000/mysql.sql').read_text(encoding='utf-8'))
    result=subprocess.run(['docker','exec','-i','supermarket-cassandra','cqlsh'],input=(DIR/'restore-cassandra.cql').read_text(encoding='utf-8'),encoding='utf-8',capture_output=True)
    check(result.returncode==0 and not result.stderr.strip(),'Cassandra replay needs review: '+result.stderr[:1000])
    check(query('SELECT COUNT(*) FROM sales_orders;')==['5000'],'Final invoice count mismatch.')
    check(query('SELECT COUNT(*) FROM products p WHERE stock_quantity!=(SELECT COALESCE(SUM(remaining_quantity),0) FROM inventory_batches b WHERE b.product_id=p.product_id);')==['0'],'Stock sum mismatch.')
    (DIR/'completed.json').write_text(plan.read_text(),encoding='utf-8')
    print('Removed 2,000 sample receipts; restored 5k stock and history; catalog refresh queued.',flush=True)

if __name__=='__main__':main()
