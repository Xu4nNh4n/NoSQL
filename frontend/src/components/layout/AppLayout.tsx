import React, { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

const TITLE_MAP: Record<string, string> = {
  '/pos': 'Bán tại quầy',
  '/orders': 'Đơn hàng & trả hàng',
  '/shift': 'Chốt ca thu ngân',
  '/promotions': 'Khuyến mãi',
  '/products': 'Sản phẩm & thẻ kho',
  '/categories': 'Nhóm hàng',
  '/inventory': 'Tồn kho theo lô',
  '/purchase-orders': 'Phiếu nhập hàng',
  '/suppliers': 'Nhà cung cấp',
  '/stats': 'Thống kê bán hàng',
  '/users': 'Người dùng',
  '/system': 'Trạng thái hệ thống',
};

export const AppLayout: React.FC = () => {
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const location = useLocation();

  const pageTitle = TITLE_MAP[location.pathname] || 'Hệ thống Quản lý Siêu thị';

  return (
    <div className="min-h-screen bg-slate-50 flex text-slate-900">
      {/* Sidebar */}
      <Sidebar isMobileOpen={isMobileOpen} onCloseMobile={() => setIsMobileOpen(false)} />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 lg:pl-64">
        <Header onToggleMobileMenu={() => setIsMobileOpen(!isMobileOpen)} isMobileMenuOpen={isMobileOpen} pageTitle={pageTitle} />
        <main className="flex-1 p-4 sm:p-6 xl:p-8 max-w-[1600px] w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
