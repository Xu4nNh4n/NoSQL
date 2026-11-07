import { useState } from 'react';
import { api, errorMessage, qs } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Td, Toolbar } from '../components/ui/Table';
import { formatDateTime, formatVND } from '../utils/formatters';
import type { Category, Page, Product, Promotion, PromotionType, Suggestions } from '../types';

const TYPE_LABEL: Record<PromotionType, string> = {
  PERCENT: 'Giảm %', FIXED: 'Giảm tiền / sản phẩm', BUY_X_GET_Y: 'Mua X tặng Y', ORDER_THRESHOLD: 'Giảm theo tổng đơn',
};

type Form = {
  id?: string; name: string; type: PromotionType; startsAt: string; endsAt: string; productIds: string[]; categoryIds: string[];
  percent: string; amount: string; buyQty: string; getQty: string; giftProductId: string; minTotal: string; maxDiscount: string;
  active: boolean; version?: number;
};

const toLocal = (iso: string) => new Date(new Date(iso).getTime() + 7 * 3600000).toISOString().slice(0, 16);
const fromLocal = (v: string) => new Date(`${v}:00+07:00`).toISOString();
const num = (v: string) => (v === '' ? null : Number(v));

function describe(p: Promotion): string {
  const r = p.rule;
  switch (p.type) {
    case 'PERCENT': return `Giảm ${r.percent}%`;
    case 'FIXED': return `Giảm ${formatVND(r.amount)} mỗi sản phẩm`;
    case 'BUY_X_GET_Y': return `Mua ${r.buyQty} tặng ${r.getQty}${r.giftProductId ? ' (quà khác loại)' : ''}`;
    case 'ORDER_THRESHOLD': return `Đơn từ ${formatVND(r.minTotal)} giảm ${r.amount ? formatVND(r.amount) : `${r.percent}%${r.maxDiscount ? ` tối đa ${formatVND(r.maxDiscount)}` : ''}`}`;
  }
}

