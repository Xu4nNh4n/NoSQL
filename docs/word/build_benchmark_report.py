"""Build Word/HTML evidence from four completed, real Drill benchmark stages."""
from pathlib import Path
import base64
import csv
import html
import io
import json
import re
import zipfile
import xml.etree.ElementTree as E

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/benchmark'
environment=json.loads((FOLDER/'environment.json').read_text(encoding='utf-8-sig'))
results=[json.loads((FOLDER/str(count)/'result.json').read_text(encoding='utf-8')) for count in [1000,3000,5000,7000]]
for name in ['SALES','CASH_FLOW']:
    queries=[(FOLDER/str(r['orders'])/(name+'.sql')).read_text(encoding='utf-8') for r in results]
    if len(set(queries))!=1:raise RuntimeError('SQL differs between stages; review before claiming a comparable benchmark: '+name)
if any(r['options']!=results[0]['options'] or r['range']!=results[0]['range'] for r in results):
    raise RuntimeError('Settings/date range differ between stages')
headers=['Hóa đơn','Dòng hàng','Báo cáo','Trung vị (ms)','Nhỏ nhất (ms)','Lớn nhất (ms)','Lập kế hoạch (ms)','Thực thi (ms)','Đỉnh fragment (MiB)']
rows=[]
for result in results:
    for name,label in [('SALES','Bán chạy'),('CASH_FLOW','Dòng tiền')]:
        r=result['reports'][name]
        rows.append([result['orders'],result['orderLines'],label+f' ({r.get("successfulMeasuredRuns",3)}/3 đạt)',*[r[k] if r[k] is not None else 'Không đo được' for k in ['medianTotalMs','minTotalMs','maxTotalMs','medianPlanningMs','medianExecutionMs']],round(r['medianMaxFragmentPeakBytes']/1048576,2) if r['medianMaxFragmentPeakBytes'] is not None else 'Không đo được'])
