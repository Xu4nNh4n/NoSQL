import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { ReceiptView } from '../components/sales/Receipt';
import type { SalesOrder, SalesReturn } from '../types';

type Job = { order: SalesOrder; returns: SalesReturn[]; reprint: boolean; printedAt: string };

/**
 * In riêng hóa đơn: gắn hóa đơn vào thẳng <body> với lớp .receipt-print-root
 * (index.css ẩn mọi thứ khác khi in), mở hộp thoại in, in xong thì gỡ ra.
 */
export function usePrintReceipt() {
  const [job, setJob] = useState<Job | null>(null);

  useEffect(() => {
    if (!job) return;
    const done = () => setJob(null);
    window.addEventListener('afterprint', done, { once: true });
    const t = window.setTimeout(() => window.print(), 50); // đợi portal vẽ xong
    return () => { window.clearTimeout(t); window.removeEventListener('afterprint', done); };
  }, [job]);

  return {
    print: (order: SalesOrder, returns: SalesReturn[] = [], reprint = false) => setJob({ order, returns, reprint, printedAt: new Date().toISOString() }),
    portal: job ? createPortal(<div className="receipt-print-root"><ReceiptView {...job} /></div>, document.body) : null,
  };
}
