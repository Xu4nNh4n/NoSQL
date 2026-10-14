import React, { useEffect, useRef, useState } from 'react';
import { AlertCircle, Database, Download, Layers, RefreshCw, Send, ShieldCheck } from 'lucide-react';
import { api } from '../api/client';
import type { CashFlowReport, IntegrationStatus, ReconciliationReport, ReconciliationRow } from '../types';
import { formatVND, formatDateTime } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Badge } from '../components/ui/Badge';
import { Card } from '../components/ui/Card';
import { DrillReportExplanation } from '../components/analytics/DrillReportExplanation';
import { useToast } from '../components/ui/Toast';

const utcToday = () => new Date().toISOString().slice(0, 10);
const messageOf = (reason: unknown) => reason instanceof Error ? reason.message : 'Không nhận được phản hồi từ máy chủ';
const amountOf = (value: number | undefined) => Number(value ?? 0);
const hasDelta = (value: number) => Math.abs(Number(value)) > 0.01;

function formatUtcDay(value: string | number | undefined) {
  if (value === undefined) return '—';
  const date = new Date(typeof value === 'number' ? value : value.slice(0, 10) + 'T00:00:00Z');
  return Number.isNaN(date.getTime()) ? String(value)
    : new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC' }).format(date);
}

function explainRow(row: ReconciliationRow) {
  if (row.quality_status === 'PASS') return 'Tiền, số giao dịch và tổng tiền hàng đang khớp.';
  const reasons: string[] = [];
  if (hasDelta(row.item_subtotal_delta)) reasons.push('Tổng tiền hàng trên đơn khác tổng các món.');
  if (hasDelta(row.collected_delta)) reasons.push('Astra ghi tiền thu ' + (Number(row.collected_delta) > 0 ? 'ít hơn ' : 'nhiều hơn ') + formatVND(Math.abs(Number(row.collected_delta))) + ' so với MySQL.');
  if (hasDelta(row.refunded_delta)) reasons.push('Astra ghi tiền hoàn ' + (Number(row.refunded_delta) > 0 ? 'ít hơn ' : 'nhiều hơn ') + formatVND(Math.abs(Number(row.refunded_delta))) + ' so với MySQL.');
  if (Number(row.mysql_payment_count) !== Number(row.astra_payment_count)) reasons.push('Số lượt thu tiền khác nhau.');
  if (Number(row.mysql_refund_count) !== Number(row.astra_refund_count)) reasons.push('Số lượt hoàn tiền khác nhau.');
  return reasons.join(' ') || 'Có kết quả cần kiểm tra; xem chi tiết giao dịch bên dưới.';
}

function MoneyPair({ mysql, astra, mysqlCount, astraCount }: { mysql: number; astra: number; mysqlCount: number; astraCount: number }) {
  return <div className="space-y-1 whitespace-nowrap text-xs">
    <div><span className="text-gray-500">MySQL: </span><strong className="text-gray-900">{formatVND(Number(mysql))}</strong></div>
    <div><span className="text-gray-500">Astra: </span><strong className="text-gray-900">{formatVND(Number(astra))}</strong></div>
    <div className="text-[11px] text-gray-500">{mysqlCount} / {astraCount} giao dịch</div>
  </div>;
}

function Delta({ value }: { value: number }) {
  const delta = Number(value);
  return <span className={hasDelta(delta) ? 'font-bold text-rose-700' : 'font-semibold text-emerald-700'}>
    {delta > 0.01 ? '+' : ''}{formatVND(hasDelta(delta) ? delta : 0)}
  </span>;
}