export function PromotionsPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<Form | null>(null);
  const list = useLoad(() => api.get<Page<Promotion>>(`/promotions${qs({ page, size: 20 })}`), [page]);
  const products = useLoad(() => api.get<Page<Product>>('/products?size=100&active=true'), []);
  const categories = useLoad(() => api.get<Category[]>('/categories?active=true'), []);
  const [suggest, setSuggest] = useState<Suggestions | null>(null);

  const blank = (): Form => {
    const now = new Date();
    return { name: '', type: 'PERCENT', startsAt: toLocal(now.toISOString()), endsAt: toLocal(new Date(now.getTime() + 30 * 86400000).toISOString()),
    productIds: [], categoryIds: [], percent: '10', amount: '', buyQty: '2', getQty: '1', giftProductId: '', minTotal: '', maxDiscount: '', active: true };
  };
  const edit = (p: Promotion) => setForm({ id: p.id, name: p.name, type: p.type, startsAt: toLocal(p.startsAt), endsAt: toLocal(p.endsAt),
    productIds: p.scope?.productIds ?? [], categoryIds: p.scope?.categoryIds ?? [], percent: String(p.rule.percent ?? ''), amount: String(p.rule.amount ?? ''),
    buyQty: String(p.rule.buyQty ?? ''), getQty: String(p.rule.getQty ?? ''), giftProductId: p.rule.giftProductId ?? '', minTotal: String(p.rule.minTotal ?? ''),
    maxDiscount: String(p.rule.maxDiscount ?? ''), active: p.active, version: p.version });

  const save = async () => {
    if (!form) return;
    const t = form.type;
    const body = {
      name: form.name, type: t, startsAt: fromLocal(form.startsAt), endsAt: fromLocal(form.endsAt),
      productIds: t === 'ORDER_THRESHOLD' ? [] : form.productIds, categoryIds: t === 'PERCENT' || t === 'FIXED' ? form.categoryIds : [],
      percent: t === 'PERCENT' || (t === 'ORDER_THRESHOLD' && form.amount === '') ? num(form.percent) : null,
      amount: t === 'FIXED' || (t === 'ORDER_THRESHOLD' && form.amount !== '') ? num(form.amount) : null,
      buyQty: t === 'BUY_X_GET_Y' ? num(form.buyQty) : null, getQty: t === 'BUY_X_GET_Y' ? num(form.getQty) : null,
      giftProductId: t === 'BUY_X_GET_Y' && form.giftProductId ? form.giftProductId : null,
      minTotal: t === 'ORDER_THRESHOLD' ? num(form.minTotal) : null,
      maxDiscount: t === 'ORDER_THRESHOLD' && form.amount === '' ? num(form.maxDiscount) : null,
      active: form.active, version: form.version,
    };
    try {
      if (form.id) await api.put(`/promotions/${form.id}`, body);
      else await api.post('/promotions', body);
      toast.success('Đã lưu khuyến mãi (cache Redis được xóa, quầy áp ngay)');
      setForm(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const toggle = async (p: Promotion) => {
    try { await api.put(`/promotions/${p.id}/active`, { active: !p.active }); void list.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const loadSuggest = async (productId: string) => {
    if (!productId) { setSuggest(null); return; }
    setSuggest(await api.get<Suggestions>(`/products/${productId}/also-bought`).catch(() => null));
  };
  const toggleId = (ids: string[], id: string) => (ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]);

  return (
    <div>
      <Toolbar><Button onClick={() => setForm(blank())}>Tạo khuyến mãi</Button></Toolbar>
      <Table headers={['Tên', 'Loại', 'Quy tắc', 'Hiệu lực', 'Trạng thái', '']} loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(p => (
          <tr key={p.id}>
            <Td className="font-medium">{p.name}</Td><Td>{TYPE_LABEL[p.type]}</Td><Td>{describe(p)}</Td>
            <Td className="text-xs">{formatDateTime(p.startsAt)} → {formatDateTime(p.endsAt)}</Td>
            <Td>{p.active ? 'Đang bật' : 'Tắt'}</Td>
            <Td className="space-x-2 whitespace-nowrap">
              <Button size="sm" variant="outline" onClick={() => edit(p)}>Sửa</Button>
              <Button size="sm" variant="ghost" onClick={() => toggle(p)}>{p.active ? 'Tắt' : 'Bật'}</Button>
            </Td>
          </tr>))}
      </Table>
      <ServerPager page={list.data} onPage={setPage} />

      <Modal isOpen={!!form} onClose={() => setForm(null)} title={form?.id ? 'Sửa khuyến mãi' : 'Tạo khuyến mãi'} maxWidth="2xl"
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Hủy</Button><Button onClick={save}>Lưu</Button></>}>
        {form && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Tên *"><input className={inputClass} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Loại"><select className={inputClass} value={form.type} onChange={e => setForm({ ...form, type: e.target.value as PromotionType })}>
              {Object.entries(TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
            <Field label="Bắt đầu"><input type="datetime-local" className={inputClass} value={form.startsAt} onChange={e => setForm({ ...form, startsAt: e.target.value })} /></Field>
            <Field label="Kết thúc"><input type="datetime-local" className={inputClass} value={form.endsAt} onChange={e => setForm({ ...form, endsAt: e.target.value })} /></Field>

            {form.type === 'PERCENT' && <Field label="Phần trăm giảm"><input type="number" className={inputClass} value={form.percent} onChange={e => setForm({ ...form, percent: e.target.value })} /></Field>}
            {form.type === 'FIXED' && <Field label="Số tiền giảm mỗi sản phẩm"><input type="number" className={inputClass} value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></Field>}
            {form.type === 'BUY_X_GET_Y' && <>
              <Field label="Mua (X)"><input type="number" min={1} className={inputClass} value={form.buyQty} onChange={e => setForm({ ...form, buyQty: e.target.value })} /></Field>
              <Field label="Tặng (Y)"><input type="number" min={1} className={inputClass} value={form.getQty} onChange={e => setForm({ ...form, getQty: e.target.value })} /></Field>
              <Field label="Quà khác loại (để trống = tặng cùng sản phẩm)"><select className={inputClass} value={form.giftProductId} onChange={e => setForm({ ...form, giftProductId: e.target.value })}>
                <option value="">— cùng sản phẩm —</option>{products.data?.content.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></Field>
            </>}
            {form.type === 'ORDER_THRESHOLD' && <>
              <Field label="Tổng đơn tối thiểu"><input type="number" className={inputClass} value={form.minTotal} onChange={e => setForm({ ...form, minTotal: e.target.value })} /></Field>
              <Field label="Giảm số tiền (để trống nếu giảm %)"><input type="number" className={inputClass} value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></Field>
              {form.amount === '' && <>
                <Field label="Giảm %"><input type="number" className={inputClass} value={form.percent} onChange={e => setForm({ ...form, percent: e.target.value })} /></Field>
                <Field label="Giảm tối đa"><input type="number" className={inputClass} value={form.maxDiscount} onChange={e => setForm({ ...form, maxDiscount: e.target.value })} /></Field>
              </>}
            </>}

            {form.type !== 'ORDER_THRESHOLD' && (
              <div className="sm:col-span-2">
                <p className="mb-1 text-xs font-semibold text-gray-700">Áp cho sản phẩm</p>
                <div className="flex max-h-40 flex-wrap gap-1 overflow-y-auto rounded border border-gray-200 p-2">
                  {products.data?.content.map(p => (
                    <button key={p.id} type="button" onClick={() => { setForm({ ...form, productIds: toggleId(form.productIds, p.id) }); void loadSuggest(p.id); }}
                      className={`rounded border px-2 py-1 text-xs ${form.productIds.includes(p.id) ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-200'}`}>{p.name}</button>))}
                </div>
                {suggest?.available && suggest.items.length > 0 && (
                  <p className="mt-2 text-xs text-gray-600">Neo4j gợi ý hay mua kèm: {suggest.items.map(s => `${s.name} (${s.times} đơn)`).join(', ')} – có thể dùng làm quà "mua X tặng Y".</p>)}
              </div>)}
            {(form.type === 'PERCENT' || form.type === 'FIXED') && (
              <div className="sm:col-span-2">
                <p className="mb-1 text-xs font-semibold text-gray-700">Hoặc theo nhóm hàng</p>
                <div className="flex flex-wrap gap-1">{categories.data?.map(c => (
                  <button key={c.id} type="button" onClick={() => setForm({ ...form, categoryIds: toggleId(form.categoryIds, c.id) })}
                    className={`rounded border px-2 py-1 text-xs ${form.categoryIds.includes(c.id) ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-200'}`}>{c.name}</button>))}</div>
              </div>)}
            <label className="text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Đang bật</label>
          </div>)}
      </Modal>
    </div>
  );
}
