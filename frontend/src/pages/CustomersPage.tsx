import { useState } from 'react';
import { api, errorMessage, qs } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Td, Toolbar } from '../components/ui/Table';
import type { Customer, Page } from '../types';

type Form = { id?: string; fullName: string; phone: string; email: string; address: string; active: boolean; version?: number };
const empty: Form = { fullName: '', phone: '', email: '', address: '', active: true };

export function CustomersPage() {
  const { role } = useAuth();
  const toast = useToast();
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<Form | null>(null);
  const list = useLoad(() => api.get<Page<Customer>>(`/customers${qs({ keyword, page, size: 20 })}`), [keyword, page]);

  const save = async () => {
    if (!form) return;
    try {
      const body = { ...form, email: form.email || null, address: form.address || null };
      if (form.id) await api.put(`/customers/${form.id}`, body);
      else await api.post('/customers', body);
      toast.success('Đã lưu khách hàng');
      setForm(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div>
      <Toolbar>
        <Field label="Tìm theo tên, SĐT, email"><input className={inputClass} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></Field>
        <Button onClick={() => setForm({ ...empty })}>Thêm khách hàng</Button>
      </Toolbar>
      <Table headers={['Họ tên', 'SĐT', 'Email', 'Địa chỉ', 'Trạng thái', '']} loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(c => (
          <tr key={c.id}>
            <Td className="font-medium">{c.fullName}</Td><Td>{c.phone}</Td><Td>{c.email}</Td><Td>{c.address}</Td>
            <Td>{c.active ? 'Hoạt động' : 'Ngừng'}</Td>
            <Td><Button size="sm" variant="outline" onClick={() => setForm({ id: c.id, fullName: c.fullName, phone: c.phone, email: c.email ?? '', address: c.address ?? '', active: c.active, version: c.version })}>Sửa</Button></Td>
          </tr>))}
      </Table>
      <ServerPager page={list.data} onPage={setPage} />
      <Modal isOpen={!!form} onClose={() => setForm(null)} title={form?.id ? 'Sửa khách hàng' : 'Thêm khách hàng'}
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Hủy</Button><Button onClick={save}>Lưu</Button></>}>
        {form && (
          <div className="grid gap-3">
            <Field label="Họ tên *"><input className={inputClass} value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></Field>
            <Field label="Số điện thoại *"><input className={inputClass} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></Field>
            <Field label="Email"><input className={inputClass} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field>
            <Field label="Địa chỉ"><input className={inputClass} value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} /></Field>
            {form.id && role === 'ADMIN' && <label className="text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Đang hoạt động</label>}
          </div>)}
      </Modal>
    </div>
  );
}
