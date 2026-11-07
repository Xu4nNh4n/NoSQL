import { useState } from 'react';

/** Phân trang danh sách đã tải hết về trình duyệt. Đổi {@code resetKey} (ví dụ từ khóa tìm) thì về trang đầu. */
export function useClientPage<T>(rows: T[], size: number, resetKey = '') {
  const [state, setState] = useState({ page: 0, key: resetKey });
  const totalPages = Math.max(1, Math.ceil(rows.length / size));
  const page = Math.min(state.key === resetKey ? state.page : 0, totalPages - 1);
  return {
    rows: rows.slice(page * size, (page + 1) * size),
    pager: { page, totalPages, totalElements: rows.length, onPage: (p: number) => setState({ page: p, key: resetKey }) },
  };
}
