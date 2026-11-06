import { Pagination, useListPage } from '../components/ui/Pagination';
import React, { useState, useEffect } from 'react';
import {
  Plus,
  SlidersHorizontal,
  Calendar,
  ChevronDown,
  ChevronRight,
  AlertTriangle,
  RefreshCw,
  Search,
} from 'lucide-react';
import { api } from '../api/client';
import type {
  InventoryBatch,
  Product,
  InventoryBatchRequest,
  BatchAdjustmentRequest,
  Supplier,
} from '../types';
import { normalizeProduct } from '../types';
import { formatDate, formatVND, BATCH_STATUS_LABELS } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';

interface OptionPage {
  content: any[];
  totalPages: number;
}

async function loadAllOptions<T>(endpoint: string, normalize: (raw: any) => T): Promise<T[]> {
  const first = await api.get<OptionPage | any[]>(`${endpoint}?page=0&size=100`);
  if (Array.isArray(first)) return first.map(normalize);
  const records = [...first.content];
  for (let page = 1; page < first.totalPages; page++) {
    const next = await api.get<OptionPage>(`${endpoint}?page=${page}&size=100`);
    records.push(...next.content);
  }
  return records.map(normalize);
}

function batchExpiryTime(batch: InventoryBatch): number {
  return batch.expiryDate ? new Date(`${batch.expiryDate}T00:00:00`).getTime() : Number.POSITIVE_INFINITY;
}

function displayedBatchStatus(batch: InventoryBatch): InventoryBatch['status'] {
  if (batch.status !== 'ACTIVE') return batch.status;
  if (batch.remainingQuantity <= 0) return 'DEPLETED';
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return batchExpiryTime(batch) < today.getTime() ? 'EXPIRED' : 'ACTIVE';
}

