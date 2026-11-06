/**
 * Utility functions for Vietnamese currency, date-time, and status label formatting.
 */

export const vietnamDateKey = (date = new Date()) => new Date(date.getTime() + 7 * 3600000).toISOString().slice(0, 10);

export function formatVND(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || isNaN(amount)) {
    return '0 ₫';
  }
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return '--';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return isoString;
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date);
  } catch {
    return isoString;
  }
}

export function formatDate(isoString: string | number | null | undefined): string {
  if (!isoString) return '--';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return String(isoString);
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date);
  } catch {
    return String(isoString);
  }
}

export const ORDER_STATUS_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  PAID: { label: 'Đã thanh toán', color: 'text-blue-700', bg: 'bg-blue-50 border-blue-200' },
  PARTIALLY_RETURNED: { label: 'Trả một phần', color: 'text-purple-700', bg: 'bg-purple-50 border-purple-200' },
  RETURNED: { label: 'Đã trả hàng', color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' },
};

export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
};

export const PAYMENT_STATUS_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  PAID: { label: 'Đã thanh toán', color: 'text-blue-700', bg: 'bg-blue-50 border-blue-200' },
  REFUND_PENDING: { label: 'Chờ hoàn tiền', color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' },
  REFUNDED: { label: 'Đã hoàn tiền', color: 'text-slate-700', bg: 'bg-slate-100 border-slate-300' },
  PARTIALLY_REFUNDED: { label: 'Hoàn một phần', color: 'text-purple-700', bg: 'bg-purple-50 border-purple-200' },
};

export const BATCH_STATUS_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  ACTIVE: { label: 'Còn hạn', color: 'text-blue-700', bg: 'bg-blue-50 border-blue-200' },
  EXPIRED: { label: 'Hết hạn', color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' },
  DEPLETED: { label: 'Hết hàng', color: 'text-slate-600', bg: 'bg-slate-100 border-slate-200' },
  QUARANTINED: { label: 'Cách ly/Hỏng', color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' },
};
