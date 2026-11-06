import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Download, RefreshCw } from 'lucide-react';
import { api } from '../api/client';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { DrillReportExplanation } from '../components/analytics/DrillReportExplanation';
import { formatVND, vietnamDateKey } from '../utils/formatters';

type Kind = 'SALES' | 'EXPIRY' | 'REPLENISHMENT' | 'PROMOTIONS';
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
  parameters?: { top: number; horizon: number; leadDays: number; coverageDays: number };
}

const tabs: { id: Kind; label: string; question: string; description: string }[] = [
  { id: 'SALES', label: 'Sản phẩm bán chạy', question: 'Sản phẩm nào bán được nhiều nhất trong khoảng ngày đã chọn?', description: 'Xếp theo số lượng bán ròng: đã mua trừ đã nhận trả. Biểu đồ và tỷ trọng đều theo số lượng; giá trị tiền bán chỉ là thông tin tham khảo. Drill ghép giao dịch với thông tin sản phẩm MySQL theo SKU.' },
  { id: 'EXPIRY', label: 'Lô sắp hết hạn', question: 'Lô nào cần ưu tiên xử lý trước khi hết hạn?', description: 'Xem hàng còn trong lô và giá trị vốn có rủi ro trong 7, 14 hoặc 30 ngày tới. Hạn dùng và số lượng lấy từ MySQL; thông tin sản phẩm lấy từ MySQL.' },
  { id: 'REPLENISHMENT', label: 'Gợi ý nhập thêm', question: 'Sản phẩm nào có nguy cơ thiếu hàng?', description: 'So tốc độ bán với tồn khả dụng; thời gian giao và số ngày muốn dự trữ do bạn nhập. Đây là gợi ý để xem xét, không tự tạo đơn nhập kho.' },
  { id: 'PROMOTIONS', label: 'Thống kê khuyến mãi', question: 'Các chương trình khuyến mãi đã được áp dụng trên hóa đơn như thế nào?', description: 'Chỉ thống kê chương trình đã thực sự áp dụng trên hóa đơn, theo ngày tạo đơn trong khoảng ngày Việt Nam đã chọn. Mỗi dòng là một phiên bản chương trình và một sản phẩm, dùng thông tin đã lưu trên hóa đơn khi bán, không lấy tên chương trình hiện tại. Chương trình chưa dùng hoặc dành cho tương lai vẫn ở trang quản lý, không được liệt kê thành dòng 0 ở đây.' },
];

const vietnamToday = () => vietnamDateKey();
const defaultFrom = () => { const d = new Date(vietnamDateKey() + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() - 29); return d.toISOString().slice(0, 10); };
const amountFields = new Set(['net_sales_amount', 'discount_amount', 'value_at_risk', 'unit_cost']);
const labels: Record<string, string> = {
  product_id: 'Mã sản phẩm', sku: 'SKU', product_name: 'Sản phẩm', category_name: 'Nhóm hàng', net_quantity: 'Bán sau trả',
  net_sales_amount: 'Tiền bán hàng',
  promotion_id: 'Mã chương trình', promotion_name: 'Chương trình', discount_percent: 'Mức giảm (%)',
  sold_quantity: 'Đã bán', returned_quantity: 'Đã trả', discount_amount: 'Tổng tiền giảm',
  quantity_share_percent: 'Tỷ trọng số lượng (%)',
  batch_code: 'Mã lô', expiry_date: 'Hạn dùng', remaining_quantity: 'Còn trong lô',
  available_quantity: 'Khả dụng', unit_cost: 'Giá vốn/đơn vị', value_at_risk: 'Giá trị vốn rủi ro',
  daily_sales: 'Bán TB/ngày', usable_quantity: 'Tồn khả dụng', days_of_stock: 'Số ngày còn hàng', suggested_quantity: 'Gợi ý nhập',
  stock_status: 'Đánh giá', order_count: 'Số đơn', reorder_point: 'Ngưỡng nhập',
};

