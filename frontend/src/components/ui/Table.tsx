import React from 'react';
import { Button } from './Button';
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

/** Phân trang theo server (PageResponse của backend, trang bắt đầu từ 0). */
export function ServerPager<T>({ page, onPage }: { page: Page<T> | null; onPage: (p: number) => void }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="mt-3 flex items-center justify-between text-sm text-gray-600">
      <span>Trang {page.page + 1}/{page.totalPages} · {page.totalElements} bản ghi</span>
      <div className="flex gap-2">
        <Button size="sm" variant="outline" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>Trước</Button>
        <Button size="sm" variant="outline" disabled={page.last} onClick={() => onPage(page.page + 1)}>Sau</Button>
      </div>
    </div>
  );
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
