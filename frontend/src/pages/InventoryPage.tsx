import { useState, type ReactNode } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { api, errorMessage, qs } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useLoad } from '../hooks/useLoad';
import { useClientPage } from '../hooks/useClientPage';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, Pager, ServerPager, Table, Tabs, Td, Toolbar } from '../components/ui/Table';
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
      {tab === 'soon' && <GroupedBatches key="soon" url="/inventory/batches/expiring-soon" note="Lô còn hàng hết hạn trong 3 ngày tới. Job 07:00 tính sẵn và lưu Redis 24 giờ." />}
      {tab === 'expired' && <GroupedBatches key="expired" url="/inventory/batches/expired" note="Lô đã quá hạn nhưng còn tồn: quầy không bán được, nên điều chỉnh giảm (hủy hàng)." expired />}
      {tab === 'trace' && <TraceTab />}
    </div>
  );
}

const todayKey = () => new Date(Date.now() + 7 * 3600000).toISOString().slice(0, 10);

/** Mở/đóng từng nhóm sản phẩm. */
function useExpanded() {
  const [open, setOpen] = useState<Set<string>>(() => new Set());
  return {
    isOpen: (id: string) => open.has(id),
    toggle: (id: string) => setOpen(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    }),
    openOne: (id: string) => setOpen(prev => new Set(prev).add(id)),
    setAll: (ids: string[]) => setOpen(new Set(ids)),
  };
}

/** Hộp điều chỉnh giảm tồn một lô, dùng chung cho các tab. */
function useAdjust(onDone: () => void) {
  const toast = useToast();
  const [adjust, setAdjust] = useState<{ batch: Batch; quantity: number; reason: string } | null>(null);
  const save = async () => {
    if (!adjust) return;
    try {
      await api.post(`/inventory/batches/${adjust.batch.id}/adjustments`, { quantity: adjust.quantity, reason: adjust.reason });
      toast.success('Đã điều chỉnh giảm tồn');
      setAdjust(null);
      onDone();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const modal = (
    <Modal isOpen={!!adjust} onClose={() => setAdjust(null)} title={`Điều chỉnh giảm lô ${adjust?.batch.batchCode ?? ''}`}
      footer={<><Button variant="outline" onClick={() => setAdjust(null)}>Hủy</Button><Button variant="danger" onClick={save}>Giảm tồn</Button></>}>
      {adjust && (
        <div className="grid gap-3">
          <p className="text-sm text-gray-600">{adjust.batch.productName} · còn {adjust.batch.remainingQty}. Chỉ được giảm; muốn tăng phải nhập lô.</p>
          <Field label="Số lượng giảm"><input type="number" min={1} max={adjust.batch.remainingQty} className={inputClass} value={adjust.quantity} onChange={e => setAdjust({ ...adjust, quantity: Number(e.target.value) })} /></Field>
          <Field label="Lý do *"><input className={inputClass} placeholder="Hư hỏng, hết hạn, hao hụt…" value={adjust.reason} onChange={e => setAdjust({ ...adjust, reason: e.target.value })} /></Field>
          {adjust.batch.adjustments?.length > 0 && <ul className="text-xs text-gray-500">{adjust.batch.adjustments.map(a => <li key={a.id}>{formatDateTime(a.at)} · −{a.quantity} · {a.reason} · {a.by}</li>)}</ul>}
        </div>)}
    </Modal>
  );
  return { start: (b: Batch, quantity = 1, reason = '') => setAdjust({ batch: b, quantity, reason }), modal };
}

/** Một dòng sản phẩm có mũi tên; bấm để xổ phần chi tiết bên dưới. */
function GroupRow({ open, onToggle, cells, span, children }: {
  open: boolean; onToggle: () => void; cells: ReactNode; span: number; children: ReactNode;
}) {
  return (
    <>
      <tr className={`cursor-pointer hover:bg-gray-50 ${open ? 'bg-blue-50/60' : ''}`} onClick={onToggle}>
        <Td className="w-8 text-gray-500">{open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}</Td>
        {cells}
      </tr>
      {open && <tr><td colSpan={span} className="bg-gray-50 px-4 py-3">{children}</td></tr>}
    </>
  );
}

/** Bảng lô bên trong một sản phẩm. */
function BatchTable({ rows, onAdjust, markNext = true }: { rows: Batch[]; onAdjust?: (b: Batch) => void; markNext?: boolean }) {
  const today = todayKey();
  // Lô bán tiếp theo theo FEFO: còn hàng, chưa hết hạn, hạn gần nhất (danh sách đã sắp theo hạn tăng dần)
  const next = markNext ? rows.find(b => b.remainingQty > 0 && !(b.expiryDate && b.expiryDate < today)) : undefined;
  const daysLeft = (d: string) => Math.round((new Date(d).getTime() - new Date(today).getTime()) / 86400000);
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
            const left = b.expiryDate ? daysLeft(b.expiryDate) : null;
            return (
              <tr key={b.id} className={`border-t border-gray-200 ${expired && !empty ? 'bg-rose-50' : ''} ${empty ? 'text-gray-400' : ''}`}>
                <td className="px-2 py-1.5 font-mono text-xs whitespace-nowrap">{b.batchCode}
                  {b.id === next?.id && <span className="ml-2 rounded bg-green-100 px-1.5 py-0.5 font-sans text-[10px] font-semibold text-green-700">Bán tiếp theo</span>}
                  {expired && !empty && <span className="ml-2 rounded bg-rose-100 px-1.5 py-0.5 font-sans text-[10px] font-semibold text-rose-700">Hết hạn</span>}
                  {empty && <span className="ml-2 font-sans text-[10px]">đã hết</span>}
                </td>
                <td className="px-2 py-1.5 whitespace-nowrap">{b.supplierName}</td>
                <td className="px-2 py-1.5">{b.receivedQty}</td>
                <td className="px-2 py-1.5 font-semibold">{b.remainingQty}</td>
                <td className="px-2 py-1.5">{b.damagedQty}</td>
                <td className="px-2 py-1.5">{formatVND(b.unitCost)}</td>
                <td className={`px-2 py-1.5 whitespace-nowrap ${expired && !empty ? 'text-rose-600' : left !== null && left <= 3 && !empty ? 'text-amber-600' : ''}`}>
                  {b.expiryDate ? formatDate(b.expiryDate) : 'Không hạn'}
                  {left !== null && !empty && <span className="ml-1 text-[11px]">({left < 0 ? `quá ${-left} ngày` : left === 0 ? 'hôm nay' : `còn ${left} ngày`})</span>}
                </td>
                <td className="px-2 py-1.5 text-xs whitespace-nowrap">{formatDateTime(b.receivedAt)} · {b.purchaseOrderCode ?? 'lô lẻ'}</td>
                <td className="px-2 py-1.5 text-right whitespace-nowrap">{onAdjust && !empty && <Button size="sm" variant="outline" onClick={() => onAdjust(b)}>Điều chỉnh</Button>}</td>
              </tr>);
          })}
        </tbody>
      </table>
    </div>
  );
}

