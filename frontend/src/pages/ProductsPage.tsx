import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api, errorMessage, qs } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Td, Toolbar } from '../components/ui/Table';
import { formatDateTime, formatVND } from '../utils/formatters';
import type { AttributeValue, Category, Page, Product, StockMovement, Supplier } from '../types';

type Attr = { key: string; value: string };
type Form = { id?: string; sku: string; name: string; price: string; categoryId: string; supplierId: string; reorderPoint: string; imageUrl: string; active: boolean; attrs: Attr[]; version?: number };

const MOVEMENT_LABEL: Record<StockMovement['type'], string> = {
  RECEIPT: 'Nhập', SALE: 'Bán', RETURN: 'Trả về kho', RETURN_DAMAGED: 'Trả (hỏng)', ADJUSTMENT: 'Điều chỉnh',
};

function toValue(v: string): AttributeValue {
  if (v === 'true' || v === 'false') return v === 'true';
  return v.trim() !== '' && !isNaN(Number(v)) ? Number(v) : v;
}

export function ProductsPage() {
  const { role } = useAuth();
  const canEdit = role === 'ADMIN' || role === 'WAREHOUSE';
  const toast = useToast();
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<Form | null>(null);
  const [card, setCard] = useState<Product | null>(null);
  const categories = useLoad(() => api.get<Category[]>('/categories'), []);
  const suppliers = useLoad(() => canEdit ? api.get<Page<Supplier>>('/suppliers?size=100') : Promise.resolve(null), [canEdit]);
  const list = useLoad(() => api.get<Page<Product>>(`/products${qs({ keyword, categoryId, page, size: 20 })}`), [keyword, categoryId, page]);

  const edit = (p?: Product) => setForm(p ? {
    id: p.id, sku: p.sku, name: p.name, price: String(p.price), categoryId: p.categoryId, supplierId: p.supplierId ?? '',
    reorderPoint: String(p.reorderPoint), imageUrl: p.imageUrl ?? '', active: p.active, version: p.version,
    attrs: Object.entries(p.attributes ?? {}).map(([key, value]) => ({ key, value: String(value) })),
  } : { sku: '', name: '', price: '', categoryId: categories.data?.[0]?.id ?? '', supplierId: '', reorderPoint: '0', imageUrl: '', active: true, attrs: [] });

  const save = async () => {
    if (!form) return;
    const attributes = Object.fromEntries(form.attrs.filter(a => a.key.trim()).map(a => [a.key.trim(), toValue(a.value)]));
    const body = { sku: form.sku, name: form.name, price: Number(form.price), categoryId: form.categoryId, supplierId: form.supplierId || null,
      reorderPoint: Number(form.reorderPoint), imageUrl: form.imageUrl || null, attributes, active: form.active, version: form.version };
    try {
      if (form.id) await api.put(`/products/${form.id}`, body);
      else await api.post('/products', body);
      toast.success('Đã lưu sản phẩm');
      setForm(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div>
      <Toolbar>
        <Field label="Tìm (có dấu hoặc không dấu)"><input className={inputClass} value={keyword} placeholder="sua tuoi" onChange={e => { setKeyword(e.target.value); setPage(0); }} /></Field>
        <Field label="Nhóm hàng">
          <select className={inputClass} value={categoryId} onChange={e => { setCategoryId(e.target.value); setPage(0); }}>
            <option value="">Tất cả</option>{categories.data?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        {canEdit && <Button onClick={() => edit()}>Thêm sản phẩm</Button>}
      </Toolbar>
      <Table headers={['SKU', 'Tên', 'Nhóm', 'Giá', 'Tồn', 'Ngưỡng', 'Thuộc tính', '']} loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(p => (
          <tr key={p.id} className={p.active ? '' : 'opacity-50'}>
            <Td className="font-mono text-xs">{p.sku}</Td><Td className="font-medium">{p.name}</Td><Td>{p.categoryName}</Td>
            <Td className="text-right">{formatVND(p.price)}</Td>
            <Td className={p.stockQuantity <= p.reorderPoint ? 'font-semibold text-rose-600' : ''}>{p.stockQuantity}{p.damagedQuantity > 0 && <span className="text-xs text-gray-400"> (+{p.damagedQuantity} hỏng)</span>}</Td>
            <Td>{p.reorderPoint}</Td>
            <Td className="text-xs text-gray-600">{Object.entries(p.attributes ?? {}).map(([k, v]) => `${k}: ${v}`).join(' · ')}</Td>
            <Td className="space-x-2 whitespace-nowrap">
              <Button size="sm" variant="outline" onClick={() => setCard(p)}>Thẻ kho</Button>
              {canEdit && <Button size="sm" variant="outline" onClick={() => edit(p)}>Sửa</Button>}
            </Td>
          </tr>))}
      </Table>
      <ServerPager page={list.data} onPage={setPage} />

      <Modal isOpen={!!form} onClose={() => setForm(null)} title={form?.id ? 'Sửa sản phẩm' : 'Thêm sản phẩm'} maxWidth="xl"
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Hủy</Button><Button onClick={save}>Lưu</Button></>}>
        {form && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="SKU *"><input className={inputClass} value={form.sku} onChange={e => setForm({ ...form, sku: e.target.value })} /></Field>
            <Field label="Tên *"><input className={inputClass} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Giá bán (VND) *"><input type="number" min={0} className={inputClass} value={form.price} onChange={e => setForm({ ...form, price: e.target.value })} /></Field>
            <Field label="Ngưỡng đặt lại"><input type="number" min={0} className={inputClass} value={form.reorderPoint} onChange={e => setForm({ ...form, reorderPoint: e.target.value })} /></Field>
            <Field label="Nhóm hàng *"><select className={inputClass} value={form.categoryId} onChange={e => setForm({ ...form, categoryId: e.target.value })}>
              {categories.data?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
            <Field label="Nhà cung cấp mặc định"><select className={inputClass} value={form.supplierId} onChange={e => setForm({ ...form, supplierId: e.target.value })}>
              <option value="">—</option>{suppliers.data?.content.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
            <Field label="Ảnh (URL)"><input className={inputClass} value={form.imageUrl} onChange={e => setForm({ ...form, imageUrl: e.target.value })} /></Field>
            {form.id && <label className="flex items-end gap-2 text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Đang kinh doanh</label>}
            <div className="sm:col-span-2">
              <p className="mb-1 text-xs font-semibold text-gray-700">Thuộc tính riêng theo ngành hàng (schema linh hoạt MongoDB)</p>
              {form.attrs.map((a, i) => (
                <div key={i} className="mb-2 flex gap-2">
                  <input className={inputClass} placeholder="tenThuocTinh (vd dungTich)" value={a.key} onChange={e => setForm({ ...form, attrs: form.attrs.map((x, j) => j === i ? { ...x, key: e.target.value } : x) })} />
                  <input className={inputClass} placeholder="giá trị (vd 1L, 12, true)" value={a.value} onChange={e => setForm({ ...form, attrs: form.attrs.map((x, j) => j === i ? { ...x, value: e.target.value } : x) })} />
                  <Button variant="ghost" aria-label="Xóa thuộc tính" onClick={() => setForm({ ...form, attrs: form.attrs.filter((_, j) => j !== i) })}><Trash2 className="h-4 w-4" /></Button>
                </div>))}
              <Button size="sm" variant="outline" leftIcon={<Plus className="h-3 w-3" />} onClick={() => setForm({ ...form, attrs: [...form.attrs, { key: '', value: '' }] })}>Thêm thuộc tính</Button>
            </div>
            <p className="text-xs text-gray-500 sm:col-span-2">Tồn kho không sửa ở đây: chỉ thay đổi qua nhập lô, bán, trả và điều chỉnh.</p>
          </div>)}
      </Modal>
      {card && <StockCard product={card} onClose={() => setCard(null)} />}
    </div>
  );
}

function StockCard({ product, onClose }: { product: Product; onClose: () => void }) {
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));
  const rows = useLoad(() => api.get<StockMovement[]>(`/products/${product.id}/stock-movements${qs({ month })}`), [product.id, month]);
  const net = (rows.data ?? []).filter(r => r.type !== 'RETURN_DAMAGED').reduce((s, r) => s + r.quantity, 0);
  return (
    <Modal isOpen onClose={onClose} title={`Thẻ kho · ${product.name}`} maxWidth="2xl">
      <Toolbar>
        <Field label="Tháng (partition Cassandra)"><input type="month" className={inputClass} value={month} onChange={e => setMonth(e.target.value)} /></Field>
        <p className="text-sm text-gray-600">Biến động ròng trong tháng: <b>{net > 0 ? `+${net}` : net}</b> · tồn hiện tại {product.stockQuantity}</p>
      </Toolbar>
      <Table headers={['Thời điểm', 'Loại', 'SL', 'Lô', 'Chứng từ', 'Ghi chú', 'Người']} loading={rows.loading} error={rows.error} empty={rows.data?.length === 0}>
        {rows.data?.map(m => (
          <tr key={m.id}>
            <Td>{formatDateTime(m.createdAt)}</Td><Td>{MOVEMENT_LABEL[m.type]}</Td>
            <Td className={m.quantity < 0 ? 'text-rose-600' : 'text-emerald-700'}>{m.quantity > 0 ? `+${m.quantity}` : m.quantity}</Td>
            <Td className="font-mono text-xs">{m.batchCode}</Td><Td className="text-xs">{m.refCode}</Td><Td className="text-xs">{m.note}</Td><Td>{m.performedBy}</Td>
          </tr>))}
      </Table>
    </Modal>
  );
}
