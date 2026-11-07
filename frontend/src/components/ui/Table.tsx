import React from 'react';
import type { Page } from '../../types';

/** Bảng đơn giản: tiêu đề cột + nội dung. Trạng thái tải/trống/lỗi hiển thị trong thân bảng. */
export function Table({ headers, children, loading, error, empty }: {
  headers: React.ReactNode[];
  children: React.ReactNode;
  loading?: boolean;
  error?: string | null;
  empty?: boolean;
}) {
  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
      <table className="min-w-full text-sm">
        <thead className="bg-gray-50 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
          <tr>{headers.map((h, i) => <th key={i} className="px-3 py-2.5 whitespace-nowrap">{h}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {loading ? <Row span={headers.length}>Đang tải…</Row>
            : error ? <Row span={headers.length} className="text-rose-600">{error}</Row>
            : empty ? <Row span={headers.length}>Không có dữ liệu</Row>
            : children}
        </tbody>
      </table>
    </div>
  );
}

function Row({ span, children, className = 'text-gray-500' }: { span: number; children: React.ReactNode; className?: string }) {
  return <tr><td colSpan={span} className={`px-3 py-6 text-center ${className}`}>{children}</td></tr>;
}

export function Td({ children, className = '' }: { children?: React.ReactNode; className?: string }) {
  return <td className={`px-3 py-2.5 align-top ${className}`}>{children}</td>;
}

/** Các số trang cần hiện (trang bắt đầu từ 0): đầu, cuối, quanh trang hiện tại; khoảng trống thành "…". */
function pageItems(current: number, total: number): (number | 'gap')[] {
  const keep = new Set<number>([0, total - 1, current - 1, current, current + 1]);
  if (current <= 2) [1, 2, 3].forEach(p => keep.add(p));
  if (current >= total - 3) [total - 2, total - 3, total - 4].forEach(p => keep.add(p));
  const pages = [...keep].filter(p => p >= 0 && p < total).sort((a, b) => a - b);
  const items: (number | 'gap')[] = [];
  pages.forEach((p, i) => {
    if (i > 0 && p - pages[i - 1] > 1) items.push('gap');
    items.push(p);
  });
  return items;
}

/** Phân trang có số: ‹ 1 2 3 … n ›. {@code page} bắt đầu từ 0. Chỉ 1 trang thì ẩn. */
export function Pager({ page, totalPages, totalElements, onPage }: { page: number; totalPages: number; totalElements: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  const btn = 'min-w-8 h-8 rounded-md border px-2 text-sm disabled:opacity-40 disabled:cursor-not-allowed';
  return (
    <nav aria-label="Phân trang" className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm text-gray-600">
      <span>{totalElements} bản ghi · trang {page + 1}/{totalPages}</span>
      <div className="flex flex-wrap items-center gap-1">
        <button type="button" className={`${btn} border-gray-300 bg-white hover:bg-gray-50`} disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Trang trước">‹</button>
        {pageItems(page, totalPages).map((p, i) => p === 'gap'
          ? <span key={`gap-${i}`} className="px-1 text-gray-400">…</span>
          : <button key={p} type="button" aria-current={p === page ? 'page' : undefined} onClick={() => onPage(p)}
              className={`${btn} ${p === page ? 'border-blue-600 bg-blue-600 font-semibold text-white' : 'border-gray-300 bg-white hover:bg-gray-50'}`}>{p + 1}</button>)}
        <button type="button" className={`${btn} border-gray-300 bg-white hover:bg-gray-50`} disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)} aria-label="Trang sau">›</button>
      </div>
    </nav>
  );
}

/** Phân trang theo server (PageResponse của backend, trang bắt đầu từ 0). */
export function ServerPager<T>({ page, onPage }: { page: Page<T> | null; onPage: (p: number) => void }) {
  if (!page) return null;
  return <Pager page={page.page} totalPages={page.totalPages} totalElements={page.totalElements} onPage={onPage} />;
}


export function Tabs<K extends string>({ tabs, value, onChange }: { tabs: { key: K; label: string }[]; value: K; onChange: (k: K) => void }) {
  return (
    <div className="mb-4 flex flex-wrap gap-1 border-b border-gray-200">
      {tabs.map(t => (
        <button key={t.key} type="button" onClick={() => onChange(t.key)}
          className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${value === t.key ? 'border-blue-600 text-blue-700' : 'border-transparent text-gray-500 hover:text-gray-800'}`}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function Toolbar({ children }: { children: React.ReactNode }) {
  return <div className="mb-4 flex flex-wrap items-end gap-3">{children}</div>;
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block text-xs font-semibold text-gray-700">
      <span className="mb-1 block">{label}</span>
      {children}
    </label>
  );
}

export const inputClass = 'w-full min-h-10 rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:border-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-700';
