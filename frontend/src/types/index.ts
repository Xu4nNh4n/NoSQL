// Kiểu dữ liệu khớp API backend bản NoSQL. ID là chuỗi (ObjectId của MongoDB), tiền là số VND nguyên.

export type RoleName = 'ADMIN' | 'WAREHOUSE' | 'STAFF';

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

// ---------- Tài khoản ----------
export interface User {
  id: string;
  username: string;
  fullName?: string;
  email?: string;
  phone?: string;
  role: RoleName;
  active: boolean;
  createdAt?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string | null;
  expiresIn: number;
  user: User;
}

export interface LoginEvent {
  at: string;
  success: boolean;
  ip?: string;
  userAgent?: string;
}

export interface RoleOption {
  name: RoleName;
  description: string;
}

// ---------- Danh mục ----------
export interface Category {
  id: string;
  name: string;
  description?: string;
  active: boolean;
}

export interface Supplier {
  id: string;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  active: boolean;
}

export type AttributeValue = string | number | boolean;

export interface Product {
  id: string;
  sku: string;
  name: string;
  price: number;
  categoryId: string;
  categoryName: string;
  supplierId?: string;
  reorderPoint: number;
  imageUrl?: string;
  active: boolean;
  attributes: Record<string, AttributeValue>;
  stockQuantity: number;
  damagedQuantity: number;
  createdAt?: string;
  version: number;
}

export interface PosProduct {
  id: string;
  sku: string;
  name: string;
  price: number;
  categoryId: string;
  categoryName: string;
  imageUrl?: string;
  stockQuantity: number;
}

// ---------- Kho ----------
export interface BatchAdjustment {
  id: string;
  quantity: number;
  reason: string;
  by: string;
  at: string;
}

export interface Batch {
  id: string;
  batchCode: string;
  productId: string;
  productSku: string;
  productName: string;
  supplierId?: string;
  supplierName?: string;
  purchaseOrderCode?: string;
  receivedQty: number;
  remainingQty: number;
  damagedQty: number;
  unitCost: number;
  mfgDate?: string;
  expiryDate?: string;
  receivedAt: string;
  receivedBy: string;
  adjustments: BatchAdjustment[];
}

export interface LowStockItem {
  productId: string;
  sku: string;
  name: string;
  reorderPoint: number;
  available: number;
  stockQuantity: number;
}

export interface StockMovement {
  id: string;
  productId: string;
  createdAt: string;
  type: 'RECEIPT' | 'SALE' | 'RETURN' | 'RETURN_DAMAGED' | 'ADJUSTMENT';
  quantity: number;
  batchCode: string;
  refType: string;
  refCode?: string;
  note?: string;
  performedBy: string;
}

export interface PurchaseOrderLine {
  lineNo: number;
  productId: string;
  sku: string;
  productName: string;
  orderedQty: number;
  unitCost: number;
  receivedQty: number;
  mfgDate?: string;
  expiryDate?: string;
  batchCode?: string;
}

export interface PurchaseOrder {
  id: string;
  poCode: string;
  supplierId: string;
  supplierName: string;
  status: 'DRAFT' | 'RECEIVED';
  note?: string;
  lines: PurchaseOrderLine[];
  createdBy: string;
  createdAt: string;
  receivedAt?: string;
  version: number;
}

// ---------- Khuyến mãi ----------
export type PromotionType = 'PERCENT' | 'FIXED' | 'BUY_X_GET_Y' | 'ORDER_THRESHOLD';

export interface Promotion {
  id: string;
  name: string;
  type: PromotionType;
  active: boolean;
  startsAt: string;
  endsAt: string;
  scope: { productIds: string[]; categoryIds: string[] };
  rule: {
    percent?: number;
    amount?: number;
    buyQty?: number;
    getQty?: number;
    giftProductId?: string;
    minTotal?: number;
    maxDiscount?: number;
  };
  version: number;
}

export interface PromotionSnapshot {
  promotionId: string;
  name: string;
  type: PromotionType;
  discount: number;
  freeUnits?: number;
}

export interface PricedLine {
  lineNo: number;
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  grossAmount: number;
  netAmount: number;
  gift: boolean;
  promotion?: PromotionSnapshot;
}

