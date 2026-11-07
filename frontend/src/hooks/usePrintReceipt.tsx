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
    // Khổ giấy = 80mm x chiều cao hóa đơn, lề 0: không còn trang A4 trắng, trình duyệt không chèn tiêu đề/ngày giờ.
    const pageStyle = document.createElement('style');
    const done = () => setJob(null);
    window.addEventListener('afterprint', done, { once: true });
    const t = window.setTimeout(() => { // đợi portal vẽ xong mới đo
      const root = document.querySelector<HTMLElement>('body > .receipt-print-root');
      const heightMm = root ? Math.ceil(root.scrollHeight * 25.4 / 96) + 2 : 200;
      pageStyle.textContent = `@page { size: 80mm ${heightMm}mm; margin: 0; }`;
      document.head.appendChild(pageStyle);
      window.print();
    }, 50);
    return () => { window.clearTimeout(t); window.removeEventListener('afterprint', done); pageStyle.remove(); };
  }, [job]);

  return {
    print: (order: SalesOrder, returns: SalesReturn[] = [], reprint = false) => setJob({ order, returns, reprint, printedAt: new Date().toISOString() }),
    portal: job ? createPortal(<div className="receipt-print-root"><ReceiptView {...job} /></div>, document.body) : null,
  };
}
