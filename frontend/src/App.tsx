import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { CartProvider } from './contexts/CartContext';
import { ToastProvider } from './components/ui/Toast';
import { ErrorBoundary } from './components/ui/ErrorBoundary';
import { AppLayout } from './components/layout/AppLayout';
import type { RoleName } from './types';

import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProductsPage } from './pages/ProductsPage';
import { InventoryPage } from './pages/InventoryPage';
import { SuppliersPage } from './pages/SuppliersPage';
import { PurchaseCostsPage } from './pages/PurchaseCostsPage';
import { PromotionsPage } from './pages/PromotionsPage';
import { PosPage } from './pages/PosPage';
import { SalesOrdersPage } from './pages/SalesOrdersPage';
import { ReportsPage } from './pages/ReportsPage';
import { UsersPage } from './pages/UsersPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#F8FAF9] flex items-center justify-center">
        <div className="flex items-center gap-2 text-sm text-gray-500 font-medium">
          <svg className="animate-spin h-5 w-5 text-blue-600" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Đang khởi tạo phiên làm việc...
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

const RoleRoute: React.FC<{ allowedRoles: RoleName[]; children: React.ReactNode }> = ({
  allowedRoles,
  children,
}) => {
  const { role } = useAuth();

  if (role && !allowedRoles.includes(role)) {
    const destination = role === 'STAFF' ? '/pos'
      : role === 'WAREHOUSE' ? '/inventory'
      : role === 'ADMIN' || role === 'MANAGER' ? '/admin'
      : '/login';
    return <Navigate to={destination} replace />;
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
            <ToastProvider>
              <Routes>
                <Route path="/" element={<Navigate to="/login" replace />} />
                <Route path="/shop" element={<Navigate to="/login" replace />} />
                <Route path="/login" element={<LoginPage />} />

                {/* 3. Không gian làm việc nội bộ (Admin Dashboard, Thu ngân POS, Quản lý kho, Nhân viên...)
                    Chỉ xuất hiện và cho phép truy cập khi nhân viên/quản trị viên đăng nhập hợp lệ */}
                <Route
                  element={
                    <ProtectedRoute>
                      <CartProvider>
                        <AppLayout />
                      </CartProvider>
                    </ProtectedRoute>
                  }
                >
                  <Route
                    path="/admin"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER']}>
                        <DashboardPage />
                      </RoleRoute>
                    }
                  />
                  <Route path="/dashboard" element={<Navigate to="/admin" replace />} />
                  <Route path="/suppliers" element={<RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'WAREHOUSE']}><SuppliersPage /></RoleRoute>} />
                  <Route path="/purchase-costs" element={<RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'WAREHOUSE']}><PurchaseCostsPage /></RoleRoute>} />
                  <Route path="/supplier-prices" element={<Navigate to="/purchase-costs" replace />} />
                  <Route path="/promotions" element={<RoleRoute allowedRoles={['ADMIN', 'MANAGER']}><PromotionsPage /></RoleRoute>} />
                  <Route
                    path="/pos"
                    element={
                      <RoleRoute allowedRoles={['STAFF']}>
                        <PosPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/products"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'STAFF', 'WAREHOUSE']}>
                        <ProductsPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/inventory"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'WAREHOUSE']}>
                        <InventoryPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/orders"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'STAFF']}>
                        <SalesOrdersPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/reports"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER']}>
                        <ReportsPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/users"
                    element={
                      <RoleRoute allowedRoles={['ADMIN']}>
                        <UsersPage />
                      </RoleRoute>
                    }
                  />
                </Route>

                <Route path="*" element={<Navigate to="/login" replace />} />
              </Routes>
            </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
};

export default App;
