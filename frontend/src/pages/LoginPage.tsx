import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowDownUp, Lock, User, AlertCircle } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { ThemeToggle } from '../components/ui/ThemeToggle';

// Tài khoản do backend tạo (AdminBootstrap, DemoDataSeeder). Mật khẩu không ghi ở đây: lấy APP_ADMIN_PASSWORD / APP_SEED_PASSWORD trong .env.
const DEMO = [
  { username: 'admin', label: 'Quản trị', role: 'ADMIN' },
  { username: 'thungan01', label: 'Thu ngân', role: 'STAFF' },
  { username: 'thukho01', label: 'Thủ kho', role: 'WAREHOUSE' },
];

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    try {
      const res = await login(username, password);
      navigate(res.role === 'WAREHOUSE' ? '/inventory' : res.role ? '/pos' : '/');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Đăng nhập không thành công');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-[var(--c-bg)] text-gray-900">
      <div className="flex items-center justify-between px-5 py-4 sm:px-8">
        <div className="flex items-center gap-2.5">
          <ArrowDownUp className="h-5 w-5" strokeWidth={2.5} />
          <span className="text-lg font-bold tracking-tight">SuperMarket</span>
          <span className="hidden text-[11px] text-gray-500 sm:inline">~/supermarket $ login</span>
        </div>
        <ThemeToggle />
      </div>

      <div className="flex flex-1 items-center justify-center px-4 pb-10">
        <div className="grid w-full max-w-5xl overflow-hidden rounded-lg border border-gray-200 bg-white lg:grid-cols-[1.1fr_1fr]">
          {/* Banner */}
          <div className="flex flex-col justify-between gap-8 border-b border-gray-200 p-7 sm:p-10 lg:border-r lg:border-b-0">
            <div>
              <p className="tag-accent">Đồ án NoSQL · 4 CSDL</p>
              <h1 className="mt-4 text-3xl leading-tight font-extrabold tracking-tight sm:text-4xl">
                Bán nhanh.<br />Kho chuẩn.
              </h1>
              <p className="mt-4 max-w-sm text-[13px] leading-relaxed text-gray-500">
                Bán tại quầy theo FEFO, khuyến mãi tự áp, trả hàng một bước.<br />
                Đơn trên MongoDB, sổ cái Cassandra, đồ thị Neo4j, cache Redis.
              </p>
            </div>
            <div>
              <div className="seg-stripe" />
              <div className="mt-4 grid grid-cols-2 gap-2 text-[11px] text-gray-500 sm:grid-cols-4">
                {['MongoDB', 'Cassandra', 'Neo4j', 'Redis'].map(db => (
                  <span key={db} className="rounded-md border border-gray-200 px-2 py-1.5 text-center">{db}</span>
                ))}
              </div>
            </div>
          </div>

          {/* Form */}
          <div className="p-7 sm:p-10">
            <p className="eyebrow">Đăng nhập</p>
            <h2 className="mt-1 text-xl font-bold">Vào hệ thống</h2>

            {error && (
              <div className="mt-5 flex items-center gap-2 rounded-md border border-rose-200 bg-rose-50 p-3 text-xs text-rose-700">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="mt-6 space-y-4">
              <Input label="Tên đăng nhập" value={username} onChange={e => setUsername(e.target.value)}
                leftIcon={<User className="h-4 w-4" />} placeholder="Nhập tên đăng nhập" required />
              <Input label="Mật khẩu" type="password" value={password} onChange={e => setPassword(e.target.value)}
                leftIcon={<Lock className="h-4 w-4" />} placeholder="Nhập mật khẩu" required />
              <Button type="submit" className="mt-2 w-full" size="lg" isLoading={isLoading}>Đăng nhập ↗</Button>
            </form>

            <div className="mt-8">
              <p className="eyebrow mb-2">Tài khoản demo · bấm để điền tên</p>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                {DEMO.map(d => (
                  <button key={d.username} type="button" onClick={() => { setUsername(d.username); setPassword(''); }}
                    className={`cursor-pointer rounded-md border p-2.5 text-left text-xs transition-colors ${
                      username === d.username ? 'border-gray-400 bg-gray-50' : 'border-gray-200 hover:border-gray-300'}`}>
                    <div className="font-semibold text-gray-900">{d.label}</div>
                    <div className="mt-0.5 text-[10px] text-gray-500">{d.username} · {d.role}</div>
                  </button>
                ))}
              </div>
              <p className="mt-2 text-[10px] text-gray-500">Mật khẩu: giá trị APP_ADMIN_PASSWORD trong file .env</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
