import React, { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

/** Tên nhóm và tên trang hiện trên đường dẫn ở thanh trên; nhóm trùng với menu trái. */
const PAGES: Record<string, { title: string; section: string }> = {
  '/pos': { title: 'Bán tại quầy', section: 'Bán hàng' },
  '/orders': { title: 'Đơn hàng & trả hàng', section: 'Bán hàng' },
  '/shift': { title: 'Chốt ca thu ngân', section: 'Bán hàng' },
  '/promotions': { title: 'Khuyến mãi', section: 'Bán hàng' },
  '/products': { title: 'Sản phẩm & thẻ kho', section: 'Hàng hóa & kho' },
  '/categories': { title: 'Nhóm hàng', section: 'Hàng hóa & kho' },
  '/inventory': { title: 'Tồn kho theo lô', section: 'Hàng hóa & kho' },
  '/purchase-orders': { title: 'Phiếu nhập hàng', section: 'Hàng hóa & kho' },
  '/suppliers': { title: 'Nhà cung cấp', section: 'Hàng hóa & kho' },
  '/stats': { title: 'Thống kê bán hàng', section: 'Quản trị' },
  '/users': { title: 'Người dùng', section: 'Quản trị' },
  '/system': { title: 'Trạng thái hệ thống', section: 'Quản trị' },
  '/intro': { title: 'Giới thiệu 4 CSDL', section: 'Giới thiệu' },
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
          pageTitle={page?.title ?? 'Siêu thị Uy Tín'}
          section={page?.section}
        />
        <main className="mx-auto w-full max-w-[1600px] flex-1 p-4 sm:p-6 xl:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
