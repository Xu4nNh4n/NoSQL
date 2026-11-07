import { useState } from 'react';
import { api, errorMessage, qs } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, Table, Td, Toolbar } from '../components/ui/Table';
import { formatDateTime, formatVND, vietnamDateKey } from '../utils/formatters';
import type { ShiftClosing, ShiftSummary } from '../types';

/** Chốt ca (FR-11): tổng thu đọc từ Cassandra payments_by_cashier_day sau khi đồng bộ các giao dịch còn chờ. */
export function ShiftPage() {
  const { role } = useAuth();
  const toast = useToast();
  const mine = useLoad(() => api.get<ShiftSummary>('/shifts/me/today'), []);
  const [counted, setCounted] = useState('');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const s = mine.data;

  const close = async () => {
    setSaving(true);
    try {
      await api.post<ShiftClosing>('/shifts/me/close', { countedCash: Number(counted), note });
      toast.success('Đã chốt ca');
      void mine.reload();
    } catch (e) { toast.error(errorMessage(e)); }
    finally { setSaving(false); }
  };

  return (
    <div className="space-y-6">
      <Card title="Ca của tôi hôm nay">
        {mine.loading ? <p className="text-sm text-gray-500">Đang đồng bộ và tính tổng…</p>
          : mine.error ? <p className="text-sm text-rose-600">{mine.error}</p>
          : s && (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
                <Stat label="Thu tiền mặt" value={formatVND(s.cashIn)} />
                <Stat label="Thu chuyển khoản" value={formatVND(s.bankIn)} />
                <Stat label="Hoàn tiền mặt" value={formatVND(s.cashRefund)} />
                <Stat label="Hoàn chuyển khoản" value={formatVND(s.bankRefund)} />
                <Stat label="Số đơn / phiếu hoàn" value={`${s.paymentCount} / ${s.refundCount}`} />
                <Stat label="Tiền mặt phải có" value={formatVND(s.expectedCash)} strong />
              </div>
              {s.closing?.status === 'CLOSED' ? (
                <p className="rounded bg-blue-50 p-3 text-sm text-blue-800">Đã chốt lúc {formatDateTime(s.closing.closedAt)}: đếm được {formatVND(s.closing.countedCash)}, chênh lệch <b>{formatVND(s.closing.difference)}</b>.</p>
              ) : (
                <div className="flex flex-wrap items-end gap-3">
                  <Field label="Tiền mặt đếm được trong két"><input type="number" min={0} className={inputClass} value={counted} onChange={e => setCounted(e.target.value)} /></Field>
                  <Field label="Ghi chú"><input className={inputClass} value={note} onChange={e => setNote(e.target.value)} /></Field>
                  {counted !== '' && <p className="text-sm">Chênh lệch: <b>{formatVND(Number(counted) - s.expectedCash)}</b></p>}
                  <Button disabled={counted === ''} isLoading={saving} onClick={close}>Chốt ca</Button>
                </div>
              )}
            </div>
          )}
      </Card>
      {role === 'ADMIN' && <AdminShifts />}
    </div>
  );
}

function AdminShifts() {
  const toast = useToast();
  const [day, setDay] = useState(() => vietnamDateKey());
  const list = useLoad(() => api.get<ShiftClosing[]>(`/shifts${qs({ day })}`), [day]);
  const reopen = async (id: string) => {
    try { await api.post(`/shifts/${id}/reopen`); toast.success('Đã mở lại ca'); void list.reload(); }
    catch (e) { toast.error(errorMessage(e)); }
  };
  return (
    <Card title="Chốt ca của các thu ngân">
      <Toolbar><Field label="Ngày"><input type="date" className={inputClass} value={day} onChange={e => setDay(e.target.value)} /></Field></Toolbar>
      <Table headers={['Thu ngân', 'Thu TM', 'Thu CK', 'Hoàn TM', 'Phải có', 'Đếm được', 'Chênh lệch', 'Trạng thái', '']}
        loading={list.loading} error={list.error} empty={list.data?.length === 0}>
        {list.data?.map(c => (
          <tr key={c.id}>
            <Td>{c.cashierName}</Td><Td>{formatVND(c.cashIn)}</Td><Td>{formatVND(c.bankIn)}</Td><Td>{formatVND(c.cashRefund)}</Td>
            <Td>{formatVND(c.expectedCash)}</Td><Td>{formatVND(c.countedCash)}</Td>
            <Td className={c.difference === 0 ? '' : 'font-semibold text-rose-600'}>{formatVND(c.difference)}</Td>
            <Td>{c.status === 'CLOSED' ? 'Đã chốt' : 'Đã mở lại'}</Td>
            <Td>{c.status === 'CLOSED' && <Button size="sm" variant="outline" onClick={() => reopen(c.id)}>Mở lại</Button>}</Td>
          </tr>))}
      </Table>
    </Card>
  );
}

function Stat({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`rounded-lg border p-3 ${strong ? 'border-blue-300 bg-blue-50' : 'border-gray-200'}`}>
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`mt-1 ${strong ? 'text-lg font-bold text-blue-800' : 'font-semibold'}`}>{value}</p>
    </div>
  );
}