export interface PricedCart {
  lines: PricedLine[];
  orderPromotion?: PromotionSnapshot;
  grossTotal: number;
  discountTotal: number;
  total: number;
  warnings: string[];
  quoteHash: string;
}

// ---------- Bán hàng ----------
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER';
export type OrderStatus = 'PAID' | 'PARTIALLY_RETURNED' | 'RETURNED';

export interface PersonRef {
  id: string;
  name: string;
  phone?: string;
}

export interface OrderLine {
  lineNo: number;
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  grossAmount: number;
  netAmount: number;
  gift: boolean;
  promotion?: PromotionSnapshot;
  returnedQuantity: number;
  allocations: { batchId: string; batchCode: string; expiryDate?: string; quantity: number; returnedQuantity: number }[];
}

export interface OrderPayment {
  paymentId: string;
  type: 'PAYMENT' | 'REFUND';
  method: PaymentMethod;
  amount: number;
  reference?: string;
  returnCode?: string;
  at: string;
  by: string;
}

export interface SalesOrder {
  id: string;
  orderCode: string;
  cashier: PersonRef;
  orderedAt: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  transactionRef?: string;
  grossTotal: number;
  discountTotal: number;
  total: number;
  refundedTotal: number;
  orderPromotion?: PromotionSnapshot;
  items: OrderLine[];
  payments: OrderPayment[];
}

export interface LedgerPayment {
  paymentId: string;
  orderCode?: string;
  type: 'PAYMENT' | 'REFUND';
  method: PaymentMethod;
  amount: number;
  reference?: string;
  returnCode?: string;
  createdAt: string;
}

export interface SalesReturn {
  id: string;
  returnCode: string;
  orderCode: string;
  refundAmount: number;
  refundMethod: PaymentMethod;
  createdAt: string;
  items: { lineNo: number; name: string; quantity: number; restock: boolean; reason: string; refundAmount: number }[];
}

export interface HeldCart {
  holdId: string;
  note?: string;
  items: { productId: string; quantity: number }[];
  heldAt: string;
  expiresAt: string;
}

export interface TopSales {
  day: string;
  available: boolean;
  items: { productId: string; sku?: string; name: string; quantity: number }[];
}

export interface Suggestions {
  available: boolean;
  items: { productId: string; sku: string; name: string; times: number }[];
}

// ---------- Ca, đồ thị, thống kê, vận hành ----------
export interface ShiftClosing {
  id: string;
  cashierId: string;
  cashierName: string;
  day: string;
  cashIn: number;
  bankIn: number;
  cashRefund: number;
  bankRefund: number;
  expectedCash: number;
  countedCash: number;
  difference: number;
  note?: string;
  status: 'CLOSED' | 'REOPENED';
  closedAt: string;
}

export interface ShiftSummary {
  cashierId: string;
  day: string;
  cashIn: number;
  bankIn: number;
  cashRefund: number;
  bankRefund: number;
  expectedCash: number;
  paymentCount: number;
  refundCount: number;
  closing?: ShiftClosing;
}

export interface TraceResult {
  batchCode: string;
  productSku: string;
  productName: string;
  supplierName?: string;
  expiryDate?: string;
  receivedQty: number;
  remainingQty: number;
  soldQty: number;
  orders: { orderCode: string; orderedAt: string; quantity: number }[];
}

export interface SalesStats {
  from: string;
  to: string;
  totals: { orders: number; gross: number; discount: number; revenue: number; refunds: number; netRevenue: number };
  daily: { day: string; orders: number; gross: number; discount: number; revenue: number; refunds: number; netRevenue: number }[];
  byCategory: { categoryId: string; categoryName: string; quantity: number; revenue: number }[];
  topProducts: { productId: string; sku: string; name: string; quantity: number; revenue: number }[];
}

export interface DatastoreStatus {
  name: string;
  role: string;
  up: boolean;
  latencyMs: number;
  error?: string;
}

export interface SyncStatusView {
  pending: number;
  failed: number;
  oldestPendingAt?: string;
  recentFailures: { id: string; type: string; refId: string; attempts: number; lastError: string; createdAt: string }[];
}
