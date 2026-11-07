import { useState } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';
import { Table, Td } from '../components/ui/Table';
import { formatDateTime } from '../utils/formatters';
import type { DatastoreStatus, SyncStatusView } from '../types';

/** Trạng thái 4 CSDL, hàng đợi đồng bộ MongoDB → Cassandra/Neo4j, dựng lại đồ thị. Dùng khi demo chịu lỗi (NFR-09). */
export function SystemPage() {
  const toast = useToast();
  const health = useLoad(() => api.get<{ datastores: DatastoreStatus[]; checkedAt: string }>('/health/datastores'), []);
  const sync = useLoad(() => api.get<SyncStatusView>('/admin/sync/status'), []);
  const [rebuilding, setRebuilding] = useState(false);

  const retry = async () => {
    try {
      const r = await api.post<{ reset: number }>('/admin/sync/retry');
      toast.success(`Đã đặt lại ${r.reset} việc lỗi về PENDING`);
      void sync.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const rebuild = async () => {
    if (!confirm('Xóa và dựng lại toàn bộ đồ thị Neo4j từ MongoDB?')) return;
    setRebuilding(true);
    try {
      const r = await api.post<{ deletedNodes: number; batches: number; orders: number; millis: number }>('/admin/graph/rebuild');
      toast.success(`Dựng lại xong: ${r.batches} lô, ${r.orders} đơn trong ${(r.millis / 1000).toFixed(1)} giây`);
    } catch (e) { toast.error(errorMessage(e)); }
    finally { setRebuilding(false); }
  };

  return (
    <div className="space-y-4">
      <Card title="Bốn CSDL NoSQL" action={<Button size="sm" variant="outline" onClick={() => health.reload()}>Kiểm tra lại</Button>}>
        {health.error && <p className="text-sm text-rose-600">{health.error}</p>}
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {health.data?.datastores.map(d => (
            <div key={d.name} className={`rounded-lg border p-4 ${d.up ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'}`}>
              <div className="flex items-center gap-2 font-semibold">
                {d.up ? <CheckCircle2 className="h-5 w-5 text-emerald-600" /> : <XCircle className="h-5 w-5 text-rose-600" />}{d.name}
              </div>
              <p className="mt-1 text-xs text-gray-600">{d.role}</p>
              <p className="mt-2 text-sm">{d.up ? `${d.latencyMs} ms` : d.error}</p>
            </div>))}
        </div>
        {health.data && <p className="mt-2 text-xs text-gray-500">Kiểm tra song song, tối đa 3 giây mỗi CSDL · {formatDateTime(health.data.checkedAt)}</p>}
      </Card>

      <Card title="Hàng đợi đồng bộ (sync_queue)" action={<Button size="sm" variant="outline" onClick={() => sync.reload()}>Tải lại</Button>}>
        {sync.data && (
          <div className="space-y-3 text-sm">
            <p>Đang chờ: <b>{sync.data.pending}</b>{sync.data.oldestPendingAt && ` (cũ nhất ${formatDateTime(sync.data.oldestPendingAt)})`} · Lỗi: <b className={sync.data.failed ? 'text-rose-600' : ''}>{sync.data.failed}</b></p>
            {sync.data.failed > 0 && <Button size="sm" onClick={retry}>Thử lại các việc lỗi</Button>}
            {sync.data.recentFailures.length > 0 && (
              <Table headers={['Loại', 'Tham chiếu', 'Lần thử', 'Lỗi cuối', 'Tạo lúc']}>
                {sync.data.recentFailures.map(f => (
                  <tr key={f.id}><Td>{f.type}</Td><Td className="font-mono text-xs">{f.refId}</Td><Td>{f.attempts}</Td>
                    <Td className="max-w-md truncate text-xs">{f.lastError}</Td><Td>{formatDateTime(f.createdAt)}</Td></tr>))}
              </Table>)}
            <p className="text-xs text-gray-500">MongoDB ghi đơn/lô và sự kiện trong cùng transaction; sự kiện được ghi sang Cassandra (thẻ kho, sổ thanh toán) và Neo4j ngay sau commit, job mỗi 60 giây thử lại phần còn chờ.</p>
          </div>)}
      </Card>

      <Card title="Đồ thị Neo4j">
        <p className="mb-3 text-sm text-gray-600">Đồ thị là dữ liệu suy ra từ MongoDB. Khi lệch hoặc Aura bị xóa, dựng lại toàn bộ (đọc theo trang 500 lô/đơn).</p>
        <Button variant="danger" isLoading={rebuilding} onClick={rebuild}>Dựng lại đồ thị</Button>
      </Card>
    </div>
  );
}
