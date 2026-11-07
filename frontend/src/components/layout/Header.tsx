import { useState } from 'react';
import { Menu, ShoppingCart, LogOut, ChevronRight, KeyRound } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { ThemeToggle } from '../ui/ThemeToggle';
import { useToast } from '../ui/Toast';
import { ChangePasswordModal } from '../auth/ChangePasswordModal';

interface HeaderProps {
  onToggleMobileMenu: () => void;
  isMobileMenuOpen: boolean;
  pageTitle?: string;
  section?: string;
}

/** Thanh trên: đường dẫn kiểu terminal "~/supermarket $ <mục> › <trang>", nút đổi giao diện, người dùng. */
export const Header: React.FC<HeaderProps> = ({ onToggleMobileMenu, isMobileMenuOpen, pageTitle, section }) => {
  const { user, role, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [changingPassword, setChangingPassword] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };
  const afterPasswordChanged = async () => {
    setChangingPassword(false);
    toast.success('Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.');
    await handleLogout();
  };

  return (
    <>
    <header className="sticky top-0 z-30 flex min-h-16 items-center justify-between gap-3 border-b border-gray-200 bg-[var(--c-bg)]/95 px-4 backdrop-blur sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={onToggleMobileMenu}
          aria-label="Mở điều hướng"
          aria-expanded={isMobileMenuOpen}
          aria-controls="main-navigation"
          className="cursor-pointer rounded-md border border-gray-300 p-1.5 text-gray-600 hover:text-gray-900 lg:hidden"
        >
          <Menu className="h-5 w-5" />
        </button>
        <div className="flex min-w-0 items-center gap-2 text-[13px]">
          <span className="hidden shrink-0 text-gray-500 md:inline">~/supermarket $ {section ?? (role ?? '').toLowerCase()}</span>
          <ChevronRight className="hidden h-3.5 w-3.5 shrink-0 text-gray-400 md:inline" />
          <h2 className="truncate font-semibold text-gray-900">{pageTitle || 'Hệ thống Quản lý Siêu thị'}</h2>
        </div>
      </div>

      <div className="flex items-center gap-2">
        {role === 'STAFF' && (
          <button
            type="button"
            onClick={() => navigate('/pos')}
            className="hidden h-9 cursor-pointer items-center gap-2 rounded-md bg-accent px-3 text-xs font-semibold text-accent-fg hover:bg-[var(--a-hover)] md:inline-flex"
          >
            <ShoppingCart className="h-4 w-4" /> Mở quầy POS
          </button>
        )}
        <ThemeToggle />

        <div className="ml-1 flex items-center gap-2.5 border-l border-gray-200 pl-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-md border border-gray-300 text-xs font-bold text-gray-900">
            {(user?.username ?? '?').charAt(0).toUpperCase()}
          </div>
          <div className="hidden text-left sm:block">
            <p className="text-xs font-semibold leading-tight text-gray-900">{user?.fullName || user?.username || 'Nhân viên'}</p>
            <p className="text-[11px] text-gray-500">{user?.role || 'STAFF'}</p>
          </div>
          <button
            type="button"
            onClick={() => setChangingPassword(true)}
            title="Đổi mật khẩu"
            aria-label="Đổi mật khẩu"
            className="ml-1 cursor-pointer rounded-md p-1.5 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900"
          >
            <KeyRound className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={handleLogout}
            title="Đăng xuất"
            aria-label="Đăng xuất tài khoản"
            className="ml-1 cursor-pointer rounded-md p-1.5 text-gray-500 transition-colors hover:bg-rose-50 hover:text-rose-600"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </header>
    {/* Ngoài <header>: header có backdrop-blur nên phần tử fixed bên trong bị giới hạn trong header */}
    {changingPassword && <ChangePasswordModal onClose={() => setChangingPassword(false)} onChanged={() => void afterPasswordChanged()} />}
    </>
  );
};
