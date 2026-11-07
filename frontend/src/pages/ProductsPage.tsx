import { Pagination, useListPage } from '../components/ui/Pagination';
import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  Plus,
  Edit2,
  Eye,
  EyeOff,
  History,
  RefreshCw,
} from 'lucide-react';
import { api } from '../api/client';
import type { Product, StockMovement, ProductRequest } from '../types';
import { normalizeProduct } from '../types';
import { formatVND, formatDateTime } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';
import { useAuth } from '../contexts/AuthContext';

export const ProductsPage: React.FC = () => {
  const { role } = useAuth();
  const canManage = role === 'ADMIN' || role === 'WAREHOUSE';
  const [products, setProducts] = useState<Product[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [isSaving, setIsSaving] = useState(false);
  const [changingProductId, setChangingProductId] = useState<number | null>(null);
  const [formError, setFormError] = useState('');
  const [listError, setListError] = useState('');

  // Modal State
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [imageMode, setImageMode] = useState<'url' | 'file'>('url');
  const [imageFile, setImageFile] = useState<File | null>(null);
  const imageInput = useRef<HTMLInputElement>(null);
  const [imagePreview, setImagePreview] = useState('');
  useEffect(() => {
    if (!imageFile || !isFormOpen) { setImagePreview(''); return; }
    const url = URL.createObjectURL(imageFile);
    setImagePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [imageFile, isFormOpen]);
  const [formData, setFormData] = useState<ProductRequest>({
    skuCode: '',
    productName: '',
    price: 15000,
    stockQuantity: 0,
    reorderPoint: 5,
    imageUrl: '',
    categoryName: '',
    isActive: true,
  });

  // Stock Movement History Modal
  const [isMovementOpen, setIsMovementOpen] = useState(false);
  const [movementList, setMovementList] = useState<StockMovement[]>([]);
  const [selectedProductName, setSelectedProductName] = useState('');
  const [isLoadingMovements, setIsLoadingMovements] = useState(false);

  const { success, error } = useToast();

  const loadAll = async <T,>(endpoint: string): Promise<T[]> => {
    const entries: T[] = [];
    for (let page = 0; ; page++) {
      const response = await api.get<{ content: T[]; totalPages: number } | T[]>(`${endpoint}?page=${page}&size=100`);
      entries.push(...(Array.isArray(response) ? response : response.content));
      if (Array.isArray(response) || page + 1 >= response.totalPages) return entries;
    }
  };

  const productPayload = (product: Product, isActive: boolean): ProductRequest => ({
    skuCode: product.skuCode, productName: product.name, price: product.sellingPrice,
    stockQuantity: product.stockQuantity ?? product.totalQuantity, reorderPoint: product.minStockLevel,
    imageUrl: product.imageUrl ?? '', categoryName: product.categoryName ?? '', isActive,
  });

  const fetchInitialData = async () => {
    setIsLoading(true);
    setListError('');
    try {
      setProducts((await loadAll('/products')).map(normalizeProduct));
    } catch {
      setProducts([]);
      setListError('Không thể tải sản phẩm. Vui lòng bấm Tải lại.');
      error('Không thể tải danh sách sản phẩm');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
  }, []);

  const handleOpenCreate = () => {
    setImageMode('url'); setImageFile(null);
    setFormError('');
    setEditingProduct(null);
    const numericSkus = products.map((product) => Number(product.skuCode)).filter(Number.isFinite);
    const nextSku = String(Math.max(0, ...numericSkus) + 1);
    setFormData({
      skuCode: nextSku,
      productName: '',
      price: 15000,
      stockQuantity: 0,
      reorderPoint: 10,
      imageUrl: '',
      categoryName: '',
      isActive: true,
    });
    setIsFormOpen(true);
  };

  const handleOpenEdit = (p: Product) => {
    setImageMode(p.imageUrl?.startsWith('/api/product-images/') ? 'file' : 'url');
    setImageFile(null);
    setFormError('');
    setEditingProduct(p);
    setFormData(productPayload(p, p.active));
    setIsFormOpen(true);
  };

  const handleSaveProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSaving || !canManage) return;
    setFormError(''); setIsSaving(true);
    try {
      const payload = { ...formData, productName: formData.productName.trim(), skuCode: formData.skuCode.trim(), imageUrl: formData.imageUrl?.trim() };
      if (imageMode === 'url' && payload.imageUrl && !payload.imageUrl.startsWith('/api/product-images/')) {
        let url: URL;
        try { url = new URL(payload.imageUrl); }
        catch { throw new Error('Đường dẫn ảnh phải là URL hợp lệ, ví dụ https://...'); }
        if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://');
      }
      if (imageMode === 'file' && imageFile) {
        const body = new FormData(); body.append('file', imageFile);
        const uploaded = await api.post<{ imageUrl: string }>('/product-images', body);
        payload.imageUrl = uploaded.imageUrl;
        setFormData(previous => ({ ...previous, imageUrl: uploaded.imageUrl }));
        setImageFile(null);
      }
      if (editingProduct) {
        // Stock is controlled by batch operations. Read its current value for
        // the existing full-update API; never write a stale form quantity.
        const current = normalizeProduct(await api.get(`/products/${editingProduct.id}`));
        payload.stockQuantity = current.stockQuantity ?? current.totalQuantity;
        await api.put(`/products/${editingProduct.id}`, payload);
        success('Cập nhật thông tin sản phẩm thành công');
      } else {
        payload.stockQuantity = 0;
        await api.post('/products', payload);
        success('Thêm sản phẩm mới thành công');
      }
      setIsFormOpen(false);
      await fetchInitialData();
    } catch (err: unknown) {
      setFormError(err instanceof Error ? err.message : 'Không thể lưu sản phẩm');
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleProduct = async (product: Product) => {
    if (!canManage || changingProductId != null) return;
    if (product.active && !window.confirm(`Ngừng bán “${product.name}” tại POS? Tồn kho và hóa đơn đã tạo được giữ nguyên.`)) return;
    setChangingProductId(product.id);
    try {
      if (product.active) {
        await api.delete(`/products/${product.id}`);
        success('Đã ngừng bán sản phẩm tại POS');
      } else {
        const current = normalizeProduct(await api.get(`/products/${product.id}`));
        const updated = normalizeProduct(await api.put(`/products/${product.id}`, productPayload(current, true)));
        success(updated.availableQuantity > 0 ? 'Đã mở bán lại sản phẩm' : 'Đã mở bán; cần nhập lô có thể bán để thu ngân lập hóa đơn');
      }
      await fetchInitialData();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    } finally {
      setChangingProductId(null);
    }
  };

  const handleViewMovements = async (p: Product) => {
    setSelectedProductName(p.name);
    setIsMovementOpen(true);
    setIsLoadingMovements(true);
    try {
      setMovementList(await loadAll<StockMovement>(`/products/${p.id}/stock-movements`));
    } catch {
      setMovementList([]);
      error('Không thể tải lịch sử kho. Vui lòng đóng và mở lại.');
    } finally {
      setIsLoadingMovements(false);
    }
  };

  // Filter products by search and category
  const filteredProducts = products.filter((p: Product) => {
    const pName = p.name || p.productName || '';
    const pSku = p.skuCode || '';
    const pBarcode = p.barcode || '';
    const kw = searchKeyword.toLowerCase();

    const matchesKeyword =
      pName.toLowerCase().includes(kw) ||
      pSku.toLowerCase().includes(kw) ||
      pBarcode.toLowerCase().includes(kw);

    const matchesCategory =
      selectedCategory === 'ALL' ||
      p.categoryName === selectedCategory;

    const matchesStatus = selectedStatus === 'ALL'
      || (selectedStatus === 'HIDDEN' && !p.active)
      || (selectedStatus === 'SELLING' && p.active && p.availableQuantity > 0)
      || (selectedStatus === 'OUT' && p.active && p.availableQuantity <= 0);
    return matchesKeyword && matchesCategory && matchesStatus;
  });

  const pagination = useListPage(filteredProducts.sort((a, b) => b.id - a.id), JSON.stringify([searchKeyword, selectedCategory, selectedStatus]));

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-white p-4 rounded-xl border border-gray-200">
        <div>
          <h2 className="text-base font-bold text-gray-900">Danh mục sản phẩm</h2>
          <p className="text-xs text-gray-500">
            Quản lý mã hàng, đơn giá, ngưỡng tồn kho và theo dõi lịch sử biến động thẻ kho
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={fetchInitialData}
          >
            Tải lại
          </Button>
          {canManage && (
            <Button
              size="sm"
              variant="primary"
              leftIcon={<Plus className="w-3.5 h-3.5" />}
              onClick={handleOpenCreate}
            >
              Thêm sản phẩm
            </Button>
          )}
        </div>
      </div>

      {/* Filter and Search Bar */}
      <Card>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <Input
              aria-label="Tìm sản phẩm theo tên, SKU hoặc mã vạch"
              placeholder="Tên sản phẩm, SKU hoặc mã vạch"
              leftIcon={<Search className="w-4 h-4" />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
            />
          </div>
          <div>
            <Select
              aria-label="Lọc nhóm hàng"
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              options={[
                { value: 'ALL', label: 'Tất cả danh mục' },
                ...[...new Set(products.map(product => product.categoryName).filter((name): name is string => Boolean(name)))].sort().map(categoryName => ({ value: categoryName, label: categoryName })),
              ]}
            />
          </div>
          <Select aria-label="Lọc trạng thái sản phẩm" value={selectedStatus} onChange={event => setSelectedStatus(event.target.value)} options={[
            { value: 'ALL', label: 'Tất cả trạng thái' }, { value: 'SELLING', label: 'Đang bán · còn hàng' },
            { value: 'OUT', label: 'Đang bán · hết hàng' }, { value: 'HIDDEN', label: 'Ngừng kinh doanh' },
          ]} />
        </div>
        <p className="mt-3 text-xs leading-relaxed text-gray-500">Hết hàng: chưa thể bán cho đến khi nhập thêm lô hợp lệ. Ngừng kinh doanh: thu ngân không thể thêm vào hóa đơn mới. Mở/ngừng bán không thay đổi tồn kho hoặc hóa đơn đã tạo.</p>
      </Card>

      {/* Products Table */}
      <Card>
        <p className="mb-3 text-xs text-gray-500" aria-live="polite">{isLoading ? 'Đang tải sản phẩm…' : `Hiển thị ${filteredProducts.length} / ${products.length} sản phẩm`}</p>
        <div className="overflow-x-auto -mx-5 -mb-5">
          <table className="w-full min-w-[780px] text-left text-sm">
            <thead className="border-b border-gray-200 text-gray-600 font-medium">
              <tr>
                <th className="px-4 py-3">Mã SKU</th>
                <th className="px-4 py-3">Tên sản phẩm</th>
                <th className="px-4 py-3">Danh mục</th>
                <th className="px-4 py-3 text-right">Giá bán</th>
                <th className="px-4 py-3 text-right">Tồn khả dụng</th>
                <th className="px-4 py-3 text-center">Trạng thái</th>
                <th className="px-4 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {filteredProducts.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-10 text-gray-400">
                    {isLoading ? 'Đang tải danh sách sản phẩm...' : listError || 'Không tìm thấy sản phẩm phù hợp'}
                  </td>
                </tr>
              ) : (
                pagination.rows.map((p: Product) => {
                  const available = p.availableQuantity ?? p.totalQuantity;
                  return (
                    <tr key={p.id} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-4 py-3.5 font-mono font-medium text-blue-800">
                        {p.skuCode}
                      </td>
                      <td className="px-4 py-3.5 font-medium text-gray-900 max-w-[200px] truncate">
                        {p.name}
                      </td>
                      <td className="px-4 py-3.5 text-gray-600">
                        {p.categoryName || 'Chưa phân loại'}
                      </td>
                      <td className="px-4 py-3.5 text-right font-bold text-gray-900">
                        {formatVND(p.sellingPrice)}
                      </td>
                      <td className="px-4 py-3.5 text-right font-bold">
                        <span
                          className={
                            available <= 0
                              ? 'text-rose-600'
                              : available <= p.minStockLevel
                              ? 'text-amber-600'
                              : 'text-blue-700'
                          }
                        >
                          {available}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <Badge variant={!p.active ? 'neutral' : available > 0 ? 'success' : 'warning'}>
                          {!p.active ? 'Ngừng kinh doanh' : available > 0 ? 'Đang bán' : 'Tạm hết hàng'}
                        </Badge>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            type="button"
                            onClick={() => handleViewMovements(p)}
                            title="Lịch sử thẻ kho"
                            aria-label={`Xem lịch sử kho: ${p.name}`}
                            className="p-1.5 text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg cursor-pointer"
                          >
                            <History className="w-3.5 h-3.5" />
                          </button>
                          {canManage && (
                            <>
                              <button
                                type="button"
                                onClick={() => handleOpenEdit(p)}
                                disabled={changingProductId != null || isSaving}
                                title="Sửa thông tin"
                                aria-label={`Sửa thông tin: ${p.name}`}
                                className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg cursor-pointer"
                              >
                                <Edit2 className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => handleToggleProduct(p)}
                                title={p.active ? 'Ngừng bán' : 'Mở bán lại'}
                                aria-label={`${p.active ? 'Ngừng bán' : 'Mở bán lại'}: ${p.name}`}
                                disabled={changingProductId != null || isSaving}
                                className={`inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-semibold focus-visible:ring-2 focus-visible:ring-blue-600 disabled:opacity-40 ${p.active ? 'text-rose-600 hover:bg-rose-50' : 'text-blue-700 hover:bg-blue-50'}`}
                              >
                                {changingProductId === p.id ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : p.active ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                                {p.active ? 'Ngừng bán' : 'Mở bán lại'}
                              </button>
                            </>
                          )}
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

      {/* Add / Edit Product Modal */}
      <Modal
        isOpen={isFormOpen}
        onClose={() => { if (!isSaving) setIsFormOpen(false); }}
        title={editingProduct ? 'Chỉnh sửa thông tin sản phẩm' : 'Thêm sản phẩm mới'}
        maxWidth="lg"
        footer={
          <>
            <Button variant="outline" size="sm" disabled={isSaving} onClick={() => setIsFormOpen(false)}>
              Hủy bỏ
            </Button>
            <Button variant="primary" size="sm" type="submit" form="product-editor" isLoading={isSaving}>
              {editingProduct ? 'Lưu thay đổi' : 'Tạo sản phẩm'}
            </Button>
          </>
        }
      >
        <form id="product-editor" onSubmit={handleSaveProduct} className="space-y-4">
          {formError && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{formError}</p>}
          <fieldset disabled={isSaving} className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <Input label="Mã sản phẩm" value={formData.skuCode} onChange={event => setFormData({ ...formData, skuCode: event.target.value })} required maxLength={50} />
              <Input label="Tên sản phẩm" value={formData.productName} onChange={event => setFormData({ ...formData, productName: event.target.value })} required maxLength={150} />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Input label="Nhóm hàng" value={formData.categoryName} onChange={event => setFormData({ ...formData, categoryName: event.target.value })} required maxLength={100} />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Input label="Giá bán (₫)" type="number" min="1" step="1" value={formData.price} onChange={event => setFormData({ ...formData, price: Number(event.target.value) })} required />
              <Input label="Ngưỡng cảnh báo tồn" type="number" min="0" step="1" value={formData.reorderPoint} onChange={event => setFormData({ ...formData, reorderPoint: Number(event.target.value) })} required />
            </div>
            <fieldset className="space-y-3 rounded-lg border border-gray-200 p-3">
              <legend className="px-1 text-xs font-semibold text-gray-700">Ảnh sản phẩm (không bắt buộc)</legend>
              <div className="flex flex-wrap gap-4 text-sm">
                <label className="flex items-center gap-2"><input type="radio" name="product-image-mode" checked={imageMode === 'url'} onChange={() => { setImageMode('url'); setImageFile(null); }} />Dán đường dẫn ảnh</label>
                <label className="flex items-center gap-2"><input type="radio" name="product-image-mode" checked={imageMode === 'file'} onChange={() => setImageMode('file')} />Chọn ảnh từ máy</label>
              </div>
              {imageMode === 'url' ? <Input label="Đường dẫn ảnh" value={formData.imageUrl || ''} onChange={event => setFormData({ ...formData, imageUrl: event.target.value })} placeholder="https://..." /> :
                <div className="space-y-1"><Input ref={imageInput} label="File ảnh" type="file" accept="image/jpeg,image/png" onChange={event => {
                  const file = event.target.files?.[0];
                  if (!file) { setImageFile(null); return; }
                  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
                    setFormError('Chọn ảnh JPG hoặc PNG, tối đa 5 MB'); event.target.value = ''; setImageFile(null); return;
                  }
                  setFormError(''); setImageFile(file);
                }} /><p className="text-xs text-gray-500">JPG hoặc PNG, tối đa 5 MB và 4096 × 4096 pixel. Ảnh được tải lên khi lưu sản phẩm.</p></div>}
              {(imagePreview || formData.imageUrl) && <div className="flex items-start gap-3">
                <img src={imagePreview || formData.imageUrl} alt="Xem trước ảnh sản phẩm" className="h-24 w-24 rounded-lg border border-gray-200 bg-gray-50 object-contain" />
                <Button type="button" variant="ghost" size="sm" onClick={() => { setImageFile(null); if (imageInput.current) imageInput.current.value = ''; setFormData({ ...formData, imageUrl: '' }); }}>Bỏ ảnh</Button>
              </div>}
            </fieldset>
            <Select label="Trạng thái kinh doanh" value={formData.isActive ? 'true' : 'false'} onChange={event => setFormData({ ...formData, isActive: event.target.value === 'true' })} options={[{ value: 'true', label: 'Đang bán · thu ngân chọn khi còn hàng' }, { value: 'false', label: 'Ngừng bán · không thêm vào hóa đơn mới' }]} />
          </fieldset>
          <p className="rounded-lg bg-gray-50 p-3 text-xs leading-relaxed text-gray-600">{editingProduct ? 'Sửa thông tin và trạng thái không thay đổi số lượng kho. Nhập hoặc điều chỉnh hàng tại Kho & Lô hàng FEFO.' : 'Sản phẩm mới có tồn bằng 0. Nhập lô tại Kho & Lô hàng FEFO để có hàng bán.'} Hết hàng không tự chuyển sang trạng thái ẩn.</p>
        </form>
      </Modal>

      {/* Stock Movements History Modal */}
      <Modal
        isOpen={isMovementOpen}
        onClose={() => setIsMovementOpen(false)}
        title={`Lịch sử biến động thẻ kho: ${selectedProductName}`}
        maxWidth="xl"
      >
        {isLoadingMovements ? (
          <div className="py-8 text-center text-xs text-gray-500">Đang tải thẻ kho...</div>
        ) : movementList.length === 0 ? (
          <div className="py-8 text-center text-xs text-gray-400">
            Chưa có biến động xuất nhập kho nào được ghi nhận cho sản phẩm này.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-gray-50 border-b border-gray-200 text-gray-500 font-semibold">
                <tr>
                  <th className="px-3 py-2">Thời gian</th>
                  <th className="px-3 py-2">Loại biến động</th>
                  <th className="px-3 py-2 text-right">Số lượng</th>
                  <th className="px-3 py-2">Ghi chú</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {movementList.map((m: StockMovement) => (
                  <tr key={m.movementId}>
                    <td className="px-3 py-2 text-gray-600">{formatDateTime(m.createdAt)}</td>
                    <td className="px-3 py-2 font-semibold">
                      <span
                        className={
                          m.quantity > 0
                            ? 'text-blue-700'
                            : m.quantity < 0
                            ? 'text-rose-700'
                            : 'text-indigo-700'
                        }
                      >
                        {{ OPENING: 'Tồn đầu', RECEIPT: 'Nhập lô', SALE: 'Xuất bán',
                          RETURN: 'Nhận trả bán lại', RETURN_DAMAGED: 'Nhận trả hỏng',
                          ADJUSTMENT: 'Điều chỉnh kiểm kê' }[m.movementType] ?? m.movementType}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-right font-bold">{m.quantity}</td>
                    <td className="px-3 py-2 text-gray-500">{m.note || '--'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>
    </div>
  );
};
