import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

/** "receipt": giấy cuộn 80mm, cao vừa nội dung. "a4": tờ A4 dọc, lề 12mm. */
export type PrintFormat = 'receipt' | 'a4';

type Job = { node: ReactNode; format: PrintFormat };

/**
 * In riêng một tài liệu: gắn nội dung vào thẳng <body> với lớp .receipt-print-root
 * (index.css ẩn mọi thứ khác khi in), đặt khổ giấy bằng @page, mở hộp thoại in, in xong thì gỡ ra.
 * Lề 0 (hóa đơn) hoặc lề riêng (A4) đặt trong @page nên trình duyệt không chèn tiêu đề/ngày giờ vào giấy.
 */
export function usePrintDocument() {
  const [job, setJob] = useState<Job | null>(null);

  useEffect(() => {
    if (!job) return;
    const pageStyle = document.createElement('style');
    const done = () => setJob(null);
    window.addEventListener('afterprint', done, { once: true });
    const t = window.setTimeout(() => { // đợi portal vẽ xong mới đo
      if (job.format === 'receipt') {
        const root = document.querySelector<HTMLElement>('body > .receipt-print-root');
        const heightMm = root ? Math.ceil(root.scrollHeight * 25.4 / 96) + 2 : 200;
        pageStyle.textContent = `@page { size: 80mm ${heightMm}mm; margin: 0; }`;
      } else {
        pageStyle.textContent = '@page { size: A4 portrait; margin: 12mm; }';
      }
      document.head.appendChild(pageStyle);
      window.print();
    }, 50);
    return () => { window.clearTimeout(t); window.removeEventListener('afterprint', done); pageStyle.remove(); };
  }, [job]);

  return {
    /** {@code render} nhận thời điểm in (ISO) để in lên giấy. */
    print: (render: (printedAt: string) => ReactNode, format: PrintFormat) => setJob({ node: render(new Date().toISOString()), format }),
    portal: job
      ? createPortal(<div className={`receipt-print-root ${job.format === 'a4' ? 'print-a4' : ''}`}>{job.node}</div>, document.body)
      : null,
  };
}
