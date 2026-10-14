import React, { useEffect, useMemo, useState } from 'react';
import { Download, RefreshCw } from 'lucide-react';
import { api } from '../api/client';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { DrillReportExplanation } from '../components/analytics/DrillReportExplanation';
import { formatVND } from '../utils/formatters';

type Kind = 'SALES' | 'EXPIRY' | 'REPLENISHMENT' | 'ABC' | 'CHANNELS' | 'QUALITY';
type Dataset = 'OPERATIONS' | 'SIMULATED';
type Row = Record<string, string | number | boolean | null>;
interface Result {
  rows: Row[];
  columns?: string[];
  reportKind: Kind;
  dataset: Dataset;
  from: string;
  to: string;
  stockAsOf?: string;
  sourceNames?: string[];
  definition?: string;
  managementAction?: string;
  assumptions?: string[];
  rowLimit?: number;
  queryId?: string;
  queryState?: string;
  sql?: string;
  generatedAt?: string;
  profilesUrl?: string;
  requestElapsedMs?: number;
}

const tabs: { id: Kind; label: string; question: string; description: string }[] = [
  { id: 'SALES', label: 'Bán hàng', question: 'Mặt hàng và nhóm nào có giá trị món bán cao nhất?', description: 'Nối đơn hàng MySQL với catalog MongoDB theo SKU. Giá dùng là giá lưu lúc bán, đã trừ lượng trả nhưng chưa phân bổ giảm giá cấp đơn.' },
  { id: 'EXPIRY', label: 'Hạn dùng', question: 'Lô nào sắp hết hạn và đang có bao nhiêu giá trị vốn?', description: 'Liệt kê lô còn hàng trong khoảng 7, 14 hoặc 30 ngày tính từ ngày chụp tồn kho.' },
  { id: 'REPLENISHMENT', label: 'Nhập hàng', question: 'SKU nào có thể thiếu hàng trước khi nhà cung cấp giao?', description: 'So tốc độ bán 30 ngày với tồn khả dụng; thời gian giao và ngày dự trữ là tham số giả định để quản lý điều chỉnh.' },
  { id: 'ABC', label: 'ABC', question: 'Doanh thu tập trung vào những SKU nào?', description: 'Xếp SKU theo doanh thu bán ròng tích lũy. A tới ngưỡng 80%, B tới 95%, còn lại C.' },
  { id: 'CHANNELS', label: 'Kênh / giờ', question: 'Kênh bán và giờ UTC nào có nhiều đơn?', description: 'So sánh IN_STORE và ONLINE theo ngày, giờ UTC và phương thức thanh toán.' },
  { id: 'QUALITY', label: 'Chất lượng dữ liệu', question: 'Có SKU lệch nguồn hoặc số lượng không hợp lệ không?', description: 'Đếm một số quy tắc chất lượng giữa MySQL và MongoDB. Số 0 không chứng minh mọi dữ liệu đều đúng.' },
];

const utcToday = () => new Date().toISOString().slice(0, 10);
const defaultFrom = () => { const d = new Date(); d.setUTCDate(d.getUTCDate() - 29); return d.toISOString().slice(0, 10); };
const amountFields = new Set(['net_sales_amount', 'value_at_risk', 'unit_cost', 'suggested_quantity']);
const labels: Record<string, string> = {
  sku: 'SKU', product_name: 'Sản phẩm', category_name: 'Nhóm hàng', net_quantity: 'SL bán ròng',
  net_sales_amount: 'Giá trị món sau trả', share_percent: 'Tỷ trọng (%)', cumulative_percent: 'Tích lũy (%)',
  abc_class: 'Nhóm ABC', batch_code: 'Mã lô', expiry_date: 'Hạn dùng', remaining_quantity: 'Còn trong lô',
  reserved_quantity: 'Đang giữ', available_quantity: 'Khả dụng', unit_cost: 'Giá vốn/đơn vị', value_at_risk: 'Giá trị vốn rủi ro',
  daily_sales: 'Bán TB/ngày', usable_quantity: 'Tồn khả dụng', days_of_stock: 'Số ngày còn hàng', suggested_quantity: 'Gợi ý nhập',
  stock_status: 'Đánh giá', event_date: 'Ngày UTC', hour_utc: 'Giờ UTC', order_type: 'Kênh', payment_method: 'Thanh toán',
  order_count: 'Số đơn', metric: 'Quy tắc', issue_count: 'Số vấn đề', catalog_count: 'Bản catalog', reorder_point: 'Ngưỡng nhập',
};

