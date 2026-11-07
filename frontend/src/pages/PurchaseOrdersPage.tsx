import { useState } from 'react';
import type React from 'react';
import { Eye, Plus, Trash2 } from 'lucide-react';
import { api, errorMessage, qs } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Td, Toolbar } from '../components/ui/Table';
import { formatDate, formatDateTime, formatVND } from '../utils/formatters';
import type { Page, Product, PurchaseOrder, Supplier } from '../types';

type DraftLine = { productId: string; orderedQty: number; unitCost: number };
type ReceiveLine = { lineNo: number; receivedQty: number; mfgDate: string; expiryDate: string };

/** Số lượng tính tiền: phiếu đã nhận tính theo thực nhận, phiếu nháp tính theo số đặt. */
const qtyOf = (po: PurchaseOrder, l: PurchaseOrder['lines'][number]) => (po.status === 'RECEIVED' ? l.receivedQty : l.orderedQty);
const totalQty = (po: PurchaseOrder) => po.lines.reduce((s, l) => s + qtyOf(po, l), 0);
const totalValue = (po: PurchaseOrder) => po.lines.reduce((s, l) => s + qtyOf(po, l) * l.unitCost, 0);

export function PurchaseOrdersPage() {
  const toast = useToast();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const list = useLoad(() => api.get<Page<PurchaseOrder>>(`/purchase-orders${qs({ status, page, size: 20 })}`), [status, page]);
  const suppliers = useLoad(() => api.get<Page<Supplier>>('/suppliers?size=100&active=true'), []);
  const products = useLoad(() => api.get<Page<Product>>('/products?size=100&active=true'), []);
  const [draft, setDraft] = useState<{ id?: string; supplierId: string; note: string; lines: DraftLine[]; version?: number } | null>(null);
  const [receiving, setReceiving] = useState<{ po: PurchaseOrder; lines: ReceiveLine[] } | null>(null);
  const [view, setView] = useState<PurchaseOrder | null>(null);

  const saveDraft = async () => {
    if (!draft) return;
    try {
      if (draft.id) await api.put(`/purchase-orders/${draft.id}`, draft);
      else await api.post('/purchase-orders', draft);
      toast.success('Đã lưu phiếu nhập nháp');
      setDraft(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const receive = async () => {
    if (!receiving) return;
    try {
      const lines = receiving.lines.map(l => ({ ...l, mfgDate: l.mfgDate || null, expiryDate: l.expiryDate || null }));
      await api.post(`/purchase-orders/${receiving.po.id}/receive`, { lines });
      toast.success('Đã nhận hàng: tạo lô, cộng tồn, ghi thẻ kho');
      setReceiving(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const remove = async (po: PurchaseOrder) => {
    if (!confirm(`Xóa phiếu nháp ${po.poCode}?`)) return;
    try { await api.delete(`/purchase-orders/${po.id}`); void list.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div>
      <Toolbar>
        <Field label="Trạng thái"><select className={inputClass} value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">Tất cả</option><option value="DRAFT">Nháp</option><option value="RECEIVED">Đã nhận</option></select></Field>
        <Button onClick={() => setDraft({ supplierId: suppliers.data?.content[0]?.id ?? '', note: '', lines: [] })}>Tạo phiếu nhập</Button>
      </Toolbar>
      <Table headers={['Mã phiếu', 'Nhà cung cấp', 'Số dòng', 'Tổng SL', 'Giá trị', 'Tạo lúc', 'Trạng thái', '']} loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(po => (
          <tr key={po.id}>
            <Td className="font-medium text-blue-700"><button type="button" onClick={() => setView(po)}>{po.poCode}</button></Td>
            <Td>{po.supplierName}</Td><Td>{po.lines.length}</Td><Td>{totalQty(po)}</Td>
            <Td className="whitespace-nowrap font-semibold">{formatVND(totalValue(po))}</Td><Td>{formatDateTime(po.createdAt)}</Td>
            <Td>{po.status === 'DRAFT' ? 'Nháp' : `Đã nhận ${formatDateTime(po.receivedAt)}`}</Td>
            <Td className="space-x-2 whitespace-nowrap">
              <Button size="sm" variant="outline" leftIcon={<Eye className="h-4 w-4" />} onClick={() => setView(po)}>Xem</Button>
              {po.status === 'DRAFT' && <>
              <Button size="sm" onClick={() => setReceiving({ po, lines: po.lines.map(l => ({ lineNo: l.lineNo, receivedQty: l.orderedQty, mfgDate: '', expiryDate: '' })) })}>Nhận hàng</Button>
              <Button size="sm" variant="outline" onClick={() => setDraft({ id: po.id, supplierId: po.supplierId, note: po.note ?? '', version: po.version, lines: po.lines.map(l => ({ productId: l.productId, orderedQty: l.orderedQty, unitCost: l.unitCost })) })}>Sửa</Button>
              <Button size="sm" variant="ghost" onClick={() => remove(po)}>Xóa</Button></>}</Td>
          </tr>))}
      </Table>
      <ServerPager page={list.data} onPage={setPage} />

      <Modal isOpen={!!draft} onClose={() => setDraft(null)} title={draft?.id ? 'Sửa phiếu nhập' : 'Phiếu nhập mới'} maxWidth="2xl"
        footer={<><Button variant="outline" onClick={() => setDraft(null)}>Hủy</Button><Button onClick={saveDraft}>Lưu nháp</Button></>}>
        {draft && (
          <div className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Nhà cung cấp"><select className={inputClass} value={draft.supplierId} onChange={e => setDraft({ ...draft, supplierId: e.target.value })}>
                {suppliers.data?.content.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
              <Field label="Ghi chú"><input className={inputClass} value={draft.note} onChange={e => setDraft({ ...draft, note: e.target.value })} /></Field>
            </div>
            {draft.lines.map((l, i) => (
              <div key={i} className="grid grid-cols-[2fr_1fr_1fr_auto] gap-2">
                <select className={inputClass} value={l.productId} onChange={e => setDraft({ ...draft, lines: draft.lines.map((x, j) => j === i ? { ...x, productId: e.target.value } : x) })}>
                  {products.data?.content.map(p => <option key={p.id} value={p.id}>{p.sku} · {p.name}</option>)}</select>
                <input type="number" min={1} className={inputClass} placeholder="SL" value={l.orderedQty} onChange={e => setDraft({ ...draft, lines: draft.lines.map((x, j) => j === i ? { ...x, orderedQty: Number(e.target.value) } : x) })} />
                <input type="number" min={0} className={inputClass} placeholder="Giá vốn" value={l.unitCost} onChange={e => setDraft({ ...draft, lines: draft.lines.map((x, j) => j === i ? { ...x, unitCost: Number(e.target.value) } : x) })} />
                <Button variant="ghost" aria-label="Xóa dòng" onClick={() => setDraft({ ...draft, lines: draft.lines.filter((_, j) => j !== i) })}><Trash2 className="h-4 w-4" /></Button>
              </div>))}
            <Button size="sm" variant="outline" leftIcon={<Plus className="h-3 w-3" />}
              onClick={() => setDraft({ ...draft, lines: [...draft.lines, { productId: products.data?.content[0]?.id ?? '', orderedQty: 10, unitCost: 0 }] })}>Thêm dòng</Button>
          </div>)}
      </Modal>

      <Modal isOpen={!!receiving} onClose={() => setReceiving(null)} title={`Nhận hàng ${receiving?.po.poCode ?? ''}`} maxWidth="2xl"
        footer={<><Button variant="outline" onClick={() => setReceiving(null)}>Hủy</Button><Button onClick={receive}>Xác nhận nhận hàng</Button></>}>
        {receiving && (
          <div className="space-y-2">
            <p className="text-xs text-gray-500">Một transaction MongoDB: tạo lô (mã L…), cộng tồn, đặt RECEIVED; thẻ kho ghi sang Cassandra, quan hệ lô–NCC sang Neo4j. Để SL = 0 nếu dòng chưa về.</p>
            {receiving.lines.map((l, i) => {
              const line = receiving.po.lines[i];
              return (
                <div key={l.lineNo} className="grid grid-cols-[2fr_1fr_1fr_1fr] items-end gap-2">
                  <p className="text-sm">{line.sku} · {line.productName}<br /><span className="text-xs text-gray-500">Đặt {line.orderedQty} · {formatVND(line.unitCost)}</span></p>
                  <Field label="Thực nhận"><input type="number" min={0} className={inputClass} value={l.receivedQty} onChange={e => setReceiving({ ...receiving, lines: receiving.lines.map((x, j) => j === i ? { ...x, receivedQty: Number(e.target.value) } : x) })} /></Field>
                  <Field label="Ngày SX"><input type="date" className={inputClass} value={l.mfgDate} onChange={e => setReceiving({ ...receiving, lines: receiving.lines.map((x, j) => j === i ? { ...x, mfgDate: e.target.value } : x) })} /></Field>
                  <Field label="Hạn dùng"><input type="date" className={inputClass} value={l.expiryDate} onChange={e => setReceiving({ ...receiving, lines: receiving.lines.map((x, j) => j === i ? { ...x, expiryDate: e.target.value } : x) })} /></Field>
                </div>);
            })}
          </div>)}
      </Modal>

      {view && <PurchaseOrderDetail po={view} onClose={() => setView(null)}
        onReceive={() => { setView(null); setReceiving({ po: view, lines: view.lines.map(l => ({ lineNo: l.lineNo, receivedQty: l.orderedQty, mfgDate: '', expiryDate: '' })) }); }} />}
    </div>
  );
}

/** Chi tiết phiếu nhập: thông tin chung, từng dòng hàng và lô sinh ra, tổng giá trị. */
function PurchaseOrderDetail({ po, onClose, onReceive }: { po: PurchaseOrder; onClose: () => void; onReceive: () => void }) {
  const received = po.status === 'RECEIVED';
  const info: [string, React.ReactNode][] = [
    ['Mã phiếu', <span key="code" className="font-mono">{po.poCode}</span>],
    ['Trạng thái', <span key="status" className={`rounded border px-2 py-0.5 text-xs ${received ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-amber-200 bg-amber-50 text-amber-700'}`}>{received ? 'Đã nhận hàng' : 'Nháp'}</span>],
    ['Nhà cung cấp', po.supplierName],
    ['Ghi chú', po.note || '—'],
    ['Lập phiếu', `${po.createdBy} · ${formatDateTime(po.createdAt)}`],
    ['Nhận hàng', received ? `${po.receivedBy ?? '—'} · ${formatDateTime(po.receivedAt)}` : 'Chưa nhận'],
  ];
  return (
    <Modal isOpen onClose={onClose} title={`Phiếu nhập ${po.poCode}`} maxWidth="5xl"
      footer={<>{!received && <Button onClick={onReceive}>Nhận hàng</Button>}<Button variant="outline" onClick={onClose}>Đóng</Button></>}>
      <div className="space-y-4 text-sm">
        <dl className="grid gap-x-6 gap-y-2 rounded-md border border-gray-200 p-3 sm:grid-cols-2">
          {info.map(([k, v]) => (
            <div key={k} className="flex justify-between gap-3 sm:block">
              <dt className="eyebrow">{k}</dt><dd className="mt-0.5 text-gray-900">{v}</dd>
            </div>))}
        </dl>

        <Table headers={['#', 'SKU', 'Sản phẩm', 'Đặt', 'Nhận', 'Giá vốn', 'Thành tiền', 'Lô', 'NSX', 'HSD']}>
          {po.lines.map(l => {
            const short = received && l.receivedQty < l.orderedQty;
            return (
              <tr key={l.lineNo}>
                <Td>{l.lineNo}</Td>
                <Td className="font-mono text-xs whitespace-nowrap">{l.sku}</Td>
                <Td className="font-medium">{l.productName}</Td>
                <Td>{l.orderedQty}</Td>
                <Td className={short ? 'font-semibold text-amber-700' : ''}>{received ? l.receivedQty : '—'}{short && <span className="ml-1 text-xs">(thiếu {l.orderedQty - l.receivedQty})</span>}</Td>
                <Td className="whitespace-nowrap">{formatVND(l.unitCost)}</Td>
                <Td className="whitespace-nowrap font-semibold">{formatVND(qtyOf(po, l) * l.unitCost)}</Td>
                <Td className="font-mono text-xs whitespace-nowrap">{l.batchCode ?? '—'}</Td>
                <Td className="whitespace-nowrap">{l.mfgDate ? formatDate(l.mfgDate) : '—'}</Td>
                <Td className="whitespace-nowrap">{l.expiryDate ? formatDate(l.expiryDate) : '—'}</Td>
              </tr>);
          })}
        </Table>

        <div className="flex flex-wrap justify-end gap-x-6 gap-y-1 border-t border-gray-200 pt-3">
          <span className="text-gray-500">{po.lines.length} dòng</span>
          <span className="text-gray-500">Tổng SL {received ? 'nhận' : 'đặt'}: <b className="text-gray-900">{totalQty(po)}</b></span>
          <span>Tổng giá trị: <b className="text-base">{formatVND(totalValue(po))}</b></span>
        </div>
        {!received && <p className="text-xs text-gray-500">Phiếu nháp: giá trị tính theo số đặt. Khi nhận hàng, mỗi dòng sinh một lô và cộng tồn kho.</p>}
      </div>
    </Modal>
  );
}
