// ==========================================
// AUTHENTICATION & USERS
// ==========================================
export type RoleName = 'ADMIN' | 'MANAGER' | 'STAFF' | 'WAREHOUSE' | 'CUSTOMER';

export interface User {
  id: number;
  username: string;
  fullName?: string;
  email?: string;
  phone?: string;
  role: RoleName;
  active: boolean;
  address?: string;
}

export interface AccountProfile {
  userId: number;
  username: string;
  fullName: string;
  email?: string;
  phone?: string;
  address?: string;
  role: RoleName;
}

export interface UserItem {
  userId: number;
  fullName: string;
  userName: string;
  email?: string;
  phone?: string;
  roleName: string;
  isActive: boolean;
  createAt?: string;
}

export interface RoleOption {
  roleId: number;
  roleName: string;
  description?: string;
}

export interface CreateUserForm {
  fullName: string;
  username: string;
  password: string;
  email?: string;
  phone?: string;
  roleId: number;
  isActive: boolean;
}

export interface UpdateUserForm {
  fullName: string;
  username: string;
  password?: string;
  email?: string;
  phone?: string;
  roleId: number;
  isActive: boolean;
}

export interface LoginResponse {
  userId: number;
  username: string;
  role: RoleName;
  message: string;
  accessToken: string;
  refreshToken: string;
}

export interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  message?: string;
}

// ==========================================
// CATEGORIES & SUPPLIERS
// ==========================================
export interface Category {
  id: number;
  categoryId?: number;
  categoryName: string;
  description?: string;
  active: boolean;
  isActive?: boolean;
}

export function normalizeCategory(raw: any): Category {
  if (!raw) return { id: 0, categoryName: '', active: true };
  const id = raw.id ?? raw.categoryId ?? 0;
  return {
    id,
    categoryId: id,
    categoryName: raw.categoryName || '',
    description: raw.description || '',
    active: raw.active ?? raw.isActive ?? true,
    isActive: raw.isActive ?? raw.active ?? true,
  };
}

export interface Supplier {
  id: number;
  supplierId?: number;
  supplierName: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  active: boolean;
  isActive?: boolean;
}

export function normalizeSupplier(raw: any): Supplier {
  if (!raw) return { id: 0, supplierName: '', active: true };
  const id = raw.id ?? raw.supplierId ?? 0;
  return {
    id,
    supplierId: id,
    supplierName: raw.supplierName || '',
    contactPerson: raw.contactPerson || '',
    email: raw.email ?? raw.supplierEmail ?? '',
    phone: raw.phone ?? raw.supplierPhone ?? '',
    address: raw.address ?? raw.supplierAddress ?? '',
    active: raw.active ?? raw.isActive ?? true,
    isActive: raw.isActive ?? raw.active ?? true,
  };
}

// ==========================================
// PRODUCTS & STOCK
// ==========================================
export interface Product {
  id: number;
  productId?: number;
  skuCode: string;
  name: string;
  productName?: string;
  description?: string;
  barcode?: string;
  costPrice: number;
  sellingPrice: number;
  price?: number;
  minStockLevel: number;
  maxStockLevel: number;
  totalQuantity: number;
  stockQuantity?: number;
  availableQuantity: number;
  reservedQuantity?: number;
  damagedQuantity?: number;
  unit: string;
  imageUrl?: string;
  active: boolean;
  isActive?: boolean;
  expiryDate?: string | null;
  categoryId: number;
  categoryName?: string;
  supplierId: number;
  supplierName?: string;
}

