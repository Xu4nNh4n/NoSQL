import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, Lock, User, AlertCircle } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';

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
      if (res.role === 'STAFF' || res.role === 'ADMIN') {
        navigate('/pos');
      } else if (res.role === 'WAREHOUSE') {
        navigate('/inventory');
      } else {
        navigate('/');
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Đăng nhập không thành công');
      }
    } finally {
      setIsLoading(false);
    }
  };

  // Tài khoản do backend tạo (AdminBootstrap, DemoDataSeeder). Mật khẩu không ghi ở đây: lấy APP_ADMIN_PASSWORD / APP_SEED_PASSWORD trong .env.
  const DEMO = [
    { username: 'admin', label: 'Quản trị (ADMIN)' },
    { username: 'thungan01', label: 'Thu ngân (STAFF)' },
    { username: 'thukho01', label: 'Thủ kho (WAREHOUSE)' },
  ];

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-white border border-gray-200 rounded-xl p-6 sm:p-8">
        {/* Brand */}
        <div className="text-center mb-8">
          <div className="inline-flex w-12 h-12 rounded-xl bg-blue-600 items-center justify-center text-white mb-3 shadow-sm">
            <Store className="w-6 h-6" />
          </div>
          <h1 className="text-xl font-bold text-gray-900">HỆ THỐNG SIÊU THỊ & POS</h1>
          <p className="text-xs text-gray-500 mt-1">Đăng nhập tài khoản để vào hệ thống điều hành</p>
        </div>

        {error && (
          <div className="mb-6 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="Tên đăng nhập"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            leftIcon={<User className="w-4 h-4" />}
            placeholder="Nhập tên đăng nhập"
            required
          />

          <Input
            label="Mật khẩu"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            leftIcon={<Lock className="w-4 h-4" />}
            placeholder="Nhập mật khẩu"
            required
          />

          <Button
            type="submit"
            className="w-full mt-2"
            size="lg"
            isLoading={isLoading}
          >
            Đăng nhập hệ thống
          </Button>
        </form>

        {/* Demo accounts */}
        <div className="mt-8 pt-6 border-t border-gray-100">
          <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2 text-center">
            Tài khoản demo (bấm để điền tên)
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
            {DEMO.map(d => (
              <button
                key={d.username}
                type="button"
                onClick={() => { setUsername(d.username); setPassword(''); }}
                className="p-2 text-left rounded-lg border border-gray-200 hover:border-blue-300 hover:bg-blue-50/50 transition-colors text-xs cursor-pointer"
              >
                <div className="font-semibold text-gray-800">{d.label}</div>
                <div className="text-[10px] text-gray-500 font-mono">{d.username}</div>
              </button>
            ))}
          </div>
          <p className="mt-2 text-[10px] text-gray-400 text-center">Mật khẩu: giá trị APP_ADMIN_PASSWORD trong file .env</p>
        </div>
      </div>
    </div>
  );
};