export const InventoryPage: React.FC = () => {
  const [batches, setBatches] = useState<InventoryBatch[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [expandedProductIds, setExpandedProductIds] = useState<Set<number>>(new Set());
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Receive Batch Modal State
  const [isReceiveOpen, setIsReceiveOpen] = useState(false);
  const [receiveData, setReceiveData] = useState<InventoryBatchRequest>({
    productId: 1,
    supplierId: 0,
    batchCode: '',
    quantity: 50,
    unitCost: 10000,
    manufacturingDate: '',
    expiryDate: '',
  });

  // Adjustment Modal State
  const [isAdjustOpen, setIsAdjustOpen] = useState(false);
  const [selectedBatch, setSelectedBatch] = useState<InventoryBatch | null>(null);
  const [adjustData, setAdjustData] = useState<BatchAdjustmentRequest>({
    adjustmentQuantity: 0,
    reason: 'Kiểm kê định kỳ thực tế',
  });

  const { success, error } = useToast();

  const fetchBatchesAndOptions = async () => {
    setIsLoading(true);
    setLoadError('');
    try {
      const [prodList, bList, supplierList] = await Promise.all([
        loadAllOptions('/products', normalizeProduct),
        api.get<any[]>('/inventory/batches'),
        api.get<Supplier[]>('/suppliers'),
      ]);
      setProducts(prodList);
      setSuppliers(supplierList);
      setSelectedProductId((current) => current !== null && !prodList.some((product) => product.id === current) ? null : current);
      const productsById = new Map(prodList.map((product) => [product.id, product]));
      setBatches(
          bList.map((b: any) => ({
            id: b.batchId ?? b.id ?? 0,
            productId: b.productId,
            productName: productsById.get(b.productId)?.name || '',
            skuCode: productsById.get(b.productId)?.skuCode || '',
            supplierName: b.supplierName ?? '',
            batchCode: b.batchCode ?? b.code ?? '',
            receivedQuantity: b.receivedQuantity ?? b.received ?? 0,
            remainingQuantity: b.remainingQuantity ?? b.remaining ?? 0,
            damagedQuantity: b.damagedQuantity ?? b.damaged ?? 0,
            availableQuantity: b.availableQuantity ?? 0,
            unitCost: b.unitCost ?? 0,
            manufacturingDate: b.manufacturingDate,
            expiryDate: b.expiryDate,
            createdAt: b.receivedAt,
            status: b.status || 'ACTIVE',
          }))
      );
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Không tải được thông tin kho';
      setLoadError(message);
      error(message);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchBatchesAndOptions();
  }, []);

  const handleOpenReceive = () => {
    const prodId = selectedProductId || products[0]?.id || 1;
    setReceiveData({
      productId: prodId,
      supplierId: 0,
      // Backend assigns the next short numeric code when this is empty.
      batchCode: '',
      quantity: 50,
      unitCost: 10000,
      manufacturingDate: '',
      expiryDate: '',
    });
    setIsReceiveOpen(true);
  };

  const handleReceiveBatch = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/inventory/batches', { ...receiveData,
        manufacturingDate: receiveData.manufacturingDate || null,
        expiryDate: receiveData.expiryDate || null });
      success('Đã nhập lô hàng mới vào kho thành công (chuẩn FEFO)');
      setIsReceiveOpen(false);
      await fetchBatchesAndOptions();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleOpenAdjust = (batch: InventoryBatch) => {
    setSelectedBatch(batch);
    setAdjustData({
      adjustmentQuantity: 0,
      reason: 'Kiểm kê kho thực tế',
    });
    setIsAdjustOpen(true);
  };

  const handleAdjustBatch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedBatch) return;
    try {
      const payload = {
        quantity: -Math.abs(adjustData.adjustmentQuantity),
        reason: adjustData.reason || 'Kiểm kê kho',
      };
      await api.post(`/inventory/batches/${selectedBatch.id}/adjustments`, payload);
      success(`Đã điều chỉnh số lượng tồn lô ${selectedBatch.batchCode}`);
      setIsAdjustOpen(false);
      await fetchBatchesAndOptions();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  // Within each product, dated lots come first; tie-break like the FEFO allocation policy.
  const sortedBatches = [...batches].sort((a, b) => {
    if (a.productId !== b.productId) return b.productId - a.productId;
    const expiryA = batchExpiryTime(a);
    const expiryB = batchExpiryTime(b);
    if (expiryA !== expiryB) return expiryA < expiryB ? -1 : 1;
    return String(a.createdAt || '').localeCompare(String(b.createdAt || '')) || a.id - b.id;
  });

  const filteredBatches = sortedBatches.filter((b) => {
    const matchesSearch =
      b.batchCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (b.productName && b.productName.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (b.skuCode && b.skuCode.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (b.supplierName && b.supplierName.toLowerCase().includes(searchTerm.toLowerCase()));

    const matchesProduct = selectedProductId === null || b.productId === selectedProductId;
    const matchesStatus = statusFilter === 'ALL' || displayedBatchStatus(b) === statusFilter;
    return matchesSearch && matchesProduct && matchesStatus;
  });

  const groupedBatches = Array.from(
    filteredBatches.reduce((groups, batch) => {
      const group = groups.get(batch.productId) || [];
      group.push(batch);
      groups.set(batch.productId, group);
      return groups;
    }, new Map<number, InventoryBatch[]>()).entries()
  );

  const pagination = useListPage(groupedBatches, JSON.stringify([selectedProductId, searchTerm, statusFilter]));

  const toggleProductLots = (productId: number) => {
    setExpandedProductIds((current) => {
      const next = new Set(current);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  };

  const productCount = new Set(batches.map((batch) => batch.productId)).size;
  const filteredProductCount = new Set(filteredBatches.map((batch) => batch.productId)).size;
  const selectedProduct = products.find((product) => product.id === selectedProductId);
  const hasFilters = selectedProductId !== null || searchTerm !== '' || statusFilter !== 'ALL';
  const resetFilters = () => {
    setSelectedProductId(null);
    setSearchTerm('');
    setStatusFilter('ALL');
  };

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="page-heading">
        <div>
          <h2 className="text-base font-bold text-gray-900">Quản lý Kho & Lô hàng FEFO</h2>
          <p className="text-xs text-gray-500">
            Một sản phẩm có thể có nhiều lô nhập. Trong từng sản phẩm, lô có hạn gần nhất được ưu tiên xuất trước.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={() => fetchBatchesAndOptions()}
            disabled={isLoading}
          >
            Tải lại
          </Button>
          <Button
            size="sm"
            variant="primary"
            leftIcon={<Plus className="w-3.5 h-3.5" />}
            onClick={handleOpenReceive}
            disabled={isLoading || products.length === 0 || !!loadError}
          >
            Nhập lô mới
          </Button>
        </div>
      </div>

      {loadError && (
        <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
          Không tải được dữ liệu kho: {loadError}. Bấm “Tải lại” để lấy danh sách mới.
        </div>
      )}
      {/* Filter Bar */}
      <Card>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <div className="lg:col-span-2">
            <Input
              label="Tìm lô hàng"
              placeholder="Tìm theo mã lô (Lot/Batch), tên sản phẩm hoặc SKU..."
              leftIcon={<Search className="w-4 h-4" />}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          <div>
            <Select
              label="Sản phẩm"
              value={selectedProductId === null ? 'ALL' : String(selectedProductId)}
              onChange={(e) => setSelectedProductId(e.target.value === 'ALL' ? null : Number(e.target.value))}
              options={[
                { value: 'ALL', label: 'Tất cả sản phẩm' },
                ...products.map((product) => ({ value: String(product.id), label: `${product.name} · SKU ${product.skuCode}` })),
              ]}
              disabled={isLoading || !!loadError}
            />
          </div>
          <div>
            <Select
              label="Trạng thái lô"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={[
                { value: 'ALL', label: 'Tất cả trạng thái lô' },
                { value: 'ACTIVE', label: 'Đang sử dụng / còn hạn' },
                { value: 'EXPIRED', label: 'Hết hạn (EXPIRED)' },
                { value: 'DEPLETED', label: 'Hết hàng (DEPLETED)' },
                { value: 'QUARANTINED', label: 'Cách ly / Hỏng (QUARANTINED)' },
              ]}
            />
          </div>
        </div>
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-gray-100 pt-3">
          <p aria-live="polite" className="text-xs text-gray-600">
            {isLoading ? 'Đang tải toàn bộ kho…' : loadError ? 'Chưa xác nhận được số liệu kho' : (
              <>Toàn kho: <strong>{productCount} sản phẩm / {batches.length} lô</strong>.
                {' '}Đang xem {filteredProductCount} sản phẩm / {filteredBatches.length} lô
                {selectedProduct ? ` của ${selectedProduct.name}` : ''}.</>
            )}
          </p>
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={resetFilters}>Xóa bộ lọc</Button>
          )}
        </div>
        <p className="mt-2 text-xs text-gray-500">
          Kho được nhóm theo sản phẩm. Mở từng sản phẩm để xem hạn dùng và kiểm kê riêng từng lô; tồn khả dụng là tổng hàng có thể bán của các lô.
          {selectedProduct && !loadError && !isLoading && (
            <> Tồn khả dụng của {selectedProduct.name}: <strong>{selectedProduct.availableQuantity}</strong> (tất cả lô).</>
          )}
        </p>
      </Card>

      {/* Product groups with expandable batch details */}
      <Card title="Kho theo sản phẩm & lô hàng">
        <div className="overflow-x-auto -mx-5 -my-5">
          <table className="w-full min-w-[1040px] text-left text-sm">
            <thead className="border-b border-gray-200 text-gray-600 font-medium">
              <tr>
                <th className="px-4 py-3">Sản phẩm</th>
                <th className="px-4 py-3">Lô hàng</th>
                <th className="px-4 py-3">Hạn sử dụng (FEFO)</th>
                <th className="px-4 py-3 text-right">Giá nhập/đơn vị</th>
                <th className="px-4 py-3 text-right">Nhập</th>
                <th className="px-4 py-3 text-right">Tồn lại</th>
                <th className="px-4 py-3 text-right">Hỏng</th>
                <th className="px-4 py-3 text-right">Khả dụng</th>
                <th className="px-4 py-3 text-center">Trạng thái</th>
                <th className="px-4 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {isLoading || loadError || filteredBatches.length === 0 ? (
                <tr>
                  <td colSpan={10} className="text-center py-10 text-gray-400">
                    {isLoading ? 'Đang tải danh sách lô kho...' : loadError ? 'Danh sách chưa tải được. Vui lòng tải lại.' : hasFilters ? 'Không có lô phù hợp. Xóa bộ lọc để xem toàn bộ kho.' : 'Kho chưa có lô hàng. Chọn “Nhập lô mới” để bắt đầu.'}
                  </td>
                </tr>
              ) : (
                pagination.rows.flatMap(([productId, lots]) => {
                  const expanded = expandedProductIds.has(productId);
                  const firstExpiry = lots.find((lot) => lot.expiryDate)?.expiryDate;
                  const total = (field: 'receivedQuantity' | 'remainingQuantity' | 'damagedQuantity' | 'availableQuantity') =>
                    lots.reduce((sum, lot) => sum + lot[field], 0);
                  const groupRow = (
                    <tr key={`product-${productId}`} className="bg-blue-50/40 hover:bg-blue-50/70">
                      <td className="px-4 py-3.5 text-gray-900">
                        <div className="font-semibold">{lots[0].productName || `Sản phẩm #${productId}`}</div>
                        <div className="mt-0.5 text-[11px] text-gray-500">SKU {lots[0].skuCode || '—'}</div>
                      </td>
                      <td className="px-4 py-3.5 text-gray-600">{lots.length} lô</td>
                      <td className="px-4 py-3.5 font-medium text-gray-700">
                        {firstExpiry ? formatDate(firstExpiry) : 'Không có hạn dùng'}
                      </td>
                      <td className="px-4 py-3.5 text-right text-xs text-gray-500">Theo từng lô</td>
                      <td className="px-4 py-3.5 text-right text-gray-500">{total('receivedQuantity')}</td>
                      <td className="px-4 py-3.5 text-right font-medium text-gray-700">{total('remainingQuantity')}</td>
                      <td className="px-4 py-3.5 text-right text-rose-600 font-medium">{total('damagedQuantity')}</td>
                      <td className="px-4 py-3.5 text-right font-bold text-blue-700 text-sm">{total('availableQuantity')}</td>
                      <td className="px-4 py-3.5 text-center text-gray-500">Tổng các lô</td>
                      <td className="px-4 py-3.5 text-center">
                        <Button size="sm" variant="outline" leftIcon={expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />} onClick={() => toggleProductLots(productId)}>
                          {expanded ? 'Ẩn lô' : 'Xem lô'}
                        </Button>
                      </td>
                    </tr>
                  );
                  if (!expanded) return [groupRow];

                  const lotRows = lots.map((b) => {
                    const status = displayedBatchStatus(b);
                    const isExpiringSoon = b.expiryDate && status === 'ACTIVE'
                      && batchExpiryTime(b) - Date.now() < 30 * 86400000;
                    const statusInfo = BATCH_STATUS_LABELS[status] || {
                      label: status,
                      color: 'text-gray-700',
                      bg: 'bg-gray-50 border-gray-200',
                    };
                    return (
                      <tr key={`batch-${b.id}`} className="bg-white hover:bg-gray-50/70">
                        <td className="px-4 py-3.5 pl-9 text-gray-500">
                          <div>↳ {b.productName || `Sản phẩm #${b.productId}`}</div>
                          <div className="mt-1 text-xs">Nhà cung cấp: {b.supplierName || 'Chưa ghi nhận'}</div>
                        </td>
                        <td className="px-4 py-3.5 font-mono font-medium text-blue-800">{b.batchCode}</td>
                        <td className="px-4 py-3.5">
                          <div className="flex items-center gap-1.5 font-semibold text-gray-800">
                            <Calendar className="w-3.5 h-3.5 text-gray-400" />
                            <span>{b.expiryDate ? formatDate(b.expiryDate) : 'Không có hạn dùng'}</span>
                            {isExpiringSoon && (
                              <span title="Hạn dùng dưới 30 ngày">
                                <AlertTriangle className="w-3.5 h-3.5 text-amber-500" />
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-4 py-3.5 text-right whitespace-nowrap text-gray-700">{formatVND(b.unitCost)}</td>
                        <td className="px-4 py-3.5 text-right text-gray-500">{b.receivedQuantity}</td>
                        <td className="px-4 py-3.5 text-right font-medium text-gray-700">{b.remainingQuantity}</td>
                        <td className="px-4 py-3.5 text-right text-rose-600 font-medium">{b.damagedQuantity}</td>
                        <td className="px-4 py-3.5 text-right font-bold text-blue-700 text-sm">{b.availableQuantity}</td>
                        <td className="px-4 py-3.5 text-center">
                          <span className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-medium border ${statusInfo.bg} ${statusInfo.color}`}>
                            {statusInfo.label}
                          </span>
                        </td>
                        <td className="px-4 py-3.5 text-center">
                          <Button size="sm" variant="outline" leftIcon={<SlidersHorizontal className="w-3 h-3" />} onClick={() => handleOpenAdjust(b)}>
                            Kiểm kê lô
                          </Button>
                        </td>
                      </tr>
                    );
                  });
                  return [groupRow, ...lotRows];
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Pagination {...pagination} />

      {/* Receive Batch Modal */}
      <Modal
        isOpen={isReceiveOpen}
        onClose={() => setIsReceiveOpen(false)}
        title="Nhập lô hàng mới (Áp dụng FEFO)"
        maxWidth="lg"
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsReceiveOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" size="sm" type="submit" form="receive-batch">
              Xác nhận nhập kho
            </Button>
          </>
        }
      >
        <form id="receive-batch" onSubmit={handleReceiveBatch} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Select
              label="Sản phẩm nhập"
              value={receiveData.productId.toString()}
              onChange={(e) => setReceiveData({ ...receiveData, productId: Number(e.target.value) })}
              options={products.map((p: Product) => ({ value: p.id.toString(), label: `${p.name} (${p.skuCode})` }))}
            />
            <Select label="Nhà cung cấp" value={String(receiveData.supplierId || '')}
              onChange={(e) => setReceiveData({ ...receiveData, supplierId: Number(e.target.value) })} required
              options={[{ value: '', label: 'Chọn nhà cung cấp' }, ...suppliers.filter(s => s.isActive).map(s => ({ value: String(s.supplierId), label: s.supplierName }))]} />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <Input
              label="Mã lô (để trống để tạo tự động)"
              value={receiveData.batchCode}
              onChange={(e) => setReceiveData({ ...receiveData, batchCode: e.target.value })}
            />
            <Input
              label="Số lượng nhập"
              type="number"
              min={1}
              value={receiveData.quantity}
              onChange={(e) => setReceiveData({ ...receiveData, quantity: Number(e.target.value) })}
              required
            />
            <Input
              label="Đơn giá vốn (₫)"
              type="number"
              min={0}
              value={receiveData.unitCost}
              onChange={(e) => setReceiveData({ ...receiveData, unitCost: Number(e.target.value) })}
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Ngày sản xuất"
              type="date"
              value={receiveData.manufacturingDate || ''}
              onChange={(e) => setReceiveData({ ...receiveData, manufacturingDate: e.target.value })}
            />
            <Input
              label="Hạn sử dụng (nếu có)"
              type="date"
              value={receiveData.expiryDate}
              onChange={(e) => setReceiveData({ ...receiveData, expiryDate: e.target.value })}
            />
          </div>
        </form>
      </Modal>

      {/* Adjust Batch Modal */}
      <Modal
        isOpen={isAdjustOpen}
        onClose={() => setIsAdjustOpen(false)}
        title={`Điều chỉnh tồn kho lô: ${selectedBatch?.batchCode || ''}`}
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsAdjustOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" size="sm" type="submit" form="adjust-batch">
              Lưu kết quả kiểm kê
            </Button>
          </>
        }
      >
        <form id="adjust-batch" onSubmit={handleAdjustBatch} className="space-y-4">
          <div className="p-3 bg-gray-50 rounded-lg text-xs space-y-1">
            <p>
              Sản phẩm: <span className="font-semibold text-gray-900">{selectedBatch?.productName}</span>
            </p>
            <p>
              Tồn khả dụng hiện tại:{' '}
              <span className="font-bold text-blue-700">{selectedBatch?.availableQuantity}</span>
            </p>
            <p>
              Hạn dùng: <span className="font-medium">{formatDate(selectedBatch?.expiryDate)}</span>
            </p>
          </div>

          <div className="grid grid-cols-1 gap-3">
            <Input
              label="Số lượng cần giảm khỏi lô"
              type="number"
              min={1}
              value={adjustData.adjustmentQuantity}
              onChange={(e) =>
                setAdjustData({ ...adjustData, adjustmentQuantity: Number(e.target.value) })
              }
              helperText="Nhập số món bị thiếu hoặc phải loại khỏi tồn bán được"
              required
            />
          </div>

          <Input
            label="Lý do điều chỉnh kiểm kê"
            value={adjustData.reason}
            onChange={(e) => setAdjustData({ ...adjustData, reason: e.target.value })}
            required
          />
        </form>
      </Modal>
    </div>
  );
};