with (FOLDER/'comparison.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.writer(f);writer.writerow(headers);writer.writerows(rows)
charts=[('sales-time.png','Thời gian báo cáo bán chạy'),('cash-time.png','Thời gian báo cáo dòng tiền'),('fragment-memory.png','Bộ nhớ fragment lớn nhất')]
method='Mỗi mốc là tổng số hóa đơn POS: 1.000, 3.000, 5.000, 7.000. Giữ nguyên SQL, khoảng ngày 30/09–29/10/2026 và cấu hình Drill: width.max_per_node=1, hashjoin.num_partitions=4. Chạy tuần tự; một lượt warm-up rồi ba lượt đo. Ngoài ra API được gọi trước để lấy SQL. Không xóa cache hoặc khởi động lại Drill giữa các mốc.'
limits='Mốc có lỗi phải đọc kèm tỷ lệ thành công: thống kê chỉ tính lượt thành công, không coi lỗi là 0 ms. Không so sánh trung vị 1 lượt với trung vị 3 lượt như độ tin cậy tương đương. Lỗi OOM sau chuỗi truy vấn có thể liên quan bộ nhớ tích lũy, không chứng minh chỉ riêng số hóa đơn gây lỗi. Thời gian lấy từ Profile của Drill; HTTP elapsed được lưu riêng trong từng lượt. Bộ nhớ là trung vị của giá trị maxMemoryUsed lớn nhất giữa các minor fragment, không phải tổng RAM hay đỉnh RAM toàn container. Trung vị các thành phần được tính riêng nên không bắt buộc cộng bằng trung vị tổng. Ba lượt chưa đủ kết luận hiệu năng sản xuất. Dữ liệu là dữ liệu mẫu; số sản phẩm giữ ổn định, số hóa đơn/dòng hàng và lô nhập tăng theo mốc.'
hardware=f'Môi trường: {environment["cpu"]}, {environment["logicalProcessors"]} logical processors, RAM máy {environment["hostRamGiB"]} GiB; container Drill giới hạn {environment["drillContainerMemoryBytes"]/1073741824:g} GiB, heap 1 GiB, direct memory 1 GiB.'
recovery_path=FOLDER/'7000/recovery-after-restart/result.json'
recovery_text=''
if recovery_path.exists():
    recovery=json.loads(recovery_path.read_text(encoding='utf-8'))
    recovery_text='Kiểm tra riêng sau khi khởi động lại Drill ở 7.000 hóa đơn: '+ '; '.join(name+': '+r['queryState']+' ('+str(r['totalMs'])+' ms, query '+r['queryId']+')' for name,r in recovery['reports'].items())+'. Đây là một lượt kiểm tra khôi phục, không phải trung vị warm-run và không được trộn vào biểu đồ so sánh. Cả hai truy vấn chạy lại được sau restart cho thấy không thể kết luận 7.000 hóa đơn là giới hạn của Drill từ chuỗi có lỗi OOM này.'
def esc(value):return html.escape(str(value))
table='<div class="scroll"><table><tr>'+''.join('<th>'+esc(h)+'</th>' for h in headers)+'</tr>'+''.join('<tr>'+''.join('<td>'+esc(v)+'</td>' for v in row)+'</tr>' for row in rows)+'</table></div>'
parts=[]
for file,title in charts:
    encoded=base64.b64encode((FOLDER/file).read_bytes()).decode()
    parts.append('<section><h2>'+title+'</h2><img alt="'+title+'" src="data:image/png;base64,'+encoded+'"></section>')
profiles=[]
for r in results:
    items=[]
    for name,label in [('SALES','Bán chạy'),('CASH_FLOW','Dòng tiền')]:
        query=r['reports'][name]['representativeQueryId']
        items.append(f'<p>{label}: <code>{query}</code> · <a href="{r["orders"]}/{name}-profile-summary.png">Ảnh tóm tắt</a> · <a href="{r["orders"]}/{name}-profile.png">Ảnh toàn trang</a></p>')
        if r['reports'][name].get('failedMeasuredRuns',0):items.append(f'<p><strong>Có {r["reports"][name]["failedMeasuredRuns"]}/3 lượt đo lỗi.</strong> <a href="{r["orders"]}/{name}-failed-profile-summary.png">Ảnh Profile lỗi</a></p>')
    profiles.append(f'<details><summary>Minh chứng mốc {r["orders"]} hóa đơn</summary>'+''.join(items)+'</details>')
page='<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Benchmark Apache Drill</title><style>body{margin:0;background:#f5f8fc;color:#182437;font:16px/1.7 system-ui}main{max-width:1180px;margin:auto;padding:32px 20px}section,details{background:white;border:1px solid #dce3ee;border-radius:8px;padding:20px;margin:20px 0}img{width:100%;height:auto}table{border-collapse:collapse;font-size:13px;width:100%}td,th{padding:10px;border-bottom:1px solid #e2e8f0;text-align:right}th{background:#eff6ff}.scroll{overflow:auto}a,summary{color:#1d4ed8}summary{cursor:pointer}code{overflow-wrap:anywhere}</style><main><h1>Apache Drill: so sánh 1k → 3k → 5k → 7k hóa đơn</h1><p>'+method+'</p><p>'+hardware+'</p>'+table+''.join(parts)+'<h2>Ảnh Profile thật</h2>'+''.join(profiles)+'<h2>Kiểm tra khôi phục riêng</h2><p>'+recovery_text+'</p><h2>Cách đọc và giới hạn</h2><p>'+limits+'</p></main></html>'
(FOLDER/'benchmark.html').write_text(page,encoding='utf-8')

W='http://schemas.openxmlformats.org/wordprocessingml/2006/main';R='http://schemas.openxmlformats.org/officeDocument/2006/relationships';REL='http://schemas.openxmlformats.org/package/2006/relationships'
E.register_namespace('w',W)
def tag(name):return '{'+W+'}'+name
def sub(parent,name,**attrs):return E.SubElement(parent,tag(name),{tag(k):str(v) for k,v in attrs.items()})
source=ROOT/'docs/word/BigData_ApacheDrill_Nhom6_CoBieuDo.docx'
output=ROOT/'docs/word/BigData_ApacheDrill_Nhom6_Benchmark.docx'
with zipfile.ZipFile(source) as z:files={n:z.read(n) for n in z.namelist()}
original=files['word/document.xml'];doc=E.fromstring(original);body=doc.find(tag('body'));sect=body.find(tag('sectPr'))
if sect is not None:body.remove(sect)
relationships=E.fromstring(files['word/_rels/document.xml.rels'])
def para(text,heading=False):
    p=sub(body,'p')
    if heading:pp=sub(p,'pPr');sub(pp,'pStyle',val='Heading1');sub(pp,'pageBreakBefore')
    sub(sub(p,'r'),'t').text=text
def picture(path,title,index):
    rid='rIdBenchmark'+str(index)
    E.SubElement(relationships,'{'+REL+'}Relationship',Id=rid,Type=R+'/image',Target=f'media/benchmark-{index}.png')
    files[f'word/media/benchmark-{index}.png']=path.read_bytes()
    height=3331028 if index<10 else 4191000
    fragment=f'<w:p xmlns:w="{W}" xmlns:r="{R}" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:r><w:drawing><wp:inline><wp:extent cx="5486400" cy="{height}"/><wp:docPr id="{2000+index}" name="Benchmark {index}"/><wp:cNvGraphicFramePr/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="benchmark-{index}.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="5486400" cy="{height}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
    body.append(E.fromstring(fragment));para(title)
para('PHỤ LỤC C. BENCHMARK DRILL THEO SỐ HÓA ĐƠN',True)
para('Các chương và biểu đồ nghiệp vụ trước đó minh họa snapshot 1.000 hóa đơn. Phụ lục này bổ sung kết quả đo thực tế ở bốn mức dữ liệu; sau thử nghiệm, hệ thống đang có 7.000 hóa đơn mẫu.')
para(method);para(hardware);para(limits)
if recovery_text:para(recovery_text)
tbl=sub(body,'tbl');pr=sub(tbl,'tblPr');sub(pr,'tblW',w=9000,type='dxa');borders=sub(pr,'tblBorders')
for side in ['top','left','bottom','right','insideH','insideV']:sub(borders,side,val='single',sz=4,color='DCE3EE')
for i,row in enumerate([headers]+rows):
    tr=sub(tbl,'tr')
    if i==0:sub(sub(tr,'trPr'),'tblHeader')
    for value in row:
        tc=sub(tr,'tc');props=sub(tc,'tcPr');sub(props,'tcW',w=1000,type='dxa')
        if i==0:sub(props,'shd',fill='EAF1FC')
        run=sub(sub(tc,'p'),'r');sub(sub(run,'rPr'),'sz',val=17);sub(run,'t').text=str(value)
for index,(file,title) in enumerate(charts,1):
    para('C.'+str(index)+'. '+title,True);picture(FOLDER/file,title,index)
for i,result in enumerate(results):
    para(f'C.{i+4}. Profile minh chứng — {result["orders"]} hóa đơn',True)
    for j,(name,label) in enumerate([('SALES','Bán chạy'),('CASH_FLOW','Dòng tiền')]):
        para(label+' · Query ID: '+result['reports'][name]['representativeQueryId'])
        picture(FOLDER/str(result['orders'])/(name+'-profile-summary.png'),label,10+i*2+j)
        if result['reports'][name].get('failedMeasuredRuns',0):
            para('Lượt lỗi cần giữ lại trong đánh giá; không tính là 0 ms.');picture(FOLDER/str(result['orders'])/(name+'-failed-profile-summary.png'),'Profile của lượt thất bại',100+i*2+j)
if sect is not None:body.append(sect)
xml=E.tostring(doc,encoding='unicode');start=re.search(r'<[A-Za-z_][^>]*>',xml).group(0)
namespaces=dict((p,u) for _,(p,u) in E.iterparse(io.BytesIO(original),events=['start-ns']) if p)
extra=''.join(' xmlns:'+p+'="'+u+'"' for p,u in namespaces.items() if 'xmlns:'+p+'=' not in start)
xml=xml.replace(start,start[:-1]+extra+'>',1);E.fromstring(xml)
files['word/document.xml']=xml.encode('utf-8');files['word/_rels/document.xml.rels']=E.tostring(relationships,encoding='utf-8',xml_declaration=True)
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
    for name,data in files.items():z.writestr(name,data)
with zipfile.ZipFile(output) as z:
    if z.testzip():raise RuntimeError('Invalid Word archive')
print('Built benchmark HTML, CSV and Word with actual data and Profile screenshots.')
