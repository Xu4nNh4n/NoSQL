import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Package,
  Boxes,
  ShoppingCart,
  Receipt,
  BarChart3,
  Store,
  UserCheck,
  Truck,
  Tags,
  FileSpreadsheet,
} from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';

interface SidebarProps {
  isMobileOpen: boolean;
  onCloseMobile: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ isMobileOpen, onCloseMobile }) => {
  const { role } = useAuth();

  const navSections = [
    {
      title: 'TỔNG QUAN',
      items: [
        { to: '/admin', label: 'Bàn làm việc (Dashboard)', icon: LayoutDashboard, roles: ['ADMIN', 'MANAGER'] },
      ],
    },
    {
      title: 'BÁN HÀNG & THU NGÂN',
      items: [
        { to: '/pos', label: 'Thu ngân tại quầy (POS)', icon: ShoppingCart, roles: ['STAFF'] },
        { to: '/promotions', label: 'Khuyến mãi', icon: Tags, roles: ['ADMIN', 'MANAGER'] },
        { to: '/orders', label: 'Quản lý đơn & Trả hàng', icon: Receipt, roles: ['ADMIN', 'MANAGER', 'STAFF'] },
      ],
    },
    {
      title: 'QUẢN LÝ HÀNG HÓA',
      items: [
        { to: '/products', label: 'Sản phẩm & Giá bán', icon: Package, roles: ['ADMIN', 'MANAGER', 'STAFF', 'WAREHOUSE'] },
        { to: '/inventory', label: 'Kho & Lô hàng FEFO', icon: Boxes, roles: ['ADMIN', 'MANAGER', 'WAREHOUSE'] },
      ],
    },
    {
      title: 'ĐỐI TÁC & NHẬP HÀNG',
      items: [
        { to: '/suppliers', label: 'Nhà cung cấp', icon: Truck, roles: ['ADMIN', 'MANAGER', 'WAREHOUSE'] },
        { to: '/purchase-costs', label: 'Giá nhập theo lô', icon: FileSpreadsheet, roles: ['ADMIN', 'MANAGER', 'WAREHOUSE'] },
      ],
    },
    {
      title: 'BÁO CÁO & DỮ LIỆU',
      items: [
        { to: '/reports', label: 'Đối soát & Big Data', icon: BarChart3, roles: ['ADMIN', 'MANAGER'] },
      ],
    },
    {
      title: 'HỆ THỐNG & NHÂN SỰ',
      items: [
        { to: '/users', label: 'Quản lý nhân viên', icon: UserCheck, roles: ['ADMIN'] },
      ],
    },
  ];

  return (
    <>
      {/* Mobile backdrop */}
      {isMobileOpen && (
        <button
          type="button"
          aria-label="Đóng điều hướng"
          className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-2xs lg:hidden"
          onClick={onCloseMobile}
        />
      )}

      {/* Sidebar container */}
      <aside
        id="main-navigation"
        aria-label="Điều hướng chính"
        className={`fixed top-0 bottom-0 left-0 z-40 w-64 bg-white border-r border-gray-200/80 flex flex-col transition-transform duration-200 ease-in-out lg:translate-x-0 ${
          isMobileOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Brand header */}
        <div className="h-16 flex items-center gap-3 px-5 border-b border-gray-100 bg-white">
          <div className="w-9 h-9 rounded-lg bg-blue-600 flex items-center justify-center text-white shadow-xs">
            <Store className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-sm font-bold text-gray-900 tracking-tight leading-none">SUPERMARKET</h1>
            <p className="text-xs text-gray-500 mt-1">Bán hàng · Kho · Phân tích</p>
          </div>
        </div>

        {/* Navigation items */}
        <div className="flex-1 overflow-y-auto py-4 px-3 space-y-6">
          {navSections.map((section) => {
            const filteredItems = section.items.filter(
              (item) => !role || item.roles.includes(role)
            );
            if (filteredItems.length === 0) return null;

            return (
              <div key={section.title}>
                <h4 className="px-3 mb-2 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                  {section.title}
                </h4>
                <nav className="space-y-0.5">
                  {filteredItems.map((item) => {
                    const Icon = item.icon;
                    return (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        onClick={onCloseMobile}
                        className={({ isActive }) =>
                          `flex min-h-10 items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 ${
                            isActive
                              ? 'bg-slate-100 text-slate-950 font-semibold'
                              : 'text-gray-600 hover:text-gray-900 hover:bg-gray-50'
                          }`
                        }
                        aria-label={item.label}
                      >
                        <Icon className="w-4 h-4 shrink-0" />
                        <span>{item.label}</span>
                      </NavLink>
                    );
                  })}
                </nav>
              </div>
            );
          })}
        </div>

        {/* Footer info */}
        <div className="p-3 border-t border-gray-100 text-center bg-gray-50/50">
          <p className="text-xs text-gray-500">Quản lý siêu thị tại quầy</p>
        </div>
      </aside>
    </>
  );
};