export const CashFlowReportsPage: React.FC = () => {
  const [fromDate, setFromDate] = useState(utcToday);
  const [toDate, setToDate] = useState(utcToday);
  const [exportDate, setExportDate] = useState(utcToday);
  const [loadedRange, setLoadedRange] = useState<{ from: string; to: string } | null>(null);
  const [cashFlow, setCashFlow] = useState<CashFlowReport | null>(null);
  const [reconciliation, setReconciliation] = useState<ReconciliationReport | null>(null);
  const [integrationStatus, setIntegrationStatus] = useState<IntegrationStatus | null>(null);
  const [cashError, setCashError] = useState('');
  const [reconciliationError, setReconciliationError] = useState('');
  const [statusError, setStatusError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncAction, setSyncAction] = useState('');
  const syncPanel = useRef<HTMLDetailsElement>(null);
  const { success, error, warning } = useToast();

  const dayCount = (Date.parse(toDate + 'T00:00:00Z') - Date.parse(fromDate + 'T00:00:00Z')) / 86400000 + 1;
  const rangeError = !fromDate || !toDate || !Number.isFinite(dayCount) ? 'Chọn đủ ngày bắt đầu và kết thúc.'
    : dayCount < 1 ? 'Ngày kết thúc phải từ ngày bắt đầu trở đi.'
    : dayCount > 31 ? 'Mỗi lần xem báo cáo tối đa 31 ngày.' : '';

  const fetchReports = async () => {
    if (rangeError) return;
    const range = { from: fromDate, to: toDate };
    setIsLoading(true);
    setCashError('');
    setReconciliationError('');
    setStatusError('');
    try {
      const query = '?from=' + range.from + '&to=' + range.to;
      const [cash, recon, status] = await Promise.allSettled([
        api.get<CashFlowReport>('/analytics/cash-flow' + query),
        api.get<ReconciliationReport>('/analytics/reconciliation' + query),
        api.get<IntegrationStatus>('/integrations/status'),
      ]);
      setCashFlow(cash.status === 'fulfilled' ? cash.value : null);
      setCashError(cash.status === 'rejected' ? messageOf(cash.reason) : '');
      setReconciliation(recon.status === 'fulfilled' ? recon.value : null);
      setReconciliationError(recon.status === 'rejected' ? messageOf(recon.reason) : '');
      setIntegrationStatus(status.status === 'fulfilled' ? status.value : null);
      setStatusError(status.status === 'rejected' ? messageOf(status.reason) : '');
      setLoadedRange(range);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { void fetchReports(); }, []);

  const openSync = () => {
    if (syncPanel.current) {
      syncPanel.current.open = true;
      syncPanel.current.scrollIntoView({ block: 'start' });
    }
  };

  const handleDispatch = async () => {
    setIsSyncing(true);
    setSyncAction('Đang gửi các bản ghi đang chờ sang Astra/MongoDB…');
    try {
      const result = await api.post<{ running: boolean; sent: number; attempted: number }>('/integrations/dispatch');
      if (result.running) warning('Một lượt gửi đang chạy. Chờ hoàn tất rồi xem lại hàng đợi.');
      else success('Đã gửi ' + result.sent + '/' + result.attempted + ' bản ghi trong lượt này. Xuất lại ngày cần xem để cập nhật báo cáo.');
      await fetchReports();
    } catch (reason) { error(messageOf(reason)); }
    finally { setIsSyncing(false); setSyncAction(''); }
  };

  const handleExport = async () => {
    setIsSyncing(true);
    setSyncAction('Đang xuất sự kiện ngày UTC ' + exportDate + ' cho Drill đọc…');
    try {
      const result = await api.post<{ rows: number; date: string }>('/integrations/astra/events/export?date=' + exportDate);
      success('Đã xuất ' + result.rows + ' sự kiện cho ngày UTC ' + result.date + '.');
      await fetchReports();
    } catch (reason) { error(messageOf(reason)); }
    finally { setIsSyncing(false); setSyncAction(''); }
  };

  const handleSyncCatalog = async () => {
    setIsSyncing(true);
    setSyncAction('Đang tiếp nhận yêu cầu đồng bộ catalog sản phẩm…');
    try {
      const result = await api.post<{ queued: number }>('/integrations/catalog/sync');
      success('Đã tiếp nhận yêu cầu đồng bộ catalog cho ' + result.queued + ' sản phẩm. Xem hàng đợi MongoDB để theo dõi.');
      await fetchReports();
    } catch (reason) { error(messageOf(reason)); }
    finally { setIsSyncing(false); setSyncAction(''); }
  };

  const totalReceived = (cashFlow?.rows ?? []).reduce((sum, row) => sum + amountOf(row.collected_amount ?? row.total_received), 0);
  const totalRefunded = (cashFlow?.rows ?? []).reduce((sum, row) => sum + amountOf(row.refunded_amount ?? row.total_refunded), 0);
  const checkCount = (reconciliation?.rows ?? []).filter((row) => row.quality_status !== 'PASS').length;
  const missingDates = [...new Set([...(cashFlow?.missingExportDates ?? []), ...(reconciliation?.missingExportDates ?? [])])];
  const sources = reconciliation?.dataSources ?? cashFlow?.dataSources ?? [];
  const astraPending = integrationStatus ? Number(integrationStatus.astraPendingOutbox ?? integrationStatus.astra?.pending ?? 0) : null;
  const mongoPending = integrationStatus ? Number(integrationStatus.mongodbPendingOutbox ?? integrationStatus.mongodb?.pending ?? 0) : null;
  const mongoSent = integrationStatus ? Number(integrationStatus.mongodbSentOutbox ?? integrationStatus.mongodb?.sent ?? 0) : null;
  const astraSent = integrationStatus ? Number(integrationStatus.astraSentOutbox ?? integrationStatus.astra?.sent ?? 0) : null;
  const reportErrors = [cashError, reconciliationError].filter(Boolean);
  const noExport = reportErrors.some((message) => message.toLowerCase().includes('chưa có bản xuất astra'));
  const hasResults = !!cashFlow || !!reconciliation;
  const busy = isLoading || isSyncing;
  const rangeChanged = loadedRange && (loadedRange.from !== fromDate || loadedRange.to !== toDate);
  const latestReport = reconciliation?.generatedAt ?? cashFlow?.generatedAt;

  return (
    <div className="space-y-5">
      <div className="rounded-xl border border-emerald-100 bg-emerald-50/50 p-5">
        <h2 className="flex items-center gap-2 text-lg font-bold text-gray-900"><Layers aria-hidden="true" className="h-5 w-5 text-emerald-700" />Đối soát & Big Data</h2>
        <p className="mt-2 max-w-3xl text-sm leading-relaxed text-gray-700">
          Xem tiền đã thu, tiền đã hoàn và kiểm tra dữ liệu giữa các nguồn có khớp không.
          Apache Drill đọc dữ liệu và tính báo cáo; giao dịch bán hàng vẫn do backend xử lý trong MySQL.
        </p>
        <div className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
          <div className="rounded-lg border border-emerald-100 bg-white p-3"><strong>1. Chọn ngày</strong><p className="mt-1 text-xs text-gray-600">Chọn ngày hoặc khoảng ngày cần xem, theo UTC.</p></div>
          <div className="rounded-lg border border-emerald-100 bg-white p-3"><strong>2. Tra cứu bằng Drill</strong><p className="mt-1 text-xs text-gray-600">Backend gửi SQL cho Drill để đọc và tổng hợp dữ liệu.</p></div>
          <div className="rounded-lg border border-emerald-100 bg-white p-3"><strong>3. Đọc kết quả</strong><p className="mt-1 text-xs text-gray-600">Xem dòng tiền và các ngày đang khớp hoặc cần kiểm tra.</p></div>
        </div>
      </div>

      <Card title="Bạn muốn xem ngày nào?">
        <div className="flex flex-col items-end gap-3 sm:flex-row">
          <Input label="Từ ngày (UTC)" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} disabled={busy} />
          <Input label="Đến ngày (UTC)" type="date" value={toDate} onChange={(event) => { setToDate(event.target.value); setExportDate(event.target.value); }} disabled={busy} />
          <Button onClick={fetchReports} isLoading={isLoading} disabled={isSyncing || !!rangeError} className="w-full shrink-0 sm:w-auto" leftIcon={<RefreshCw aria-hidden="true" className="h-4 w-4" />}>Tra cứu bằng Apache Drill</Button>
        </div>
        {rangeError && <p role="alert" className="mt-2 text-xs text-rose-700">{rangeError}</p>}
        <p className="mt-3 text-xs leading-relaxed text-gray-600">Giờ Việt Nam = UTC + 7. Ví dụ 02:00 ngày 09/10 ở Việt Nam thuộc ngày UTC 08/10. Khoảng ngày tối đa 31 ngày.</p>
        {rangeChanged && <p className="mt-2 text-xs font-medium text-amber-700">Bạn đã đổi khoảng ngày. Bấm tra cứu để cập nhật; bảng bên dưới vẫn là kết quả của lần tra cứu trước.</p>}
        {loadedRange && <p className="mt-2 text-xs text-gray-600">Khoảng ngày đã tra cứu: <strong>{formatUtcDay(loadedRange.from)} – {formatUtcDay(loadedRange.to)} UTC</strong>{latestReport ? ' · Nhận báo cáo lúc ' + formatDateTime(latestReport) : ''}.</p>}
      </Card>

      <div aria-live="polite" className="space-y-2">
        {isSyncing && <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">{syncAction}</p>}
        {isLoading ? <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">Drill đang đọc dữ liệu và tính báo cáo…</p> : (
          <>
            {reportErrors.length > 0 && <div role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
              <strong>{noExport ? 'Chưa có bản xuất sự kiện cho khoảng ngày này.' : 'Chưa lấy được đầy đủ báo cáo.'}</strong>
              <p className="mt-1 text-xs leading-relaxed">{noExport ? 'Cần xuất dữ liệu từ Astra trước khi Drill đọc được. Chưa có bản xuất không có nghĩa là doanh số bằng 0.' : 'Truy vấn chưa thành công; chưa thể kết luận dữ liệu bị lệch. Xem lỗi và kiểm tra Drill/storage.'}</p>
              <p className="mt-1 break-words text-xs">{reportErrors.join(' · ')}</p>
              {noExport && <Button variant="outline" size="sm" onClick={openSync} className="mt-3">Mở hướng dẫn đồng bộ dữ liệu</Button>}
            </div>}
            {hasResults && <div className="flex flex-wrap items-center gap-2 text-xs text-gray-600"><Badge variant="info">Đã nhận kết quả từ Drill</Badge><span>Đây là kết quả truy vấn thật, không phải nhãn kết nối cố định.</span></div>}
            {astraPending !== null && astraPending > 0 && <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-relaxed text-amber-900">Toàn hệ thống còn <strong>{astraPending} bản ghi chờ gửi sang Astra</strong>. Dữ liệu phân tích có thể chưa cập nhật đủ; con số này không chỉ riêng khoảng ngày đã chọn.</div>}
            {missingDates.length > 0 && <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-relaxed text-amber-900"><strong>{missingDates.length} ngày chưa có bản xuất:</strong> {missingDates.join(', ')}. Chưa xác định được là không có sự kiện hay chưa xuất dữ liệu; kết quả hiện tại chưa chứng minh độ đầy đủ cho toàn khoảng ngày.</div>}
          </>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        {[{ label: 'Tiền đã thu', value: totalReceived, note: 'Từ sự kiện thanh toán đã ghi nhận trong Astra.' },
          { label: 'Tiền đã hoàn', value: totalRefunded, note: 'Gồm hoàn tiền hủy đơn và theo phiếu trả đã xác nhận.' },
          { label: 'Tiền thu ròng', value: totalReceived - totalRefunded, note: 'Tiền đã thu trừ tiền đã hoàn; chưa trừ giá vốn và chi phí.' }].map((item) => (
          <Card key={item.label}><p className="text-xs font-semibold uppercase text-gray-600">{item.label}</p><p className="mt-2 text-xl font-bold text-gray-900">{isLoading ? '…' : cashFlow ? formatVND(item.value) : '—'}</p><p className="mt-2 text-xs leading-relaxed text-gray-500">{item.note}</p></Card>
        ))}
      </div>

      <Card title="Dòng tiền theo ngày" action={<Badge variant="info">Nguồn: bản xuất Astra</Badge>}>
        <p className="mb-4 text-xs leading-relaxed text-gray-600">Drill cộng sự kiện PAYMENT_RECEIVED, trừ REFUND_CONFIRMED từ JSON/Parquet. Bảng này chưa so sánh với MySQL.</p>
        {cashFlow && !isLoading ? <div className="overflow-x-auto">
          <table className="w-full text-left text-xs"><caption className="sr-only">Dòng tiền theo ngày UTC từ các bản xuất Astra</caption>
            <thead className="border-b border-gray-200 bg-gray-50 text-gray-600"><tr><th className="p-3">Ngày UTC</th><th className="p-3 text-right">Tiền đã thu</th><th className="p-3 text-right">Tiền đã hoàn</th><th className="p-3 text-right">Tiền thu ròng</th></tr></thead>
            <tbody>{cashFlow.rows.length > 0 ? cashFlow.rows.map((row, index) => <tr key={index} className="border-b border-gray-100"><td className="p-3">{formatUtcDay(row.event_date ?? row.report_date)}</td><td className="p-3 text-right text-emerald-700">{formatVND(amountOf(row.collected_amount ?? row.total_received))}</td><td className="p-3 text-right text-rose-700">{formatVND(amountOf(row.refunded_amount ?? row.total_refunded))}</td><td className="p-3 text-right font-semibold">{formatVND(amountOf(row.net_cash_amount ?? row.net_revenue))}</td></tr>) : <tr><td colSpan={4} className="p-6 text-center text-gray-500">Truy vấn đã hoàn tất nhưng không có dòng dữ liệu.</td></tr>}</tbody>
          </table>
        </div> : <p className="py-4 text-sm text-gray-500">{isLoading ? 'Đang tổng hợp dòng tiền…' : cashError ? 'Chưa lấy được bảng dòng tiền. Xem thông báo phía trên.' : 'Chọn ngày và tra cứu để xem kết quả.'}</p>}
      </Card>

      <Card title="Đối soát: MySQL có khớp với Astra không?" action={reconciliation && reconciliation.rows.length > 0 && !isLoading ? <Badge variant={checkCount > 0 || missingDates.length > 0 ? 'warning' : 'success'}>{checkCount > 0 ? checkCount + ' ngày cần kiểm tra' : missingDates.length > 0 ? 'Khớp ở các ngày đã đối chiếu' : 'Các phép đối chiếu đang khớp'}</Badge> : undefined}>
        <p className="mb-3 text-xs leading-relaxed text-gray-600">MySQL là sổ giao dịch gốc. Astra là bản sao sự kiện đã xuất thành JSON/Parquet. Drill so cả <strong>số tiền và số giao dịch</strong>. Chênh lệch = MySQL − Astra: số dương là Astra ít hơn, số âm là Astra nhiều hơn.</p>
        <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3 text-xs leading-relaxed text-gray-600">Ví dụ minh họa: MySQL thu 100.000đ, Astra ghi 80.000đ → lệch +20.000đ. Có thể chưa đồng bộ hoặc chưa xuất lại; cần kiểm tra trước khi kết luận thất thoát.</div>
        {reconciliation && !isLoading && reconciliation.rows.length > 0 ? <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs"><caption className="sr-only">So sánh tiền thu và hoàn giữa MySQL và bản xuất Astra theo ngày UTC</caption>
              <thead className="border-b border-gray-200 bg-gray-50 text-gray-600"><tr><th className="p-3 whitespace-nowrap">Ngày UTC</th><th className="p-3">Tiền thu<br /><span className="font-normal">MySQL / Astra</span></th><th className="p-3 whitespace-nowrap">Lệch thu</th><th className="p-3">Tiền hoàn<br /><span className="font-normal">MySQL / Astra</span></th><th className="p-3 whitespace-nowrap">Lệch hoàn</th><th className="p-3 min-w-64">Kết quả và giải thích</th></tr></thead>
              <tbody>{reconciliation.rows.map((row, index) => <tr key={index} className="border-b border-gray-100 align-top">
                <td className="p-3 whitespace-nowrap font-medium">{formatUtcDay(row.event_date)}</td>
                <td className="p-3"><MoneyPair mysql={row.mysql_collected_amount} astra={row.astra_collected_amount} mysqlCount={row.mysql_payment_count} astraCount={row.astra_payment_count} /></td>
                <td className="p-3 whitespace-nowrap"><Delta value={row.collected_delta} /></td>
                <td className="p-3"><MoneyPair mysql={row.mysql_refunded_amount} astra={row.astra_refunded_amount} mysqlCount={row.mysql_refund_count} astraCount={row.astra_refund_count} /></td>
                <td className="p-3 whitespace-nowrap"><Delta value={row.refunded_delta} /></td>
                <td className="p-3"><Badge variant={row.quality_status === 'PASS' ? 'success' : 'warning'}><span className="flex items-center gap-1">{row.quality_status === 'PASS' ? <ShieldCheck aria-hidden="true" className="h-3 w-3" /> : <AlertCircle aria-hidden="true" className="h-3 w-3" />}{row.quality_status === 'PASS' ? 'Khớp (PASS)' : 'Cần kiểm tra (CHECK)'}</span></Badge><p className="mt-2 leading-relaxed text-gray-600">{explainRow(row)}</p></td>
              </tr>)}</tbody>
            </table>
          </div>
          <details className="mt-4 rounded-lg border border-gray-200">
            <summary className="cursor-pointer rounded-lg px-3 py-2 text-xs font-semibold text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500">Xem đối chiếu tổng tiền hàng trên đơn và các món</summary>
            <p className="px-3 py-2 text-xs leading-relaxed text-gray-600">So tiền hàng trên đơn với tổng chi tiết món, gồm cả đơn chưa thu tiền. Hai tổng này không nhất thiết bằng tiền đã thu, vì trạng thái thanh toán và phí vận chuyển khác nhau.</p>
            <div className="overflow-x-auto"><table className="w-full text-left text-xs"><thead className="bg-gray-50 text-gray-600"><tr><th className="p-3">Ngày UTC</th><th className="p-3">Số đơn</th><th className="p-3">Tiền hàng trên đơn</th><th className="p-3">Tổng các món</th><th className="p-3">Chênh lệch</th></tr></thead><tbody>{reconciliation.rows.map((row, index) => <tr key={index} className="border-t border-gray-100"><td className="p-3">{formatUtcDay(row.event_date)}</td><td className="p-3">{row.mysql_order_count}</td><td className="p-3 whitespace-nowrap">{formatVND(Number(row.mysql_order_subtotal))}</td><td className="p-3 whitespace-nowrap">{formatVND(Number(row.mysql_item_subtotal))}</td><td className="p-3 whitespace-nowrap"><Delta value={row.item_subtotal_delta} /></td></tr>)}</tbody></table></div>
          </details>
          {checkCount > 0 && <p className="mt-3 text-xs leading-relaxed text-amber-800">Nếu có chênh lệch, gửi các sự kiện đang chờ rồi xuất lại đúng ngày và tra cứu lại. Nếu vẫn lệch, dùng SQL và mã truy vấn bên dưới để tìm nguyên nhân.</p>}
        </> : <p className="py-4 text-sm text-gray-500">{isLoading ? 'Drill đang so sánh hai nguồn…' : reconciliationError ? 'Chưa có kết quả đối soát. Xem thông báo phía trên.' : 'Không có dòng đối soát trong kết quả hiện tại.'}</p>}
        <p className="mt-3 text-xs text-gray-500">PASS xác nhận các phép đối chiếu đang khớp trong dữ liệu đã đọc; không chứng minh toàn bộ nghiệp vụ hoặc mọi bản xuất đều đầy đủ.</p>
      </Card>

      <details className="rounded-xl border border-gray-200 bg-white">
        <summary className="cursor-pointer rounded-xl px-5 py-4 text-sm font-semibold text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500">Drill đã làm gì? Xem luồng xử lý và SQL thực tế</summary>
        <div className="space-y-4 border-t border-gray-100 p-5">
          <div className="grid gap-3 text-xs sm:grid-cols-3">
            <div className="rounded-lg bg-gray-50 p-3"><strong>1. Backend gửi SQL</strong><p className="mt-1 leading-relaxed text-gray-600">Khoảng ngày bạn chọn được đưa vào câu SQL cố định. Giao diện không tự cộng dữ liệu từ một trang đơn hàng.</p></div>
            <div className="rounded-lg bg-gray-50 p-3"><strong>2. Drill đọc và tính</strong><p className="mt-1 leading-relaxed text-gray-600">Dòng tiền đọc bản xuất Astra. Đối soát đọc thêm MySQL và tính chênh lệch giữa hai nguồn.</p></div>
            <div className="rounded-lg bg-gray-50 p-3"><strong>3. Giao diện hiển thị</strong><p className="mt-1 leading-relaxed text-gray-600">Các bảng và thẻ tiền phía trên dùng kết quả Drill trả về, không phải số liệu minh họa.</p></div>
          </div>
          <p className="text-xs leading-relaxed text-gray-600">Drill đọc Cassandra qua file xuất, không kết nối trực tiếp Astra. MongoDB giữ catalog và được dùng trong SQL JOIN sản phẩm khác của đề tài; hai báo cáo tiền trên trang này không đọc MongoDB. JSON là bản xuất sự kiện; Parquet là bản chuẩn hóa và nén. Backend chọn Parquet khi còn khớp JSON hiện tại.</p>
          <DrillReportExplanation title="SQL báo cáo dòng tiền" report={cashFlow} description="Đọc bản xuất Astra theo ngày, cộng tiền thu và tiền hoàn để tính tiền thu ròng." />
          <DrillReportExplanation title="SQL đối soát nhiều nguồn" report={reconciliation} description="Đọc MySQL và bản xuất Astra; so số giao dịch, tiền thu/hoàn và tổng tiền hàng của đơn với chi tiết món." />
          {sources.length === 0 && <p className="text-xs text-gray-500">Chưa có truy vấn thành công để xác nhận các file đã đọc.</p>}
        </div>
      </details>

      <details ref={syncPanel} className="rounded-xl border border-gray-200 bg-white">
        <summary className="cursor-pointer rounded-xl px-5 py-4 text-sm font-semibold text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500">Đồng bộ dữ liệu — mở khi chưa có bản xuất hoặc báo cáo chưa cập nhật</summary>
        <div className="space-y-4 border-t border-gray-100 p-5">
          <p className="text-sm leading-relaxed text-gray-600">Outbox là hàng đợi ghi cùng giao dịch MySQL. Tác vụ nền gửi dữ liệu sang Astra/MongoDB; tác vụ xuất cập nhật ngày UTC hôm nay và hôm qua. Với ngày cũ hoặc cần cập nhật ngay, dùng các bước bên dưới.</p>
          {statusError && <p role="alert" className="text-xs text-amber-800">Chưa đọc được hàng đợi: {statusError}. Không coi số chưa xác nhận là 0.</p>}
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-lg border border-gray-200 p-3 text-xs"><strong className="flex items-center gap-1 text-gray-800"><Database aria-hidden="true" className="h-3.5 w-3.5" />MySQL</strong><p className="mt-2 text-gray-600">{integrationStatus?.mysqlConnected ? 'Đã đọc được trạng thái hàng đợi.' : 'Chưa xác nhận được kết nối từ yêu cầu này.'}</p></div>
            <div className="rounded-lg border border-gray-200 p-3 text-xs"><strong>Astra: sự kiện nghiệp vụ</strong><p className="mt-2 text-gray-600">{astraPending ?? '—'} chờ / {astraSent ?? '—'} đã gửi</p></div>
            <div className="rounded-lg border border-gray-200 p-3 text-xs"><strong>MongoDB: catalog sản phẩm</strong><p className="mt-2 text-gray-600">{mongoPending ?? '—'} chờ / {mongoSent ?? '—'} đã gửi</p></div>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-lg border border-gray-200 p-4"><h4 className="text-sm font-semibold">1. Gửi sự kiện đang chờ</h4><p className="mt-2 text-xs leading-relaxed text-gray-600">Gửi một nhóm bản ghi mỗi lượt. Nếu còn chờ, đợi tác vụ nền hoặc gửi tiếp; chưa xuất file mới thì báo cáo vẫn có thể đọc dữ liệu cũ.</p><Button variant="outline" size="sm" className="mt-3" onClick={handleDispatch} disabled={busy} leftIcon={<Send aria-hidden="true" className="h-3.5 w-3.5" />}>Gửi dữ liệu đang chờ</Button></div>
            <div className="rounded-lg border border-gray-200 p-4"><h4 className="text-sm font-semibold">2. Xuất đúng ngày cần xem</h4><p className="mt-2 text-xs leading-relaxed text-gray-600">Lấy sự kiện đã có trong Astra thành JSON cho Drill đọc. Mỗi lần xuất một ngày; xem nhiều ngày thì xuất từng ngày còn thiếu.</p><div className="mt-3 flex flex-wrap items-end gap-2"><Input label="Ngày xuất (UTC)" type="date" value={exportDate} onChange={(event) => setExportDate(event.target.value)} disabled={busy} /><Button variant="outline" size="sm" onClick={handleExport} disabled={busy || !exportDate} leftIcon={<Download aria-hidden="true" className="h-3.5 w-3.5" />}>Xuất ngày này</Button></div></div>
          </div>
          <p className="text-xs leading-relaxed text-gray-600"><strong>3. Tra cứu lại.</strong> Sau thao tác, trang tự tra cứu lại khoảng ngày đang chọn. Nếu Astra chưa có sự kiện, xuất file sẽ báo chưa có dữ liệu; không tự tạo doanh số. Xuất lại JSON có thể làm báo cáo dùng JSON thay cho Parquet cũ, cho đến khi chuyển đổi Parquet lại.</p>
          <details className="rounded-lg border border-gray-200"><summary className="cursor-pointer rounded-lg p-3 text-xs font-semibold text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500">Catalog MongoDB — chỉ dùng khi cần đồng bộ thông tin sản phẩm</summary><div className="space-y-3 px-3 pb-3"><p className="text-xs leading-relaxed text-gray-600">Yêu cầu đồng bộ catalog không sửa tiền và không giải quyết chênh lệch MySQL/Astra. Dữ liệu được đưa qua hàng đợi, không phải cập nhật tức thì chỉ sau một lần bấm.</p><Button variant="outline" size="sm" onClick={handleSyncCatalog} disabled={busy}>Yêu cầu đồng bộ catalog</Button></div></details>
        </div>
      </details>
    </div>
  );
};

\n