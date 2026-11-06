"""Read-only Drill benchmark: 1 warm-up + 3 measured runs/report at a fixed invoice count.
Profiles and SQL are saved before summarizing. No cache clearing or container restart.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import statistics
import time
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--orders',type=int,required=True)
    args=parser.parse_args()
    folder=ROOT/f'docs/benchmark/{args.orders}';folder.mkdir(parents=True,exist_ok=True)
    token=None
    def request(url,body=None):
        headers={'Content-Type':'application/json'}
        if url.startswith('http://127.0.0.1:8080') and token:headers['Authorization']='Bearer '+token
        req=urllib.request.Request(url,headers=headers,data=None if body is None else json.dumps(body).encode())
        with urllib.request.urlopen(req,timeout=180) as response:return json.load(response)
    token=request('http://127.0.0.1:8080/api/auth/login',{'username':'admin','password':'123456'})['accessToken']
    current=request('http://127.0.0.1:8080/api/orders?page=0&size=1')['totalElements']
    if current!=args.orders:raise RuntimeError(f'Expected {args.orders} invoices, actual {current}')
    line_count=request('http://127.0.0.1:8048/query.json',{'queryType':'SQL','query':'SELECT COUNT(*) AS line_count FROM mysql_source.supermarket_api.sales_order_items'})
    if line_count['queryState']!='COMPLETED':raise RuntimeError('Cannot count invoice lines for benchmark metadata')
    options=request('http://127.0.0.1:8048/query.json',{'queryType':'SQL','query':"SELECT name, val FROM sys.options WHERE name IN ('planner.width.max_per_node','exec.hashjoin.num_partitions')"})
    output={'orders':current,'range':{'from':'2026-09-30','to':'2026-10-29'},'measuredAt':dt.datetime.now(dt.timezone.utc).isoformat(),'method':'one warmup + three measured sequential runs; no cache flush; same SQL and fixed date range','options':options['rows'],'reports':{}}
    output['orderLines']=int(line_count['rows'][0]['line_count'])
    endpoints={'SALES':'/api/analytics/operational?report=SALES&dataset=OPERATIONS&from=2026-09-30&to=2026-10-29&top=20','CASH_FLOW':'/api/analytics/cash-flow?dataset=OPERATIONS&from=2026-09-30&to=2026-10-29'}
    for name,endpoint in endpoints.items():
        sql=(ROOT/'docs/benchmark/1000'/(name+'.sql')).read_text(encoding='utf-8')
        (folder/(name+'.sql')).write_text(sql,encoding='utf-8')
        runs=[]
        for index in range(4):
            saved=folder/(name+'-'+str(index)+'-profile.json')
            elapsed=None
            if saved.exists():
                profile=json.loads(saved.read_text(encoding='utf-8'))
                query_id=profile['queryId']
                success=profile.get('state') in (2,'COMPLETED')
                result_rows=None
            else:
                started=time.perf_counter()
                result=request('http://127.0.0.1:8048/query.json',{'queryType':'SQL','query':sql})
                elapsed=(time.perf_counter()-started)*1000
                query_id=result['queryId'];success=result['queryState']=='COMPLETED';result_rows=len(result['rows']) if success else None
                profile=request('http://127.0.0.1:8048/profiles/'+query_id+'.json')
                saved.write_text(json.dumps(profile,ensure_ascii=False,indent=2),encoding='utf-8')
            start=profile['start'];end=profile['end'];plan=profile.get('planEnd',start);queue=profile.get('queueWaitEnd',plan)
            peak=max((minor.get('maxMemoryUsed',0) for major in profile.get('fragmentProfile',[]) for minor in major.get('minorFragmentProfile',[])),default=0)
            row={'run':index,'warmup':index==0,'queryId':query_id,'rows':result_rows,'success':success,'profileTotalMs':end-start,'planningMs':max(0,plan-start),'executionAfterQueueMs':max(0,end-max(queue,plan)),'httpElapsedMs':None if elapsed is None else round(elapsed,2),'maxFragmentPeakBytes':peak}
            row['totalFragments']=profile.get('totalFragments')
            row['error']=profile.get('error') if not success else None
            runs.append(row)
            print(json.dumps({'orders':current,'report':name,**row}),flush=True)
        measured=[r for r in runs[1:] if r['success']]
        median=statistics.median(r['profileTotalMs'] for r in measured) if measured else None
        representative=min(measured,key=lambda r:abs(r['profileTotalMs']-median)) if measured else runs[1]
        output['reports'][name]={'runs':runs,'successfulMeasuredRuns':len(measured),'failedMeasuredRuns':3-len(measured),'medianTotalMs':median,'minTotalMs':min((r['profileTotalMs'] for r in measured),default=None),'maxTotalMs':max((r['profileTotalMs'] for r in measured),default=None),'medianPlanningMs':statistics.median(r['planningMs'] for r in measured) if measured else None,'medianExecutionMs':statistics.median(r['executionAfterQueueMs'] for r in measured) if measured else None,'medianMaxFragmentPeakBytes':statistics.median(r['maxFragmentPeakBytes'] for r in measured) if measured else None,'representativeQueryId':representative['queryId']}
    (folder/'result.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'Benchmark {current} saved.',flush=True)

if __name__=='__main__':main()
