import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider } from './components/ui/Toast';
import { ErrorBoundary } from './components/ui/ErrorBoundary';
import { AppLayout } from './components/layout/AppLayout';
import type { RoleName } from './types';

import { LoginPage } from './pages/LoginPage';
import { PosPage } from './pages/PosPage';
import { OrdersPage } from './pages/OrdersPage';
import { ShiftPage } from './pages/ShiftPage';
import { CustomersPage } from './pages/CustomersPage';
import { ProductsPage } from './pages/ProductsPage';
import { CategoriesPage } from './pages/CategoriesPage';
import { SuppliersPage } from './pages/SuppliersPage';
import { PurchaseOrdersPage } from './pages/PurchaseOrdersPage';
import { InventoryPage } from './pages/InventoryPage';
import { PromotionsPage } from './pages/PromotionsPage';
import { UsersPage } from './pages/UsersPage';
import { StatsPage } from './pages/StatsPage';
import { SystemPage } from './pages/SystemPage';

const homeFor =(role: RoleName | null) => (role === 'WAREHOUSE' ? '/inventory' : role ? '/pos' : '/login');

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-gray-500">Đang khởi tạo phiên làm việc…</div>;
  }
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
};

const RoleRoute: React.FC<{ allowedRoles: RoleName[]; children: React.ReactNode }> = ({ allowedRoles, children }) => {
  const { role } = useAuth();
  if (role && !allowedRoles.includes(role)) return <Navigate to={homeFor(role)} replace />;
  return <>{children}</>;
};

const Home: React.FC = () => {
  const { role } = useAuth();
  return <Navigate to={homeFor(role)} replace />;
};

const ALL: RoleName[] = ['ADMIN', 'WAREHOUSE', 'STAFF'];
const routes: { path: string; roles: RoleName[]; element: React.ReactNode }[] = [
  { path: '/pos', roles: ['ADMIN', 'STAFF'], element: <PosPage /> },
  { path: '/orders', roles: ['ADMIN', 'STAFF'], element: <OrdersPage /> },
  { path: '/shift', roles: ['ADMIN', 'STAFF'], element: <ShiftPage /> },
  { path: '/customers', roles: ['ADMIN', 'STAFF'], element: <CustomersPage /> },
  { path: '/products', roles: ALL, element: <ProductsPage /> },
  { path: '/categories', roles: ['ADMIN'], element: <CategoriesPage /> },
  { path: '/suppliers', roles: ['ADMIN', 'WAREHOUSE'], element: <SuppliersPage /> },
  { path: '/purchase-orders', roles: ['ADMIN', 'WAREHOUSE'], element: <PurchaseOrdersPage /> },
  { path: '/inventory', roles: ['ADMIN', 'WAREHOUSE'], element: <InventoryPage /> },
  { path: '/promotions', roles: ['ADMIN'], element: <PromotionsPage /> },
  { path: '/users', roles: ['ADMIN'], element: <UsersPage /> },
  { path: '/stats', roles: ['ADMIN'], element: <StatsPage /> },
  { path: '/system', roles: ['ADMIN'], element: <SystemPage /> },
];

export const App: React.FC = () => (
  <ErrorBoundary>
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
              <Route path="/" element={<Home />} />
              {routes.map(r => (
                <Route key={r.path} path={r.path} element={<RoleRoute allowedRoles={r.roles}>{r.element}</RoleRoute>} />
              ))}
              <Route path="*" element={<Home />} />
            </Route>
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  </ErrorBoundary>
);

export default App;