export function normalizeProduct(raw: any): Product {
  if (!raw) {
    return {
      id: 0,
      skuCode: '',
      name: '',
      costPrice: 0,
      sellingPrice: 0,
      minStockLevel: 0,
      maxStockLevel: 0,
      totalQuantity: 0,
      availableQuantity: 0,
      unit: 'Cái',
      active: true,
      categoryId: 0,
      supplierId: 0,
    };
  }
  const id = raw.id ?? raw.productId ?? 0;
  const name = raw.name ?? raw.productName ?? 'Sản phẩm';
  const price = raw.sellingPrice ?? raw.price ?? 0;
  const available = raw.availableQuantity ?? raw.stockQuantity ?? raw.totalQuantity ?? 0;
  const total = raw.totalQuantity ?? raw.stockQuantity ?? available;
  return {
    id,
    productId: id,
    skuCode: raw.skuCode || '',
    name,
    productName: name,
    description: raw.description || '',
    barcode: raw.barcode || raw.skuCode || '',
    costPrice: raw.costPrice ?? 0,
    sellingPrice: price,
    price,
    minStockLevel: raw.minStockLevel ?? raw.reorderPoint ?? 5,
    maxStockLevel: raw.maxStockLevel ?? 500,
    totalQuantity: total,
    stockQuantity: total,
    availableQuantity: available,
    reservedQuantity: raw.reservedQuantity ?? 0,
    damagedQuantity: raw.damagedQuantity ?? 0,
    unit: raw.unit || 'Cái',
    imageUrl: raw.imageUrl,
    expiryDate: raw.expiryDate,
    active: raw.active ?? raw.isActive ?? true,
    isActive: raw.isActive ?? raw.active ?? true,
    categoryId: raw.categoryId ?? raw.category?.categoryId ?? 0,
    categoryName: raw.categoryName ?? raw.category?.categoryName ?? '',
    supplierId: raw.supplierId ?? raw.supplier?.supplierId ?? 0,
    supplierName: raw.supplierName ?? raw.supplier?.supplierName ?? '',
  };
}

export interface ProductRequest {
  skuCode: string;
  productName: string;
  price: number;
  stockQuantity: number;
  reorderPoint: number;
  expiryDate?: string | null;
  imageUrl?: string;
  categoryId: number;
  supplierId: number;
  isActive: boolean;
}

export interface StockMovement {
  id: number;
  movementType: 'IN' | 'OUT' | 'ADJUST' | 'RESERVE' | 'RELEASE' | 'DAMAGED';
  quantity: number;
  balanceBefore: number;
  balanceAfter: number;
  batchCode?: string;
  referenceType?: string;
  referenceId?: number;
  note?: string;
  createdAt: string;
  createdBy?: string;
}

// ==========================================
// INVENTORY BATCHES (FEFO)
// ==========================================
export interface InventoryBatch {
  id: number;
  productId: number;
  productName?: string;
  skuCode?: string;
  supplierId: number;
  supplierName?: string;
  batchCode: string;
  receivedQuantity: number;
  remainingQuantity: number;
  reservedQuantity: number;
  damagedQuantity: number;
  availableQuantity: number;
  unitCost: number;
  manufacturingDate?: string;
  expiryDate?: string | null;
  status: 'ACTIVE' | 'EXPIRED' | 'DEPLETED' | 'QUARANTINED';
  createdAt?: string;
}

export interface InventoryBatchRequest {
  productId: number;
  supplierId: number;
  batchCode: string;
  quantity: number;
  unitCost: number;
  manufacturingDate?: string;
  expiryDate: string;
}

export interface BatchAdjustmentRequest {
  adjustmentQuantity: number;
  damagedQuantity?: number;
  reason: string;
}

// ==========================================
// SALES ORDERS & POS
// ==========================================
export type OrderType = 'IN_STORE' | 'ONLINE';
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'COD';
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'COD_PENDING'
  | 'PAID'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'RETURN_IN_TRANSIT'
  | 'PARTIALLY_RETURNED'
  | 'RETURNED';

export type PaymentStatus =
  | 'PENDING'
  | 'COD_PENDING'
  | 'PAID'
  | 'FAILED'
  | 'REFUND_PENDING'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'
  | 'CANCELLED';

export type DeliveryStatus =
  | 'NOT_REQUIRED'
  | 'PENDING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'RETURN_IN_TRANSIT'
  | 'RETURNED'
  | 'CANCELLED';

