import { useEffect, useRef, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { api } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import type { DrillReportMetadata } from '../types';
import { vietnamDateKey } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Pagination, useListPage } from '../components/ui/Pagination';
import { DrillReportExplanation } from '../components/analytics/DrillReportExplanation';

interface PurchaseCostRow {
  batch_id: number; batch_code: string; product_id: number; sku: string;
  product_name: string; supplier_name: string | null; received_at: number | string;
  unit_cost: string; previous_unit_cost: string | null; price_change: string | null;
  received_quantity: number | string; remaining_quantity: number | string;
  received_value: string; stock_value: string;
}
interface PurchaseCostReport extends DrillReportMetadata { rows: PurchaseCostRow[] }
interface DateRange { from: string; to: string }

function initialRange(): DateRange {
  const to = vietnamDateKey();
  const from = new Date(to + 'T00:00:00Z');
  from.setUTCDate(from.getUTCDate() - 29);
  return { from: from.toISOString().slice(0, 10), to };
}

// Decimal strings stay exact even above JavaScript's safe integer range.
function money(value: string | null | undefined) {
  if (value == null) return '—';
  const decimal = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value);
  if (!decimal) return '—';
  const fraction = decimal[3] || '';
  const cents = BigInt(decimal[2]) * 100n + BigInt((fraction + '00').slice(0, 2)) + (fraction[2] >= '5' ? 1n : 0n);
  const remainder = (cents % 100n).toString().padStart(2, '0').replace(/0+$/, '');
  return `${decimal[1]}${(cents / 100n).toLocaleString('vi-VN')}${remainder ? ',' + remainder : ''} ₫`;
}

function quantity(value: number | string) {
  const integer = String(value);
  return /^-?\d+$/.test(integer) ? BigInt(integer).toLocaleString('vi-VN') : '—';
}

const vietnamDateTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
});

function receivedAt(value: number | string) {
  let date: Date;
  if (typeof value === 'number') {
    date = new Date(value);
  } else {
    const timestamp = value.trim().replace(' ', 'T');
    if (!timestamp) return '—';
    // Offset-free database timestamps are UTC, regardless of the browser's timezone.
    const utc = /^\d{4}-\d{2}-\d{2}$/.test(timestamp) ? timestamp + 'T00:00:00Z'
      : /(?:Z|[+-]\d{2}:?\d{2})$/i.test(timestamp) ? timestamp : timestamp + 'Z';
    date = new Date(utc);
  }
  return Number.isNaN(date.getTime()) ? '—' : vietnamDateTime.format(date);
}