function display(value: Row[string], column: string) {
  if (value === null || value === undefined || value === '') return '—';
  if (column === 'stock_status') return value === 'REORDER' ? 'Cần xem xét nhập thêm' : value === 'SUFFICIENT' ? 'Chưa chạm ngưỡng nhập thêm' : String(value);
  if (amountFields.has(column)) return formatVND(Number(value));
  if (column.endsWith('_percent') || column === 'daily_sales' || column === 'days_of_stock') return Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 2 });
  if (column.endsWith('_date') || column === 'event_date') {
    return typeof value === 'number' ? new Date(value).toISOString().slice(0, 10) : String(value).slice(0, 10);
  }
  return String(value);
}

function safeCsv(value: unknown) {
  let raw = String(value ?? '');
  if (typeof value === 'string' && /^[=+@\-\t\r]/.test(raw)) raw = "'" + raw;
  return /[",\r\n;]/.test(raw) ? '"' + raw.replaceAll('"', '""') + '"' : raw;
}

export const OperationalAnalyticsPage: React.FC = () => {
  const [kind, setKind] = useState<Kind>('SALES');
  const dataset: Dataset = 'OPERATIONS';
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(vietnamToday);
  const [top, setTop] = useState(20);
  const [horizon, setHorizon] = useState(7);
  const [leadDays, setLeadDays] = useState(7);
  const [coverageDays, setCoverageDays] = useState(14);
  const [result, setResult] = useState<Result | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  const selected = tabs.find((tab) => tab.id === kind)!;
  const days = (Date.parse(to + 'T00:00:00Z') - Date.parse(from + 'T00:00:00Z')) / 86400000 + 1;
  const rangeError = !from || !to || days < 1 || days > 366 ? 'Chọn khoảng ngày hợp lệ, tối đa 366 ngày.' : '';

  const fetchReport = async () => {
    if (rangeError) return;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setBusy(true); setError('');
    try {
      const params = new URLSearchParams({ report: kind, dataset, from, to, top: String(top), horizon: String(horizon), leadDays: String(leadDays), coverageDays: String(coverageDays) });
      const next = await api.get<Result>('/analytics/operational?' + params.toString(), { signal: controller.signal });
      if (!controller.signal.aborted) setResult(next);
    } catch (reason) {
      if (controller.signal.aborted) return;
      setResult(null);
      setError(reason instanceof Error ? reason.message : 'Không nhận được kết quả từ Apache Drill.');
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };

  useEffect(() => { void fetchReport(); return () => request.current?.abort(); }, []);

  const columns = useMemo(() => (result?.columns?.filter((column) => result.rows?.some((row) => column in row))
    ?? [...new Set((result?.rows ?? []).flatMap((row) => Object.keys(row)))]).filter((column) => column !== 'catalog_count'), [result]);
  const chartRows = useMemo(() => {
    if (!result || !['SALES', 'EXPIRY', 'REPLENISHMENT'].includes(kind)) return [];
    const metric = kind === 'SALES' ? 'net_quantity' : kind === 'EXPIRY' ? 'value_at_risk' : 'suggested_quantity';
    return result.rows.slice(0, 10).map((row) => ({ label: String(row.product_name ?? row.sku ?? '—'), value: Number(row[metric] ?? 0) }));
  }, [kind, result]);
  const chartMax = Math.max(1, ...chartRows.map((row) => row.value));
  const resultMatches = result?.reportKind === kind && result.dataset === dataset && result.from === from && result.to === to
    && result.parameters?.top === top && result.parameters?.horizon === horizon
    && result.parameters?.leadDays === leadDays && result.parameters?.coverageDays === coverageDays;

  const downloadCsv = () => {
    if (!result || !columns.length) return;
    const lines = [columns.map((column) => safeCsv(labels[column] ?? column)).join(';'), ...result.rows.map((row) => columns.map((column) => safeCsv(row[column])).join(';'))];
    const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = `drill-${result.reportKind.toLowerCase()}-${result.dataset.toLowerCase()}-${result.from}-${result.to}.csv`; link.click(); URL.revokeObjectURL(url);
  };

  return <div className="space-y-5">
    <header><h2 className="text-lg font-bold text-gray-900">Bạn muốn tìm hiểu điều gì?</h2><p className="mt-1 text-sm text-gray-600">Chọn báo cáo, chọn ngày rồi bấm Xem kết quả. Mở phần SQL bên dưới khi cần giải thích cách Drill truy vấn.</p></header>
    <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">{tabs.map(tab => <button key={tab.id} aria-pressed={kind === tab.id} disabled={busy} onClick={() => { setKind(tab.id); setResult(null); setError(''); }} className={`rounded-lg border px-4 py-3 text-left text-sm font-semibold ${kind === tab.id ? 'border-blue-600 bg-blue-50 text-blue-800' : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'}`}>{tab.label}</button>)}</div>
    <Card title={selected.question}>
      <fieldset disabled={busy} className="space-y-4 disabled:opacity-70">
        <p className="text-sm leading-relaxed text-gray-600">{selected.description}</p>
        {kind === 'PROMOTIONS' && <div className="space-y-2 text-xs leading-relaxed text-gray-600">
          <p>Đã bán là số lượng trên hóa đơn; Đã trả là số lượng đã nhận trả; Còn lại là Đã bán trừ Đã trả. Tiền bán hàng đã tính theo số lượng còn lại sau trả hàng.</p>
          <p>Tổng tiền giảm là số tiền giảm đã áp dụng lúc bán ban đầu, chưa trừ theo hàng trả và không phải tiền hoàn trả.</p>
          <p>Số đơn chỉ tính trong từng dòng. Một hóa đơn có thể xuất hiện ở nhiều dòng sản phẩm hoặc chương trình; không cộng các dòng để tính tổng số đơn duy nhất.</p>
          <p>Kết quả mô tả giao dịch đã có, không chứng minh khuyến mãi làm tăng lượng bán.</p>
        </div>}
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Input label="Từ ngày (giờ Việt Nam)" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <Input label="Đến ngày (giờ Việt Nam)" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          {kind === 'SALES' || kind === 'PROMOTIONS' ? <label className="text-xs font-medium text-gray-600">Số dòng Top<select value={top} onChange={(e) => setTop(Number(e.target.value))} className="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm"><option value={10}>10 dòng</option><option value={20}>20 dòng</option><option value={50}>50 dòng</option><option value={100}>100 dòng</option></select></label> : null}
          {kind === 'EXPIRY' ? <label className="text-xs font-medium text-gray-600">Sắp hết hạn trong<select value={horizon} onChange={(e) => setHorizon(Number(e.target.value))} className="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm"><option value={7}>7 ngày</option><option value={14}>14 ngày</option><option value={30}>30 ngày</option></select></label> : null}
          {kind === 'REPLENISHMENT' ? <><Input label="Thời gian giao giả định (ngày)" type="number" min={1} max={90} value={leadDays} onChange={(e) => setLeadDays(Number(e.target.value))} /><Input label="Mục tiêu dự trữ (ngày)" type="number" min={1} max={180} value={coverageDays} onChange={(e) => setCoverageDays(Number(e.target.value))} /></> : null}
        </div>
        {rangeError && <p role="alert" className="text-sm text-rose-700">{rangeError}</p>}
        <div className="flex flex-wrap items-center gap-2"><Button onClick={() => void fetchReport()} disabled={busy || !!rangeError} isLoading={busy} leftIcon={<RefreshCw className="h-4 w-4" />}>Chạy báo cáo bằng Drill</Button><Button variant="outline" onClick={downloadCsv} disabled={!result?.rows?.length} leftIcon={<Download className="h-4 w-4" />}>Tải CSV</Button><span className="text-xs text-gray-500">Tối đa 366 ngày · kết quả thực tế từ Drill</span></div>
      </fieldset>
    </Card>
    {error && <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{error}<p className="mt-1 text-xs">Kiểm tra Drill đang chạy, storage plugin và quyền truy cập nguồn đã chọn.</p></div>}
      {result && !resultMatches && <div role="status" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">Đang hiển thị lần truy vấn trước: {result.reportKind} · Vận hành · {result.from} → {result.to}. Bấm “Chạy báo cáo bằng Drill” để cập nhật theo lựa chọn hiện tại.</div>}
      {result && <>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><Card title="Nguồn đã đọc"><p className="text-sm font-semibold text-gray-900">{result.sourceNames?.join(' + ') ?? '—'}</p></Card><Card title="Khoảng dữ liệu"><p className="text-sm font-semibold text-gray-900">{result.from} → {result.to}</p></Card><Card title="Kết quả"><p className="text-sm font-semibold text-gray-900">{result.rows?.length ?? 0} dòng{result.rowLimit ? ` / tối đa ${result.rowLimit}` : ''}</p></Card><Card title="Thời gian nhận từ Drill"><p className="text-sm font-semibold text-gray-900">{result.requestElapsedMs ?? '—'} ms</p><p className="mt-1 text-[11px] text-gray-500">Đo tại API, gồm chờ trả lời; không phải benchmark chuẩn.</p></Card></div>
      {result.definition && <Card title="Cách đọc kết quả"><p className="text-sm leading-relaxed text-gray-700">{result.definition}</p>{result.managementAction && <p className="mt-2 text-sm leading-relaxed text-blue-800"><strong>Ứng dụng:</strong> {result.managementAction}</p>}{result.assumptions?.map((item) => <p key={item} className="mt-2 text-xs leading-relaxed text-gray-500">Giả định: {item}</p>)}</Card>}
      {chartRows.length > 0 && <Card title={kind === 'EXPIRY' ? '10 lô sắp hết hạn đầu tiên' : kind === 'REPLENISHMENT' ? '10 SKU có lượng gợi ý nhập cao nhất' : 'Bán chạy theo số lượng (tối đa 10 sản phẩm)'}><div className="space-y-3">{chartRows.map((item, index) => <div key={index} className="grid grid-cols-[minmax(6rem,1fr)_3fr_auto] items-center gap-3 text-xs"><span className="truncate text-gray-700" title={item.label}>{item.label}</span><div className="h-3 overflow-hidden rounded bg-gray-100"><div className="h-full rounded bg-blue-600" style={{ width: `${Math.max(0, item.value / chartMax * 100)}%` }} /></div><strong className="whitespace-nowrap text-gray-800">{kind === 'REPLENISHMENT' || kind === 'SALES' ? `${item.value.toLocaleString('vi-VN')} sp` : formatVND(item.value)}</strong></div>)}</div></Card>}
      <Card title={`Kết quả: ${selected.label}`}><div className="overflow-x-auto"><table className="w-full text-left text-xs"><thead className="sticky top-0 bg-gray-50 text-gray-600"><tr>{columns.map((column) => <th key={column} className="whitespace-nowrap border-b p-3">{labels[column] ?? column}</th>)}</tr></thead><tbody>{result.rows?.map((row, index) => <tr key={index} className="border-b border-gray-100 align-top hover:bg-gray-50">{columns.map((column) => <td key={column} className="max-w-72 p-3 text-gray-700">{display(row[column], column)}</td>)}</tr>)}</tbody></table>{!result.rows?.length && <p className="p-8 text-center text-sm text-gray-500">Drill trả về 0 dòng cho lựa chọn này.</p>}</div></Card>
      <DrillReportExplanation title="SQL và cách Drill thực thi" report={result} description="SQL và mã truy vấn lấy từ phản hồi thực tế. Mở Profiles để xem kế hoạch và từng bước đọc nguồn." />
    </>}
  </div>;
};
