import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  DollarSign,
  TrendingUp,
  AlertTriangle,
  PackageX,
  PlusCircle,
  Database,
  ArrowRight,
  RefreshCw,
} from 'lucide-react';
import { api } from '../api/client';
import type { Product, DashboardReport, IntegrationStatus } from '../types';
import { normalizeProduct } from '../types';
import { formatVND, vietnamDateKey } from '../utils/formatters';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { NativeWeeklySalesChart, type DaySalesData } from '../components/chart/NativeWeeklySalesChart';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(true);
  const [report, setReport] = useState<DashboardReport | null>(null);
  const [reportError, setReportError] = useState('');
  const [lowStockProducts, setLowStockProducts] = useState<Product[]>([]);
  const [integrationStatus, setIntegrationStatus] = useState<IntegrationStatus | null>(null);

  const fetchDashboardData = async () => {
    setIsLoading(true);
    setReportError('');
    try {
      const to = new Date(vietnamDateKey() + 'T00:00:00Z');
      const from = new Date(to);
      from.setUTCDate(from.getUTCDate() - 6);
      const [reportRes, lowStockRes, syncRes] = await Promise.allSettled([
        api.get<DashboardReport>(`/analytics/dashboard?from=${from.toISOString().slice(0, 10)}&to=${to.toISOString().slice(0, 10)}`),
        api.get<any>('/products/low-stock'),
        api.get<IntegrationStatus>('/integrations/status'),
      ]);

      if (reportRes.status === 'fulfilled') {
        setReport(reportRes.value);
      } else {
        setReport(null);
        setReportError(reportRes.reason instanceof Error ? reportRes.reason.message : 'Không tải được số liệu tổng hợp');
      }
      if (lowStockRes.status === 'fulfilled') {
        const raw = Array.isArray(lowStockRes.value) ? lowStockRes.value : (lowStockRes.value as any)?.content || [];
        setLowStockProducts(raw.map(normalizeProduct));
      }
      if (syncRes.status === 'fulfilled') {
        setIntegrationStatus(syncRes.value);
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const rows = report?.rows ?? [];
  const totalCollected = rows.reduce((sum, row) => sum + Number(row.collected_amount), 0);
  const totalRefunded = rows.reduce((sum, row) => sum + Number(row.refunded_amount), 0);
  const netCash = totalCollected - totalRefunded;
  const paymentCount = rows.reduce((sum, row) => sum + Number(row.payment_count), 0);
  const lowStockCount = report ? Number(rows[0]?.low_stock_count ?? 0) : '—';
  const outOfStockCount = report ? Number(rows[0]?.out_of_stock_count ?? 0) : '—';

  // Drill aggregates the complete ledger; dates and the chart use Vietnam calendar dates.
  const chartData: DaySalesData[] = React.useMemo(() => {
    const dayNames = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    const today = vietnamDateKey();
    return (report?.rows ?? []).map((row) => {
      const d = typeof row.event_date === 'number'
        ? new Date(row.event_date) : new Date(`${String(row.event_date).slice(0, 10)}T00:00:00Z`);
      return {
        dayLabel: d.toISOString().slice(0, 10) === today ? 'Hôm nay' : dayNames[d.getUTCDay()],
        dateStr: `${d.getUTCDate()}/${d.getUTCMonth() + 1}`,
        revenue: Number(row.collected_amount),
        orderCount: Number(row.payment_count),
      };
    });
  }, [report]);

  return (
    <div className="space-y-6">
      {/* Top Banner: Actions & Refresh */}
      <div className="page-heading">
        <div>
          <h2 className="text-base font-bold text-gray-900">Tổng quan hoạt động siêu thị</h2>
          <p className="text-xs text-gray-500">
            Tổng hợp toàn bộ giao dịch 7 ngày Việt Nam qua Apache Drill; tồn kho hiện tại theo FEFO
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={fetchDashboardData}
          >
            Làm mới
          </Button>
          <Button
            size="sm"
            variant="primary"
            leftIcon={<PlusCircle className="w-3.5 h-3.5" />}
            onClick={() => navigate('/inventory')}
          >
            Nhập lô hàng mới
          </Button>
        </div>
      </div>

      {reportError && (
        <div role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          Không tải được báo cáo: {reportError}. Các KPI chưa có số liệu.
        </div>
      )}
      {/* 4 KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Card 1: Total Sales */}
        <Card className="hover:border-blue-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
              Tổng tiền đã thu
            </span>
            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-bold text-gray-900">{report ? formatVND(totalCollected) : '—'}</div>
            <p className="text-[11px] text-gray-400 mt-0.5">Tiền thu trong 7 ngày Việt Nam</p>
          </div>
        </Card>

        {/* Card 2: Net Revenue */}
        <Card className="hover:border-blue-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
              Tiền thu ròng
            </span>
            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
              <TrendingUp className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-bold text-gray-900">{report ? formatVND(netCash) : '—'}</div>
            <p className="text-[11px] text-gray-400 mt-0.5">Đã trừ {report ? formatVND(totalRefunded) : '—'} hoàn tiền</p>
          </div>
        </Card>

        {/* Card 3: Low Stock */}
        <Card className="hover:border-amber-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
              Sắp hết hàng
            </span>
            <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
              <AlertTriangle className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-bold text-amber-600">{lowStockCount} mã</div>
            <p className="text-[11px] text-gray-400 mt-0.5">Dưới ngưỡng đặt hàng tối thiểu</p>
          </div>
        </Card>

        {/* Card 4: Out of Stock */}
        <Card className="hover:border-rose-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
              Hết hàng trong kho
            </span>
            <div className="w-8 h-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
              <PackageX className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl font-bold text-rose-600">{outOfStockCount} mã</div>
            <p className="text-[11px] text-gray-400 mt-0.5">Tồn khả dụng bằng 0</p>
          </div>
        </Card>
      </div>

      {/* Middle Section: Weekly Sales Chart & Big Data Status */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <Card
            title="Tiền đã thu trong 7 ngày Việt Nam"
            action={
              <span className="text-xs text-gray-500 font-medium">
                Lượt thu tiền: {report ? paymentCount : '—'}
              </span>
            }
          >
            <NativeWeeklySalesChart data={chartData} isLoading={isLoading} />
          </Card>
        </div>

        <div>
          <Card title="Trạng thái tích hợp Big Data">
            <div className="space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-gray-100">
                <div className="flex items-center gap-2 text-xs text-gray-700">
                  <Database className="w-4 h-4 text-blue-600" />
                  <span>MySQL Chính (Source of Truth)</span>
                </div>
                <Badge variant={integrationStatus?.mysqlConnected ? 'success' : 'warning'}>
                  {integrationStatus?.mysqlConnected ? 'Đã kết nối' : 'Chưa xác nhận'}
                </Badge>
              </div>

              <div className="space-y-2 text-xs">
                <div className="flex justify-between text-gray-600">
                  <span>Outbox POS_LOG (JSON):</span>
                  <span className="font-semibold text-gray-800">
                    {integrationStatus?.posLog?.pending ?? '—'} chờ / {integrationStatus?.posLog?.sent ?? '—'} đã ghi
                  </span>
                </div>
                <div className="flex justify-between text-gray-600">
                  <span>Outbox ASTRA (Cassandra):</span>
                  <span className="font-semibold text-gray-800">
                    {integrationStatus?.astraPendingOutbox ?? integrationStatus?.astra?.pending ?? '—'} chờ / {integrationStatus?.astraSentOutbox ?? integrationStatus?.astra?.sent ?? '—'} đã gửi
                  </span>
                </div>
                <div className="flex justify-between text-gray-600">
                  <span>Apache Drill:</span>
                  <span className="font-medium text-blue-700">
                    {report ? 'Đã nhận báo cáo' : 'Chưa có báo cáo'}
                  </span>
                </div>
              </div>

              <div className="pt-2">
                <Button
                  size="sm"
                  variant="outline"
                  className="w-full text-xs"
                  onClick={() => navigate('/reports')}
                  rightIcon={<ArrowRight className="w-3.5 h-3.5" />}
                >
                  Xem đối soát chi tiết
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Bottom Section: Low Stock Products Table */}
      <Card
        title="Cảnh báo: Sản phẩm tồn kho thấp cần bổ sung"
        action={
          <Button
            size="sm"
            variant="ghost"
            onClick={() => navigate('/inventory')}
            rightIcon={<ArrowRight className="w-3.5 h-3.5" />}
          >
            Quản lý kho
          </Button>
        }
      >
        <div className="overflow-x-auto -mx-5 -mb-5">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b border-gray-200 text-gray-600 font-medium">
              <tr>
                <th className="px-5 py-3">Sản phẩm</th>
                <th className="px-5 py-3">Mã SKU</th>
                <th className="px-5 py-3">Danh mục</th>
                <th className="px-5 py-3 text-right">Tồn khả dụng</th>
                <th className="px-5 py-3 text-right">Ngưỡng tối thiểu</th>
                <th className="px-5 py-3 text-center">Trạng thái</th>
                <th className="px-5 py-3 text-center">Hành động</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {lowStockProducts.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-8 text-gray-400">
                    Hiện không có sản phẩm nào chạm ngưỡng tồn thấp. Kho hàng đang ở mức an toàn!
                  </td>
                </tr>
              ) : (
                lowStockProducts.slice(0, 5).map((prod) => {
                  const available = prod.availableQuantity ?? prod.totalQuantity;
                  return (
                    <tr key={prod.id} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-3.5 font-medium text-gray-900">{prod.name}</td>
                      <td className="px-5 py-3.5 font-mono text-gray-500">{prod.skuCode}</td>
                      <td className="px-5 py-3.5 text-gray-600">{prod.categoryName || 'Mặc định'}</td>
                      <td className="px-5 py-3.5 text-right font-bold text-amber-600">
                        {available} {prod.unit}
                      </td>
                      <td className="px-5 py-3.5 text-right text-gray-500">
                        {prod.minStockLevel} {prod.unit}
                      </td>
                      <td className="px-5 py-3.5 text-center">
                        <Badge variant={available === 0 ? 'danger' : 'warning'}>
                          {available === 0 ? 'Hết hàng' : 'Sắp hết'}
                        </Badge>
                      </td>
                      <td className="px-5 py-3.5 text-center">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => navigate('/inventory')}
                        >
                          Nhập lô
                        </Button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
