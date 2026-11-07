import { Fragment, useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { api, errorMessage, qs } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Tabs, Td, Toolbar } from '../components/ui/Table';
import { formatDate, formatDateTime, formatVND } from '../utils/formatters';
import type { Batch, LowStockItem, Page, Product, TraceResult } from '../types';

type Tab = 'batches' | 'low' | 'soon' | 'expired' | 'trace';

export function InventoryPage() {
  const { role } = useAuth();
  const [tab, setTab] = useState<Tab>('batches');
  const tabs: { key: Tab; label: string }[] = [
    { key: 'batches', label: 'Lô hàng (FEFO)' },
    { key: 'low', label: 'Sắp hết hàng' },
    { key: 'soon', label: 'Sắp hết hạn (3 ngày)' },
    { key: 'expired', label: 'Đã hết hạn' },
    ...(role === 'ADMIN' ? [{ key: 'trace' as Tab, label: 'Truy vết & thu hồi lô' }] : []),
  ];
  return (
    <div>
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab === 'batches' && <BatchesTab />}
      {tab === 'low' && <LowStockTab />}
      {tab === 'soon' && <BatchList url="/inventory/batches/expiring-soon" note="Job 07:00 tính sẵn và lưu Redis 24 giờ." />}
      {tab === 'expired' && <BatchList url="/inventory/batches/expired" note="Lô hết hạn còn tồn: điều chỉnh giảm ở tab Lô hàng." />}
      {tab === 'trace' && <TraceTab />}
    </div>
  );
}

