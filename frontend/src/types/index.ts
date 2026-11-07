// ==========================================
// AUTHENTICATION & USERS
// ==========================================
export type RoleName = 'ADMIN' | 'STAFF' | 'WAREHOUSE';

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
  damagedQuantity?: number;
  unit: string;
  imageUrl?: string;
  active: boolean;
  isActive?: boolean;
  categoryName?: string;
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
      categoryName: '',
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
    damagedQuantity: raw.damagedQuantity ?? 0,
    unit: raw.unit || 'Cái',
    imageUrl: raw.imageUrl,
    active: raw.active ?? raw.isActive ?? true,
    isActive: raw.isActive ?? raw.active ?? true,
    categoryName: raw.categoryName ?? raw.category?.categoryName ?? '',
  };
}

export interface ProductRequest {
  skuCode: string;
  productName: string;
  price: number;
  stockQuantity: number;
  reorderPoint: number;
  imageUrl?: string;
  categoryName: string;
  isActive: boolean;
}

export interface StockMovement {
  movementId: number;
  productId: number;
  orderId?: number;
  movementType: 'OPENING' | 'RECEIPT' | 'SALE' | 'RETURN' | 'RETURN_DAMAGED' | 'ADJUSTMENT';
  quantity: number;
  note?: string;
  createdAt: string;
  performedBy?: string;
}

// ==========================================
// INVENTORY BATCHES (FEFO)
// ==========================================
export interface InventoryBatch {
  id: number;
  productId: number;
  productName?: string;
  skuCode?: string;
  supplierName?: string;
  batchCode: string;
  receivedQuantity: number;
  remainingQuantity: number;
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

export interface Supplier {
  supplierId: number;
  supplierName: string;
  contactName?: string;
  phone?: string;
  address?: string;
  isActive: boolean;
}

export interface BatchAdjustmentRequest {
  adjustmentQuantity: number;
  reason: string;
}

// ==========================================
// SALES ORDERS & POS
// ==========================================
export type OrderType = 'IN_STORE';
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER';
export type OrderStatus =
  | 'PAID'
  | 'PARTIALLY_RETURNED'
  | 'RETURNED';

export type PaymentStatus =
  | 'PAID'
  | 'REFUND_PENDING'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export interface OrderBatchAllocation {
  batchId: number;
  batchCode: string;
  expiryDate?: string;
  quantity: number;
  returnedQuantity: number;
  status: string;
}

export interface SalesOrderItem {
  id: number;
  orderItemId?: number;
  productId: number;
  productName: string;
  skuCode: string;
  quantity: number;
  unitPrice: number;
  originalUnitPrice: number;
  netUnitPrice: number;
  grossSubtotal: number;
  discountAmount: number;
  promotionSnapshot?: PromotionSnapshot | null;
  totalPrice: number;
  batchId?: number;
  batchCode?: string;
  returnedQuantity?: number;
  batchAllocations?: OrderBatchAllocation[];
}

export interface SalesOrder {
  id: number;
  orderId?: number;
  orderCode: string;
  customerName?: string;
  customerPhone?: string;
  cashierName?: string;
  staffName?: string;
  orderType: OrderType;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  subtotal?: number;
  discountAmount?: number;
  finalAmount: number;
  note?: string;
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
    customerName: raw.customerName,
    customerPhone: raw.customerPhone,
    cashierName: raw.staffName ?? raw.cashierName ?? 'Thu ngân',
    staffName: raw.staffName,
    orderType: raw.orderType || 'IN_STORE',
    status: raw.status || 'PAID',
    paymentMethod: raw.paymentMethod || 'CASH',
    paymentStatus: raw.paymentStatus || 'PAID',
    totalAmount: total,
    subtotal: raw.subtotal ?? total,
    discountAmount: raw.discountAmount ?? 0,
    finalAmount: total,
    note: raw.note,
    createdAt: raw.createdAt || new Date().toISOString(),
    updatedAt: raw.updatedAt,
    items: rawItems.map((i: any) => ({
      id: i.id ?? i.orderItemId ?? 0,
      orderItemId: i.orderItemId ?? i.id ?? 0,
      productId: i.productId ?? i.product?.productId ?? i.product?.id ?? 0,
      productName: i.productName ?? i.product?.productName ?? i.product?.name ?? 'Sản phẩm',
      skuCode: i.skuCode ?? i.product?.skuCode ?? '',
      quantity: i.quantity ?? 1,
      returnedQuantity: i.returnedQuantity ?? 0,
      batchAllocations: i.batchAllocations ?? [],
      unitPrice: i.unitPrice ?? i.price ?? i.product?.price ?? 0,
      originalUnitPrice: i.originalUnitPrice ?? i.unitPrice ?? i.price ?? 0,
      netUnitPrice: i.netUnitPrice ?? i.unitPrice ?? i.price ?? 0,
      grossSubtotal: i.grossSubtotal ?? (i.unitPrice ?? i.price ?? 0) * (i.quantity ?? 1),
      discountAmount: i.discountAmount ?? 0,
      promotionSnapshot: i.promotionSnapshot,
      totalPrice: i.totalPrice ?? i.subTotal ?? (i.netUnitPrice ?? i.unitPrice ?? i.product?.price ?? 0) * (i.quantity ?? 1),
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
  quoteToken: string;
  paymentReference?: string;
  customerName?: string;
  customerPhone?: string;
  orderType: OrderType;
  paymentMethod: PaymentMethod;
  note?: string;
  items: CreateOrderItemRequest[];
}

export interface PromotionSnapshot {
  promotionId: number;
  name: string;
  percent: number;
  startsAt: string;
  endsAt: string;
}

export interface Promotion extends PromotionSnapshot {
  productId: number;
  productName: string;
  active: boolean;
}

export interface PosQuoteItem {
  productId: number;
  quantity: number;
  originalUnitPrice: number;
  netUnitPrice: number;
  grossSubtotal: number;
  discountAmount: number;
  subTotal: number;
  promotionSnapshot: PromotionSnapshot | null;
}

export interface PosQuote {
  quoteToken: string;
  quotedAt: string;
  timezone: string;
  subtotal: number;
  discountAmount: number;
  totalAmount: number;
  items: PosQuoteItem[];
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
  refundStatus: 'NO_REFUND' | 'REFUND_PENDING' | 'REFUNDED' | 'REJECTED';
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

export interface ApiErrorResponse {
  timestamp?: string;
  status: number;
  error?: string;
  message: string;
  path?: string;
}
