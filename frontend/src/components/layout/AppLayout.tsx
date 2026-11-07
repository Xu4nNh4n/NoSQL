import React, { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

const TITLE_MAP: Record<string, string> = {
  '/products': 'Quản lý Sản phẩm',
  '/inventory': 'Quản lý Kho & Lô hàng FEFO',
  '/suppliers': 'Nhà cung cấp',
  '/pos': 'Thu ngân tại quầy (POS)',
  '/orders': 'Quản lý Đơn hàng & Trả hàng',
  '/reports': 'Báo cáo Dòng tiền & Big Data',
  '/users': 'Quản lý Nhân viên & Phân quyền',
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
