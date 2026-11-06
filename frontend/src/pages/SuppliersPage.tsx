import { Pagination, useListPage } from '../components/ui/Pagination';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Pencil, Search, EyeOff, RotateCcw } from 'lucide-react';
import { api } from '../api/client';
import type { Supplier } from '../types';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';

export const SuppliersPage = () => {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [form, setForm] = useState({ supplierName: '', contactName: '', phone: '', address: '' });
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const mutationLock = useRef(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('ALL');
  const { success, error } = useToast();
  const load = async () => {
    setLoading(true); setLoadError('');
    try { setSuppliers(await api.get<Supplier[]>('/suppliers')); }
    catch (e) { setLoadError(e instanceof Error ? e.message : 'Không tải được nhà cung cấp'); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const openForm = (supplier: Supplier | null) => {
    setEditing(supplier);
    setForm({ supplierName: supplier?.supplierName || '', contactName: supplier?.contactName || '', phone: supplier?.phone || '', address: supplier?.address || '' });
    setFormError(''); setIsOpen(true);
  };
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (mutationLock.current) return;
    if (!form.supplierName.trim()) { setFormError('Vui lòng nhập tên nhà cung cấp'); return; }
    mutationLock.current = true; setSaving(true); setFormError('');
    try {
      const body = { supplierName: form.supplierName.trim(), contactName: form.contactName.trim() || null, phone: form.phone.trim() || null, address: form.address.trim() || null };
      if (editing) await api.put('/suppliers/' + editing.supplierId, body);
      else await api.post('/suppliers', body);
      success(editing ? 'Đã cập nhật nhà cung cấp' : 'Đã thêm nhà cung cấp');
      setIsOpen(false); await load();
    } catch (e) { setFormError(e instanceof Error ? e.message : 'Không lưu được nhà cung cấp'); }
    finally { mutationLock.current = false; setSaving(false); }
  };
  const toggle = async (supplier: Supplier) => {
    if (mutationLock.current) return;
    if (supplier.isActive && !window.confirm('Ngừng nhập hàng từ ' + supplier.supplierName + '? Các lô và lịch sử đã nhập vẫn được giữ.')) return;
    mutationLock.current = true; setBusyId(supplier.supplierId);
    try {
      if (supplier.isActive) await api.delete('/suppliers/' + supplier.supplierId);
      else await api.put('/suppliers/' + supplier.supplierId + '/restore');
      success(supplier.isActive ? 'Đã ngừng nhập từ nhà cung cấp' : 'Đã cho phép nhập hàng trở lại');
      await load();
    } catch (e) { error(e instanceof Error ? e.message : 'Không đổi được trạng thái'); }
    finally { mutationLock.current = false; setBusyId(null); }
  };
  const keyword = search.trim().toLocaleLowerCase('vi');
  const visible = suppliers.filter(s =>
    (status === 'ALL' || s.isActive === (status === 'ACTIVE')) &&
    [s.supplierName, s.contactName, s.phone, s.address, String(s.supplierId)].some(value => value?.toLocaleLowerCase('vi').includes(keyword)));
  const pagination = useListPage(visible.sort((a, b) => b.supplierId - a.supplierId), JSON.stringify([search, status]));
  const disabled = saving || busyId !== null;
  return <div className="space-y-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><h1 className="text-xl font-bold text-gray-900">Nhà cung cấp</h1>
        <p className="mt-1 text-sm text-gray-600">Quản lý thông tin đối tác và chọn khi <Link to="/inventory" className="text-blue-700 underline">nhập lô hàng</Link>.</p></div>
      <Button leftIcon={<Plus className="h-4 w-4" />} disabled={disabled || loading || Boolean(loadError)} onClick={() => openForm(null)}>Thêm nhà cung cấp</Button>
    </div>
    <Card><div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-end">
      <div className="flex-1"><Input label="Tìm nhà cung cấp" value={search} onChange={e => setSearch(e.target.value)} placeholder="Nhà cung cấp, người liên hệ, mã hoặc số điện thoại" leftIcon={<Search className="h-4 w-4" />} /></div>
      <div className="sm:w-52"><Select label="Trạng thái" value={status} onChange={e => setStatus(e.target.value)} options={[{ value: 'ALL', label: 'Tất cả' }, { value: 'ACTIVE', label: 'Đang hợp tác' }, { value: 'INACTIVE', label: 'Ngừng nhập hàng' }]} /></div>
    </div></Card>
    <p className="text-xs text-gray-500">Ngừng nhập hàng sẽ chặn nhập lô mới từ nhà cung cấp đó. Các lô đã nhập vẫn được quản lý và bán theo điều kiện tồn kho, hạn dùng.</p>
    {loadError ? <div role="alert" className="text-sm text-rose-700">{loadError} <Button variant="outline" onClick={load}>Tải lại</Button></div> :
    <Card><div className="overflow-x-auto"><table className="w-full min-w-[720px] text-left text-sm">
      <thead className="bg-gray-50 text-gray-600"><tr><th className="p-4">Mã</th><th className="p-4">Nhà cung cấp</th><th className="p-4">Số điện thoại</th><th className="p-4">Địa chỉ</th><th className="p-4">Trạng thái</th><th className="p-4">Thao tác</th></tr></thead>
      <tbody className="divide-y divide-gray-100">{loading ? <tr><td colSpan={6} className="p-6 text-center">Đang tải...</td></tr> :
        visible.length === 0 ? <tr><td colSpan={6} className="p-6 text-center text-gray-500">{suppliers.length === 0 ? 'Chưa có nhà cung cấp. Bấm Thêm nhà cung cấp để bắt đầu.' : 'Không có nhà cung cấp khớp bộ lọc.'}</td></tr> :
        pagination.rows.map(s => <tr key={s.supplierId} className="hover:bg-gray-50">
          <td className="p-4 text-gray-500">{s.supplierId}</td>
          <td className="p-4"><button type="button" className="text-left font-semibold text-blue-700 hover:underline disabled:opacity-50" disabled={disabled} onClick={() => openForm(s)}>{s.supplierName}</button>
            <p className="mt-1 text-xs text-gray-500">Liên hệ: {s.contactName || 'Chưa có'}</p></td>
          <td className="p-4">{s.phone || '—'}</td><td className="max-w-xs break-words p-4">{s.address || '—'}</td>
          <td className="p-4"><span className={'whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium ' + (s.isActive ? 'bg-blue-50 text-blue-700' : 'bg-gray-100 text-gray-600')}>{s.isActive ? 'Đang hợp tác' : 'Ngừng nhập hàng'}</span></td>
          <td className="p-4"><div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={disabled} onClick={() => openForm(s)} leftIcon={<Pencil className="h-3.5 w-3.5" />}>Sửa</Button>
            <Button variant={s.isActive ? 'outline' : 'primary'} size="sm" disabled={disabled} isLoading={busyId === s.supplierId} onClick={() => toggle(s)} leftIcon={s.isActive ? <EyeOff className="h-3.5 w-3.5" /> : <RotateCcw className="h-3.5 w-3.5" />}>{s.isActive ? 'Ngừng nhập' : 'Cho nhập lại'}</Button>
          </div></td></tr>)}</tbody>
    </table></div><Pagination {...pagination} /></Card>}
    <Modal isOpen={isOpen} onClose={() => { if (!mutationLock.current) setIsOpen(false); }} title={editing ? 'Sửa nhà cung cấp' : 'Thêm nhà cung cấp'} maxWidth="lg" footer={<>
      <Button variant="outline" disabled={saving} onClick={() => setIsOpen(false)}>Hủy</Button>
      <Button type="submit" form="supplier-form" isLoading={saving}>{editing ? 'Lưu thay đổi' : 'Thêm nhà cung cấp'}</Button>
    </>}>
      <form id="supplier-form" onSubmit={save} className="space-y-4">
        {formError && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{formError}</p>}
        <fieldset disabled={saving} className="space-y-4">
          <Input label="Tên nhà cung cấp" value={form.supplierName} onChange={e => setForm({ ...form, supplierName: e.target.value })} required maxLength={150} autoFocus />
          <Input label="Người liên hệ (không bắt buộc)" value={form.contactName} onChange={e => setForm({ ...form, contactName: e.target.value })} maxLength={150} placeholder="Tên chủ hoặc nhân viên phụ trách nhập hàng" />
          <Input label="Số điện thoại (không bắt buộc)" type="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} maxLength={20} />
          <Input label="Địa chỉ (không bắt buộc)" value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} maxLength={255} />
        </fieldset>
        {editing && <p className="text-xs text-gray-500">Thông tin trên các lô đã nhập được giữ theo lúc nhập hàng.</p>}
      </form>
    </Modal>
  </div>;
};
