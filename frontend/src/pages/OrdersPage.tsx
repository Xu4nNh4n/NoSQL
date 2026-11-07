import { useState } from 'react';
import { api, errorMessage, newRequestKey, qs } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Td, Toolbar } from '../components/ui/Table';
import { formatDateTime, formatVND, ORDER_STATUS_LABELS, PAYMENT_METHOD_LABELS, vietnamDateKey } from '../utils/formatters';
import type { LedgerPayment, Page, PaymentMethod, SalesOrder, SalesReturn } from '../types';

export function OrdersPage() {
  const [from, setFrom] = useState(vietnamDateKey(new Date(Date.now() - 6 * 86400000)));
  const [to, setTo] = useState(vietnamDateKey());
  const [status, setStatus] = useState('');
  const [code, setCode] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<SalesOrder | null>(null);
  const list = useLoad(() => api.get<Page<SalesOrder>>(`/orders${qs({ from, to, status, page, size: 20 })}`), [from, to, status, page]);
  const toast = useToast();

  const openByCode = async () => {
    if (!code.trim()) return;
    try { setSelected(await api.get<SalesOrder>(`/orders/code/${encodeURIComponent(code.trim())}`)); }
    catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div>
      <Toolbar>
        <Field label="Từ ngày"><input type="date" className={inputClass} value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} /></Field>
        <Field label="Đến ngày"><input type="date" className={inputClass} value={to} onChange={e => { setTo(e.target.value); setPage(0); }} /></Field>
        <Field label="Trạng thái">
          <select className={inputClass} value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
            <option value="">Tất cả</option>
            {Object.entries(ORDER_STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
          </select>
        </Field>
        <Field label="Tìm theo mã đơn">
          <div className="flex gap-2"><input className={inputClass} placeholder="HD261107-0001" value={code} onChange={e => setCode(e.target.value)} onKeyDown={e => e.key === 'Enter' && openByCode()} />
            <Button variant="outline" onClick={openByCode}>Mở</Button></div>
        </Field>
      </Toolbar>
      <Table headers={['Mã đơn', 'Thời gian', 'Thu ngân', 'Khách', 'Thanh toán', 'Tổng', 'Trạng thái']}
        loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(o => (
          <tr key={o.id} className="cursor-pointer hover:bg-gray-50" onClick={() => setSelected(o)}>
            <Td className="font-medium text-blue-700">{o.orderCode}</Td>
            <Td>{formatDateTime(o.orderedAt)}</Td>
            <Td>{o.cashier.name}</Td>
            <Td>{o.customer?.name ?? 'Khách lẻ'}</Td>
            <Td>{PAYMENT_METHOD_LABELS[o.paymentMethod]}</Td>
            <Td className="text-right font-semibold">{formatVND(o.total)}</Td>
            <Td><span className={`rounded border px-2 py-0.5 text-xs ${ORDER_STATUS_LABELS[o.status].bg} ${ORDER_STATUS_LABELS[o.status].color}`}>{ORDER_STATUS_LABELS[o.status].label}</span></Td>
          </tr>))}
      </Table>
      <ServerPager page={list.data} onPage={setPage} />
      {selected && <OrderDetail order={selected} onClose={() => setSelected(null)} onChanged={o => { setSelected(o); void list.reload(); }} />}
    </div>
  );
}

