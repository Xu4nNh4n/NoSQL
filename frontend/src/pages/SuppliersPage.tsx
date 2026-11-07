import { useState } from 'react';
import { api, errorMessage, qs } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Td, Toolbar } from '../components/ui/Table';
import type { Page, Supplier } from '../types';

type Form = { id?: string; name: string; phone: string; email: string; address: string; active: boolean };

export function SuppliersPage() {
  const toast = useToast();
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<Form | null>(null);
  const list = useLoad(() => api.get<Page<Supplier>>(`/suppliers${qs({ keyword, page, size: 20 })}`), [keyword, page]);

  const save = async () => {
    if (!form) return;
    const body = { ...form, phone: form.phone || null, email: form.email || null, address: form.address || null };
    try {
      if (form.id) await api.put(`/suppliers/${form.id}`, body);
      else await api.post('/suppliers', body);
      toast.success('Đã lưu nhà cung cấp');
      setForm(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const remove = async (s: Supplier) => {
    if (!confirm(`Xóa nhà cung cấp "${s.name}"?`)) return;
    try { await api.delete(`/suppliers/${s.id}`); void list.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div>
      <Toolbar>
        <Field label="Tìm"><input className={inputClass} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></Field>
        <Button onClick={() => setForm({ name: '', phone: '', email: '', address: '', active: true })}>Thêm nhà cung cấp</Button>
      </Toolbar>
      <Table headers={['Tên', 'SĐT', 'Email', 'Địa chỉ', 'Trạng thái', '']} loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(s => (
          <tr key={s.id}>
            <Td className="font-medium">{s.name}</Td><Td>{s.phone}</Td><Td>{s.email}</Td><Td>{s.address}</Td><Td>{s.active ? 'Hoạt động' : 'Ngừng'}</Td>
            <Td className="space-x-2 whitespace-nowrap">
              <Button size="sm" variant="outline" onClick={() => setForm({ id: s.id, name: s.name, phone: s.phone ?? '', email: s.email ?? '', address: s.address ?? '', active: s.active })}>Sửa</Button>
              <Button size="sm" variant="ghost" onClick={() => remove(s)}>Xóa</Button>
            </Td>
          </tr>))}
      </Table>
      <ServerPager page={list.data} onPage={setPage} />
      <Modal isOpen={!!form} onClose={() => setForm(null)} title={form?.id ? 'Sửa nhà cung cấp' : 'Thêm nhà cung cấp'}
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Hủy</Button><Button onClick={save}>Lưu</Button></>}>
        {form && (
          <div className="grid gap-3">
            <Field label="Tên *"><input className={inputClass} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Số điện thoại"><input className={inputClass} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></Field>
            <Field label="Email"><input className={inputClass} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field>
            <Field label="Địa chỉ"><input className={inputClass} value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} /></Field>
            <label className="text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Đang hoạt động (ngừng thì không nhận hàng mới)</label>
          </div>)}
      </Modal>
    </div>
  );
}