function display(value: Row[string], column: string) {
  if (value === null || value === undefined || value === '') return '—';
  if (amountFields.has(column)) return column === 'suggested_quantity' ? String(value) : formatVND(Number(value));
  if (column.endsWith('_percent') || column === 'daily_sales' || column === 'days_of_stock') return Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 2 });
  if (column.endsWith('_date') || column === 'event_date') return String(value).slice(0, 10);
  return String(value);
}

function safeCsv(value: unknown) {
  let raw = String(value ?? '');
  if (typeof value === 'string' && /^[=+@\-\t\r]/.test(raw)) raw = "'" + raw;
  return /[",\r\n;]/.test(raw) ? '"' + raw.replaceAll('"', '""') + '"' : raw;
}

export const OperationalAnalyticsPage: React.FC = () => {
  const [kind, setKind] = useState<Kind>('SALES');
  const [dataset, setDataset] = useState<Dataset>('OPERATIONS');
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(utcToday);
  const [top, setTop] = useState(20);
  const [horizon, setHorizon] = useState(7);
  const [leadDays, setLeadDays] = useState(7);
  const [coverageDays, setCoverageDays] = useState(14);
  const [result, setResult] = useState<Result | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const selected = tabs.find((tab) => tab.id === kind)!;
  const days = (Date.parse(to + 'T00:00:00Z') - Date.parse(from + 'T00:00:00Z')) / 86400000 + 1;
  const rangeError = !from || !to || days < 1 || days > 366 ? 'Chọn khoảng ngày hợp lệ, tối đa 366 ngày.' : '';

  const fetchReport = async () => {
    if (rangeError) return;
    setBusy(true); setError('');
    try {
      const params = new URLSearchParams({ report: kind, dataset, from, to, top: String(top), horizon: String(horizon), leadDays: String(leadDays), coverageDays: String(coverageDays) });
      setResult(await api.get<Result>('/analytics/operational?' + params.toString()));
    } catch (reason) {
      setResult(null);
      setError(reason instanceof Error ? reason.message : 'Không nhận được kết quả từ Apache Drill.');
    } finally { setBusy(false); }
  };

  useEffect(() => { void fetchReport(); }, []);

  const columns = useMemo(() => result?.columns?.filter((column) => result.rows?.some((row) => column in row))
    ?? [...new Set((result?.rows ?? []).flatMap((row) => Object.keys(row)))], [result]);
  const chartRows = useMemo(() => {
    if (!result || !['SALES', 'ABC', 'EXPIRY', 'REPLENISHMENT'].includes(kind)) return [];
    const metric = kind === 'EXPIRY' ? 'value_at_risk' : kind === 'REPLENISHMENT' ? 'suggested_quantity' : 'net_sales_amount';
    return result.rows.slice(0, 10).map((row) => ({ label: String(row.product_name ?? row.sku ?? '—'), value: Number(row[metric] ?? 0) }));
  }, [kind, result]);
  const chartMax = Math.max(1, ...chartRows.map((row) => row.value));
  const resultMatches = result?.reportKind === kind && result.dataset === dataset && result.from === from && result.to === to;

  const downloadCsv = () => {
    if (!result || !columns.length) return;
    const lines = [columns.map((column) => safeCsv(labels[column] ?? column)).join(';'), ...result.rows.map((row) => columns.map((column) => safeCsv(row[column])).join(';'))];
    const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = `drill-${kind.toLowerCase()}-${dataset.toLowerCase()}-${from}-${to}.csv`; link.click(); URL.revokeObjectURL(url);
  };

  return <div className="space-y-5">
    <header><p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">Apache Drill · phân tích vận hành</p><h1 className="mt-1 text-2xl font-bold text-gray-900">Dữ liệu nhiều nguồn thành câu trả lời quản lý</h1><p className="mt-2 max-w-4xl text-sm leading-relaxed text-gray-600">Chọn câu hỏi để xem. Drill chạy SQL trên MySQL và MongoDB; các sự kiện Astra được đọc từ file JSON/Parquet trong phần dòng tiền và đối soát.</p></header>
    <div className="flex flex-wrap gap-2" role="tablist" aria-label="Loại báo cáo">{tabs.map((tab) => <button key={tab.id} role="tab" aria-selected={kind === tab.id} onClick={() => { setKind(tab.id); setResult(null); setError(''); }} className={`rounded-lg border px-3 py-2 text-sm font-medium ${kind === tab.id ? 'border-emerald-600 bg-emerald-50 text-emerald-800' : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'}`}>{tab.label}</button>)}</div>
    <Card title={selected.question}>
      <div className="space-y-4">
        <p className="text-sm leading-relaxed text-gray-600">{selected.description}</p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="text-xs font-medium text-gray-600">Nguồn dữ liệu<select value={dataset} onChange={(e) => { setDataset(e.target.value as Dataset); setResult(null); }} className="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900"><option value="OPERATIONS">Dữ liệu vận hành</option><option value="SIMULATED">Dữ liệu mô phỏng</option></select></label>
          <Input label="Từ ngày (UTC)" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <Input label="Đến ngày (UTC)" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          {kind === 'SALES' || kind === 'ABC' ? <label className="text-xs font-medium text-gray-600">Số dòng Top<select value={top} onChange={(e) => setTop(Number(e.target.value))} className="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm"><option value={10}>10 SKU</option><option value={20}>20 SKU</option><option value={50}>50 SKU</option><option value={100}>100 SKU</option></select></label> : null}
          {kind === 'EXPIRY' ? <label className="text-xs font-medium text-gray-600">Sắp hết hạn trong<select value={horizon} onChange={(e) => setHorizon(Number(e.target.value))} className="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm"><option value={7}>7 ngày</option><option value={14}>14 ngày</option><option value={30}>30 ngày</option></select></label> : null}
          {kind === 'REPLENISHMENT' ? <><Input label="Thời gian giao giả định (ngày)" type="number" min={1} max={90} value={leadDays} onChange={(e) => setLeadDays(Number(e.target.value))} /><Input label="Mục tiêu dự trữ (ngày)" type="number" min={1} max={180} value={coverageDays} onChange={(e) => setCoverageDays(Number(e.target.value))} /></> : null}
        </div>
        {rangeError && <p role="alert" className="text-sm text-rose-700">{rangeError}</p>}
        {dataset === 'SIMULATED' && <p className="rounded-lg border border-sky-200 bg-sky-50 p-3 text-xs leading-relaxed text-sky-900">Đây là môi trường dữ liệu riêng. Cần nạp schema mô phỏng vào MySQL và catalog mô phỏng vào MongoDB trước khi truy vấn; kết quả không đại diện giao dịch thật.</p>}
        <div className="flex flex-wrap items-center gap-2"><Button onClick={() => void fetchReport()} disabled={busy || !!rangeError} isLoading={busy} leftIcon={<RefreshCw className="h-4 w-4" />}>Chạy báo cáo bằng Drill</Button><Button variant="outline" onClick={downloadCsv} disabled={!result?.rows?.length} leftIcon={<Download className="h-4 w-4" />}>Tải CSV</Button><span className="text-xs text-gray-500">Tối đa 366 ngày · kết quả thực tế từ Drill</span></div>
      </div>
    </Card>
    {error && <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{error}<p className="mt-1 text-xs">Kiểm tra Drill đang chạy, storage plugin và quyền truy cập nguồn đã chọn.</p></div>}
      {result && !resultMatches && <div role="status" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">Đang hiển thị lần truy vấn trước: {result.reportKind} · {result.dataset === 'SIMULATED' ? 'Mô phỏng' : 'Vận hành'} · {result.from} → {result.to}. Bấm “Chạy báo cáo bằng Drill” để cập nhật theo lựa chọn hiện tại.</div>}
      {result && <>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><Card title="Nguồn đã đọc"><p className="text-sm font-semibold text-gray-900">{result.sourceNames?.join(' + ') ?? '—'}</p></Card><Card title="Khoảng dữ liệu"><p className="text-sm font-semibold text-gray-900">{result.from} → {result.to}</p></Card><Card title="Kết quả"><p className="text-sm font-semibold text-gray-900">{result.rows?.length ?? 0} dòng{result.rowLimit ? ` / tối đa ${result.rowLimit}` : ''}</p></Card><Card title="Thời gian nhận từ Drill"><p className="text-sm font-semibold text-gray-900">{result.requestElapsedMs ?? '—'} ms</p><p className="mt-1 text-[11px] text-gray-500">Đo tại API, gồm chờ trả lời; không phải benchmark chuẩn.</p></Card></div>
      {result.definition && <Card title="Cách đọc kết quả"><p className="text-sm leading-relaxed text-gray-700">{result.definition}</p>{result.managementAction && <p className="mt-2 text-sm leading-relaxed text-emerald-800"><strong>Ứng dụng:</strong> {result.managementAction}</p>}{result.assumptions?.map((item) => <p key={item} className="mt-2 text-xs leading-relaxed text-gray-500">Giả định: {item}</p>)}</Card>}
      {chartRows.length > 0 && <Card title={kind === 'EXPIRY' ? '10 lô sắp hết hạn đầu tiên' : kind === 'REPLENISHMENT' ? '10 SKU có lượng gợi ý nhập cao nhất' : '10 SKU dẫn đầu'}><div className="space-y-3">{chartRows.map((item, index) => <div key={index} className="grid grid-cols-[minmax(6rem,1fr)_3fr_auto] items-center gap-3 text-xs"><span className="truncate text-gray-700" title={item.label}>{item.label}</span><div className="h-3 overflow-hidden rounded bg-gray-100"><div className="h-full rounded bg-emerald-600" style={{ width: `${Math.max(1, item.value / chartMax * 100)}%` }} /></div><strong className="whitespace-nowrap text-gray-800">{kind === 'REPLENISHMENT' ? `${item.value.toLocaleString('vi-VN')} sp` : formatVND(item.value)}</strong></div>)}</div></Card>}
      <Card title={`Kết quả: ${selected.label}`}><div className="overflow-x-auto"><table className="w-full text-left text-xs"><thead className="sticky top-0 bg-gray-50 text-gray-600"><tr>{columns.map((column) => <th key={column} className="whitespace-nowrap border-b p-3">{labels[column] ?? column}</th>)}</tr></thead><tbody>{result.rows?.map((row, index) => <tr key={index} className="border-b border-gray-100 align-top hover:bg-gray-50">{columns.map((column) => <td key={column} className="max-w-72 p-3 text-gray-700">{display(row[column], column)}</td>)}</tr>)}</tbody></table>{!result.rows?.length && <p className="p-8 text-center text-sm text-gray-500">Drill trả về 0 dòng cho lựa chọn này.</p>}</div></Card>
      <DrillReportExplanation title="SQL và cách Drill thực thi" report={result} description="SQL và mã truy vấn lấy từ phản hồi thực tế. Mở Profiles để xem kế hoạch và từng bước đọc nguồn." />
    </>}
  </div>;
};