function OrderDetail({ order, onClose, onChanged }: { order: SalesOrder; onClose: () => void; onChanged: (o: SalesOrder) => void }) {
  const toast = useToast();
  const ledger = useLoad(() => api.get<LedgerPayment[]>(`/orders/${order.id}/payments`), [order.id, order.payments.length]);
  const returns = useLoad(() => api.get<SalesReturn[]>(`/orders/${order.id}/returns`), [order.id, order.payments.length]);
  const [qty, setQty] = useState<Record<number, number>>({});
  const [damaged, setDamaged] = useState<Record<number, boolean>>({});
  const [reason, setReason] = useState('Khách đổi ý');
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [ref, setRef] = useState('');
  const [saving, setSaving] = useState(false);
  const canReturn = order.status !== 'RETURNED';

  const submitReturn = async () => {
    const items = Object.entries(qty).filter(([, q]) => q > 0)
      .map(([lineNo, q]) => ({ lineNo: Number(lineNo), quantity: q, restock: !damaged[Number(lineNo)], reason }));
    if (items.length === 0) { toast.warning('Chọn số lượng trả'); return; }
    setSaving(true);
    try {
      const r = await api.post<SalesReturn>(`/orders/${order.id}/returns`, {
        requestKey: newRequestKey(), items, refundMethod: method, refundReference: method === 'BANK_TRANSFER' ? ref : null,
      });
      toast.success(`Đã lập phiếu ${r.returnCode}, hoàn ${formatVND(r.refundAmount)}`);
      setQty({});
      onChanged(await api.get<SalesOrder>(`/orders/${order.id}`));
    } catch (e) { toast.error(errorMessage(e)); }
    finally { setSaving(false); }
  };

  return (
    <Modal isOpen onClose={onClose} title={`Đơn ${order.orderCode}`} maxWidth="2xl">
      <div className="space-y-4 text-sm">
        <p className="text-gray-600">{formatDateTime(order.orderedAt)} · {order.cashier.name} · {order.customer ? `${order.customer.name} (${order.customer.phone})` : 'Khách lẻ'} · {ORDER_STATUS_LABELS[order.status].label}</p>
        <Table headers={['#', 'Sản phẩm', 'SL', 'Đã trả', 'Thực trả', 'Lô đã xuất', canReturn ? 'Trả' : '']}>
          {order.items.map(l => (
            <tr key={l.lineNo}>
              <Td>{l.lineNo}</Td>
              <Td>{l.gift && '🎁 '}{l.name}{l.promotion && <p className="text-xs text-emerald-700">{l.promotion.name}</p>}</Td>
              <Td>{l.quantity}</Td>
              <Td>{l.returnedQuantity}</Td>
              <Td className="text-right">{formatVND(l.netAmount)}</Td>
              <Td className="text-xs">{l.allocations.map(a => <div key={a.batchId}>{a.batchCode} ×{a.quantity}{a.expiryDate ? ` (HSD ${a.expiryDate})` : ''}</div>)}</Td>
              <Td>{canReturn && l.returnedQuantity < l.quantity && (
                <div className="flex items-center gap-1">
                  <input type="number" min={0} max={l.quantity - l.returnedQuantity} className="h-8 w-14 rounded border border-gray-300 text-center"
                    value={qty[l.lineNo] ?? 0} onChange={e => setQty({ ...qty, [l.lineNo]: Math.min(Number(e.target.value), l.quantity - l.returnedQuantity) })} />
                  <label className="text-xs"><input type="checkbox" checked={!!damaged[l.lineNo]} onChange={e => setDamaged({ ...damaged, [l.lineNo]: e.target.checked })} /> hỏng</label>
                </div>)}</Td>
            </tr>))}
        </Table>
        <div className="flex justify-end gap-6">
          <span>Tạm tính {formatVND(order.grossTotal)}</span><span className="text-emerald-700">Giảm {formatVND(order.discountTotal)}</span>
          <b>Tổng {formatVND(order.total)}</b><span className="text-rose-600">Đã hoàn {formatVND(order.refundedTotal)}</span>
        </div>
        {canReturn && (
          <div className="grid gap-2 rounded-lg border border-gray-200 p-3 sm:grid-cols-4">
            <Field label="Lý do"><input className={inputClass} value={reason} onChange={e => setReason(e.target.value)} /></Field>
            <Field label="Hoàn bằng"><select className={inputClass} value={method} onChange={e => setMethod(e.target.value as PaymentMethod)}>
              <option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản</option></select></Field>
            {method === 'BANK_TRANSFER' && <Field label="Mã tham chiếu"><input className={inputClass} value={ref} onChange={e => setRef(e.target.value)} /></Field>}
            <div className="flex items-end"><Button isLoading={saving} onClick={submitReturn}>Lập phiếu trả & hoàn tiền</Button></div>
          </div>)}
        <div>
          <h4 className="mb-1 font-semibold">Sổ thanh toán (Cassandra · payments_by_order)</h4>
          {ledger.error ? <p className="text-amber-700">{ledger.error}</p> : (
            <Table headers={['Thời điểm', 'Loại', 'Phương thức', 'Số tiền', 'Tham chiếu']} loading={ledger.loading} empty={ledger.data?.length === 0}>
              {ledger.data?.map(p => (
                <tr key={p.paymentId}><Td>{formatDateTime(p.createdAt)}</Td><Td>{p.type === 'PAYMENT' ? 'Thu' : `Hoàn ${p.returnCode ?? ''}`}</Td>
                  <Td>{PAYMENT_METHOD_LABELS[p.method]}</Td><Td className="text-right">{formatVND(p.amount)}</Td><Td>{p.reference}</Td></tr>))}
            </Table>)}
          {ledger.data && ledger.data.length < order.payments.length && <p className="mt-1 text-xs text-gray-500">Sổ cái đang đồng bộ (nhất quán sau, tối đa ~60 giây).</p>}
        </div>
        {(returns.data?.length ?? 0) > 0 && (
          <div><h4 className="mb-1 font-semibold">Phiếu trả</h4>
            <ul className="space-y-1">{returns.data!.map(r => (
              <li key={r.id} className="text-xs">{r.returnCode} · {formatDateTime(r.createdAt)} · hoàn {formatVND(r.refundAmount)} · {r.items.map(i => `${i.name} ×${i.quantity}${i.restock ? '' : ' (hỏng)'}`).join(', ')}</li>))}</ul>
          </div>)}
      </div>
    </Modal>
  );
}
