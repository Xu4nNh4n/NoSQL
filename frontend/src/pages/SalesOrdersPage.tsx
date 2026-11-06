import { Pagination, useListPage } from '../components/ui/Pagination';
import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  Eye,
  RotateCcw,
  RefreshCw,
  DollarSign,
  Printer,
} from 'lucide-react';
import { api } from '../api/client';
import type {
  SalesOrder,
  ReturnOrderRequest,
  SalesOrderReturnResponse,
  OrderBatchAllocation,
} from '../types';
import { useAuth } from '../contexts/AuthContext';
import { normalizeOrder } from '../types';
import {
  formatVND,
  formatDateTime,
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
} from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { OrderReceipt } from '../components/OrderReceipt';
import { useToast } from '../components/ui/Toast';

function canReceiveReturnedItems(order: SalesOrder): boolean {
  return order.orderType === 'IN_STORE' && ['PAID', 'PARTIALLY_RETURNED'].includes(order.status)
    && ['PAID', 'PARTIALLY_REFUNDED', 'REFUND_PENDING'].includes(order.paymentStatus);
}

interface ReturnDraft {
  orderItemId: number;
  productName: string;
  purchasedQuantity: number;
  returnedQuantity: number;
  maxQty: number;
  quantity: number;
  restock: boolean;
  reason: string;
  batchId?: number;
  lots: OrderBatchAllocation[];
}

function returnLimit(item: ReturnDraft): number {
  const lot = item.lots.find((candidate) => candidate.batchId === item.batchId);
  return lot ? Math.min(item.maxQty, lot.quantity - lot.returnedQuantity) : item.maxQty;
}

function restockAllowed(item: ReturnDraft): boolean {
  const lots = item.batchId == null ? item.lots : item.lots.filter((lot) => lot.batchId === item.batchId);
  return lots.length > 0 && lots.every((lot) => lot.status === 'CONSUMED'
    && (!lot.expiryDate || new Date(`${lot.expiryDate}T23:59:59`).getTime() >= Date.now()));
}

const ORDER_FILTER_GROUPS = [
  { label: 'Trả hàng và hoàn tiền', options: [
    { value: 'RETURNED', label: 'Đơn đã trả hàng' },
    { value: 'REFUND_PENDING', label: 'Chờ hoàn tiền' },
  ] },
  { label: 'Bán tại quầy', options: [
    { value: 'POS_PAID', label: 'Đã bán tại quầy' },
  ] },
];

function matchesOrderFilter(order: SalesOrder, filter: string): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'POS_PAID') return order.orderType === 'IN_STORE' && order.status === 'PAID';
  if (filter === 'REFUND_PENDING') return order.paymentStatus === 'REFUND_PENDING';
  if (filter === 'RETURNED') return ['RETURNED', 'PARTIALLY_RETURNED'].includes(order.status);
  return order.status === filter;
}