function BatchesTab() {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState<Set<string>>(() => new Set());
  const [version, setVersion] = useState(0);
  const products = useLoad(() => api.get<Page<Product>>('/products?size=100&active=true'), [version]);
  const [adjust, setAdjust] = useState<{ batch: Batch; quantity: number; reason: string } | null>(null);
  const [loose, setLoose] = useState<{ productId: string; quantity: number; unitCost: number; mfgDate: string; expiryDate: string } | null>(null);

  const refresh = () => setVersion(v => v + 1);
  const toggle = (id: string) => setOpen(prev => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  const saveAdjust = async () => {
    if (!adjust) return;
    try {
      await api.post(`/inventory/batches/${adjust.batch.id}/adjustments`, { quantity: adjust.quantity, reason: adjust.reason });
      toast.success('Đã điều chỉnh giảm tồn');
      setAdjust(null);
      refresh();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const saveLoose = async () => {
    if (!loose) return;
    try {
      await api.post('/inventory/batches', { ...loose, mfgDate: loose.mfgDate || null, expiryDate: loose.expiryDate || null });
      toast.success('Đã nhập lô');
      setOpen(prev => new Set(prev).add(loose.productId));
      setLoose(null);
      refresh();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const newLoose = (productId: string) => setLoose({ productId, quantity: 10, unitCost: 0, mfgDate: '', expiryDate: '' });

  const q = search.trim().toLowerCase();
  const shown = (products.data?.content ?? []).filter(p => !q || p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q));
  const allOpen = shown.length > 0 && shown.every(p => open.has(p.id));

  return (
    <>
      <Toolbar>
        <Field label="Tìm sản phẩm"><input className={inputClass} placeholder="Tên hoặc SKU" value={search} onChange={e => setSearch(e.target.value)} /></Field>
        <Button variant="outline" onClick={() => setOpen(allOpen ? new Set() : new Set(shown.map(p => p.id)))}>{allOpen ? 'Thu gọn tất cả' : 'Mở tất cả'}</Button>
        <Button variant="outline" onClick={() => newLoose(products.data?.content[0]?.id ?? '')}>Nhập lô lẻ</Button>
      </Toolbar>
      <Table headers={['', 'SKU', 'Sản phẩm', 'Nhóm hàng', 'Tồn kho', 'Hỏng', 'Mức đặt lại', '']}
        loading={products.loading} error={products.error} empty={shown.length === 0}>
        {shown.map(p => {
          const isOpen = open.has(p.id);
          const low = p.stockQuantity <= p.reorderPoint;
          return (
            <Fragment key={p.id}>
              <tr className={`cursor-pointer hover:bg-gray-50 ${isOpen ? 'bg-blue-50/60' : ''}`} onClick={() => toggle(p.id)}>
                <Td className="w-8 text-gray-500">{isOpen ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}</Td>
                <Td className="font-mono text-xs">{p.sku}</Td>
                <Td className="font-medium">{p.name}</Td>
                <Td>{p.categoryName}</Td>
                <Td className={`font-semibold ${p.stockQuantity === 0 ? 'text-rose-600' : low ? 'text-amber-600' : ''}`}>{p.stockQuantity}</Td>
                <Td>{p.damagedQuantity}</Td>
                <Td>{p.reorderPoint}</Td>
                <Td><Button size="sm" variant="ghost" onClick={e => { e.stopPropagation(); newLoose(p.id); }}>+ Nhập lô</Button></Td>
              </tr>
              {isOpen && (
                <tr><td colSpan={8} className="bg-gray-50 px-4 py-3">
                  <ProductBatches productId={p.id} version={version} onAdjust={bt => setAdjust({ batch: bt, quantity: 1, reason: '' })} />
                </td></tr>)}
            </Fragment>);
        })}
      </Table>

      <Modal isOpen={!!adjust} onClose={() => setAdjust(null)} title={`Điều chỉnh giảm lô ${adjust?.batch.batchCode ?? ''}`}
        footer={<><Button variant="outline" onClick={() => setAdjust(null)}>Hủy</Button><Button variant="danger" onClick={saveAdjust}>Giảm tồn</Button></>}>
        {adjust && (
          <div className="grid gap-3">
            <p className="text-sm text-gray-600">Còn {adjust.batch.remainingQty}. Chỉ được giảm; muốn tăng phải nhập lô.</p>
            <Field label="Số lượng giảm"><input type="number" min={1} max={adjust.batch.remainingQty} className={inputClass} value={adjust.quantity} onChange={e => setAdjust({ ...adjust, quantity: Number(e.target.value) })} /></Field>
            <Field label="Lý do *"><input className={inputClass} placeholder="Hư hỏng, hết hạn, hao hụt…" value={adjust.reason} onChange={e => setAdjust({ ...adjust, reason: e.target.value })} /></Field>
            {adjust.batch.adjustments?.length > 0 && <ul className="text-xs text-gray-500">{adjust.batch.adjustments.map(a => <li key={a.id}>{formatDateTime(a.at)} · −{a.quantity} · {a.reason} · {a.by}</li>)}</ul>}
          </div>)}
      </Modal>

      <Modal isOpen={!!loose} onClose={() => setLoose(null)} title="Nhập lô lẻ (ngoài phiếu nhập)"
        footer={<><Button variant="outline" onClick={() => setLoose(null)}>Hủy</Button><Button onClick={saveLoose}>Nhập lô</Button></>}>
        {loose && (
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2"><Field label="Sản phẩm"><select className={inputClass} value={loose.productId} onChange={e => setLoose({ ...loose, productId: e.target.value })}>
              {products.data?.content.map(p => <option key={p.id} value={p.id}>{p.sku} · {p.name}</option>)}</select></Field></div>
            <Field label="Số lượng"><input type="number" min={1} className={inputClass} value={loose.quantity} onChange={e => setLoose({ ...loose, quantity: Number(e.target.value) })} /></Field>
            <Field label="Giá vốn"><input type="number" min={0} className={inputClass} value={loose.unitCost} onChange={e => setLoose({ ...loose, unitCost: Number(e.target.value) })} /></Field>
            <Field label="Ngày sản xuất"><input type="date" className={inputClass} value={loose.mfgDate} onChange={e => setLoose({ ...loose, mfgDate: e.target.value })} /></Field>
            <Field label="Hạn dùng"><input type="date" className={inputClass} value={loose.expiryDate} onChange={e => setLoose({ ...loose, expiryDate: e.target.value })} /></Field>
          </div>)}
      </Modal>
    </>
  );
}

function ProductBatches({ productId, version, onAdjust }: { productId: string; version: number; onAdjust: (b: Batch) => void }) {
  const [page, setPage] = useState(0);
  const [today] = useState(() => new Date().toISOString().slice(0, 10));
  const list = useLoad(() => api.get<Page<Batch>>(`/inventory/batches${qs({ productId, page, size: 20 })}`), [productId, page, version]);
  if (list.loading && !list.data) return <p className="text-sm text-gray-500">Đang tải lô…</p>;
  if (list.error) return <p className="text-sm text-rose-600">{list.error}</p>;
  const rows = list.data?.content ?? [];
  if (rows.length === 0) return <p className="text-sm text-gray-500">Chưa có lô nào. Bấm "+ Nhập lô" hoặc nhận phiếu nhập.</p>;
  // Lô bán tiếp theo theo FEFO: còn hàng, chưa hết hạn, hạn gần nhất (API đã sắp theo hạn tăng dần)
  const next = rows.find(b => b.remainingQty > 0 && !(b.expiryDate && b.expiryDate < today));
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead><tr className="text-left text-xs uppercase text-gray-500">
          {['Mã lô', 'NCC', 'Nhận', 'Còn', 'Hỏng', 'Giá vốn', 'Hạn dùng', 'Nhận lúc', ''].map(h => <th key={h} className="px-2 py-1 font-medium">{h}</th>)}
        </tr></thead>
        <tbody>
          {rows.map(b => {
            const expired = !!b.expiryDate && b.expiryDate < today;
            const empty = b.remainingQty === 0;
            return (
              <tr key={b.id} className={`border-t border-gray-200 ${expired && !empty ? 'bg-rose-50' : ''} ${empty ? 'text-gray-400' : ''}`}>
                <td className="px-2 py-1.5 font-mono text-xs whitespace-nowrap">{b.batchCode}
                  {b.id === next?.id && <span className="ml-2 rounded bg-green-100 px-1.5 py-0.5 font-sans text-[10px] font-semibold text-green-700">Bán tiếp theo</span>}
                  {expired && !empty && <span className="ml-2 rounded bg-rose-100 px-1.5 py-0.5 font-sans text-[10px] font-semibold text-rose-700">Hết hạn</span>}
                  {empty && <span className="ml-2 font-sans text-[10px]">đã hết</span>}
                </td>
                <td className="px-2 py-1.5">{b.supplierName}</td>
                <td className="px-2 py-1.5">{b.receivedQty}</td>
                <td className="px-2 py-1.5 font-semibold">{b.remainingQty}</td>
                <td className="px-2 py-1.5">{b.damagedQty}</td>
                <td className="px-2 py-1.5">{formatVND(b.unitCost)}</td>
                <td className={`px-2 py-1.5 ${expired && !empty ? 'text-rose-600' : ''}`}>{b.expiryDate ? formatDate(b.expiryDate) : 'Không hạn'}</td>
                <td className="px-2 py-1.5 text-xs whitespace-nowrap">{formatDateTime(b.receivedAt)} · {b.purchaseOrderCode ?? 'lô lẻ'}</td>
                <td className="px-2 py-1.5 text-right whitespace-nowrap">{!empty && <Button size="sm" variant="outline" onClick={() => onAdjust(b)}>Điều chỉnh</Button>}</td>
              </tr>);
          })}
        </tbody>
      </table>
      <ServerPager page={list.data} onPage={setPage} />
    </div>
  );
}

function LowStockTab() {
  const list = useLoad(() => api.get<LowStockItem[]>('/inventory/low-stock'), []);
  return (
    <>
      <p className="mb-3 text-sm text-gray-500">Tồn khả dụng = tổng tồn của các lô còn hạn (aggregation MongoDB, cache Redis 1 phút).</p>
      <Table headers={['SKU', 'Sản phẩm', 'Khả dụng', 'Tồn (gồm lô hết hạn)', 'Ngưỡng']} loading={list.loading} error={list.error} empty={list.data?.length === 0}>
        {list.data?.map(i => (
          <tr key={i.productId}><Td className="font-mono text-xs">{i.sku}</Td><Td>{i.name}</Td>
            <Td className="font-semibold text-rose-600">{i.available}</Td><Td>{i.stockQuantity}</Td><Td>{i.reorderPoint}</Td></tr>))}
      </Table>
    </>
  );
}

function BatchList({ url, note }: { url: string; note: string }) {
  const list = useLoad(() => api.get<Batch[]>(url), [url]);
  return (
    <>
      <p className="mb-3 text-sm text-gray-500">{note}</p>
      <Table headers={['Mã lô', 'Sản phẩm', 'Còn', 'Hạn dùng', 'NCC']} loading={list.loading} error={list.error} empty={list.data?.length === 0}>
        {list.data?.map(b => (
          <tr key={b.id}><Td className="font-mono text-xs">{b.batchCode}</Td><Td>{b.productName}</Td><Td>{b.remainingQty}</Td>
            <Td>{b.expiryDate ? formatDate(b.expiryDate) : '—'}</Td><Td>{b.supplierName}</Td></tr>))}
      </Table>
    </>
  );
}

function TraceTab() {
  const toast = useToast();
  const [code, setCode] = useState('');
  const [result, setResult] = useState<TraceResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [reason, setReason] = useState('');

  const trace = async () => {
    if (!code.trim()) return;
    setLoading(true);
    try { setResult(await api.get<TraceResult>(`/batches/${encodeURIComponent(code.trim())}/trace`)); }
    catch (e) { toast.error(errorMessage(e)); setResult(null); }
    finally { setLoading(false); }
  };
  const recall = async () => {
    if (!result || !reason.trim()) { toast.warning('Nhập lý do thu hồi'); return; }
    if (!confirm(`Thu hồi lô ${result.batchCode}: giảm toàn bộ ${result.remainingQty} còn tồn?`)) return;
    try {
      setResult(await api.post<TraceResult>(`/batches/${encodeURIComponent(result.batchCode)}/recall`, { reason }));
      toast.success('Đã thu hồi lô; danh sách khách cần liên hệ ở bên dưới');
    } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div className="space-y-4">
      <Toolbar>
        <Field label="Mã lô"><input className={inputClass} placeholder="L261107-0001" value={code} onChange={e => setCode(e.target.value)} onKeyDown={e => e.key === 'Enter' && trace()} /></Field>
        <Button isLoading={loading} onClick={trace}>Truy vết (Neo4j)</Button>
      </Toolbar>
      {result && (
        <>
          <div className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
            <p className="font-semibold">{result.batchCode} · {result.productName} ({result.productSku})</p>
            <p className="text-gray-600">NCC {result.supplierName ?? '—'} · HSD {result.expiryDate ? formatDate(result.expiryDate) : '—'} · nhận {result.receivedQty} · đã bán {result.soldQty} · còn {result.remainingQty}</p>
            {result.remainingQty > 0 && (
              <div className="mt-3 flex flex-wrap items-end gap-2">
                <Field label="Lý do thu hồi"><input className={inputClass} value={reason} onChange={e => setReason(e.target.value)} placeholder="NCC báo lỗi bao bì" /></Field>
                <Button variant="danger" onClick={recall}>Thu hồi lô</Button>
              </div>)}
          </div>
          <h4 className="font-semibold">Khách cần liên hệ ({result.contacts.length})</h4>
          <Table headers={['Khách', 'SĐT', 'Số lượng đã mua']} empty={result.contacts.length === 0}>
            {result.contacts.map(c => <tr key={c.customerId}><Td>{c.name}</Td><Td>{c.phone}</Td><Td>{c.quantity}</Td></tr>)}
          </Table>
          <h4 className="font-semibold">Các đơn đã lấy hàng từ lô ({result.orders.length})</h4>
          <Table headers={['Đơn', 'Thời điểm', 'SL', 'Khách']} empty={result.orders.length === 0}>
            {result.orders.map(o => <tr key={o.orderCode}><Td>{o.orderCode}</Td><Td>{formatDateTime(o.orderedAt)}</Td><Td>{o.quantity}</Td><Td>{o.customerName ?? 'Khách lẻ'}</Td></tr>)}
          </Table>
        </>)}
    </div>
  );
}
