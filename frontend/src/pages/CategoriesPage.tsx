import { useState } from 'react';
import { api, errorMessage } from '../api/client';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, Table, Td, Toolbar } from '../components/ui/Table';
import type { Category } from '../types';

type Form = { id?: string; name: string; description: string; active: boolean };

export function CategoriesPage() {
  const toast = useToast();
  const list = useLoad(() => api.get<Category[]>('/categories'), []);
  const [form, setForm] = useState<Form | null>(null);

  const save = async () => {
    if (!form) return;
    try {
      if (form.id) await api.put(`/categories/${form.id}`, form);
      else await api.post('/categories', form);
      toast.success('Đã lưu nhóm hàng (tên mới được cập nhật vào mọi sản phẩm của nhóm)');
      setForm(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const remove = async (c: Category) => {
    if (!confirm(`Xóa nhóm "${c.name}"?`)) return;
    try { await api.delete(`/categories/${c.id}`); void list.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div>
      <Toolbar><Button onClick={() => setForm({ name: '', description: '', active: true })}>Thêm nhóm hàng</Button></Toolbar>
      <Table headers={['Tên', 'Mô tả', 'Trạng thái', '']} loading={list.loading} error={list.error} empty={list.data?.length === 0}>
        {list.data?.map(c => (
          <tr key={c.id}>
            <Td className="font-medium">{c.name}</Td><Td>{c.description}</Td><Td>{c.active ? 'Hoạt động' : 'Ngừng'}</Td>
            <Td className="space-x-2 whitespace-nowrap">
              <Button size="sm" variant="outline" onClick={() => setForm({ id: c.id, name: c.name, description: c.description ?? '', active: c.active })}>Sửa</Button>
              <Button size="sm" variant="ghost" onClick={() => remove(c)}>Xóa</Button>
            </Td>
          </tr>))}
      </Table>
      <Modal isOpen={!!form} onClose={() => setForm(null)} title={form?.id ? 'Sửa nhóm hàng' : 'Thêm nhóm hàng'}
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Hủy</Button><Button onClick={save}>Lưu</Button></>}>
        {form && (
          <div className="grid gap-3">
            <Field label="Tên *"><input className={inputClass} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Mô tả"><input className={inputClass} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></Field>
            <label className="text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Đang hoạt động</label>
          </div>)}
      </Modal>
    </div>
  );
}
