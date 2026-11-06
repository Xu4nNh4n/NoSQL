"""Export operational SQL/CQL plus an existing MongoDB JSONL export; read-only.
Usage: python database/export-pos-snapshot.py --mongo-jsonl PATH
MongoDB JSONL must be exported after outbox synchronization has finished.
Credentials are read locally from .env and never written to the seed files.
"""
import argparse
import decimal
import json
import os
from pathlib import Path
import re
import subprocess

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mongo-jsonl',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    config={}
    for line in (root/'.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key,value=line.split('=',1);config[key.strip()]=value.strip().strip('\"').strip("'")
    password=config.get('DB_PASSWORD')
    if password is None:
        password=re.search(r'\$\{DB_PASSWORD:([^}]*)\}',(root/'backend/src/main/resources/application.properties').read_text(encoding='utf-8')).group(1)
    env=os.environ.copy();env['MYSQL_PWD']=password
    command=[os.getenv('MYSQL_CLIENT',r'D:\MySQL\Server84\bin\mysql.exe'),'--host=127.0.0.1','--user='+config.get('DB_USERNAME','root'),'--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','supermarket_api']
    def query(sql):
        result=subprocess.run(command,input="SET time_zone='+00:00';\n"+sql,encoding='utf-8',capture_output=True,env=env)
        if result.returncode:raise RuntimeError(result.stderr)
        return result.stdout.splitlines()
    def quote(value):
        if value is None:return 'NULL'
        if isinstance(value,bool):return '1' if value else '0'
        if isinstance(value,(int,decimal.Decimal)):return str(value)
        if isinstance(value,(dict,list)):value=json.dumps(value,ensure_ascii=False,default=str)
        return "'"+str(value).replace('\\','\\\\').replace("'","''").replace('\n','\\n').replace('\r','\\r')+"'"
    names=['suppliers','products','inventory_batches','sales_orders','sales_order_items','sales_order_returns','payment_records','stock_movements']
    tables={};columns={}
    for name in names:
        columns[name]=query("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+name+"' ORDER BY ORDINAL_POSITION;")
        expression=','.join("'"+column+"',`"+column+'`' for column in columns[name])
        tables[name]=[json.loads(row,parse_float=decimal.Decimal) for row in query('SELECT JSON_OBJECT('+expression+') FROM `'+name+'`;')]
    users={r['user_id']:r['username'] for r in (json.loads(row) for row in query("SELECT JSON_OBJECT('user_id',user_id,'username',username) FROM users;"))}
    mongo=[json.loads(row) for row in args.mongo_jsonl.read_text(encoding='utf-8').splitlines() if row.strip()]
    if {doc['productId'] for doc in mongo}!={row['product_id'] for row in tables['products']}:
        raise RuntimeError('MongoDB catalog product IDs do not match MySQL; synchronize and export again.')
    sql=['-- Snapshot toàn bộ dữ liệu vận hành; giữ nguyên ID và thời gian UTC.','-- Chỉ nhập trên schema tương thích chưa có các ID này. Dừng tại lỗi đầu tiên.','-- Cần có các tài khoản nền trước khi nhập; không chứa mật khẩu/hash người dùng.','USE supermarket_api;',"SET NAMES utf8mb4;", "SET time_zone='+00:00';",'START TRANSACTION;']
    for name,rows in tables.items():
        sql.append('-- '+name+': '+str(len(rows)))
        for row in rows:
            values=[]
            for column in columns[name]:
                value=row[column]
                if (name=='sales_orders' and column=='user_id' or name=='payment_records' and column=='confirmed_by') and value is not None:
                    values.append('(SELECT user_id FROM users WHERE username='+quote(users[value])+')')
                else:values.append(quote(value))
            sql.append('INSERT INTO `'+name+'` ('+','.join('`'+column+'`' for column in columns[name])+') VALUES ('+','.join(values)+');')
    sql.append('COMMIT;')
    javascript=['// Catalog snapshot; run using mongosh after importing MySQL.','const catalogDb=db.getSiblingDB("supermarket_catalog");','catalogDb.products_catalog.createIndex({sku:1},{unique:true});']
    for doc in mongo:
        javascript.append('catalogDb.products_catalog.updateOne({_id:'+json.dumps(doc['_id'])+'},{$setOnInsert:EJSON.parse('+json.dumps(json.dumps(doc,ensure_ascii=False),ensure_ascii=False)+')},{upsert:true});')
    events={}
    for file in (root/'analytics/cassandra').glob('events-*.json'):
        for line in file.read_text(encoding='utf-8').splitlines():
            if not line.strip() or line.strip()=='[]':continue
            event=json.loads(line,parse_float=decimal.Decimal);events[event['event_id']]=event
    fields=['event_date','event_time','event_id','event_type','order_id','product_sku','quantity','amount','payment_method','status','metadata']
    cql=['-- Snapshot sự kiện vận hành; giữ nguyên UUID và thời gian UTC.']
    for event in events.values():
        values=[]
        for field in fields:
            value=event.get(field)
            values.append(str(value) if field=='event_id' else "'"+value.replace("'","''")+"'" if isinstance(value,str) else quote(value))
        cql.append('INSERT INTO supermarket_analytics.business_events_by_day ('+','.join(fields)+') VALUES ('+','.join(values)+');')
    manifest={'mysql':{name:len(rows) for name,rows in tables.items()},'mysqlTotal':sum(map(len,tables.values())),'mongodbDocuments':len(mongo),'cassandraEvents':len(events),'note':'Read-only operational snapshot; import files not executed.'}
    for filename,lines in [('seed-mysql.sql',sql),('seed-mongodb.js',javascript),('seed-cassandra.cql',cql)]:
        (root/'database'/filename).write_text('\n'.join(lines)+'\n',encoding='utf-8')
    (root/'database/seed-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=False))

if __name__=='__main__':main()
