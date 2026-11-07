import { STORE_NAME } from '../sales/Receipt';
import { formatDate, formatDateTime, formatVND } from '../../utils/formatters';
import type { PurchaseOrder } from '../../types';

/** Phiếu nhập kho khổ A4. Màu cố định trắng/đen để bản in không phụ thuộc chế độ tối/sáng. */
export function PurchaseOrderPrint({ po, printedAt }: { po: PurchaseOrder; printedAt: string }) {
  const received = po.status === 'RECEIVED';
  const qty = (l: PurchaseOrder['lines'][number]) => (received ? l.receivedQty : l.orderedQty);
  const totalQty = po.lines.reduce((s, l) => s + qty(l), 0);
  const total = po.lines.reduce((s, l) => s + qty(l) * l.unitCost, 0);
  const cell = 'border border-[#000000] px-1.5 py-1 align-top';

  return (
    <div className="bg-[#ffffff] text-[11px] leading-snug text-[#000000]" style={{ fontFamily: 'Arial, Helvetica, sans-serif' }}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[13px] font-bold">{STORE_NAME}</p>
          <p>Bộ phận kho</p>
        </div>
        <div className="text-right">
          <p>Số: <b>{po.poCode}</b></p>
          <p>Ngày lập: {formatDate(po.createdAt)}</p>
        </div>
      </div>

      <div className="mt-4 text-center">
        <p className="text-[18px] font-bold">PHIẾU NHẬP KHO</p>
        <p className="mt-0.5">{received ? `Đã nhận hàng lúc ${formatDateTime(po.receivedAt)}` : '(PHIẾU NHÁP – CHƯA NHẬN HÀNG)'}</p>
      </div>

      <table className="mt-4 w-full">
        <tbody>
          <tr><td className="w-40 py-0.5">Nhà cung cấp:</td><td className="py-0.5 font-bold">{po.supplierName}</td></tr>
          <tr><td className="py-0.5">Ghi chú:</td><td className="py-0.5">{po.note || '—'}</td></tr>
          <tr><td className="py-0.5">Người lập phiếu:</td><td className="py-0.5">{po.createdBy} · {formatDateTime(po.createdAt)}</td></tr>
          <tr><td className="py-0.5">Người nhận hàng:</td><td className="py-0.5">{received ? `${po.receivedBy ?? '—'} · ${formatDateTime(po.receivedAt)}` : '—'}</td></tr>
        </tbody>
      </table>

      <table className="mt-3 w-full border-collapse">
        <thead>
          <tr className="text-center font-bold">
            <td className={cell}>STT</td><td className={cell}>Mã hàng</td><td className={cell}>Tên hàng</td>
            <td className={cell}>SL đặt</td><td className={cell}>SL nhận</td><td className={cell}>Đơn giá</td>
            <td className={cell}>Thành tiền</td><td className={cell}>Số lô</td><td className={cell}>NSX</td><td className={cell}>HSD</td>
          </tr>
        </thead>
        <tbody>
          {po.lines.map(l => (
            <tr key={l.lineNo} style={{ breakInside: 'avoid' }}>
              <td className={`${cell} text-center`}>{l.lineNo}</td>
              <td className={cell}>{l.sku}</td>
              <td className={cell}>{l.productName}</td>
              <td className={`${cell} text-right`}>{l.orderedQty}</td>
              <td className={`${cell} text-right`}>{received ? l.receivedQty : ''}</td>
              <td className={`${cell} text-right whitespace-nowrap`}>{formatVND(l.unitCost)}</td>
              <td className={`${cell} text-right whitespace-nowrap`}>{formatVND(qty(l) * l.unitCost)}</td>
              <td className={`${cell} whitespace-nowrap`}>{l.batchCode ?? ''}</td>
              <td className={`${cell} whitespace-nowrap`}>{l.mfgDate ? formatDate(l.mfgDate) : ''}</td>
              <td className={`${cell} whitespace-nowrap`}>{l.expiryDate ? formatDate(l.expiryDate) : ''}</td>
            </tr>
          ))}
          <tr className="font-bold">
            <td className={cell} colSpan={3}>Cộng ({po.lines.length} dòng)</td>
            <td className={`${cell} text-right`}>{po.lines.reduce((s, l) => s + l.orderedQty, 0)}</td>
            <td className={`${cell} text-right`}>{received ? totalQty : ''}</td>
            <td className={cell} />
            <td className={`${cell} text-right whitespace-nowrap`}>{formatVND(total)}</td>
            <td className={cell} colSpan={3} />
          </tr>
        </tbody>
      </table>
      {!received && <p className="mt-1 text-[10px]">Phiếu nháp: thành tiền tính theo số lượng đặt.</p>}

      <div className="mt-8 grid grid-cols-3 text-center" style={{ breakInside: 'avoid' }}>
        {['Người lập phiếu', 'Người giao hàng', 'Thủ kho'].map(r => (
          <div key={r}>
            <p className="font-bold">{r}</p>
            <p className="text-[10px]">(Ký, ghi rõ họ tên)</p>
            <div className="h-16" />
          </div>
        ))}
      </div>
      <p className="mt-6 text-right text-[10px]">In lúc {formatDateTime(printedAt)}</p>
    </div>
  );
}