export function PurchaseCostsPage() {
  const { role } = useAuth();
  const allowed = role !== null && ['ADMIN', 'MANAGER', 'WAREHOUSE'].includes(role);
  const [range, setRange] = useState(initialRange);
  const [loadedRange, setLoadedRange] = useState<DateRange | null>(null);
  const [report, setReport] = useState<PurchaseCostReport | null>(null);
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  const requestLock = useRef(false);
  const days = (Date.parse(range.to + 'T00:00:00Z') - Date.parse(range.from + 'T00:00:00Z')) / 86400000 + 1;
  const rangeError = !range.from || !range.to || !Number.isFinite(days) || days < 1 || days > 366
    ? 'Chọn khoảng ngày hợp lệ từ 1 đến 366 ngày, tính cả ngày đầu và ngày cuối.' : '';
  const keyword = search.trim().toLocaleLowerCase('vi');
  const filtered = (report?.rows || []).filter(row => [row.sku, row.product_name, row.supplier_name]
    .some(value => value?.toLocaleLowerCase('vi').includes(keyword)));
  const page = useListPage(filtered, JSON.stringify([search, loadedRange]));

  useEffect(() => () => request.current?.abort(), []);

  const fetchReport = async () => {
    if (!allowed || rangeError || requestLock.current) return;
    requestLock.current = true;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    const requestedRange = { ...range };
    setBusy(true); setError(''); setReport(null); setLoadedRange(null);
    try {
      const result = await api.get<PurchaseCostReport>('/analytics/purchase-costs?' + new URLSearchParams({ ...requestedRange }), { signal: controller.signal });
      if (!controller.signal.aborted) { setReport(result); setLoadedRange(requestedRange); }
    } catch (reason) {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không tải được giá nhập theo lô.');
    } finally {
      requestLock.current = false;
      if (!controller.signal.aborted) setBusy(false);
    }
  };

  if (!allowed) return <p role="alert">Chỉ ADMIN, MANAGER và WAREHOUSE được xem giá nhập theo lô.</p>;

  return <div className="space-y-5">
    <header><h1 className="text-xl font-bold text-gray-900">Giá nhập theo lô</h1>
      <p className="mt-1 text-sm text-gray-600">Giá nhập thực tế đã lưu trên từng lô hàng trong MySQL, được tra cứu qua Apache Drill.</p>
      <p className="mt-2 text-sm text-gray-600">Đây là giá lịch sử của lô, không phải báo giá nhà cung cấp hoặc giá nhập mới nhất của sản phẩm. Báo cáo này không tính biên lợi nhuận.</p>
    </header>
    <Card title="Khoảng ngày nhập lô">
      <form onSubmit={event => { event.preventDefault(); void fetchReport(); }} className="flex flex-col items-end gap-3 sm:flex-row">
        <Input label="Từ ngày (Việt Nam)" type="date" required value={range.from} disabled={busy} onChange={event => setRange({ ...range, from: event.target.value })} />
        <Input label="Đến ngày (Việt Nam)" type="date" required value={range.to} disabled={busy} onChange={event => setRange({ ...range, to: event.target.value })} />
        <Button type="submit" disabled={busy || !!rangeError} isLoading={busy} leftIcon={<RefreshCw className="h-4 w-4" aria-hidden="true" />}>Xem báo cáo</Button>
      </form>
      {rangeError && <p role="alert" className="mt-2 text-xs text-amber-800">{rangeError}</p>}
      <p className="mt-3 text-xs text-gray-500">Mặc định 30 ngày Việt Nam gần nhất, gồm hôm nay. Tối đa 366 ngày mỗi lần tra cứu.</p>
    </Card>
    {busy && <p role="status" className="text-sm text-blue-700">Đang đọc giá nhập theo lô…</p>}
    {error && <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{error} Chưa có kết quả báo cáo.</p>}
    {!report && !busy && !error && <p className="text-sm text-gray-500">Chọn khoảng ngày và bấm Xem báo cáo để đọc các lô đã nhập.</p>}
    {report && <Card title="Giá nhập thực tế đã ghi nhận">
      <p className="mb-3 text-xs text-gray-600">Ngày nhập đã tra cứu: {loadedRange?.from} → {loadedRange?.to} (Việt Nam). Nguồn: {report.sourceNames?.join(' · ') || 'Chưa có metadata nguồn'}.</p>
      {loadedRange && (loadedRange.from !== range.from || loadedRange.to !== range.to) && <p role="status" className="mb-3 text-xs text-amber-800">Khoảng ngày đã đổi; bảng vẫn là lần tra cứu trước. Bấm Xem báo cáo để cập nhật.</p>}
      <Input label="Tìm trong kết quả đã tải" placeholder="SKU, tên sản phẩm hoặc nhà cung cấp" value={search} onChange={event => setSearch(event.target.value)} leftIcon={<Search className="h-4 w-4" aria-hidden="true" />} />
      <p className="my-3 text-xs text-gray-500">Tiền: VND. Đơn giá tính trên một đơn vị hàng; số lượng theo đơn vị ghi nhận của sản phẩm. Chênh lệch = giá lô − giá lô trước; dấu — nghĩa là thiếu giá đối chiếu. Tồn còn lại là số lượng hiện được lưu trên lô, không phải tồn chốt cuối khoảng ngày.</p>
      <div className="overflow-x-auto"><table className="w-full min-w-[1350px] text-left text-xs">
        <caption className="sr-only">Giá nhập, chênh lệch và giá trị theo từng lô đã nhập trong khoảng ngày Việt Nam</caption>
        <thead className="border-b border-gray-200 text-gray-600"><tr>
          {['Lô / mã lô', 'SKU / sản phẩm', 'Nhà cung cấp', 'Nhập lúc (giờ VN)'].map(label => <th key={label} scope="col" className="whitespace-nowrap p-3">{label}</th>)}
          {['Giá lô (VND/đv)', 'Giá lô trước (VND/đv)', 'Chênh lệch (VND/đv)', 'SL nhập (đv)', 'SL còn lại (đv)', 'Giá trị nhập (VND)', 'Giá trị tồn (VND)'].map(label => <th key={label} scope="col" className="whitespace-nowrap p-3 text-right">{label}</th>)}
        </tr></thead>
        <tbody className="divide-y divide-gray-100">{page.rows.map(row => <tr key={row.batch_id}>
          <td className="p-3"><span className="font-medium">{row.batch_code || '—'}</span><p className="mt-1 text-gray-500">#{row.batch_id}</p></td>
          <td className="p-3"><span className="font-medium">{row.product_name}</span><p className="mt-1 text-gray-500">{row.sku} · #{row.product_id}</p></td>
          <td className="p-3">{row.supplier_name || '—'}</td>
          <td className="whitespace-nowrap p-3">{receivedAt(row.received_at)}</td>
          <td className="whitespace-nowrap p-3 text-right tabular-nums">{money(row.unit_cost)}</td>
          <td className="whitespace-nowrap p-3 text-right tabular-nums">{money(row.previous_unit_cost)}</td>
          <td className="whitespace-nowrap p-3 text-right tabular-nums">{money(row.price_change)}</td>
          <td className="p-3 text-right tabular-nums">{quantity(row.received_quantity)}</td>
          <td className="p-3 text-right tabular-nums">{quantity(row.remaining_quantity)}</td>
          <td className="whitespace-nowrap p-3 text-right tabular-nums">{money(row.received_value)}</td>
          <td className="whitespace-nowrap p-3 text-right tabular-nums">{money(row.stock_value)}</td>
        </tr>)}{page.rows.length === 0 && <tr><td colSpan={11} className="p-6 text-center text-gray-500">{report.rows.length === 0 ? 'Không có lô nhập trong kết quả của khoảng ngày này.' : 'Không có lô khớp từ khóa tìm kiếm.'}</td></tr>}</tbody>
      </table></div>
      {report.rowLimit && report.rows.length >= report.rowLimit ? <p role="status" className="mt-3 text-xs text-amber-800">Đã chạm giới hạn {report.rowLimit.toLocaleString('vi-VN')} dòng; kết quả có thể chưa đầy đủ. Thu hẹp khoảng ngày để xem tiếp. Tìm kiếm và phân trang chỉ áp dụng trên các dòng đã tải.</p> : null}
      <Pagination {...page} />
    </Card>}
    <DrillReportExplanation title="Nguồn và truy vấn giá nhập theo lô" report={report} description="Đọc chi phí nhập và số lượng đã lưu trên lô hàng trong MySQL. Giá trị nhập = đơn giá lô × số lượng nhập; giá trị tồn = đơn giá lô × số lượng còn lại hiện tại. Tra cứu này chỉ đọc dữ liệu." />
  </div>;
}
