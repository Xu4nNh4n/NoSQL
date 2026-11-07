import { formatDateTime, formatVND, PAYMENT_METHOD_LABELS } from '../../utils/formatters';
import type { SalesOrder, SalesReturn } from '../../types';

export const STORE_NAME = 'SIÊU THỊ & POS';

/** Nội dung hóa đơn khổ 80mm: dùng cho xem trước trên màn hình và bản in. */
export function ReceiptView({ order, returns = [], reprint = false, printedAt }: { order: SalesOrder; returns?: SalesReturn[]; reprint?: boolean; printedAt?: string }) {
  const ref = order.transactionRef ?? order.payments.find(p => p.type === 'PAYMENT')?.reference;
  const line = 'border-t border-dashed border-gray-400';
  return (
    <div className="mx-auto w-full max-w-[80mm] bg-white font-mono text-[12px] leading-snug text-black">
      <div className="text-center">
        <p className="text-sm font-bold">{STORE_NAME}</p>
        <p className="mt-1 font-bold">HÓA ĐƠN BÁN HÀNG</p>
        {reprint && <p className="mt-0.5 text-[11px] font-semibold">(BẢN IN LẠI)</p>}
      </div>
      <div className={`mt-2 pt-2 ${line}`}>
        <Row label="Số HĐ" value={order.orderCode} />
        <Row label="Ngày" value={formatDateTime(order.orderedAt)} />
        <Row label="Thu ngân" value={order.cashier.name} />
        <Row label="Khách" value={order.customer ? `${order.customer.name}${order.customer.phone ? ` · ${order.customer.phone}` : ''}` : 'Khách lẻ'} />
      </div>

      <div className={`mt-2 pt-2 ${line}`}>
        {order.items.map(l => (
          <div key={l.lineNo} className="mb-1.5">
            <p>{l.lineNo}. {l.name}{l.gift && !/quà tặng/i.test(l.name) ? ' (quà tặng)' : ''}</p>
            <div className="flex justify-between pl-3">
              <span>{l.quantity} x {formatVND(l.unitPrice)}</span>
              <span>{formatVND(l.netAmount)}</span>
            </div>
            {l.grossAmount !== l.netAmount && (
              <p className="pl-3 text-[11px]">KM {l.promotion?.name ?? ''}: −{formatVND(l.grossAmount - l.netAmount)}</p>
            )}
            {l.returnedQuantity > 0 && <p className="pl-3 text-[11px]">Đã trả {l.returnedQuantity}</p>}
          </div>
        ))}
      </div>

      <div className={`mt-1 space-y-0.5 pt-2 ${line}`}>
        <Row label="Tạm tính" value={formatVND(order.grossTotal)} />
        {order.discountTotal > 0 && <Row label="Khuyến mãi" value={`−${formatVND(order.discountTotal)}`} />}
        {order.orderPromotion && <p className="text-[11px]">({order.orderPromotion.name})</p>}
        <div className="flex justify-between text-sm font-bold"><span>TỔNG CỘNG</span><span>{formatVND(order.total)}</span></div>
        <Row label="Thanh toán" value={PAYMENT_METHOD_LABELS[order.paymentMethod]} />
        {order.paymentMethod === 'BANK_TRANSFER' && ref && <Row label="Mã GD" value={ref} />}
      </div>

      {returns.length > 0 && (
        <div className={`mt-2 pt-2 ${line}`}>
          <p className="font-bold">TRẢ HÀNG</p>
          {returns.map(r => (
            <div key={r.id} className="mt-1">
              <Row label={r.returnCode} value={`−${formatVND(r.refundAmount)}`} />
              <p className="pl-3 text-[11px]">{formatDateTime(r.createdAt)} · {PAYMENT_METHOD_LABELS[r.refundMethod]}</p>
              {r.items.map((i, k) => <p key={k} className="pl-3 text-[11px]">{i.name} x{i.quantity}</p>)}
            </div>
          ))}
          <div className="mt-1 flex justify-between font-bold"><span>Còn lại</span><span>{formatVND(order.total - order.refundedTotal)}</span></div>
        </div>
      )}

      <div className={`mt-2 pt-2 text-center text-[11px] ${line}`}>
        <p>Cảm ơn quý khách!</p>
        <p>Đổi trả trong 7 ngày kèm hóa đơn.</p>
        {reprint && printedAt && <p className="mt-1">In lại lúc {formatDateTime(printedAt)}</p>}
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return <div className="flex justify-between gap-2"><span>{label}</span><span className="text-right">{value}</span></div>;
}
