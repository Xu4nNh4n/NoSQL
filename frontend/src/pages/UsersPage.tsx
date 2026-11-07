import { useState } from 'react';
import { api, errorMessage, qs } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useLoad } from '../hooks/useLoad';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';
import { Field, inputClass, ServerPager, Table, Td, Toolbar } from '../components/ui/Table';
import { formatDateTime } from '../utils/formatters';
import type { LoginEvent, Page, RoleName, RoleOption, User } from '../types';

type Form = { id?: string; username: string; password: string; fullName: string; email: string; phone: string; role: RoleName; active: boolean };

export function UsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const [keyword, setKeyword] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<Form | null>(null);
  const [history, setHistory] = useState<User | null>(null);
  const roles = useLoad(() => api.get<RoleOption[]>('/roles'), []);
  const list = useLoad(() => api.get<Page<User>>(`/users${qs({ keyword, role, page, size: 20 })}`), [keyword, role, page]);

  const save = async () => {
    if (!form) return;
    try {
      const common = { fullName: form.fullName, email: form.email || null, phone: form.phone || null, role: form.role, active: form.active };
      if (form.id) await api.put(`/users/${form.id}`, { ...common, newPassword: form.password || null });
      else await api.post('/users', { ...common, username: form.username, password: form.password });
      toast.success('Đã lưu tài khoản');
      setForm(null);
      void list.reload();
    } catch (e) { toast.error(errorMessage(e)); }
  };
  const lock = async (u: User) => {
    if (!confirm(`Khóa tài khoản ${u.username}? Mọi phiên đăng nhập sẽ bị thu hồi.`)) return;
    try { await api.delete(`/users/${u.id}`); void list.reload(); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div>
      <Toolbar>
        <Field label="Tìm"><input className={inputClass} value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} /></Field>
        <Field label="Vai trò"><select className={inputClass} value={role} onChange={e => { setRole(e.target.value); setPage(0); }}>
          <option value="">Tất cả</option>{roles.data?.map(r => <option key={r.name} value={r.name}>{r.name}</option>)}</select></Field>
        <Button onClick={() => setForm({ username: '', password: '', fullName: '', email: '', phone: '', role: 'STAFF', active: true })}>Thêm tài khoản</Button>
      </Toolbar>
      <Table headers={['Tên đăng nhập', 'Họ tên', 'Email', 'SĐT', 'Vai trò', 'Trạng thái', '']} loading={list.loading} error={list.error} empty={list.data?.content.length === 0}>
        {list.data?.content.map(u => (
          <tr key={u.id} className={u.active ? '' : 'opacity-50'}>
            <Td className="font-medium">{u.username}</Td><Td>{u.fullName}</Td><Td>{u.email}</Td><Td>{u.phone}</Td><Td>{u.role}</Td>
            <Td>{u.active ? 'Hoạt động' : 'Đã khóa'}</Td>
            <Td className="space-x-2 whitespace-nowrap">
              <Button size="sm" variant="outline" onClick={() => setForm({ id: u.id, username: u.username, password: '', fullName: u.fullName ?? '', email: u.email ?? '', phone: u.phone ?? '', role: u.role, active: u.active })}>Sửa</Button>
              <Button size="sm" variant="ghost" onClick={() => setHistory(u)}>Đăng nhập</Button>
              {u.active && u.id !== me?.id && <Button size="sm" variant="ghost" onClick={() => lock(u)}>Khóa</Button>}
            </Td>
          </tr>))}
      </Table>
      <ServerPager page={list.data} onPage={setPage} />

      <Modal isOpen={!!form} onClose={() => setForm(null)} title={form?.id ? 'Sửa tài khoản' : 'Thêm tài khoản'}
        footer={<><Button variant="outline" onClick={() => setForm(null)}>Hủy</Button><Button onClick={save}>Lưu</Button></>}>
        {form && (
          <div className="grid gap-3">
            {!form.id && <Field label="Tên đăng nhập *"><input className={inputClass} value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} /></Field>}
            <Field label={form.id ? 'Đặt lại mật khẩu (để trống nếu giữ nguyên)' : 'Mật khẩu * (8–72 byte)'}><input type="password" className={inputClass} value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></Field>
            <Field label="Họ tên *"><input className={inputClass} value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></Field>
            <Field label="Email"><input className={inputClass} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field>
            <Field label="Số điện thoại"><input className={inputClass} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></Field>
            <Field label="Vai trò"><select className={inputClass} value={form.role} onChange={e => setForm({ ...form, role: e.target.value as RoleName })}>
              {roles.data?.map(r => <option key={r.name} value={r.name}>{r.name} – {r.description}</option>)}</select></Field>
            {form.id && <label className="text-sm"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Đang hoạt động</label>}
          </div>)}
      </Modal>
      {history && <LoginHistory user={history} onClose={() => setHistory(null)} />}
    </div>
  );
}

export function LoginHistory({ user, onClose, self }: { user: User; onClose: () => void; self?: boolean }) {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const url = self ? `/auth/me/logins${qs({ month })}` : `/users/${user.id}/logins${qs({ month })}`;
  const rows = useLoad(() => api.get<LoginEvent[]>(url), [url]);
  return (
    <Modal isOpen onClose={onClose} title={`Lịch sử đăng nhập · ${user.username}`} maxWidth="xl">
      <Toolbar><Field label="Tháng (Cassandra, tự xóa sau 90 ngày)"><input type="month" className={inputClass} value={month} onChange={e => setMonth(e.target.value)} /></Field></Toolbar>
      <Table headers={['Thời điểm', 'Kết quả', 'IP', 'Trình duyệt']} loading={rows.loading} error={rows.error} empty={rows.data?.length === 0}>
        {rows.data?.map((r, i) => (
          <tr key={i}><Td>{formatDateTime(r.at)}</Td><Td className={r.success ? 'text-emerald-700' : 'text-rose-600'}>{r.success ? 'Thành công' : 'Thất bại'}</Td>
            <Td>{r.ip}</Td><Td className="max-w-xs truncate text-xs">{r.userAgent}</Td></tr>))}
      </Table>
    </Modal>
  );
}