export interface SalesOrderItem {
  id: number;
  orderItemId?: number;
  productId: number;
  productName: string;
  skuCode: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  batchId?: number;
  batchCode?: string;
}

export interface SalesOrder {
  id: number;
  orderId?: number;
  orderCode: string;
  customerId?: number;
  customerName?: string;
  customerPhone?: string;
  cashierName?: string;
  staffName?: string;
  orderType: OrderType;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  deliveryStatus: DeliveryStatus;
  totalAmount: number;
  subtotal?: number;
  discountAmount?: number;
  finalAmount: number;
  note?: string;
  shippingAddress?: string;
  receiverName?: string;
  receiverPhone?: string;
  paymentExpiresAt?: string;
  items: SalesOrderItem[];
  createdAt: string;
  updatedAt?: string;
}

export function normalizeOrder(raw: any): SalesOrder {
  if (!raw) {
    return {
      id: 0,
      orderCode: '',
      cashierName: 'Thu ngân',
      orderType: 'IN_STORE',
      status: 'PAID',
      paymentMethod: 'CASH',
      paymentStatus: 'PAID',
      deliveryStatus: 'NOT_REQUIRED',
      totalAmount: 0,
      finalAmount: 0,
      items: [],
      createdAt: new Date().toISOString(),
    };
  }
  const id = raw.id ?? raw.orderId ?? 0;
  const total = raw.finalAmount ?? raw.totalAmount ?? raw.subtotal ?? 0;
  const rawItems = raw.items || [];
  return {
    id,
    orderId: id,
    orderCode: raw.orderCode || String(id),
    customerId: raw.customerId,
    customerName: raw.customerName,
    customerPhone: raw.customerPhone,
    cashierName: raw.staffName ?? raw.cashierName ?? 'Thu ngân',
    staffName: raw.staffName,
    orderType: raw.orderType || 'IN_STORE',
    status: raw.status || 'PAID',
    paymentMethod: raw.paymentMethod || 'CASH',
    paymentStatus: raw.paymentStatus || 'PAID',
    deliveryStatus: raw.deliveryStatus || 'NOT_REQUIRED',
    totalAmount: total,
    subtotal: raw.subtotal ?? total,
    discountAmount: raw.discountAmount ?? 0,
    finalAmount: total,
    note: raw.note,
    shippingAddress: raw.shippingAddress,
    receiverName: raw.receiverName,
    receiverPhone: raw.receiverPhone,
    paymentExpiresAt: raw.paymentExpiresAt,
    createdAt: raw.createdAt || new Date().toISOString(),
    updatedAt: raw.updatedAt,
    items: rawItems.map((i: any) => ({
      id: i.id ?? i.orderItemId ?? 0,
      orderItemId: i.orderItemId ?? i.id ?? 0,
      productId: i.productId ?? i.product?.productId ?? i.product?.id ?? 0,
      productName: i.productName ?? i.product?.productName ?? i.product?.name ?? 'Sản phẩm',
      skuCode: i.skuCode ?? i.product?.skuCode ?? '',
      quantity: i.quantity ?? 1,
      unitPrice: i.unitPrice ?? i.price ?? i.product?.price ?? 0,
      totalPrice: i.totalPrice ?? i.subTotal ?? (i.unitPrice ?? i.product?.price ?? 0) * (i.quantity ?? 1),
      batchId: i.batchId,
      batchCode: i.batchCode,
    })),
  };
}

export interface CreateOrderItemRequest {
  productId: number;
  quantity: number;
}

export interface CreateOrderRequest {
  customerId?: number;
  orderType: OrderType;
  paymentMethod: PaymentMethod;
  note?: string;
  shippingAddress?: string;
  receiverName?: string;
  receiverPhone?: string;
  items: CreateOrderItemRequest[];
}

export interface ReturnItemRequest {
  orderItemId: number;
  batchId?: number;
  quantity: number;
  restock: boolean;
  reason: string;
}

