import { useState } from 'react';
import { Button } from './Button';

export function useListPage<T>(rows: T[], filterKey: string) {
  const [selection, setSelection] = useState({ page: 1, size: 10, key: filterKey });
  const totalPages = Math.max(1, Math.ceil(rows.length / selection.size));
  const page = Math.min(selection.key === filterKey ? selection.page : 1, totalPages);
  return {
    rows: rows.slice((page - 1) * selection.size, page * selection.size),
    page, totalPages, total: rows.length, pageSize: selection.size,
    onPageChange: (next: number) => setSelection({ ...selection, page: Math.max(1, Math.min(next, totalPages)), key: filterKey }),
    onPageSizeChange: (size: number) => setSelection({ page: 1, size, key: filterKey }),
  };
}

interface PaginationProps {
  page: number;
  totalPages: number;
  total: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}

export function Pagination({ page, totalPages, total, pageSize, onPageChange, onPageSizeChange }: PaginationProps) {
  const start = Math.max(1, Math.min(page - 2, totalPages - 4));
  const pages = Array.from({ length: Math.min(5, totalPages) }, (_, i) => start + i);
  return <nav aria-label="Phân trang danh sách" className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-gray-200 pt-4 text-sm">
    <p className="text-gray-600" aria-live="polite">{total ? (page - 1) * pageSize + 1 : 0}–{Math.min(page * pageSize, total)} / {total} bản ghi</p>
    <div className="flex flex-wrap items-center gap-2">
      <label className="flex items-center gap-2 text-gray-600">Mỗi trang
        <select className="min-h-10 rounded-md border border-gray-300 bg-white px-2" value={pageSize} onChange={e => onPageSizeChange(Number(e.target.value))}>
          {[10, 20, 50].map(size => <option key={size} value={size}>{size}</option>)}
        </select>
      </label>
      <Button size="sm" variant="outline" disabled={page === 1} onClick={() => onPageChange(1)} aria-label="Trang đầu">«</Button>
      <Button size="sm" variant="outline" disabled={page === 1} onClick={() => onPageChange(page - 1)}>Trước</Button>
      {pages.map(number => <Button key={number} size="sm" variant={number === page ? 'primary' : 'outline'} aria-current={number === page ? 'page' : undefined} aria-label={`Trang ${number}`} onClick={() => onPageChange(number)}>{number}</Button>)}
      <Button size="sm" variant="outline" disabled={page === totalPages} onClick={() => onPageChange(page + 1)}>Sau</Button>
      <Button size="sm" variant="outline" disabled={page === totalPages} onClick={() => onPageChange(totalPages)} aria-label="Trang cuối">»</Button>
    </div>
  </nav>;
}
