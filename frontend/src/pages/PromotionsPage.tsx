import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Product, Promotion } from '../types';
import { normalizeProduct } from '../types';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { Pagination, useListPage } from '../components/ui/Pagination';
import { useToast } from '../components/ui/Toast';

const emptyForm = { productId: '', name: '', percent: '', startsAt: '', endsAt: '', active: true };
// Promotion datetimes are Vietnam wall time, never interpreted in the browser's timezone.
const displayTime = (value: string) => value.replace('T', ' ');
const promotionStatus = (row: Promotion, now: number) => {
  if (!row.active) return 'DISABLED';
  if (now < Date.parse(row.startsAt + '+07:00')) return 'UPCOMING';
  if (now >= Date.parse(row.endsAt + '+07:00')) return 'EXPIRED';
  return 'ACTIVE';
};
const statusLabels: Record<string, string> = {
  ACTIVE: 'Đang hiệu lực', UPCOMING: 'Chưa bắt đầu', EXPIRED: 'Hết hạn', DISABLED: 'Đã tắt',
};

export const PromotionsPage = () => {
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const [editing, setEditing] = useState<Promotion | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const mutationLock = useRef(false);
  const { success, error } = useToast();
  const load = async () => {
    setLoading(true); setLoadError('');
    try {
      const [rows, catalog] = await Promise.all([
        api.get<Promotion[]>('/promotions'), api.get<unknown[]>('/products/active'),
      ]);
      setPromotions(rows); setProducts(catalog.map(normalizeProduct));
    } catch (e) { setLoadError(e instanceof Error ? e.message : 'Không tải được khuyến mãi'); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const openForm = (row: Promotion | null) => {
    setEditing(row); setFormError('');
    setForm(row ? { productId: String(row.productId), name: row.name, percent: String(row.percent),
      startsAt: row.startsAt.slice(0, 19), endsAt: row.endsAt.slice(0, 19), active: row.active } : emptyForm);
    setOpen(true);
  };
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (mutationLock.current) return;
    if (!form.name.trim() || !form.productId || Number(form.percent) <= 0 || Number(form.percent) > 100
      || !form.startsAt || !form.endsAt || form.endsAt <= form.startsAt) {
      setFormError('Chọn sản phẩm, nhập tên, 0 < phần trăm ≤ 100 và ngày kết thúc sau ngày bắt đầu.'); return;
    }
    mutationLock.current = true; setSaving(true); setFormError('');
    try {
      const body = { ...form, name: form.name.trim(), productId: Number(form.productId), percent: Number(form.percent) };
      if (editing) await api.put('/promotions/' + editing.promotionId, body);
      else await api.post('/promotions', body);
      setOpen(false); success('Đã lưu khuyến mãi'); await load();
    } catch (e) { setFormError(e instanceof Error ? e.message : 'Không lưu được khuyến mãi'); }
    finally { mutationLock.current = false; setSaving(false); }
  };
  const toggle = async (row: Promotion) => {
    if (mutationLock.current) return;
    mutationLock.current = true; setSaving(true);
    try {
      await api.put(`/promotions/${row.promotionId}/active`, { active: !row.active });
      success(row.active ? 'Đã tắt khuyến mãi' : 'Đã bật khuyến mãi'); await load();
    } catch (e) { error(e instanceof Error ? e.message : 'Không cập nhật được trạng thái'); }
    finally { mutationLock.current = false; setSaving(false); }
  };
  const filtered = promotions.filter(p => (status === 'ALL' || promotionStatus(p, now) === status)
    && `${p.name} ${p.productName}`.toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi')));
  const page = useListPage(filtered, search + status);
  return <div className="space-y-5">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h1 className="text-xl font-semibold text-gray-900">Khuyến mãi sản phẩm</h1>
        <p className="mt-1 text-sm text-gray-500">Giờ Việt Nam (UTC+7). Mỗi sản phẩm áp dụng một mức giảm cao nhất, không cộng dồn.</p></div>
      <Button disabled={loading || saving || !!loadError} onClick={() => openForm(null)}>Thêm khuyến mãi</Button>
    </div>
    <div className="flex flex-wrap items-end gap-3">
      <div className="max-w-sm flex-1"><Input label="Tìm tên / sản phẩm" value={search} onChange={e => setSearch(e.target.value)} /></div>
      <label className="text-xs font-semibold text-gray-700">Trạng thái
        <select className="ml-2 min-h-10 rounded-md border border-gray-300 bg-white px-3 text-sm" value={status} onChange={e => setStatus(e.target.value)}>
          <option value="ALL">Tất cả</option><option value="ACTIVE">Đang hiệu lực</option><option value="UPCOMING">Chưa bắt đầu</option><option value="EXPIRED">Hết hạn</option><option value="DISABLED">Đã tắt</option>
        </select></label>
      <Button variant="outline" disabled={loading || saving} onClick={() => void load()}>Tải lại</Button>
    </div>
    {loadError && <p role="alert" className="text-sm text-red-700">{loadError}</p>}
    <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
      <table className="w-full text-left text-sm"><thead className="border-b border-gray-200 text-gray-500"><tr>
        <th className="p-3">Khuyến mãi / Sản phẩm</th><th className="p-3 text-right">Giảm</th>
        <th className="p-3">Bắt đầu (VN)</th><th className="p-3">Kết thúc (VN)</th>
        <th className="p-3">Trạng thái</th><th className="p-3 text-right">Thao tác</th>
      </tr></thead><tbody className="divide-y divide-gray-100">
        {loading ? <tr><td colSpan={6} className="p-6 text-center" role="status">Đang tải…</td></tr>
          : loadError ? <tr><td colSpan={6} className="p-6 text-center text-gray-500">Tải lại để xem dữ liệu.</td></tr>
          : page.rows.length === 0 ? <tr><td colSpan={6} className="p-6 text-center text-gray-500">Chưa có khuyến mãi phù hợp.</td></tr>
          : page.rows.map(row => <tr key={row.promotionId}>
            <td className="p-3"><p className="font-medium text-gray-900">{row.name}</p><p className="text-xs text-gray-500">#{row.promotionId} · {row.productName}</p></td>
            <td className="p-3 text-right tabular-nums">{row.percent}%</td>
            <td className="whitespace-nowrap p-3">{displayTime(row.startsAt)}</td><td className="whitespace-nowrap p-3">{displayTime(row.endsAt)}</td>
            <td className="p-3">{statusLabels[promotionStatus(row, now)]}</td>
            <td className="p-3 text-right"><div className="flex justify-end gap-2">
              <Button size="sm" variant="outline" disabled={saving} onClick={() => openForm(row)}>Sửa</Button>
              <Button size="sm" variant="outline" disabled={saving} onClick={() => void toggle(row)}>{row.active ? 'Tắt' : 'Bật'}</Button>
            </div></td>
          </tr>)}
      </tbody></table>
    </div>
    <Pagination {...page} />
    <p className="text-xs text-gray-500">Bật chương trình chưa có nghĩa được giảm ngay: cần đến ngày bắt đầu và chưa qua ngày kết thúc theo giờ Việt Nam. Nếu cùng phần trăm, ưu tiên mã khuyến mãi nhỏ nhất. Giá trên hóa đơn cũ được giữ nguyên.</p>
    <Modal isOpen={open} onClose={() => { if (!saving) setOpen(false); }} title={editing ? 'Sửa khuyến mãi' : 'Thêm khuyến mãi'}
      footer={<><Button variant="outline" disabled={saving} onClick={() => setOpen(false)}>Hủy</Button>
        <Button type="submit" form="promotion-form" isLoading={saving}>Lưu khuyến mãi</Button></>}>
      <form id="promotion-form" onSubmit={save} className="space-y-4"><fieldset disabled={saving} className="space-y-4">
        <Input label="Tên khuyến mãi" required maxLength={150} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
        <label className="block text-xs font-semibold text-gray-700">Sản phẩm
          <select required className="mt-1 min-h-10 w-full rounded-md border border-gray-300 bg-white px-3 text-sm" value={form.productId} onChange={e => setForm({ ...form, productId: e.target.value })}>
            <option value="">Chọn sản phẩm</option>
            {editing && !products.some(p => p.id === editing.productId) && <option value={editing.productId}>{editing.productName} (ngừng bán)</option>}
            {products.map(p => <option key={p.id} value={p.id}>{p.skuCode} · {p.name}</option>)}
          </select></label>
        <Input label="Phần trăm giảm" type="number" required min="0.01" max="100" step="0.01" value={form.percent} onChange={e => setForm({ ...form, percent: e.target.value })} />
        <Input label="Bắt đầu (giờ Việt Nam)" type="datetime-local" step="1" required value={form.startsAt} onChange={e => setForm({ ...form, startsAt: e.target.value })} />
        <Input label="Kết thúc (giờ Việt Nam)" type="datetime-local" step="1" required value={form.endsAt} onChange={e => setForm({ ...form, endsAt: e.target.value })} />
        <label className="flex min-h-10 items-center gap-2 text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} />Bật khuyến mãi trong thời gian đã chọn</label>
      </fieldset>{formError && <p role="alert" className="text-sm text-red-700">{formError}</p>}</form>
    </Modal>
  </div>;
};
