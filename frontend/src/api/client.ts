import type { ApiErrorResponse, TokenResponse } from '../types';

const BASE_URL = '/api';
export const TOKEN_KEY = 'access_token';
export const REFRESH_KEY = 'refresh_token';
export const USER_KEY = 'auth_user';

export class ApiError extends Error {
  status: number;
  data: unknown;
  constructor(message: string, status: number, data: unknown) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

interface RequestOptions extends RequestInit {
  skipAuth?: boolean;
}

let refreshing: Promise<boolean> | null = null;

/** Làm mới phiên bằng refresh token (xoay vòng trên Redis). Nhiều request 401 cùng lúc chỉ gọi refresh một lần. */
async function refreshSession(): Promise<boolean> {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!refreshToken) return false;
  refreshing ??= (async () => {
    try {
      const res = await fetch(`${BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return false;
      const data = (await res.json()) as TokenResponse;
      localStorage.setItem(TOKEN_KEY, data.accessToken);
      if (data.refreshToken) localStorage.setItem(REFRESH_KEY, data.refreshToken);
      else localStorage.removeItem(REFRESH_KEY);
      localStorage.setItem(USER_KEY, JSON.stringify(data.user));
      return true;
    } catch {
      return false;
    } finally {
      setTimeout(() => (refreshing = null), 0);
    }
  })();
  return refreshing;
}

function endSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
  window.dispatchEvent(new Event('auth:unauthorized'));
}

export async function apiFetch<T>(endpoint: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const url = `${BASE_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
  const token = localStorage.getItem(TOKEN_KEY);
  const headers: Record<string, string> = {
    ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
    ...(options.headers as Record<string, string>),
  };
  if (token && !options.skipAuth) headers['Authorization'] = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(url, { ...options, headers });
  } catch {
    throw new ApiError('Không kết nối được máy chủ', 0, null);
  }

  if (response.status === 401 && !options.skipAuth) {
    // Access token 15 phút hết hạn: thử làm mới một lần. Yêu cầu ghi an toàn khi gửi lại vì có requestKey.
    if (!retried && (await refreshSession())) return apiFetch<T>(endpoint, options, true);
    endSession();
    throw new ApiError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 401, null);
  }
  if (response.status === 204) return undefined as T;
  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    let data: unknown = null;
    try {
      const err = (await response.json()) as ApiErrorResponse;
      data = err;
      message = err.message || err.error || message;
    } catch {
      message = response.statusText || message;
    }
    if (response.status === 503) message = `${message} (CSDL tạm thời không khả dụng)`;
    throw new ApiError(message, response.status, data);
  }
  const type = response.headers.get('Content-Type') || '';
  if (!type.includes('json')) return (await response.blob()) as T;
  return (await response.json()) as T;
}

export const api = {
  get: <T>(endpoint: string, options?: RequestOptions) => apiFetch<T>(endpoint, { method: 'GET', ...options }),
  post: <T>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    apiFetch<T>(endpoint, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body), ...options }),
  put: <T>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    apiFetch<T>(endpoint, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body), ...options }),
  delete: <T>(endpoint: string, options?: RequestOptions) => apiFetch<T>(endpoint, { method: 'DELETE', ...options }),
};

/** Ghép query string, bỏ tham số rỗng. */
export function qs(params: Record<string, string | number | boolean | undefined | null>): string {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  });
  const s = p.toString();
  return s ? `?${s}` : '';
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : 'Có lỗi xảy ra';
}

export function newRequestKey(): string {
  return crypto.randomUUID();
}
