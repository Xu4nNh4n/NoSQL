import type { RoleName } from '../types';

const HOME: Record<RoleName, string> = { ADMIN: '/stats', STAFF: '/pos', WAREHOUSE: '/inventory' };

/** Màn hình đầu tiên sau đăng nhập của mỗi vai trò. */
export const homeFor = (role: RoleName | null | undefined) => (role ? HOME[role] : '/login');
