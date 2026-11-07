import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  Package, Boxes, ShoppingCart, Receipt, UserCheck, Truck, Tags, Wallet, FolderTree,
  ClipboardList, BarChart3, Server, ArrowDownUp,
} from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import type { RoleName } from '../../types';

interface SidebarProps {
  isMobileOpen: boolean;
  onCloseMobile: () => void;
}

type NavItem = { to: string; label: string; icon: React.ComponentType<{ className?: string }>; roles: RoleName[] };

const NAV: { title: string; items: NavItem[] }[] = [
  {
    title: 'Bán hàng',
    items: [
      { to: '/pos', label: 'Bán tại quầy', icon: ShoppingCart, roles: ['ADMIN', 'STAFF'] },
      { to: '/orders', label: 'Đơn hàng & trả hàng', icon: Receipt, roles: ['ADMIN', 'STAFF'] },
      { to: '/shift', label: 'Chốt ca', icon: Wallet, roles: ['ADMIN', 'STAFF'] },
      { to: '/promotions', label: 'Khuyến mãi', icon: Tags, roles: ['ADMIN'] },
    ],
  },
  {
    title: 'Hàng hóa & kho',
    items: [
      { to: '/products', label: 'Sản phẩm & thẻ kho', icon: Package, roles: ['ADMIN', 'STAFF', 'WAREHOUSE'] },
      { to: '/categories', label: 'Nhóm hàng', icon: FolderTree, roles: ['ADMIN'] },
      { to: '/inventory', label: 'Tồn kho theo lô', icon: Boxes, roles: ['ADMIN', 'WAREHOUSE'] },
      { to: '/purchase-orders', label: 'Phiếu nhập', icon: ClipboardList, roles: ['ADMIN', 'WAREHOUSE'] },
      { to: '/suppliers', label: 'Nhà cung cấp', icon: Truck, roles: ['ADMIN', 'WAREHOUSE'] },
    ],
  },
  {
    title: 'Quản trị',
    items: [
      { to: '/stats', label: 'Thống kê', icon: BarChart3, roles: ['ADMIN'] },
      { to: '/users', label: 'Người dùng', icon: UserCheck, roles: ['ADMIN'] },
      { to: '/system', label: 'Trạng thái hệ thống', icon: Server, roles: ['ADMIN'] },
    ],
  },
];

export const Sidebar: React.FC<SidebarProps> = ({ isMobileOpen, onCloseMobile }) => {
  const { role } = useAuth();

  return (
    <>
      {isMobileOpen && (
        <button
          type="button"
          aria-label="Đóng điều hướng"
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={onCloseMobile}
        />
      )}

      <aside
        id="main-navigation"
        aria-label="Điều hướng chính"
        className={`fixed top-0 bottom-0 left-0 z-40 flex w-64 flex-col border-r border-gray-200 bg-white transition-transform duration-200 ease-in-out lg:translate-x-0 ${
          isMobileOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Thương hiệu: biểu tượng ⇅ + tên đậm + dòng phụ nhỏ */}
        <div className="px-5 pt-6 pb-4">
          <div className="flex items-center gap-2.5">
            <ArrowDownUp className="h-5 w-5 text-gray-900" strokeWidth={2.5} />
            <h1 className="text-lg font-bold tracking-tight text-gray-900">SuperMarket</h1>
          </div>
          <p className="mt-0.5 pl-[30px] text-[11px] text-gray-500">Bán hàng · Kho · NoSQL</p>
          <p className="tag-accent mt-4">4 CSDL · NoSQL edition</p>
        </div>

        <div className="flex-1 space-y-5 overflow-y-auto px-4 pb-4">
          {NAV.map(section => {
            const items = section.items.filter(item => !role || item.roles.includes(role));
            if (items.length === 0) return null;
            return (
              <div key={section.title}>
                <h4 className="eyebrow mb-2 px-1">{section.title}</h4>
                <nav className="space-y-1">
                  {items.map(item => {
                    const Icon = item.icon;
                    return (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        onClick={onCloseMobile}
                        aria-label={item.label}
                        className={({ isActive }) =>
                          `flex min-h-10 items-center gap-3 rounded-md border px-3 py-2 text-[13px] transition-colors ${
                            isActive
                              ? 'border-gray-300 bg-gray-50 font-semibold text-gray-900'
                              : 'border-transparent text-gray-600 hover:border-gray-200 hover:text-gray-900'
                          }`
                        }
                      >
                        {({ isActive }) => (
                          <>
                            <Icon className={`h-4 w-4 shrink-0 ${isActive ? 'text-blue-600' : 'text-gray-400'}`} />
                            <span className="truncate">{item.label}</span>
                          </>
                        )}
                      </NavLink>
                    );
                  })}
                </nav>
              </div>
            );
          })}
        </div>

        <div className="border-t border-gray-200 px-5 py-3">
          <div className="seg-stripe mb-2" />
          <p className="text-[11px] text-gray-500">MongoDB · Cassandra · Neo4j · Redis</p>
        </div>
      </aside>
    </>
  );
};
