import { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { api, errorMessage } from '../../api/client';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { Field, inputClass } from '../ui/Table';

const MIN = 8;
const MAX_BYTES = 72; // BCrypt chỉ dùng 72 byte đầu, backend từ chối mật khẩu dài hơn

/**
 * Đổi mật khẩu của chính mình (PUT /auth/password). Backend thu hồi mọi phiên sau khi đổi,
 * nên báo thành công xong thì gọi onChanged để đăng xuất và đăng nhập lại.
 */
export function ChangePasswordModal({ onClose, onChanged }: { onClose: () => void; onChanged: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [show, setShow] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const bytes = new TextEncoder().encode(next).length;
  const problems = [
    next.length > 0 && next.length < MIN && `Mật khẩu mới phải có ít nhất ${MIN} ký tự`,
    bytes > MAX_BYTES && `Mật khẩu mới tối đa ${MAX_BYTES} byte`,
    next.length > 0 && next === current && 'Mật khẩu mới phải khác mật khẩu hiện tại',
    confirm.length > 0 && confirm !== next && 'Xác nhận mật khẩu không khớp',
  ].filter(Boolean) as string[];
  const ready = current.length > 0 && next.length >= MIN && confirm === next && problems.length === 0;

  const submit = async () => {
    if (!ready) return;
    setSaving(true);
    setError(null);
    try {
      await api.put('/auth/password', { currentPassword: current, newPassword: next, confirmPassword: confirm });
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  const type = show ? 'text' : 'password';
  return (
    <Modal isOpen onClose={onClose} title="Đổi mật khẩu" maxWidth="md"
      footer={<><Button variant="outline" onClick={onClose}>Hủy</Button><Button disabled={!ready} isLoading={saving} onClick={submit}>Đổi mật khẩu</Button></>}>
      <form className="grid gap-3" onSubmit={e => { e.preventDefault(); void submit(); }}>
        <Field label="Mật khẩu hiện tại *">
          <input type={type} autoComplete="current-password" className={inputClass} value={current} onChange={e => setCurrent(e.target.value)} autoFocus />
        </Field>
        <Field label={`Mật khẩu mới * (tối thiểu ${MIN} ký tự)`}>
          <input type={type} autoComplete="new-password" className={inputClass} value={next} onChange={e => setNext(e.target.value)} />
        </Field>
        <Field label="Nhập lại mật khẩu mới *">
          <input type={type} autoComplete="new-password" className={inputClass} value={confirm} onChange={e => setConfirm(e.target.value)} />
        </Field>
        <label className="flex cursor-pointer items-center gap-2 text-xs text-gray-600">
          <input type="checkbox" checked={show} onChange={e => setShow(e.target.checked)} />
          {show ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />} Hiện mật khẩu
        </label>
        {problems.length > 0 && <ul className="space-y-0.5 text-xs text-amber-700">{problems.map(p => <li key={p}>• {p}</li>)}</ul>}
        {error && <p className="rounded-md border border-rose-200 bg-rose-50 p-2 text-xs text-rose-700">{error}</p>}
        <p className="text-xs text-gray-500">Đổi xong, mọi phiên đăng nhập của bạn (cả trên máy khác) bị đăng xuất; đăng nhập lại bằng mật khẩu mới.</p>
        <button type="submit" hidden />
      </form>
    </Modal>
  );
}
