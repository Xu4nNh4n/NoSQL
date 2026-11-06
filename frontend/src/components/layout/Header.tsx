import { Menu, ShoppingCart, LogOut, ShieldCheck, User as UserIcon } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { Button } from '../ui/Button';

interface HeaderProps {
  onToggleMobileMenu: () => void;
  isMobileMenuOpen: boolean;
  pageTitle?: string;
}

export const Header: React.FC<HeaderProps> = ({ onToggleMobileMenu, isMobileMenuOpen, pageTitle }) => {
  const { user, role, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <header className="min-h-16 bg-white border-b border-gray-200 sticky top-0 z-30 flex items-center justify-between gap-3 px-4 sm:px-6">
      {/* Left: Hamburger & Page Title */}
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onToggleMobileMenu}
          aria-label="Mở điều hướng"
          aria-expanded={isMobileMenuOpen}
          aria-controls="main-navigation"
          className="p-1.5 rounded-lg text-gray-500 hover:text-gray-700 hover:bg-gray-100 lg:hidden cursor-pointer"
        >
          <Menu className="w-5 h-5" />
        </button>
        <div className="flex items-center gap-2">
          <h2 className="text-sm sm:text-base font-semibold text-gray-900 tracking-tight truncate">
            {pageTitle || 'Hệ thống Quản lý Siêu thị'}
          </h2>
        </div>
      </div>

      {/* Right: Actions & User Info */}
      <div className="flex items-center gap-3">
        {/* Fast POS Button */}
        {role === 'STAFF' && <Button
          size="sm"
          variant="outline"
          leftIcon={<ShoppingCart className="w-4 h-4 text-blue-600" />}
          onClick={() => navigate('/pos')}
          className="hidden md:inline-flex border-blue-300 text-blue-700 hover:bg-blue-50 text-xs"
        >
          Mở quầy POS
        </Button>}

        {/* User Card */}
        <div className="flex items-center gap-2.5 pl-3 border-l border-gray-200">
          <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-800 flex items-center justify-center font-semibold text-xs border border-blue-200">
            {user?.username ? user.username.charAt(0).toUpperCase() : <UserIcon className="w-4 h-4" />}
          </div>
          <div className="hidden sm:block text-left">
            <p className="text-xs font-semibold text-gray-900 leading-tight">
              {user?.fullName || user?.username || 'Nhân viên'}
            </p>
            <div className="flex items-center gap-1 text-[11px] text-gray-500">
              <ShieldCheck className="w-3 h-3 text-blue-600" />
              <span>{user?.role || 'STAFF'}</span>
            </div>
          </div>

          {/* Logout */}
          <button
            type="button"
            onClick={handleLogout}
            title="Đăng xuất"
            aria-label="Đăng xuất tài khoản"
            className="p-1.5 rounded-lg text-gray-400 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer ml-1"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
};
