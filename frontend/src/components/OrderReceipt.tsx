import { createPortal } from 'react-dom';
import type { SalesOrder } from '../types';
import { formatDateTime, formatVND, PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS } from '../utils/formatters';

export const OrderReceipt = ({ order, reprint = false }: { order: SalesOrder; reprint?: boolean }) => {
  const receipt = <section className="space-y-3 p-3 font-mono text-xs text-black">
    <header className="border-b border-dashed border-gray-400 pb-3 text-center">
      <h2 className="text-sm font-bold">SIÊU THỊ TIỆN LỢI</h2><p className="font-semibold">HÓA ĐƠN BÁN LẺ</p>
      {reprint && <p>Bản in lại</p>}
      <p>Mã hóa đơn: {order.orderCode}</p><p>Ngày mua: {formatDateTime(order.createdAt)}</p>
      <p>Thu ngân: {order.staffName || order.cashierName || 'Chưa ghi nhận'}</p>
    </header>
    <div><p>Khách: {order.customerName || 'Khách vãng lai'}</p>
      {order.customerPhone && <p>Điện thoại: {order.customerPhone}</p>}</div>
    <table className="w-full text-[11px]"><thead className="border-y border-dashed border-gray-400"><tr>
      <th className="py-1 text-left">Mặt hàng</th><th className="px-1 text-center">SL</th><th className="text-right">Đơn giá</th><th className="pl-1 text-right">Thành tiền</th>
    </tr></thead><tbody>{order.items.map(item => <tr key={item.id} className="align-top">
      <td className="py-2 break-words">{item.productName}
        {item.promotionSnapshot && <p>{item.promotionSnapshot.name} −{item.promotionSnapshot.percent}% · Giá gốc {formatVND(item.originalUnitPrice)}</p>}
        {(item.returnedQuantity ?? 0) > 0 && <p>Đã trả: {item.returnedQuantity}</p>}</td>
      <td className="p-1 text-center">{item.quantity}</td><td className="py-2 text-right">{formatVND(item.netUnitPrice)}</td>
      <td className="py-2 pl-1 text-right">{formatVND(item.totalPrice)}</td>
    </tr>)}</tbody></table>
    <div className="space-y-1 border-t border-dashed border-gray-400 pt-2">
      <p className="flex justify-between gap-2"><span>Tiền hàng:</span><span>{formatVND(order.subtotal ?? order.totalAmount)}</span></p>
      <p className="flex justify-between gap-2"><span>Giảm giá:</span><span>{formatVND(order.discountAmount ?? 0)}</span></p>
      <p className="flex justify-between gap-2 font-bold"><span>TỔNG HÓA ĐƠN:</span><span>{formatVND(order.finalAmount ?? order.totalAmount)}</span></p>
      <p>Hình thức: {PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod}</p>
      <p>Thanh toán: {PAYMENT_STATUS_LABELS[order.paymentStatus]?.label || order.paymentStatus}</p>
    </div>
    {order.items.some(item => (item.returnedQuantity ?? 0) > 0) && <p>Tiền trên hóa đơn là tiền lúc mua; khoản hoàn được ghi nhận trên phiếu trả riêng.</p>}
    <footer className="border-t border-dashed border-gray-400 pt-3 text-center">Cảm ơn quý khách! Vui lòng giữ mã hóa đơn để tra cứu khi cần.</footer>
  </section>;
  return <><div className="rounded-lg border border-dashed border-gray-300">{receipt}</div>
    {createPortal(<div className="receipt-print-root">{receipt}</div>, document.body)}</>;
};
