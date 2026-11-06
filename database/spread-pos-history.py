"""Redistribute the existing sample workflow over 30 Vietnam days, preserving time order.
Preview by default; --apply updates MySQL, existing Cassandra event keys, and outbox payloads.
Stop the backend during --apply. Back up the database before running.
Prepared SQL/CQL and source snapshots are retained in .tmp/spread-pos-history/.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
DIRECTORY=ROOT/'.tmp/spread-pos-history'
UTC=dt.timezone.utc
VN=dt.timezone(dt.timedelta(hours=7))

def main():
    global DIRECTORY
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--target',type=int,choices=[1000,3000,5000,7000],default=1000)
    parser.add_argument('--end-date',type=dt.date.fromisoformat)
    args=parser.parse_args()
    target=args.target
    if target!=1000:DIRECTORY=ROOT/f'.tmp/spread-pos-history-{target}'
    if args.apply and (DIRECTORY/'completed.json').exists():
        print('History already redistributed; no changes.')
        return
    config={}
    for line in (ROOT/'.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            k,v=line.split('=',1);config[k.strip()]=v.strip().strip('\"').strip("'")
    password=config.get('DB_PASSWORD')
    if password is None:password=re.search(r'\$\{DB_PASSWORD:([^}]*)\}',(ROOT/'backend/src/main/resources/application.properties').read_text(encoding='utf-8')).group(1)
    env=os.environ.copy();env['MYSQL_PWD']=password
    command=[os.getenv('MYSQL_CLIENT',r'D:\MySQL\Server84\bin\mysql.exe'),'--host=127.0.0.1','--user='+config.get('DB_USERNAME','root'),'--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','supermarket_api']
    def query(sql):
        r=subprocess.run(command,input="SET time_zone='+00:00';\n"+sql,encoding='utf-8',capture_output=True,env=env)
        if r.returncode:raise RuntimeError(r.stderr)
        return r.stdout
    schema={'products':('product_id',['created_at','updated_at']),
            'inventory_batches':('batch_id',['received_at']),
            'sales_orders':('order_id',['order_date','paid_at','created_at','updated_at']),
            'sales_order_returns':('return_id',['created_at','refunded_at']),
            'payment_records':('payment_record_id',['created_at']),
            'stock_movements':('movement_id',['created_at'])}
    DIRECTORY.mkdir(parents=True,exist_ok=True)
    snapshot=DIRECTORY/'source.json'
    if snapshot.exists():data=json.loads(snapshot.read_text(encoding='utf-8'))
    else:
        data={}
        for table in [*schema,'data_sync_outbox']:
            columns=['id','destination','payload'] if table=='data_sync_outbox' else [schema[table][0],*schema[table][1]]
            if table=='inventory_batches':columns.append('manufacturing_date')
            expr=','.join("'"+c+"',`"+c+'`' for c in columns)
            condition=" WHERE destination='ASTRA'" if table=='data_sync_outbox' else ''
            data[table]=[json.loads(row) for row in query('SELECT JSON_OBJECT('+expr+') FROM `'+table+'`'+condition+';').splitlines()]
        snapshot.write_text(json.dumps(data,ensure_ascii=False),encoding='utf-8')
    if len(data['sales_orders'])!=target:raise RuntimeError('This operation is scoped to the 1,000-receipt sample snapshot.')
    def epoch(value):return int(dt.datetime.fromisoformat(value).replace(tzinfo=UTC).timestamp()*1000)
    times=set()
    for table,(_,fields) in schema.items():
        for row in data[table]:
            for field in fields:
                if row[field] is not None:times.add(epoch(row[field]))
    events=[]
    for row in data['data_sync_outbox']:
        if row['destination']=='ASTRA':
            event=json.loads(row['payload']);events.append((row,event));times.add(event['eventTime'])
    ordered=sorted(times)
    today=args.end_date or dt.datetime.now(VN).date()
    # Assign successive source instants to Vietnam business hours, in original order.
    mapped={}
    for index,source in enumerate(ordered):
        day=min(29,index*30//len(ordered))
        same_day_start=(day*len(ordered)+29)//30
        same_day_end=((day+1)*len(ordered)+29)//30
        seconds=(index-same_day_start)*8*3600/max(1,same_day_end-same_day_start)
        target_time=dt.datetime.combine(today-dt.timedelta(days=29-day),dt.time(8),VN)+dt.timedelta(seconds=seconds)
        mapped[source]=int(target_time.timestamp()*1000)
    def sqltime(value):return dt.datetime.fromtimestamp(value/1000,UTC).strftime('%Y-%m-%d %H:%M:%S.%f')
    def q(value):return "'"+str(value).replace('\\','\\\\').replace("'","''")+"'"
    sql=["SET time_zone='+00:00';",'START TRANSACTION;']
    for table,(pk,fields) in schema.items():
        for row in data[table]:
            assignments=[field+'='+q(sqltime(mapped[epoch(row[field])])) for field in fields if row[field] is not None]
            if table=='inventory_batches':
                received=dt.datetime.fromtimestamp(mapped[epoch(row['received_at'])]/1000,VN).date()
                if row['manufacturing_date'] and dt.date.fromisoformat(row['manufacturing_date'])>received:
                    assignments.append('manufacturing_date='+q(str(received-dt.timedelta(days=10))))
            sql.append('UPDATE '+table+' SET '+','.join(assignments)+' WHERE '+pk+'='+str(row[pk])+';')
    cql=[]
    fields=['event_date','event_time','event_id','event_type','order_id','product_sku','quantity','amount','payment_method','status','metadata']
    for row,event in events:
        old_time=dt.datetime.fromtimestamp(event['eventTime']/1000,UTC).isoformat(timespec='milliseconds')
        cql.append('DELETE FROM supermarket_analytics.business_events_by_day WHERE event_date='+q(event['eventDate'])+' AND event_time='+q(old_time)+' AND event_id='+event['eventId']+';')
        event['eventTime']=mapped[event['eventTime']]
        newtime=dt.datetime.fromtimestamp(event['eventTime']/1000,UTC)
        event['eventDate']=str(newtime.date())
        values=[event['eventDate'],newtime.isoformat(timespec='milliseconds'),event['eventId'],event['eventType'],event.get('orderId'),event.get('productSku'),event.get('quantity'),event.get('amount'),event.get('paymentMethod'),event.get('status'),event.get('metadata')]
        literals=[str(v) if field=='event_id' else 'null' if v is None else "'"+v.replace("'","''")+"'" if isinstance(v,str) else str(v) for field,v in zip(fields,values)]
        cql.append('INSERT INTO supermarket_analytics.business_events_by_day ('+','.join(fields)+') VALUES ('+','.join(literals)+');')
        sql.append('UPDATE data_sync_outbox SET payload='+q(json.dumps(event,ensure_ascii=False))+' WHERE id='+str(row['id'])+';')
    sql.append('COMMIT;')
    (DIRECTORY/'mysql.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
    (DIRECTORY/'cassandra.cql').write_text('\n'.join(cql)+'\n',encoding='utf-8')
    print(f'Prepared {target} receipts and {len(events)} existing events across {today-dt.timedelta(days=29)} to {today}; original workflow order preserved.',flush=True)
    if args.apply:
        query('\n'.join(sql))
        result=subprocess.run(['docker','exec','-i','supermarket-cassandra','cqlsh'],input='\n'.join(cql),encoding='utf-8',capture_output=True)
        if result.returncode or result.stderr.strip():raise RuntimeError('Cassandra replay needs review: '+result.stderr[:2000])
        (DIRECTORY/'completed.json').write_text(json.dumps({'from':str(today-dt.timedelta(days=29)),'to':str(today),'orders':target,'events':len(events)}),encoding='utf-8')
        print('MySQL, outbox and Cassandra timestamps updated.',flush=True)

if __name__=='__main__':main()
