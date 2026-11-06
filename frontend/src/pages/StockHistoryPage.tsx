import { useEffect, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { api } from '../api/client';
import type { StockHistoryReport } from '../types';
import { formatDateTime, vietnamDateKey } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Pagination, useListPage } from '../components/ui/Pagination';

function completedDay(daysAgo: number) {
  const date = new Date(vietnamDateKey() + 'T00:00:00Z');
  date.setUTCDate(date.getUTCDate() - daysAgo);
  return date.toISOString().slice(0, 10);
}

const quantity = (value: number | string | null | undefined) => value == null ? '—' : Number(value).toLocaleString('vi-VN');

export const StockHistoryPage = () => {
  const [from, setFrom] = useState(() => completedDay(7));
  const [to, setTo] = useState(() => completedDay(1));
  const [report, setReport] = useState<StockHistoryReport | null>(null);
  const [loadedRange, setLoadedRange] = useState<{ from: string; to: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  const page = useListPage(report?.rows ?? [], `${loadedRange?.from}:${loadedRange?.to}`);
  const days = (Date.parse(to + 'T00:00:00Z') - Date.parse(from + 'T00:00:00Z')) / 86400000 + 1;
  const rangeError = !from || !to || !Number.isFinite(days) || days < 1 || days > 31
    ? 'Chọn khoảng ngày hợp lệ, tối đa 31 ngày.'
    : to >= vietnamDateKey() ? 'Chọn các ngày Việt Nam đã kết thúc, tối đa đến hôm qua.' : '';

  useEffect(() => () => request.current?.abort(), []);

  const fetchHistory = async () => {
    if (rangeError || busy) return;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    const range = { from, to };
    setBusy(true); setError(''); setReport(null); setLoadedRange(null);
    try {
      const result = await api.get<StockHistoryReport>('/analytics/stock-history?' + new URLSearchParams(range), { signal: controller.signal });
      if (!controller.signal.aborted) { setReport(result); setLoadedRange(range); }
    } catch (reason) {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không tải được lịch sử kho.');
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  };

  return <div className="space-y-5">
    <Card title="Lịch sử kho theo ngày Việt Nam">
      <p className="mb-4 text-sm text-gray-600">Đọc snapshot tồn kho đã được tạo trong Cassandra. Đây là snapshot suy ra từ lịch sử nghiệp vụ; không phải bản ghi kiểm kê độc lập. Trang này chỉ đọc, không tạo hoặc ghi snapshot.</p>
      <div className="flex flex-col items-end gap-3 sm:flex-row">
        <Input label="Từ ngày (Việt Nam)" type="date" value={from} max={completedDay(1)} onChange={event => setFrom(event.target.value)} disabled={busy} />
        <Input label="Đến ngày (Việt Nam)" type="date" value={to} max={completedDay(1)} onChange={event => setTo(event.target.value)} disabled={busy} />
        <Button onClick={() => void fetchHistory()} disabled={busy || !!rangeError} isLoading={busy} leftIcon={<RefreshCw className="h-4 w-4" />}>Xem lịch sử kho</Button>
      </div>
      {rangeError && <p role="alert" className="mt-2 text-xs text-amber-800">{rangeError}</p>}
      <p className="mt-3 text-xs text-gray-500">Mặc định 7 ngày Việt Nam đã kết thúc. Bấm xem để tải dữ liệu hiện có.</p>
    </Card>
    {busy && <p role="status" className="text-sm text-blue-700">Đang đọc lịch sử kho…</p>}
    {error && <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{error}. Chưa có kết quả để kết luận tồn kho.</p>}
    {report && <Card title="Snapshot kho đã đọc">
      <p className="mb-3 text-xs text-gray-600">Khoảng ngày đã tra cứu: {loadedRange?.from} → {loadedRange?.to} (Việt Nam). Nguồn: {report.sourceNames?.join(' · ') || 'Chưa có metadata nguồn'}.</p>
      {loadedRange && (loadedRange.from !== from || loadedRange.to !== to) && <p role="status" className="mb-3 text-xs text-amber-800">Khoảng ngày đã đổi; bảng vẫn là lần tra cứu trước. Bấm xem để cập nhật.</p>}
      <div className="overflow-x-auto"><table className="w-full text-left text-xs">
        <caption className="sr-only">Tồn đầu, các biến động và tồn cuối theo snapshot ngày Việt Nam</caption>
        <thead className="border-b bg-gray-50 text-gray-600"><tr>{['Ngày Việt Nam', 'Mã sản phẩm', 'Sản phẩm', 'Tồn đầu', 'Nhập', 'Bán', 'Nhận trả', 'Điều chỉnh', 'Tồn cuối', 'Tạo snapshot lúc'].map(label => <th key={label} className="whitespace-nowrap p-3">{label}</th>)}</tr></thead>
        <tbody>{page.rows.map((row, index) => <tr key={`${row.snapshot_date}:${row.product_id}:${index}`} className="border-b border-gray-100">
          <td className="whitespace-nowrap p-3">{typeof row.snapshot_date === 'number' ? new Date(row.snapshot_date).toISOString().slice(0, 10) : row.snapshot_date}</td>
          <td className="p-3">{row.product_id}</td><td className="p-3">{row.product_name ?? '—'}</td>
          <td className="p-3">{quantity(row.opening_quantity)}</td><td className="p-3">{quantity(row.received_quantity)}</td>
          <td className="p-3">{quantity(row.sold_quantity)}</td><td className="p-3">{quantity(row.returned_quantity)}</td>
          <td className="p-3">{quantity(row.adjustment_delta)}</td><td className="p-3 font-semibold">{quantity(row.closing_quantity)}</td>
          <td className="whitespace-nowrap p-3">{row.generated_at == null ? '—' : formatDateTime(typeof row.generated_at === 'number' ? new Date(row.generated_at).toISOString() : row.generated_at)}</td>
        </tr>)}</tbody>
      </table></div>
      {!report.rows.length && <p className="p-4 text-sm text-gray-500">Không có snapshot trong kết quả. Không coi thiếu snapshot là tồn kho bằng 0.</p>}
      {report.rowLimit && report.rows.length >= report.rowLimit ? <p className="mt-3 text-xs text-amber-800">Đã chạm giới hạn {report.rowLimit} dòng; kết quả có thể chưa đầy đủ. Thu hẹp khoảng ngày để xem tiếp.</p> : null}
      <Pagination {...page} />
    </Card>}
  </div>;
};
