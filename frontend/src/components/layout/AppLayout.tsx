import React, { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

/** Tên trang và "thư mục" hiện trên đường dẫn kiểu terminal ở thanh trên. */
const PAGES: Record<string, { title: string; section: string }> = {
  '/pos': { title: 'Bán tại quầy', section: 'ban-hang' },
  '/orders': { title: 'Đơn hàng & trả hàng', section: 'ban-hang' },
  '/shift': { title: 'Chốt ca thu ngân', section: 'ban-hang' },
  '/promotions': { title: 'Khuyến mãi', section: 'ban-hang' },
  '/products': { title: 'Sản phẩm & thẻ kho', section: 'kho' },
  '/categories': { title: 'Nhóm hàng', section: 'kho' },
  '/inventory': { title: 'Tồn kho theo lô', section: 'kho' },
  '/purchase-orders': { title: 'Phiếu nhập hàng', section: 'kho' },
  '/suppliers': { title: 'Nhà cung cấp', section: 'kho' },
  '/stats': { title: 'Thống kê bán hàng', section: 'quan-tri' },
  '/users': { title: 'Người dùng', section: 'quan-tri' },
  '/system': { title: 'Trạng thái hệ thống', section: 'quan-tri' },
};

export const AppLayout: React.FC = () => {
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const location = useLocation();
  const page = PAGES[location.pathname];

  return (
    <div className="flex min-h-screen bg-[var(--c-bg)] text-gray-900">
      <Sidebar isMobileOpen={isMobileOpen} onCloseMobile={() => setIsMobileOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col lg:pl-64">
        <Header
          onToggleMobileMenu={() => setIsMobileOpen(!isMobileOpen)}
          isMobileMenuOpen={isMobileOpen}
          pageTitle={page?.title ?? 'Hệ thống Quản lý Siêu thị'}
          section={page?.section}
        />
        <main className="mx-auto w-full max-w-[1600px] flex-1 p-4 sm:p-6 xl:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
