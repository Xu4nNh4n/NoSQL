import { ReceiptView } from '../components/sales/Receipt';
import { usePrintDocument } from './usePrintDocument';
import type { SalesOrder, SalesReturn } from '../types';

/** In hóa đơn bán hàng khổ 80mm (dùng chung cơ chế in của usePrintDocument). */
export function usePrintReceipt() {
  const doc = usePrintDocument();
  return {
    print: (order: SalesOrder, returns: SalesReturn[] = [], reprint = false) =>
      doc.print(at => <ReceiptView order={order} returns={returns} reprint={reprint} printedAt={at} />, 'receipt'),
    portal: doc.portal,
  };
}