/** Tất cả lô của một sản phẩm, đọc theo trang từ API (sắp theo hạn dùng). */
function ProductBatches({ productId, version, onAdjust }: { productId: string; version: number; onAdjust?: (b: Batch) => void }) {
  const [page, setPage] = useState(0);
  const list = useLoad(() => api.get<Page<Batch>>(`/inventory/batches${qs({ productId, page, size: 10 })}`), [productId, page, version]);
  if (list.loading && !list.data) return <p className="text-sm text-gray-500">Đang tải lô…</p>;
  if (list.error) return <p className="text-sm text-rose-600">{list.error}</p>;
  const rows = list.data?.content ?? [];
  if (rows.length === 0) return <p className="text-sm text-gray-500">Chưa có lô nào. Bấm "+ Nhập lô" hoặc nhận phiếu nhập.</p>;
  return (
    <div>
      <BatchTable rows={rows} onAdjust={onAdjust} markNext={page === 0} />
      <ServerPager page={list.data} onPage={setPage} />
    </div>
  );
}

function SearchBox({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return <Field label="Tìm sản phẩm"><input className={inputClass} placeholder="Tên hoặc SKU" value={value} onChange={e => onChange(e.target.value)} /></Field>;
}

const matches = (q: string, ...fields: (string | undefined)[]) => !q || fields.some(f => f?.toLowerCase().includes(q));

function ExpandAllButton({ ids, expanded }: { ids: string[]; expanded: ReturnType<typeof useExpanded> }) {
  const allOpen = ids.length > 0 && ids.every(expanded.isOpen);
  return <Button variant="outline" onClick={() => expanded.setAll(allOpen ? [] : ids)}>{allOpen ? 'Thu gọn tất cả' : 'Mở tất cả'}</Button>;
}

function BatchesTab() {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [version, setVersion] = useState(0);
  const refresh = () => setVersion(v => v + 1);
  const expanded = useExpanded();
  const adjust = useAdjust(refresh);
  const products = useLoad(() => api.get<Page<Product>>('/products?size=100&active=true'), [version]);
  const [loose, setLoose] = useState<{ productId: string; quantity: number; unitCost: number; mfgDate: string; expiryDate: string } | null>(null);

  const saveLoose = async () => {
    if (!loose) return;
    try {
      await api.post('/inventory/batches', { ...loose, mfgDate: loose.mfgDate || null, expiryDate: loose.expiryDate || null });
      toast.success('Đã nhập lô');
      expanded.openOne(loose.productId);
      setLoose(null);
      refresh();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const newLoose = (productId: string) => setLoose({ productId, quantity: 10, unitCost: 0, mfgDate: '', expiryDate: '' });

  const q = search.trim().toLowerCase();
  const shown = (products.data?.content ?? []).filter(p => matches(q, p.name, p.sku));
  const paged = useClientPage(shown, 10, q);

  return (
    <>
      <Toolbar>
        <SearchBox value={search} onChange={setSearch} />
        <ExpandAllButton ids={paged.rows.map(p => p.id)} expanded={expanded} />
        <Button variant="outline" onClick={() => newLoose(products.data?.content[0]?.id ?? '')}>Nhập lô lẻ</Button>
      </Toolbar>
      <Table headers={['', 'SKU', 'Sản phẩm', 'Nhóm hàng', 'Tồn kho', 'Hỏng', 'Mức đặt lại', '']}
        loading={products.loading} error={products.error} empty={shown.length === 0}>
        {paged.rows.map(p => {
          const low = p.stockQuantity <= p.reorderPoint;
          return (
            <GroupRow key={p.id} open={expanded.isOpen(p.id)} onToggle={() => expanded.toggle(p.id)} span={8} cells={<>
              <Td className="font-mono text-xs">{p.sku}</Td>
              <Td className="font-medium">{p.name}</Td>
              <Td>{p.categoryName}</Td>
              <Td className={`font-semibold ${p.stockQuantity === 0 ? 'text-rose-600' : low ? 'text-amber-600' : ''}`}>{p.stockQuantity}</Td>
              <Td>{p.damagedQuantity}</Td>
              <Td>{p.reorderPoint}</Td>
              <Td><Button size="sm" variant="ghost" onClick={e => { e.stopPropagation(); newLoose(p.id); }}>+ Nhập lô</Button></Td>
            </>}>
              <ProductBatches productId={p.id} version={version} onAdjust={b => adjust.start(b)} />
            </GroupRow>);
        })}
      </Table>
      <Pager {...paged.pager} />
      {adjust.modal}

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

function LowStockTab() {
  const [search, setSearch] = useState('');
  const [version, setVersion] = useState(0);
  const expanded = useExpanded();
  const adjust = useAdjust(() => setVersion(v => v + 1));
  const list = useLoad(() => api.get<LowStockItem[]>('/inventory/low-stock'), [version]);
  const q = search.trim().toLowerCase();
  // Thiếu nhiều nhất lên đầu
  const shown = (list.data ?? []).filter(i => matches(q, i.name, i.sku)).sort((a, b) => (a.available - a.reorderPoint) - (b.available - b.reorderPoint));
  const paged = useClientPage(shown, 10, q);
  return (
    <>
      <p className="mb-3 text-sm text-gray-500">Sản phẩm có tồn khả dụng (tổng các lô còn hạn) ≤ mức đặt lại. Thiếu nhiều nhất xếp trên. Bấm vào sản phẩm để xem lô.</p>
      <Toolbar>
        <SearchBox value={search} onChange={setSearch} />
        <ExpandAllButton ids={paged.rows.map(i => i.productId)} expanded={expanded} />
      </Toolbar>
      <Table headers={['', 'SKU', 'Sản phẩm', 'Khả dụng', 'Tồn (gồm lô hết hạn)', 'Mức đặt lại', 'Cần nhập thêm']}
        loading={list.loading} error={list.error} empty={shown.length === 0}>
        {paged.rows.map(i => {
          const ratio = i.reorderPoint > 0 ? Math.min(1, i.available / i.reorderPoint) : 0;
          return (
            <GroupRow key={i.productId} open={expanded.isOpen(i.productId)} onToggle={() => expanded.toggle(i.productId)} span={7} cells={<>
              <Td className="font-mono text-xs">{i.sku}</Td>
              <Td className="font-medium">{i.name}</Td>
              <Td>
                <span className={`font-semibold ${i.available === 0 ? 'text-rose-600' : 'text-amber-600'}`}>{i.available}</span>
                <div className="mt-1 h-1.5 w-24 rounded bg-gray-200"><div className={`h-1.5 rounded ${i.available === 0 ? 'bg-rose-500' : 'bg-amber-500'}`} style={{ width: `${ratio * 100}%` }} /></div>
              </Td>
              <Td>{i.stockQuantity}{i.stockQuantity > i.available && <span className="ml-1 text-xs text-rose-600">({i.stockQuantity - i.available} hết hạn)</span>}</Td>
              <Td>{i.reorderPoint}</Td>
              <Td className="font-semibold">{Math.max(0, i.reorderPoint - i.available)}</Td>
            </>}>
              <ProductBatches productId={i.productId} version={version} onAdjust={b => adjust.start(b)} />
            </GroupRow>);
        })}
      </Table>
      <Pager {...paged.pager} />
      {adjust.modal}
    </>
  );
}

type BatchGroup = { productId: string; sku: string; name: string; batches: Batch[]; remaining: number; earliest?: string };

function groupByProduct(batches: Batch[]): BatchGroup[] {
  const map = new Map<string, BatchGroup>();
  for (const b of batches) {
    const g = map.get(b.productId) ?? { productId: b.productId, sku: b.productSku, name: b.productName, batches: [], remaining: 0 };
    g.batches.push(b);
    g.remaining += b.remainingQty;
    if (b.expiryDate && (!g.earliest || b.expiryDate < g.earliest)) g.earliest = b.expiryDate;
    map.set(b.productId, g);
  }
  const groups = [...map.values()];
  for (const g of groups) g.batches.sort((a, b) => (a.expiryDate ?? '').localeCompare(b.expiryDate ?? ''));
  return groups.sort((a, b) => (a.earliest ?? '').localeCompare(b.earliest ?? ''));
}

/** Sắp hết hạn / đã hết hạn: nhóm lô theo sản phẩm. */
function GroupedBatches({ url, note, expired = false }: { url: string; note: string; expired?: boolean }) {
  const [search, setSearch] = useState('');
  const [version, setVersion] = useState(0);
  const expanded = useExpanded();
  const adjust = useAdjust(() => setVersion(v => v + 1));
  const list = useLoad(() => api.get<Batch[]>(url), [url, version]);
  const q = search.trim().toLowerCase();
  const groups = groupByProduct(list.data ?? []).filter(g => matches(q, g.name, g.sku));
  const paged = useClientPage(groups, 10, q);
  const today = todayKey();
  const days = (d?: string) => (d ? Math.round((new Date(d).getTime() - new Date(today).getTime()) / 86400000) : null);
  return (
    <>
      <p className="mb-3 text-sm text-gray-500">{note}</p>
      <Toolbar>
        <SearchBox value={search} onChange={setSearch} />
        <ExpandAllButton ids={paged.rows.map(g => g.productId)} expanded={expanded} />
      </Toolbar>
      <Table headers={['', 'SKU', 'Sản phẩm', 'Số lô', 'Tổng còn', expired ? 'Hết hạn sớm nhất' : 'Hạn gần nhất']}
        loading={list.loading} error={list.error} empty={groups.length === 0}>
        {paged.rows.map(g => {
          const d = days(g.earliest);
          return (
            <GroupRow key={g.productId} open={expanded.isOpen(g.productId)} onToggle={() => expanded.toggle(g.productId)} span={6} cells={<>
              <Td className="font-mono text-xs">{g.sku}</Td>
              <Td className="font-medium">{g.name}</Td>
              <Td>{g.batches.length}</Td>
              <Td className="font-semibold">{g.remaining}</Td>
              <Td className={expired ? 'text-rose-600' : 'text-amber-600'}>
                {g.earliest ? formatDate(g.earliest) : '—'}
                {d !== null && <span className="ml-1 text-xs">({d < 0 ? `quá ${-d} ngày` : d === 0 ? 'hôm nay' : `còn ${d} ngày`})</span>}
              </Td>
            </>}>
              <BatchTable rows={g.batches} markNext={false}
                onAdjust={b => adjust.start(b, expired ? b.remainingQty : 1, expired ? 'Hết hạn, hủy hàng' : '')} />
            </GroupRow>);
        })}
      </Table>
      <Pager {...paged.pager} />
      {adjust.modal}
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
      toast.success('Đã thu hồi lô: giảm toàn bộ phần còn tồn');
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
          <h4 className="font-semibold">Các đơn đã lấy hàng từ lô ({result.orders.length})</h4>
          <Table headers={['Đơn', 'Thời điểm', 'SL']} empty={result.orders.length === 0}>
            {result.orders.map(o => <tr key={o.orderCode}><Td>{o.orderCode}</Td><Td>{formatDateTime(o.orderedAt)}</Td><Td>{o.quantity}</Td></tr>)}
          </Table>
        </>)}
    </div>
  );
}