export interface ReturnOrderRequest {
  requestKey: string;
  items: ReturnItemRequest[];
}

export interface SalesOrderReturnResponse {
  returnId: number;
  orderId: number;
  orderCode: string;
  refundAmount: number;
  refundStatus: 'REFUND_PENDING' | 'REFUNDED' | 'REJECTED';
  returnedAt: string;
  items: Array<{
    orderItemId: number;
    productId: number;
    productName: string;
    quantity: number;
    restock: boolean;
    reason: string;
  }>;
}

// ==========================================
// CUSTOMERS
// ==========================================
export interface Customer {
  id: number;
  customerId?: number;
  name: string;
  fullName?: string;
  phone: string;
  email?: string;
  address?: string;
  active: boolean;
  isActive?: boolean;
}

export function normalizeCustomer(raw: any): Customer {
  if (!raw) return { id: 0, name: '', phone: '', active: true };
  const id = raw.id ?? raw.customerId ?? 0;
  const name = raw.name ?? raw.fullName ?? 'Khách hàng';
  return {
    id,
    customerId: id,
    name,
    fullName: name,
    phone: raw.phone || '',
    email: raw.email || '',
    address: raw.address || '',
    active: raw.active ?? raw.isActive ?? true,
    isActive: raw.isActive ?? raw.active ?? true,
  };
}

// ==========================================
// ANALYTICS & BIG DATA
// ==========================================
export interface CashFlowRow {
  event_date?: string;
  report_date?: string;
  collected_amount?: number;
  total_received?: number;
  refunded_amount?: number;
  total_refunded?: number;
  net_cash_amount?: number;
  net_revenue?: number;
}

export interface DrillReportMetadata {
  queryId?: string;
  queryState?: string;
  sql?: string;
  generatedAt?: string;
  profilesUrl?: string;
  requestElapsedMs?: number;
  reportKind?: string;
  dataset?: 'OPERATIONS' | 'SIMULATED';
  sourceNames?: string[];
  from?: string;
  to?: string;
  stockAsOf?: string;
  definition?: string;
  managementAction?: string;
  assumptions?: string[];
  rowLimit?: number;
  missingExportDates?: string[];
  dataSources?: { date: string; format: 'JSON' | 'PARQUET'; exportedAt?: string; reference?: string }[];
}

export interface CashFlowReport extends DrillReportMetadata {
  columns: string[];
  rows: CashFlowRow[];
}

export interface DashboardRow {
  event_date: string | number;
  collected_amount: number;
  refunded_amount: number;
  net_cash_amount: number;
  payment_count: number;
  low_stock_count: number;
  out_of_stock_count: number;
}

export interface DashboardReport {
  rows: DashboardRow[];
  queryId?: string;
}

export interface ReconciliationRow {
  event_date: string | number;
  mysql_order_count: number;
  mysql_order_subtotal: number;
  mysql_item_subtotal: number;
  item_subtotal_delta: number;
  mysql_payment_count: number;
  astra_payment_count: number;
  mysql_collected_amount: number;
  astra_collected_amount: number;
  collected_delta: number;
  mysql_refund_count: number;
  astra_refund_count: number;
  mysql_refunded_amount: number;
  astra_refunded_amount: number;
  refunded_delta: number;
  quality_status: 'PASS' | 'WARN' | 'FAIL' | string;
}

export interface ReconciliationReport extends DrillReportMetadata {
  columns: string[];
  rows: ReconciliationRow[];
}

export interface IntegrationStatus {
  status?: string;
  mysqlConnected?: boolean;
  mongodbPendingOutbox?: number;
  mongodbSentOutbox?: number;
  astraPendingOutbox?: number;
  astraSentOutbox?: number;
  drillStatus?: string;
  mongodb?: { pending: number; sent: number };
  astra?: { pending: number; sent: number };
}

export interface ApiErrorResponse {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
}