export const SalesOrdersPage: React.FC = () => {
  const [orders, setOrders] = useState<SalesOrder[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [receiptCode, setReceiptCode] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);
  const { role } = useAuth();
  const canProcessReturns = role === 'ADMIN' || role === 'STAFF';

  // Detail Modal
  const [selectedOrder, setSelectedOrder] = useState<SalesOrder | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);
  const [returnReceipts, setReturnReceipts] = useState<SalesOrderReturnResponse[]>([]);
  const [returnsLoading, setReturnsLoading] = useState(false);
  const [returnsLoaded, setReturnsLoaded] = useState(false);

  const [isReturnOpen, setIsReturnOpen] = useState(false);
  const [returnSubmitting, setReturnSubmitting] = useState(false);
  const returnSubmitLock = useRef(false);
  const returnRequestKey = useRef('');
  const [returnItems, setReturnItems] = useState<ReturnDraft[]>([]);
  const refundSubmitLock = useRef(false);
  const [refundSubmitting, setRefundSubmitting] = useState(false);

  const { success, error, warning } = useToast();

  const fetchOrders = async () => {
    setIsLoading(true);
    try {
      const raw: any[] = [];
      for (let page = 0; ; page++) {
        const res = await api.get<any>(`/orders?page=${page}&size=100`);
        raw.push(...(Array.isArray(res) ? res : res.content));
        if (Array.isArray(res) || page + 1 >= res.totalPages) break;
      }
      setOrders(raw.map(normalizeOrder));
    } catch {
      error('Lỗi khi tải danh sách đơn hàng');
    } finally {
      setIsLoading(false);
    }
  };

  const loadReturnReceipts = async (orderId: number) => {
    setReturnsLoading(true);
    setReturnsLoaded(false);
    try {
      const receipts = await api.get<SalesOrderReturnResponse[]>(`/orders/${orderId}/returns`);
      setReturnReceipts(receipts);
      setReturnsLoaded(true);
    } catch (err: unknown) {
      setReturnReceipts([]);
      if (err instanceof Error) error(`Không tải được phiếu trả của đơn: ${err.message}`);
    } finally {
      setReturnsLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  // Actions on Orders
  const handleConfirmRefund = async (orderId: number) => {
    if (!canProcessReturns || refundSubmitLock.current) return;
    refundSubmitLock.current = true;
    setRefundSubmitting(true);
    try {
      const pendingReceipts = returnReceipts.filter((receipt) => receipt.refundStatus === 'REFUND_PENDING');
      if (pendingReceipts.length > 0) {
        const total = pendingReceipts.reduce((sum, receipt) => sum + receipt.refundAmount, 0);
        if (!window.confirm(`Chỉ xác nhận sau khi đã hoàn ${formatVND(total)} cho khách. Ghi nhận hoàn tiền cho ${pendingReceipts.length} phiếu của đơn #${orderId}?`)) return;

        let confirmedCount = 0;
        try {
          for (const receipt of pendingReceipts) {
            const reference = `POS-RETURN-REFUND-${receipt.returnId}`;
            const updated = await api.put<SalesOrderReturnResponse>(`/orders/returns/${receipt.returnId}/refund`, {
              reference,
              amount: receipt.refundAmount,
            });
            setReturnReceipts((current) => current.map((item) => item.returnId === updated.returnId ? updated : item));
            confirmedCount += 1;
          }
          success(`Đã xác nhận hoàn tiền cho ${confirmedCount} phiếu trả của đơn #${orderId}`);
          void fetchOrders();
        } catch (err: unknown) {
          if (err instanceof Error) {
            error(`Đã xác nhận ${confirmedCount}/${pendingReceipts.length} phiếu; các phiếu còn lại vẫn chờ hoàn. ${err.message}`);
          }
          void loadReturnReceipts(orderId);
          void fetchOrders();
        }
        return;
      }

      return;
    } finally {
      refundSubmitLock.current = false;
      setRefundSubmitting(false);
    }
  };

  const handleOpenReturnModal = async (order: SalesOrder) => {
    if (!canProcessReturns || returnSubmitLock.current) return;
    try {
      const latest = normalizeOrder(await api.get(`/orders/${order.id}`));
      if (!canReceiveReturnedItems(latest)) { warning('Hóa đơn này không còn đủ điều kiện nhận trả'); return; }
      const outstanding = latest.items.filter((item) => item.quantity > (item.returnedQuantity ?? 0));
      if (outstanding.length === 0) { warning('Đơn đã trả hết hàng'); return; }
      setSelectedOrder(latest);
      returnRequestKey.current = `return-${latest.id}-${crypto.randomUUID()}`;
      setReturnItems(outstanding.map((item) => {
        const lots = (item.batchAllocations ?? []).filter((lot) => lot.quantity > lot.returnedQuantity);
        const draft: ReturnDraft = {
          orderItemId: item.id,
          productName: item.productName,
          purchasedQuantity: item.quantity,
          returnedQuantity: item.returnedQuantity ?? 0,
          maxQty: item.quantity - (item.returnedQuantity ?? 0),
          quantity: 0,
          restock: false,
          reason: '',
          batchId: lots.length === 1 ? lots[0].batchId : undefined,
          lots,
        };
        return { ...draft, restock: restockAllowed(draft) };
      }));
      setIsDetailOpen(false);
      setIsReturnOpen(true);
    } catch (failure) {
      error(failure instanceof Error ? failure.message : 'Không tải được thông tin trả hàng');
    }
  };

  const handleLookupReceipt = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!receiptCode.trim() || lookupLoading) return;
    setLookupLoading(true);
    try {
      const order = normalizeOrder(await api.get(`/orders/code/${encodeURIComponent(receiptCode.trim())}`));
      if (order.orderType !== 'IN_STORE') { warning('Chỉ xử lý hóa đơn bán tại quầy POS'); return; }
      setSelectedOrder(order);
      setReturnReceipts([]);
      setReturnsLoaded(false);
      setIsDetailOpen(true);
      void loadReturnReceipts(order.id);
    } catch (failure) {
      error(failure instanceof Error ? failure.message : 'Không tra được hóa đơn gốc');
    } finally {
      setLookupLoading(false);
    }
  };

  const handleSubmitReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOrder || !canProcessReturns || returnSubmitLock.current) return;

    const itemsToReturn = returnItems.filter((item) => item.quantity > 0);
    if (itemsToReturn.length === 0) {
      warning('Nhập số lượng của ít nhất một món khách mang trả');
      return;
    }
    if (returnItems.some((item) => !Number.isInteger(item.quantity) || item.quantity < 0 || item.quantity > returnLimit(item))) {
      warning('Số lượng trả phải là số nguyên và không vượt lượng còn được trả của món/lô');
      return;
    }
    if (itemsToReturn.some((item) => item.lots.length > 1 && item.batchId == null && item.quantity !== item.maxQty)) {
      warning('Trả một phần món xuất từ nhiều lô: chọn lô ghi trên hàng thực tế');
      return;
    }
    if (itemsToReturn.some((item) => item.restock && !restockAllowed(item))) {
      warning('Lô không còn phù hợp để bán lại; kiểm tra và chọn hàng hỏng/không bán lại');
      return;
    }
    if (itemsToReturn.some((item) => !item.reason.trim() || item.reason.length > 255)) {
      warning('Mỗi mặt hàng cần lý do trả từ 1 đến 255 ký tự');
      return;
    }

    const payload: ReturnOrderRequest = {
      requestKey: returnRequestKey.current,
      items: itemsToReturn.map((i) => ({
        orderItemId: i.orderItemId,
        batchId: i.batchId,
        quantity: i.quantity,
        restock: i.restock,
        reason: i.reason.trim(),
      })),
    };

    let res: SalesOrderReturnResponse;
    returnSubmitLock.current = true;
    setReturnSubmitting(true);
    try {
      res = await api.put<SalesOrderReturnResponse>(`/orders/${selectedOrder.id}/return`, payload);
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
      return;
    } finally {
      returnSubmitLock.current = false;
      setReturnSubmitting(false);
    }

    success(`Đã nhận hàng trả, phiếu #${res.returnId}. ${res.refundStatus === 'NO_REFUND' ? 'Không phát sinh hoàn tiền.' : `Chờ hoàn tiền: ${formatVND(res.refundAmount)}`}`);
    setIsReturnOpen(false);
    setIsDetailOpen(true);
    void loadReturnReceipts(selectedOrder.id);
    void api.get(`/orders/${selectedOrder.id}`).then((raw) => setSelectedOrder(normalizeOrder(raw)))
      .catch(() => warning('Đã nhận trả hàng; hãy mở lại hóa đơn nếu chi tiết chưa cập nhật'));
    void fetchOrders();

  };

  const filteredOrders = orders.filter((o: SalesOrder) => {
    const matchesSearch =
      o.orderCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (o.customerPhone && o.customerPhone.includes(searchTerm)) ||
      (o.customerName && o.customerName.toLowerCase().includes(searchTerm.toLowerCase()));

    const matchesStatus = matchesOrderFilter(o, statusFilter);
    return matchesSearch && matchesStatus;
  });

  const pagination = useListPage(filteredOrders.sort((a, b) => b.id - a.id), JSON.stringify([searchTerm, statusFilter]));

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="page-heading">
        <div>
          <h2 className="text-base font-bold text-gray-900">Quản lý Đơn hàng & Trả hàng</h2>
          <p className="text-xs text-gray-500">
            Tra hóa đơn POS, nhận trả từng món và xác nhận hoàn tiền
          </p>
        </div>
        <Button
          size="sm"
          variant="outline"
          leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
          onClick={fetchOrders}
        >
          Tải lại
        </Button>
      </div>

      {/* Filter Bar */}
      <Card>
        <form onSubmit={handleLookupReceipt} className="mb-4 flex flex-col sm:flex-row sm:items-end gap-2 border-b border-gray-100 pb-4">
          <Input label="Mã hóa đơn gốc" placeholder="Nhập mã in trên hóa đơn, ví dụ 1001" required maxLength={50}
            value={receiptCode} onChange={(event) => setReceiptCode(event.target.value)}
            helperText="Tra chính xác toàn bộ lịch sử, không giới hạn các đơn đang tải." />
          <Button type="submit" size="sm" isLoading={lookupLoading} className="shrink-0">
            Tra hóa đơn
          </Button>
        </form>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="sm:col-span-2">
            <Input
              label="Tìm đơn hàng"
              placeholder="Tìm theo mã đơn hàng, số điện thoại hoặc tên khách hàng..."
              leftIcon={<Search className="w-4 h-4" />}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="order-filter" className="mb-1 block text-xs font-semibold text-gray-700">Lọc đơn hàng</label>
            <select id="order-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}
              className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
              <option value="ALL">Tất cả đơn hàng</option>
              {ORDER_FILTER_GROUPS.map((group) => (
                <optgroup key={group.label} label={group.label}>
                  {group.options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </optgroup>
              ))}
            </select>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-gray-500">
          <p aria-live="polite">{isLoading ? 'Đang tải đơn hàng…' : `Hiển thị ${filteredOrders.length} / ${orders.length} đơn đã tải`}</p>
          {(statusFilter !== 'ALL' || searchTerm !== '') && (
            <Button size="sm" variant="ghost" onClick={() => { setStatusFilter('ALL'); setSearchTerm(''); }}>Xóa bộ lọc</Button>
          )}
        </div>
      </Card>

      {/* Orders Table */}
      <Card>
        <div className="overflow-x-auto -mx-5 -my-5">
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead className="border-b border-gray-200 text-gray-600 font-medium">
              <tr>
                <th className="px-4 py-3">Mã Đơn</th>
                <th className="px-4 py-3">Thời gian</th>
                <th className="px-4 py-3">Khách hàng</th>
                <th className="px-4 py-3 text-right">Tổng tiền</th>
                <th className="px-4 py-3 text-center">Trạng thái đơn</th>
                <th className="px-4 py-3 text-center">Thanh toán</th>
                <th className="px-4 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {filteredOrders.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-10 text-gray-400">
                    {isLoading ? 'Đang tải danh sách đơn...' : 'Không có đơn hàng nào phù hợp'}
                  </td>
                </tr>
              ) : (
                pagination.rows.map((o: SalesOrder) => {
                  const statusObj = ORDER_STATUS_LABELS[o.status] || {
                    label: o.status,
                    color: 'text-gray-700',
                    bg: 'bg-gray-50 border-gray-200',
                  };
                  const payObj = PAYMENT_STATUS_LABELS[o.paymentStatus] || {
                    label: o.paymentStatus,
                    color: 'text-gray-700',
                    bg: 'bg-gray-50 border-gray-200',
                  };

                  return (
                    <tr key={o.id} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-4 py-3.5 font-mono font-medium text-blue-800">
                        {o.orderCode}
                      </td>
                      <td className="px-4 py-3.5 text-gray-500">{formatDateTime(o.createdAt)}</td>
                      <td className="px-4 py-3.5 text-gray-900 font-medium">
                        {o.customerName || 'Khách lẻ vãng lai'}
                      </td>
                      <td className="px-4 py-3.5 text-right font-bold text-gray-900">
                        {formatVND(o.finalAmount || o.totalAmount)}
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-medium border ${statusObj.bg} ${statusObj.color}`}
                        >
                          {statusObj.label}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-medium border ${payObj.bg} ${payObj.color}`}
                        >
                          {payObj.label}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => {
                              setSelectedOrder(o);
                              setReturnReceipts([]);
                              setReturnsLoaded(false);
                              setIsDetailOpen(true);
                              void loadReturnReceipts(o.id);
                            }}
                          >
                            <Eye className="w-3.5 h-3.5 mr-1" />
                            Xem
                          </Button>
                          {canProcessReturns && canReceiveReturnedItems(o) && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleOpenReturnModal(o)}
                              title="Chọn món và số lượng thực nhận từ khách"
                              className="text-amber-700 border-amber-300 hover:bg-amber-50"
                            >
                              <RotateCcw className="w-3 h-3 mr-1" />
                              Trả hàng
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Pagination {...pagination} />

      {/* Order Details Modal */}
      <Modal
        isOpen={isDetailOpen}
        onClose={() => setIsDetailOpen(false)}
        title={`Chi tiết đơn hàng: ${selectedOrder?.orderCode || ''}`}
        maxWidth="xl"
        footer={
          selectedOrder && (
            <div className="flex flex-wrap items-center gap-2">
              <Button size="sm" variant="outline" leftIcon={<Printer className="w-4 h-4" />}
                onClick={() => { setIsDetailOpen(false); setIsReceiptOpen(true); }}>In hóa đơn</Button>
              {canProcessReturns && canReceiveReturnedItems(selectedOrder) && (
                <Button size="sm" variant="outline" disabled={refundSubmitting}
                  leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
                  onClick={() => void handleOpenReturnModal(selectedOrder)}>Trả hàng</Button>
              )}
              {/* Confirm refunds recorded against return receipts. */}
              {canProcessReturns && returnsLoaded && (
                returnReceipts.some((receipt) => receipt.refundStatus === 'REFUND_PENDING')
              ) && (
                <Button
                  size="sm"
                  variant="primary"
                  className="bg-purple-700 hover:bg-purple-800 text-white"
                  leftIcon={<DollarSign className="w-4 h-4" />}
                  onClick={() => handleConfirmRefund(selectedOrder.id)}
                  isLoading={refundSubmitting}
                >
                  {`Xác nhận hoàn tiền (${returnReceipts.filter((receipt) => receipt.refundStatus === 'REFUND_PENDING').length} phiếu)`}
                </Button>
              )}

              <Button size="sm" variant="outline" onClick={() => setIsDetailOpen(false)}>
                Đóng
              </Button>
            </div>
          )
        }
      >
        {selectedOrder && (
          <div className="space-y-4 text-xs">
            {/* Meta summary */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3 bg-gray-50 rounded-xl">
              <div>
                <span className="text-gray-400 block">Thời gian tạo:</span>
                <span className="font-semibold text-gray-900">
                  {formatDateTime(selectedOrder.createdAt)}
                </span>
              </div>
              <div>
                <span className="text-gray-400 block">Thanh toán:</span>
                <span className="font-semibold text-gray-900">
                  {PAYMENT_METHOD_LABELS[selectedOrder.paymentMethod]}
                </span>
              </div>
              <div>
                <span className="text-gray-400 block">Khách hàng:</span>
                <span className="font-semibold text-gray-900">
                  {selectedOrder.customerName || 'Khách lẻ'}
                </span>
              </div>
            </div>

            {returnsLoading ? (
              <p className="text-gray-500">Đang tải phiếu trả hàng…</p>
            ) : returnReceipts.length > 0 && (
              <section className="rounded-xl border border-amber-200 bg-amber-50/60 p-3 space-y-2">
                <h3 className="font-semibold text-amber-950">Phiếu trả hàng và hoàn tiền</h3>
                {returnReceipts.map((receipt) => (
                  <div key={receipt.returnId} className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-lg bg-white p-3 border border-amber-100">
                    <div>
                      <p className="font-medium text-gray-900">Phiếu #{receipt.returnId} · {formatVND(receipt.refundAmount)}</p>
                      <p className="text-gray-500">Trạng thái: {receipt.refundStatus === 'REFUND_PENDING' ? 'Chờ hoàn tiền' : receipt.refundStatus === 'REFUNDED' ? 'Đã hoàn tiền' : receipt.refundStatus}</p>
                      <p className="text-gray-600">{receipt.items.map((item) => `${item.productName} × ${item.quantity}`).join(', ')}</p>
                    </div>
                  </div>
                ))}
              </section>
            )}

            {/* Line items table */}
            <div className="border border-gray-200 rounded-lg overflow-hidden">
              <table className="w-full text-left">
                <thead className="bg-gray-50 border-b border-gray-200 text-gray-500 font-semibold">
                  <tr>
                    <th className="p-2.5">Sản phẩm</th>
                    <th className="p-2.5">Mã SKU</th>
                    <th className="p-2.5 text-center">Đã mua</th>
                    <th className="p-2.5 text-center">Đã trả</th>
                    <th className="p-2.5 text-center">Còn được trả</th>
                    <th className="p-2.5 text-right">Đơn giá</th>
                    <th className="p-2.5 text-right">Thành tiền</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {selectedOrder.items.map((i) => (
                    <tr key={i.id}>
                      <td className="p-2.5 font-medium text-gray-900">{i.productName}
                        {i.promotionSnapshot && <p className="text-xs font-normal text-emerald-700">{i.promotionSnapshot.name} · −{i.promotionSnapshot.percent}%</p>}
                      </td>
                      <td className="p-2.5 font-mono text-gray-500">{i.skuCode}</td>
                      <td className="p-2.5 text-center font-bold">{i.quantity}</td>
                      <td className="p-2.5 text-center">{i.returnedQuantity ?? 0}</td>
                      <td className="p-2.5 text-center">{Math.max(0, i.quantity - (i.returnedQuantity ?? 0))}</td>
                      <td className="p-2.5 text-right text-gray-600">
                        {i.promotionSnapshot && <p className="text-xs line-through">{formatVND(i.originalUnitPrice)}</p>}
                        {formatVND(i.netUnitPrice)}
                      </td>
                      <td className="p-2.5 text-right font-bold text-gray-900">
                        {formatVND(i.totalPrice)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Total footer */}
            <div className="space-y-1 text-sm text-gray-600">
              <p className="flex justify-between"><span>Tiền hàng gốc:</span><span>{formatVND(selectedOrder.subtotal)}</span></p>
              <p className="flex justify-between"><span>Giảm giá:</span><span>{formatVND(selectedOrder.discountAmount)}</span></p>
            </div>
            <div className="p-3 bg-gray-50 rounded-xl flex justify-between items-center text-sm font-bold">
              <span>TỔNG TIỀN ĐƠN HÀNG:</span>
              <span className="text-blue-700 text-base">
                {formatVND(selectedOrder.finalAmount ?? selectedOrder.totalAmount)}
              </span>
            </div>
          </div>
        )}
      </Modal>

      {/* POS return receipt */}
      <Modal isOpen={isReceiptOpen} onClose={() => { setIsReceiptOpen(false); setIsDetailOpen(true); }}
        title={`In lại hóa đơn ${selectedOrder?.orderCode || ''}`} maxWidth="md"
        footer={<><Button variant="outline" size="sm" onClick={() => { setIsReceiptOpen(false); setIsDetailOpen(true); }}>Quay lại</Button>
          <Button size="sm" leftIcon={<Printer className="w-4 h-4" />} onClick={() => window.print()}>In / Lưu PDF</Button></>}>
        {selectedOrder && <OrderReceipt order={selectedOrder} reprint />}
      </Modal>

      <Modal
        isOpen={isReturnOpen}
        onClose={() => { if (!returnSubmitting) setIsReturnOpen(false); }}
        title={`Nhận trả hàng · Hóa đơn ${selectedOrder?.orderCode}`}
        maxWidth="lg"
        footer={
          <>
            <Button variant="outline" size="sm" disabled={returnSubmitting} onClick={() => setIsReturnOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" size="sm" type="submit" form="pos-return-form" isLoading={returnSubmitting}
              disabled={!returnItems.some((item) => item.quantity > 0)}>
              Xác nhận đã nhận hàng
            </Button>
          </>
        }
      >
        <form id="pos-return-form" onSubmit={handleSubmitReturn} className="space-y-4 text-xs">
          <p className="text-gray-500">
            Chỉ nhập số lượng khách thực sự mang đến; để 0 ở món không trả. Với hàng đã nhận, chọn{' '}
            <b className="text-blue-700">Bán lại</b> (cộng lại tồn có thể bán) hoặc{' '}
            <b className="text-rose-700">Hàng hỏng/không bán lại</b> (ghi nhận số lượng hỏng, không cộng vào tồn bán được).
            {' '}Tiền hoàn tính theo hóa đơn gốc sau giảm giá và chờ bạn xác nhận riêng, chưa hoàn ngay khi nhận hàng.
          </p>

          <div className="space-y-3">
            {returnItems.map((item, idx) => (
              <div
                key={item.orderItemId}
                className="p-3 border border-gray-200 rounded-xl space-y-2 bg-gray-50/40"
              >
                <div className="flex justify-between items-center">
                  <span className="font-semibold text-gray-900">{item.productName}</span>
                  <span className="text-gray-500">Đã mua {item.purchasedQuantity} · Đã trả {item.returnedQuantity}</span>
                </div>

                <Input id={`return-quantity-${item.orderItemId}`} label="Số lượng trả (0 = không trả)" type="number"
                  min={0} max={returnLimit(item)} step={1} value={item.quantity} disabled={returnSubmitting}
                  helperText={`Còn được trả: ${item.maxQty}; lô đã chọn còn tối đa ${returnLimit(item)}.`}
                  onChange={(event) => setReturnItems((current) => current.map((row, index) => index === idx
                    ? { ...row, quantity: Number(event.target.value) } : row))} />
                {item.lots.length > 1 && (
                  <Select id={`return-batch-${item.orderItemId}`} label="Lô ghi trên hàng khách mang trả"
                    value={item.batchId ?? ''} disabled={returnSubmitting || item.quantity === 0}
                    options={[
                      { value: '', label: 'Các lô gốc — chỉ dùng khi trả hết lượng còn lại của món' },
                      ...item.lots.map((lot) => ({ value: lot.batchId,
                        label: `Lô ${lot.batchCode} · còn ${lot.quantity - lot.returnedQuantity} · HSD ${lot.expiryDate ?? 'không có'}` })),
                    ]}
                    onChange={(event) => {
                      const batchId = event.target.value ? Number(event.target.value) : undefined;
                      setReturnItems((current) => current.map((row, index) => {
                        if (index !== idx) return row;
                        const next = { ...row, batchId };
                        return { ...next, quantity: Math.min(row.quantity, returnLimit(next)),
                          restock: row.restock && restockAllowed(next) };
                      }));
                    }} />
                )}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">

                  <Select
                    label="Quy trình xử lý"
                    id={`return-condition-${item.orderItemId}`}
                    disabled={returnSubmitting || item.quantity === 0}
                    value={item.restock ? 'RESTOCK' : 'DAMAGED'}
                    onChange={(e) => {
                      const restock = e.target.value === 'RESTOCK';
                      setReturnItems((current) => current.map((row, index) => index === idx ? { ...row, restock } : row));
                    }}
                    options={[
                      ...(restockAllowed(item) ? [{ value: 'RESTOCK', label: 'Bán lại' }] : []),
                      { value: 'DAMAGED', label: 'Hỏng/không bán lại' },
                    ]}
                  />

                  <Input
                    label="Lý do trả"
                    id={`return-reason-${item.orderItemId}`}
                    disabled={returnSubmitting || item.quantity === 0}
                    required={item.quantity > 0}
                    maxLength={255}
                    value={item.reason}
                    onChange={(e) => {
                      const reason = e.target.value;
                      setReturnItems((current) => current.map((row, index) => index === idx ? { ...row, reason } : row));
                    }}
                  />
                </div>
                {item.lots.length === 0 && <p className="text-amber-800">Hóa đơn cũ thiếu nguồn gốc lô: chỉ ghi nhận hàng không bán lại.</p>}
              </div>
            ))}
          </div>
        </form>
      </Modal>
    </div>
  );
};
