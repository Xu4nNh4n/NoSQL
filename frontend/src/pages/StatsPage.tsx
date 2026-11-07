import { useState } from 'react';
import { Download } from 'lucide-react';
import { api, errorMessage, qs } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, Table, Td, Toolbar } from '../components/ui/Table';
import { formatVND, vietnamDateKey } from '../utils/formatters';
import type { SalesStats } from '../types';

/** Thống kê (FR-14): một lệnh aggregation $facet trên MongoDB trả đủ các khối bên dưới. */
export function StatsPage() {
  const toast = useToast();
  const [from, setFrom] = useState(() => vietnamDateKey(new Date(Date.now() - 6 * 86400000)));
  const [to, setTo] = useState(() => vietnamDateKey());
  const stats = useLoad(() => api.get<SalesStats>(`/stats/sales${qs({ from, to })}`), [from, to]);
  const s = stats.data;

  const download = async () => {
    try {
      const blob = await api.get<Blob>(`/stats/sales.csv${qs({ from, to })}`);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `doanh-thu_${from}_${to}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div className="space-y-4">
      <Toolbar>
        <Field label="Từ ngày"><input type="date" className={inputClass} value={from} onChange={e => setFrom(e.target.value)} /></Field>
        <Field label="Đến ngày"><input type="date" className={inputClass} value={to} onChange={e => setTo(e.target.value)} /></Field>
        <Button variant="outline" leftIcon={<Download className="h-4 w-4" />} onClick={download}>Xuất CSV</Button>
      </Toolbar>
      {stats.loading && <p className="text-sm text-gray-500">Đang tính…</p>}
      {stats.error && <p className="text-sm text-rose-600">{stats.error}</p>}
      {s && (
        <>
          <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <Kpi label="Số đơn" value={String(s.totals.orders)} />
            <Kpi label="Tổng gốc" value={formatVND(s.totals.gross)} />
            <Kpi label="Khuyến mãi đã giảm" value={formatVND(s.totals.discount)} />
            <Kpi label="Thực thu" value={formatVND(s.totals.revenue)} />
            <Kpi label="Hoàn tiền" value={formatVND(s.totals.refunds)} />
            <Kpi label="Doanh thu thuần" value={formatVND(s.totals.netRevenue)} strong />
          </div>
          <Card title="Doanh thu thuần theo ngày">
            <BarChart rows={s.daily.map(d => ({ label: d.day.slice(5), value: d.netRevenue, hint: `${d.orders} đơn` }))} />
          </Card>
          <div className="grid gap-4 lg:grid-cols-2">
            <Card title="Theo nhóm hàng ($unwind + $lookup)">
              <Table headers={['Nhóm hàng', 'Số lượng', 'Doanh thu']} empty={s.byCategory.length === 0}>
                {s.byCategory.map(c => <tr key={c.categoryId}><Td>{c.categoryName}</Td><Td>{c.quantity}</Td><Td className="text-right">{formatVND(c.revenue)}</Td></tr>)}
              </Table>
            </Card>
            <Card title="Top 10 sản phẩm trong kỳ">
              <Table headers={['Sản phẩm', 'Số lượng', 'Doanh thu']} empty={s.topProducts.length === 0}>
                {s.topProducts.map(p => <tr key={p.productId}><Td>{p.name}</Td><Td>{p.quantity}</Td><Td className="text-right">{formatVND(p.revenue)}</Td></tr>)}
              </Table>
            </Card>
          </div>
        </>)}
    </div>
  );
}

function Kpi({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`rounded-lg border bg-white p-3 ${strong ? 'border-blue-300' : 'border-gray-200'}`}>
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`mt-1 font-semibold ${strong ? 'text-lg text-blue-800' : ''}`}>{value}</p>
    </div>
  );
}

/** Biểu đồ cột SVG đơn giản, không cần thư viện. */
function BarChart({ rows }: { rows: { label: string; value: number; hint: string }[] }) {
  if (rows.length === 0) return <p className="text-sm text-gray-500">Không có dữ liệu trong kỳ.</p>;
  const max = Math.max(...rows.map(r => r.value), 1);
  const w = 48;
  const h = 180;
  return (
    <div className="overflow-x-auto">
      <svg width={rows.length * w + 20} height={h + 40} role="img" aria-label="Biểu đồ doanh thu theo ngày">
        {rows.map((r, i) => {
          const bh = Math.max(2, (r.value / max) * h);
          return (
            <g key={r.label} transform={`translate(${10 + i * w}, 0)`}>
              <title>{`${r.label}: ${formatVND(r.value)} · ${r.hint}`}</title>
              <rect x={8} y={h - bh + 10} width={w - 16} height={bh} rx={3} className="fill-blue-500" />
              <text x={w / 2} y={h + 28} textAnchor="middle" className="fill-gray-500 text-[11px]">{r.label}</text>
            </g>);
        })}
      </svg>
    </div>
  );
}
