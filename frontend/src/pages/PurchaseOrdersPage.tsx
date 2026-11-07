import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
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
  const productName = (id: string) => products.data?.content.find(p => p.id === id)?.name ?? id;

  return (
    <div>
      <Toolbar>
        <Field label="Trạng thái"><select className={inputClass} value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">Tất cả</option><option value="DRAFT">Nháp</option><option value="RECEIVED">Đã nhận</option></select></Field>
        <Button onClick={() => setDraft({ supplierId: suppliers.data?.content[0]?.id ?? '', note: '', lines: [] })}>Tạo phiếu nhập</Button>
      </Toolbar>
      <Table headers={['Mã phiếu', 'Nhà cung cấp', 'Số dòng', 'Tạo lúc', 'Trạng thái', '']} loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(po => (
          <tr key={po.id}>
            <Td className="font-medium text-blue-700"><button type="button" onClick={() => setView(po)}>{po.poCode}</button></Td>
            <Td>{po.supplierName}</Td><Td>{po.lines.length}</Td><Td>{formatDateTime(po.createdAt)}</Td>
            <Td>{po.status === 'DRAFT' ? 'Nháp' : `Đã nhận ${formatDateTime(po.receivedAt)}`}</Td>
            <Td className="space-x-2 whitespace-nowrap">{po.status === 'DRAFT' && <>
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

      <Modal isOpen={!!view} onClose={() => setView(null)} title={`Phiếu ${view?.poCode ?? ''}`} maxWidth="2xl">
        {view && (
          <Table headers={['#', 'Sản phẩm', 'Đặt', 'Nhận', 'Giá vốn', 'Lô', 'Hạn dùng']}>
            {view.lines.map(l => (
              <tr key={l.lineNo}><Td>{l.lineNo}</Td><Td>{productName(l.productId) || l.productName}</Td><Td>{l.orderedQty}</Td><Td>{l.receivedQty}</Td>
                <Td>{formatVND(l.unitCost)}</Td><Td className="font-mono text-xs">{l.batchCode ?? '—'}</Td><Td>{l.expiryDate ? formatDate(l.expiryDate) : '—'}</Td></tr>))}
          </Table>)}
      </Modal>
    </div>
  );
}
