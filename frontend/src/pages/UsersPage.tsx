import { Pagination, useListPage } from '../components/ui/Pagination';
import React, { useState, useEffect } from 'react';
import {
  UserPlus,
  Search,
  RefreshCw,
  Edit2,
  Trash2,
  Lock,
  Unlock,
  ShieldCheck,
  ShieldAlert,
  Building,
  ShoppingBag,
  Package,
} from 'lucide-react';
import { api } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import type { UserItem, RoleOption, CreateUserForm, UpdateUserForm } from '../types';
import { formatDateTime } from '../utils/formatters';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';

const EMPLOYEE_ROLES = new Set(['ADMIN', 'MANAGER', 'STAFF', 'WAREHOUSE']);
type EmployeePage = { content: UserItem[]; totalPages: number };

async function loadEmployees(): Promise<UserItem[]> {
  const first = await api.get<EmployeePage | UserItem[]>('/users?employeesOnly=true&page=0&size=100');
  if (Array.isArray(first)) return first;
  const employees = [...first.content];
  for (let page = 1; page < first.totalPages; page++) {
    const next = await api.get<EmployeePage>(`/users?employeesOnly=true&page=${page}&size=100`);
    employees.push(...next.content);
  }
  return employees;
}

export const UsersPage: React.FC = () => {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<UserItem[]>([]);
  const [roles, setRoles] = useState<RoleOption[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Filters
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedRole, setSelectedRole] = useState('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');

  // Modal State
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserItem | null>(null);

  // Form states
  const [createForm, setCreateForm] = useState<CreateUserForm>({
    fullName: '',
    username: '',
    password: '',
    email: '',
    phone: '',
    roleId: 0,
    isActive: true,
  });

  const [editForm, setEditForm] = useState<UpdateUserForm>({
    fullName: '',
    username: '',
    password: '',
    email: '',
    phone: '',
    roleId: 0,
    isActive: true,
  });

  // Delete modal
  const [userToDelete, setUserToDelete] = useState<UserItem | null>(null);

  const { success, error, warning } = useToast();

  const fetchData = async () => {
    setIsLoading(true);
    try {
      const [usersRes, rolesRes] = await Promise.allSettled([
        loadEmployees(),
        api.get<RoleOption[]>('/roles'),
      ]);

      if (usersRes.status === 'fulfilled') {
        setUsers(usersRes.value.filter((user) => EMPLOYEE_ROLES.has(user.roleName?.toUpperCase() || '')));
      } else {
        error('Không thể tải danh sách nhân viên');
      }

      if (rolesRes.status === 'fulfilled') {
        const employeeRoles = rolesRes.value.filter((role) => EMPLOYEE_ROLES.has(role.roleName.toUpperCase()));
        setRoles(employeeRoles);
        const staffRole = employeeRoles.find((role) => role.roleName === 'STAFF') || employeeRoles[0];
        if (staffRole) setCreateForm((prev) => ({ ...prev, roleId: staffRole.roleId }));
      }
    } catch {
      error('Lỗi khi nạp dữ liệu nhân viên');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // Filtered users
  const filteredUsers = users.filter((u) => {
    const kw = searchKeyword.toLowerCase().trim();
    const matchKw =
      !kw ||
      u.fullName?.toLowerCase().includes(kw) ||
      u.userName?.toLowerCase().includes(kw) ||
      u.email?.toLowerCase().includes(kw) ||
      u.phone?.includes(kw);

    const matchRole =
      selectedRole === 'ALL' ||
      (u.roleName && u.roleName.toUpperCase() === selectedRole.toUpperCase());

    const matchStatus =
      selectedStatus === 'ALL' ||
      (selectedStatus === 'ACTIVE' && u.isActive) ||
      (selectedStatus === 'INACTIVE' && !u.isActive);

    return matchKw && matchRole && matchStatus;
  });

  const pagination = useListPage(filteredUsers.sort((a, b) => b.userId - a.userId), JSON.stringify([searchKeyword, selectedRole, selectedStatus]));

  // KPI calculations
  const totalEmployees = users.length;
  const activeCount = users.filter((u) => u.isActive).length;
  const inactiveCount = users.filter((u) => !u.isActive).length;
  const staffCount = users.filter((u) => u.roleName === 'STAFF').length;

  const handleOpenCreate = () => {
    const defaultRole = roles.find((r) => r.roleName === 'STAFF') || roles[0];
    if (!defaultRole) { warning('Chưa tải được vai trò nhân viên. Vui lòng làm mới.'); return; }
    setCreateForm({
      fullName: '',
      username: '',
      password: '',
      email: '',
      phone: '',
      roleId: defaultRole.roleId,
      isActive: true,
    });
    setIsCreateOpen(true);
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createForm.fullName.trim() || !createForm.username.trim() || !createForm.password.trim()) {
      warning('Vui lòng điền đầy đủ họ tên, tên đăng nhập và mật khẩu');
      return;
    }
    if (createForm.password.length < 6) {
      warning('Mật khẩu phải có tối thiểu 6 ký tự');
      return;
    }
    if (!roles.some((role) => role.roleId === createForm.roleId)) {
      warning('Vui lòng chọn vai trò nhân viên hợp lệ');
      return;
    }

    setIsSubmitting(true);
    try {
      await api.post('/users', createForm);
      success(`Đã tạo tài khoản nhân viên "${createForm.fullName}" thành công!`);
      setIsCreateOpen(false);
      fetchData();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOpenEdit = (user: UserItem) => {
    const matchedRole = roles.find((r) => r.roleName === user.roleName);
    if (!matchedRole) { warning('Chưa tải được vai trò của nhân viên. Vui lòng làm mới.'); return; }
    setEditingUser(user);
    setEditForm({
      fullName: user.fullName || '',
      username: user.userName || '',
      password: '', // leave empty if no change
      email: user.email || '',
      phone: user.phone || '',
      roleId: matchedRole.roleId,
      isActive: user.isActive,
    });
    setIsEditOpen(true);
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;
    if (!editForm.fullName.trim() || !editForm.username.trim()) {
      warning('Họ tên và tên đăng nhập không được để trống');
      return;
    }
    if (editForm.password && editForm.password.length < 6) {
      warning('Mật khẩu mới phải có tối thiểu 6 ký tự');
      return;
    }
    if (!roles.some((role) => role.roleId === editForm.roleId)) {
      warning('Vui lòng chọn vai trò nhân viên hợp lệ');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload: Partial<UpdateUserForm> = {
        fullName: editForm.fullName,
        username: editForm.username,
        email: editForm.email || undefined,
        phone: editForm.phone || undefined,
        roleId: editForm.roleId,
        isActive: editForm.isActive,
      };
      if (editForm.password && editForm.password.trim().length > 0) {
        payload.password = editForm.password.trim();
      }

      await api.put(`/users/${editingUser.userId}`, payload);
      success(`Đã cập nhật thông tin nhân viên "${editForm.fullName}"!`);
      setIsEditOpen(false);
      fetchData();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggleStatus = async (user: UserItem) => {
    if (currentUser?.id === user.userId) {
      warning('Không thể tự khóa tài khoản của chính bạn');
      return;
    }
    const newStatus = !user.isActive;
    const matchedRole = roles.find((r) => r.roleName === user.roleName);
    if (!matchedRole) { warning('Chưa tải được vai trò của nhân viên. Vui lòng làm mới.'); return; }
    try {
      await api.put(`/users/${user.userId}`, {
        fullName: user.fullName,
        username: user.userName,
        email: user.email,
        phone: user.phone,
        roleId: matchedRole.roleId,
        isActive: newStatus,
      });
      success(
        newStatus
          ? `Đã kích hoạt lại tài khoản "${user.userName}"`
          : `Đã tạm khóa tài khoản "${user.userName}"`
      );
      fetchData();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleDeleteUser = async () => {
    if (!userToDelete) return;
    if (currentUser?.id === userToDelete.userId) {
      warning('Không thể xóa tài khoản của chính bạn');
      setUserToDelete(null);
      return;
    }

    try {
      await api.delete(`/users/${userToDelete.userId}`);
      success(`Đã xóa tài khoản "${userToDelete.userName}" thành công`);
      setUserToDelete(null);
      fetchData();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  // Helper for role badge styling & label
  const renderRoleBadge = (roleName?: string) => {
    const r = (roleName || '').toUpperCase();
    switch (r) {
      case 'ADMIN':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-purple-50 text-purple-700 border border-purple-200">
            <ShieldAlert className="w-3 h-3 text-purple-600" />
            Quản trị viên (ADMIN)
          </span>
        );
      case 'MANAGER':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-50 text-blue-700 border border-blue-200">
            <Building className="w-3 h-3 text-blue-600" />
            Quản lý (MANAGER)
          </span>
        );
      case 'STAFF':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-50 text-blue-700 border border-blue-200">
            <ShoppingBag className="w-3 h-3 text-blue-600" />
            Thu ngân (STAFF)
          </span>
        );
      case 'WAREHOUSE':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
            <Package className="w-3 h-3 text-amber-600" />
            Thủ kho (WAREHOUSE)
          </span>
        );
      default:
        return <Badge variant="neutral">{roleName || 'Chưa gán'}</Badge>;
    }
  };

  // Avatar initials helper
  const getInitials = (name?: string) => {
    if (!name) return 'NV';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="page-heading">
        <div>
          <h2 className="text-base font-bold text-gray-900 flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-blue-600" />
            Quản lý Nhân viên & Phân quyền Tài khoản
          </h2>
          <p className="text-xs text-gray-500">
            Quản trị nhân sự toàn hệ thống, cấp phát tài khoản thu ngân (POS), quản lý cửa hàng và phân quyền bảo mật
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={fetchData}
          >
            Làm mới
          </Button>
          <Button
            size="sm"
            variant="primary"
            leftIcon={<UserPlus className="w-3.5 h-3.5" />}
            onClick={handleOpenCreate}
          >
            Thêm nhân viên mới
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card>
          <span className="text-xs font-semibold text-gray-500 uppercase">Tổng nhân sự</span>
          <div className="mt-2 text-2xl font-bold text-gray-900">{totalEmployees}</div>
          <p className="text-[11px] text-gray-400 mt-0.5">Tài khoản nhân viên trong hệ thống</p>
        </Card>

        <Card>
          <span className="text-xs font-semibold text-gray-500 uppercase">Đang hoạt động</span>
          <div className="mt-2 text-2xl font-bold text-blue-700">{activeCount}</div>
          <p className="text-[11px] text-gray-400 mt-0.5">Có thể đăng nhập và làm việc</p>
        </Card>

        <Card>
          <span className="text-xs font-semibold text-gray-500 uppercase">Thu ngân tại quầy (POS)</span>
          <div className="mt-2 text-2xl font-bold text-blue-700">{staffCount}</div>
          <p className="text-[11px] text-gray-400 mt-0.5">Nhân viên ca bán hàng trực tiếp</p>
        </Card>

        <Card>
          <span className="text-xs font-semibold text-gray-500 uppercase">Tạm khóa / Đã nghỉ</span>
          <div className="mt-2 text-2xl font-bold text-rose-600">{inactiveCount}</div>
          <p className="text-[11px] text-gray-400 mt-0.5">Đã bị vô hiệu hóa quyền đăng nhập</p>
        </Card>
      </div>

      {/* Filters & Search */}
      <Card>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <Input
              label="Tìm kiếm nhân viên"
              placeholder="Tên, username, email, số điện thoại..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              leftIcon={<Search className="w-3.5 h-3.5" />}
            />
          </div>
          <div>
            <Select
              label="Lọc theo vai trò"
              value={selectedRole}
              onChange={(e) => setSelectedRole(e.target.value)}
              options={[
                { value: 'ALL', label: 'Tất cả vai trò nhân viên' },
                { value: 'ADMIN', label: 'Quản trị viên (ADMIN)' },
                { value: 'MANAGER', label: 'Quản lý cửa hàng (MANAGER)' },
                { value: 'STAFF', label: 'Thu ngân / Bán hàng (STAFF)' },
                { value: 'WAREHOUSE', label: 'Thủ kho (WAREHOUSE)' },
              ]}
            />
          </div>
          <div>
            <Select
              label="Trạng thái tài khoản"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              options={[
                { value: 'ALL', label: 'Tất cả trạng thái' },
                { value: 'ACTIVE', label: 'Đang hoạt động' },
                { value: 'INACTIVE', label: 'Đã tạm khóa' },
              ]}
            />
          </div>
        </div>
      </Card>

      {/* User Table */}
      <Card
        title="Danh sách nhân viên"
        headerAction={
          <span className="text-xs text-gray-500">
            Hiển thị {filteredUsers.length} / {users.length} tài khoản
          </span>
        }
      >
        <div className="overflow-x-auto -mx-5 -mb-5">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="border-b border-gray-200 text-gray-600 font-medium">
              <tr>
                <th className="px-5 py-3">Nhân viên</th>
                <th className="px-5 py-3">Liên hệ</th>
                <th className="px-5 py-3">Vai trò phân quyền</th>
                <th className="px-5 py-3 text-center">Trạng thái</th>
                <th className="px-5 py-3">Ngày tạo</th>
                <th className="px-5 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-xs text-gray-400">
                    {isLoading ? 'Đang nạp danh sách tài khoản...' : 'Không tìm thấy nhân viên phù hợp với bộ lọc'}
                  </td>
                </tr>
              ) : (
                pagination.rows.map((u) => {
                  const isSelf = currentUser?.id === u.userId;
                  return (
                    <tr key={u.userId} className="hover:bg-gray-50/50 transition-colors">
                      {/* Name & Username */}
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-800 font-bold flex items-center justify-center text-xs uppercase shadow-2xs">
                            {getInitials(u.fullName)}
                          </div>
                          <div>
                            <div className="font-semibold text-gray-900 flex items-center gap-1.5">
                              {u.fullName}
                              {isSelf && (
                                <span className="text-[10px] px-1.5 py-0.2 rounded bg-amber-100 text-amber-800 font-medium">
                                  Bạn
                                </span>
                              )}
                            </div>
                            <div className="text-[11px] text-gray-400 font-mono">@{u.userName}</div>
                          </div>
                        </div>
                      </td>

                      {/* Contact */}
                      <td className="px-5 py-3.5 text-gray-600">
                        <div>{u.email || '--'}</div>
                        <div className="text-[11px] text-gray-400 font-mono">{u.phone || '--'}</div>
                      </td>

                      {/* Role */}
                      <td className="px-5 py-3.5">{renderRoleBadge(u.roleName)}</td>

                      {/* Status */}
                      <td className="px-5 py-3.5 text-center">
                        <Badge variant={u.isActive ? 'success' : 'danger'}>
                          {u.isActive ? 'Hoạt động' : 'Đã khóa'}
                        </Badge>
                      </td>

                      {/* Created Date */}
                      <td className="px-5 py-3.5 text-gray-500 whitespace-nowrap">
                        {formatDateTime(u.createAt)}
                      </td>

                      {/* Actions */}
                      <td className="px-5 py-3.5 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            type="button"
                            onClick={() => handleOpenEdit(u)}
                            title="Sửa thông tin"
                            className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg cursor-pointer transition-colors"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleToggleStatus(u)}
                            disabled={isSelf}
                            title={u.isActive ? 'Khóa tài khoản' : 'Kích hoạt lại tài khoản'}
                            className={`p-1.5 rounded-lg cursor-pointer transition-colors ${
                              isSelf
                                ? 'text-gray-300 cursor-not-allowed'
                                : u.isActive
                                ? 'text-gray-400 hover:text-amber-600 hover:bg-amber-50'
                                : 'text-gray-400 hover:text-blue-600 hover:bg-blue-50'
                            }`}
                          >
                            {u.isActive ? <Lock className="w-3.5 h-3.5" /> : <Unlock className="w-3.5 h-3.5" />}
                          </button>
                          <button
                            type="button"
                            onClick={() => setUserToDelete(u)}
                            disabled={isSelf}
                            title="Xóa tài khoản"
                            className={`p-1.5 rounded-lg cursor-pointer transition-colors ${
                              isSelf
                                ? 'text-gray-300 cursor-not-allowed'
                                : 'text-gray-400 hover:text-rose-600 hover:bg-rose-50'
                            }`}
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Pagination {...pagination} />

      {/* Modal Thêm nhân viên */}
      <Modal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="Thêm tài khoản nhân viên mới"
        size="md"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          <Input
            label="Họ và tên nhân viên *"
            placeholder="Ví dụ: Trần Văn Nam"
            value={createForm.fullName}
            onChange={(e) => setCreateForm({ ...createForm, fullName: e.target.value })}
            required
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Tên đăng nhập (Username) *"
              placeholder="Ví dụ: nam_pos01"
              value={createForm.username}
              onChange={(e) => setCreateForm({ ...createForm, username: e.target.value })}
              required
            />
            <Input
              label="Mật khẩu khởi tạo *"
              type="password"
              placeholder="Tối thiểu 6 ký tự"
              value={createForm.password}
              onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
              required
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Email"
              type="email"
              placeholder="nam.tran@supermarket.local"
              value={createForm.email}
              onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
            />
            <Input
              label="Số điện thoại"
              placeholder="0912345678"
              value={createForm.phone}
              onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })}
            />
          </div>

          <Select
            label="Phân quyền vai trò *"
            value={String(createForm.roleId)}
            onChange={(e) => setCreateForm({ ...createForm, roleId: Number(e.target.value) })}
            options={roles.map((r) => {
              let label = r.roleName;
              if (r.roleName === 'ADMIN') label = 'Quản trị viên (ADMIN) - Toàn quyền quản trị';
              if (r.roleName === 'MANAGER') label = 'Quản lý cửa hàng (MANAGER) - Kho, sản phẩm, báo cáo';
              if (r.roleName === 'STAFF') label = 'Thu ngân / Bán hàng (STAFF) - Quầy POS & Đơn hàng';
              if (r.roleName === 'WAREHOUSE') label = 'Thủ kho (WAREHOUSE) - Quản lý lô FEFO & Nhập xuất';
              return { value: String(r.roleId), label };
            })}
          />

          <div className="flex items-center gap-2 pt-1">
            <input
              type="checkbox"
              id="createActive"
              checked={createForm.isActive}
              onChange={(e) => setCreateForm({ ...createForm, isActive: e.target.checked })}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 cursor-pointer"
            />
            <label htmlFor="createActive" className="text-xs text-gray-700 cursor-pointer font-medium">
              Kích hoạt tài khoản ngay sau khi tạo
            </label>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-gray-100">
            <Button type="button" variant="outline" size="sm" onClick={() => setIsCreateOpen(false)}>
              Hủy bỏ
            </Button>
            <Button type="submit" variant="primary" size="sm" isLoading={isSubmitting}>
              Tạo tài khoản
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Sửa nhân viên */}
      <Modal
        isOpen={isEditOpen}
        onClose={() => setIsEditOpen(false)}
        title={`Chỉnh sửa tài khoản: ${editingUser?.fullName || ''}`}
        size="md"
      >
        <form onSubmit={handleEditSubmit} className="space-y-4">
          <Input
            label="Họ và tên nhân viên *"
            value={editForm.fullName}
            onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })}
            required
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Tên đăng nhập (Username) *"
              value={editForm.username}
              onChange={(e) => setEditForm({ ...editForm, username: e.target.value })}
              required
            />
            <Input
              label="Mật khẩu mới (Để trống nếu không đổi)"
              type="password"
              placeholder="Chỉ nhập khi muốn đổi"
              value={editForm.password || ''}
              onChange={(e) => setEditForm({ ...editForm, password: e.target.value })}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label="Email"
              type="email"
              value={editForm.email || ''}
              onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
            />
            <Input
              label="Số điện thoại"
              value={editForm.phone || ''}
              onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
            />
          </div>

          <Select
            label="Phân quyền vai trò *"
            value={String(editForm.roleId)}
            onChange={(e) => setEditForm({ ...editForm, roleId: Number(e.target.value) })}
            options={roles.map((r) => {
              let label = r.roleName;
              if (r.roleName === 'ADMIN') label = 'Quản trị viên (ADMIN) - Toàn quyền quản trị';
              if (r.roleName === 'MANAGER') label = 'Quản lý cửa hàng (MANAGER) - Kho, sản phẩm, báo cáo';
              if (r.roleName === 'STAFF') label = 'Thu ngân / Bán hàng (STAFF) - Quầy POS & Đơn hàng';
              if (r.roleName === 'WAREHOUSE') label = 'Thủ kho (WAREHOUSE) - Quản lý lô FEFO & Nhập xuất';
              return { value: String(r.roleId), label };
            })}
          />

          <div className="flex items-center gap-2 pt-1">
            <input
              type="checkbox"
              id="editActive"
              checked={editForm.isActive}
              onChange={(e) => setEditForm({ ...editForm, isActive: e.target.checked })}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 cursor-pointer"
            />
            <label htmlFor="editActive" className="text-xs text-gray-700 cursor-pointer font-medium">
              Tài khoản đang hoạt động
            </label>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-gray-100">
            <Button type="button" variant="outline" size="sm" onClick={() => setIsEditOpen(false)}>
              Hủy bỏ
            </Button>
            <Button type="submit" variant="primary" size="sm" isLoading={isSubmitting}>
              Lưu thay đổi
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Xác nhận Xóa */}
      <Modal
        isOpen={!!userToDelete}
        onClose={() => setUserToDelete(null)}
        title="Xác nhận xóa tài khoản"
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-xs text-gray-600">
            Bạn có chắc chắn muốn xóa tài khoản nhân viên{' '}
            <strong className="text-gray-900">{userToDelete?.fullName}</strong> (
            <span className="font-mono">@{userToDelete?.userName}</span>)?
          </p>
          <p className="text-[11px] text-rose-600 bg-rose-50 p-2.5 rounded-lg border border-rose-200">
            Lưu ý: Hành động này sẽ gỡ bỏ tài khoản khỏi hệ thống. Nếu tài khoản đã phát sinh đơn bán hoặc thẻ kho, bạn nên chọn chức năng <strong>Tạm khóa</strong> thay vì xóa vĩnh viễn.
          </p>
          <div className="flex justify-end gap-2 pt-2 border-t border-gray-100">
            <Button type="button" variant="outline" size="sm" onClick={() => setUserToDelete(null)}>
              Hủy
            </Button>
            <Button type="button" variant="danger" size="sm" onClick={handleDeleteUser}>
              Xác nhận xóa
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
