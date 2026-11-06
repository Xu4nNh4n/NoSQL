import React, { useEffect, useRef, useState } from 'react';
import { AlertCircle, Database, Layers, RefreshCw, Send, ShieldCheck } from 'lucide-react';
import { api } from '../api/client';
import type { CashFlowReport, PaymentLogReport, IntegrationStatus, ReconciliationReport, ReconciliationRow } from '../types';
import { formatVND, formatDateTime, vietnamDateKey } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Badge } from '../components/ui/Badge';
import { Card } from '../components/ui/Card';
import { DrillReportExplanation } from '../components/analytics/DrillReportExplanation';
import { useToast } from '../components/ui/Toast';

const vietnamToday = () => vietnamDateKey();
const messageOf = (reason: unknown) => reason instanceof Error ? reason.message : 'Không nhận được phản hồi từ máy chủ';
const amountOf = (value: number | undefined) => Number(value ?? 0);
const hasDelta = (value: number) => Math.abs(Number(value)) > 0.01;

function formatReportDay(value: string | number | undefined) {
  if (value === undefined) return '—';
  const date = new Date(typeof value === 'number' ? value : value.slice(0, 10) + 'T00:00:00Z');
  return Number.isNaN(date.getTime()) ? String(value)
    : new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC' }).format(date);
}

function explainRow(row: ReconciliationRow) {
  if (row.export_available === false) return 'Nguồn sự kiện ngày này chưa được xác nhận đầy đủ; cần kiểm tra trước khi kết luận.';
  if (row.quality_status === 'PASS' && Number(row.mysql_order_count) === 0 && Number(row.mysql_payment_count) === 0 && Number(row.mysql_refund_count) === 0) return 'Không có đơn hoặc thu/hoàn trong hai nguồn của ngày này. Kết quả đang đối chiếu các số 0.';
  const netCoverage = row.net_checked_order_count == null || row.net_skipped_order_count == null
    ? 'Chưa có metadata phạm vi kiểm tra sau giảm giá; không coi mọi đơn đã được kiểm tra.'
    : 'Sau giảm giá: đã kiểm tra ' + row.net_checked_order_count + ' đơn; bỏ qua ' + row.net_skipped_order_count + ' đơn thiếu đầy đủ dữ liệu giá net (có thể gồm đơn cũ).';
  if (row.quality_status === 'PASS') return 'Các phép đối chiếu đã thực hiện đang khớp. ' + netCoverage;
  const reasons: string[] = [];
  if (hasDelta(row.item_subtotal_delta)) reasons.push('Tổng tiền hàng trước giảm giá trên đơn khác tổng các món trước giảm giá.');
  if (row.gross_mismatch_amount != null && hasDelta(row.gross_mismatch_amount)) reasons.push('Tổng trị tuyệt đối lệch trước giảm giá từng đơn: ' + formatVND(Number(row.gross_mismatch_amount)) + '.');
  if (row.net_subtotal_delta != null && hasDelta(row.net_subtotal_delta)) reasons.push('Lệch ròng sau giảm giá ở các đơn đã kiểm tra: ' + formatVND(Number(row.net_subtotal_delta)) + '.');
  if (row.net_mismatch_amount != null && hasDelta(row.net_mismatch_amount)) reasons.push('Tổng trị tuyệt đối lệch sau giảm giá từng đơn: ' + formatVND(Number(row.net_mismatch_amount)) + '; các lệch trái dấu không bù trừ trong số này.');
  if (hasDelta(row.collected_delta)) reasons.push('Cassandra ghi tiền thu ' + (Number(row.collected_delta) > 0 ? 'ít hơn ' : 'nhiều hơn ') + formatVND(Math.abs(Number(row.collected_delta))) + ' so với MySQL.');
  if (hasDelta(row.refunded_delta)) reasons.push('Cassandra ghi tiền hoàn ' + (Number(row.refunded_delta) > 0 ? 'ít hơn ' : 'nhiều hơn ') + formatVND(Math.abs(Number(row.refunded_delta))) + ' so với MySQL.');
  if (Number(row.mysql_payment_count) !== Number(row.astra_payment_count)) reasons.push('Số lượt thu tiền khác nhau.');
  if (Number(row.mysql_refund_count) !== Number(row.astra_refund_count)) reasons.push('Số lượt hoàn tiền khác nhau.');
  return (reasons.join(' ') || 'Có kết quả cần kiểm tra; xem chi tiết giao dịch bên dưới.') + ' ' + netCoverage;
}

function MoneyPair({ mysql, astra, mysqlCount, astraCount }: { mysql: number; astra: number; mysqlCount: number; astraCount: number }) {
  return <div className="space-y-1 whitespace-nowrap text-xs">
    <div><span className="text-gray-500">MySQL: </span><strong className="text-gray-900">{formatVND(Number(mysql))}</strong></div>
    <div><span className="text-gray-500">Cassandra: </span><strong className="text-gray-900">{formatVND(Number(astra))}</strong></div>
    <div className="text-[11px] text-gray-500">{mysqlCount} / {astraCount} giao dịch</div>
  </div>;
}

function Delta({ value }: { value: number }) {
  const delta = Number(value);
  return <span className={hasDelta(delta) ? 'font-bold text-rose-700' : 'font-semibold text-blue-700'}>
    {delta > 0.01 ? '+' : ''}{formatVND(hasDelta(delta) ? delta : 0)}
  </span>;
}

export const CashFlowReportsPage: React.FC = () => {
  const dataset = 'OPERATIONS' as const;
  const [fromDate, setFromDate] = useState(vietnamToday);
  const [toDate, setToDate] = useState(vietnamToday);
  const [loadedRange, setLoadedRange] = useState<{ from: string; to: string; dataset: 'OPERATIONS' | 'SIMULATED' } | null>(null);
  const [cashFlow, setCashFlow] = useState<CashFlowReport | null>(null);
  const [reconciliation, setReconciliation] = useState<ReconciliationReport | null>(null);
  const [integrationStatus, setIntegrationStatus] = useState<IntegrationStatus | null>(null);
  const [cashError, setCashError] = useState('');
  const [reconciliationError, setReconciliationError] = useState('');
  const [statusError, setStatusError] = useState('');
  const [paymentLog, setPaymentLog] = useState<PaymentLogReport | null>(null);
  const [paymentLogError, setPaymentLogError] = useState('');
  const [paymentLogLoading, setPaymentLogLoading] = useState(false);
  const paymentLogRequest = useRef<AbortController | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncAction, setSyncAction] = useState('');
  const request = useRef<AbortController | null>(null);
  const { success, error, warning } = useToast();

  const dayCount = (Date.parse(toDate + 'T00:00:00Z') - Date.parse(fromDate + 'T00:00:00Z')) / 86400000 + 1;
  const rangeError = !fromDate || !toDate || !Number.isFinite(dayCount) ? 'Chọn đủ ngày bắt đầu và kết thúc.'
    : dayCount < 1 ? 'Ngày kết thúc phải từ ngày bắt đầu trở đi.'
    : dayCount > 31 ? 'Mỗi lần xem báo cáo tối đa 31 ngày.' : '';

  const fetchReports = async (requested?: { from: string; to: string; dataset: 'OPERATIONS' | 'SIMULATED' }) => {
    if (rangeError) return;
    const range = requested ?? { from: fromDate, to: toDate, dataset };
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setIsLoading(true);
    setCashError('');
    setReconciliationError('');
    setStatusError('');
    try {
      const query = '?from=' + range.from + '&to=' + range.to + '&dataset=' + range.dataset;
      const [cash, recon, status] = await Promise.allSettled([
        api.get<CashFlowReport>('/analytics/cash-flow' + query, { signal: controller.signal }),
        api.get<ReconciliationReport>('/analytics/reconciliation' + query, { signal: controller.signal }),
        api.get<IntegrationStatus>('/integrations/status', { signal: controller.signal }),
      ]);
      if (controller.signal.aborted) return;
      setCashFlow(cash.status === 'fulfilled' ? cash.value : null);
      setCashError(cash.status === 'rejected' ? messageOf(cash.reason) : '');
      setReconciliation(recon.status === 'fulfilled' ? recon.value : null);
      setReconciliationError(recon.status === 'rejected' ? messageOf(recon.reason) : '');
      setIntegrationStatus(status.status === 'fulfilled' ? status.value : null);
      setStatusError(status.status === 'rejected' ? messageOf(status.reason) : '');
      setLoadedRange(range);
    } finally {
      if (!controller.signal.aborted) setIsLoading(false);
    }
  };

  useEffect(() => {
    void fetchReports();
    return () => { request.current?.abort(); paymentLogRequest.current?.abort(); };
  }, []);

  const fetchPaymentLog = async () => {
    if (rangeError || paymentLogLoading) return;
    paymentLogRequest.current?.abort();
    const controller = new AbortController();
    paymentLogRequest.current = controller;
    setPaymentLogLoading(true);
    setPaymentLogError('');
    setPaymentLog(null);
    try {
      const params = new URLSearchParams({ from: fromDate, to: toDate });
      const result = await api.get<PaymentLogReport>('/analytics/payment-log?' + params, { signal: controller.signal });
      if (!controller.signal.aborted) setPaymentLog(result);
    } catch (reason) {
      if (!controller.signal.aborted) setPaymentLogError(messageOf(reason));
    } finally {
      if (!controller.signal.aborted) setPaymentLogLoading(false);
    }
  };

  const handleDispatch = async () => {
    setIsSyncing(true);
    setSyncAction('Đang gửi các bản ghi đang chờ sang ASTRA và POS_LOG…');
    try {
      const result = await api.post<{ running: boolean; sent: number; attempted: number }>('/integrations/dispatch');
      if (result.running) warning('Một lượt gửi đang chạy. Chờ hoàn tất rồi xem lại hàng đợi.');
      else success('Đã gửi ' + result.sent + '/' + result.attempted + ' bản ghi trong lượt này. Tra cứu lại để đọc sự kiện hiện có trong Cassandra.');
      await fetchReports();
    } catch (reason) { error(messageOf(reason)); }
    finally { setIsSyncing(false); setSyncAction(''); }
  };

  const totalReceived = (cashFlow?.rows ?? []).reduce((sum, row) => sum + amountOf(row.collected_amount ?? row.total_received), 0);
  const totalRefunded = (cashFlow?.rows ?? []).reduce((sum, row) => sum + amountOf(row.refunded_amount ?? row.total_refunded), 0);
  const checkCount = (reconciliation?.rows ?? []).filter((row) => row.quality_status !== 'PASS').length;
  const missingDates = [...new Set([...(cashFlow?.missingExportDates ?? []), ...(reconciliation?.missingExportDates ?? [])])];
  const sources = reconciliation?.dataSources ?? cashFlow?.dataSources ?? [];
  const astraPending = integrationStatus ? (integrationStatus.astraPendingOutbox ?? integrationStatus.astra?.pending) : null;
  const posLogPending = integrationStatus?.posLog?.pending;
  const posLogSent = integrationStatus?.posLog?.sent;
  const astraSent = integrationStatus ? (integrationStatus.astraSentOutbox ?? integrationStatus.astra?.sent) : null;
  const reportErrors = [cashError, reconciliationError].filter(Boolean);
  const hasResults = !!cashFlow || !!reconciliation;
  const busy = isLoading || isSyncing;
  const rangeChanged = loadedRange && (loadedRange.from !== fromDate || loadedRange.to !== toDate || loadedRange.dataset !== dataset);
  const latestReport = reconciliation?.generatedAt ?? cashFlow?.generatedAt;

  return (
    <div className="space-y-5">
      <header><h2 className="flex items-center gap-2 text-lg font-bold text-gray-900"><Layers className="h-5 w-5 text-blue-700" />Tiền thu, tiền hoàn và độ khớp dữ liệu</h2>
        <p className="mt-1 text-sm text-gray-600">MySQL là sổ gốc. Drill đối chiếu với sự kiện Cassandra được Drill đọc trực tiếp; dữ liệu thiếu hoặc chưa cập nhật cần được xử lý trước khi kết luận chênh lệch.</p></header>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{[
        { name: 'MySQL', connected: integrationStatus?.mysqlConnected, text: 'Hóa đơn và biên nhận gốc' },
        { name: 'Cassandra', connected: integrationStatus?.cassandraConnected, text: 'Sự kiện trực tiếp theo ngày Việt Nam' },
        { name: 'Drill HTTP', connected: integrationStatus?.drillConnected, text: 'Kết nối HTTP; kết quả SQL xác nhận riêng' },
      ].map(source => <div key={source.name} className="rounded-xl border border-gray-200 bg-white p-4"><p className="text-sm font-semibold text-gray-900">{source.name}</p>
        <p className={`mt-1 text-xs font-medium ${source.connected === true ? 'text-blue-700' : source.connected === false ? 'text-amber-700' : 'text-gray-500'}`}>{source.connected === true ? 'Đã kết nối' : source.connected === false ? 'Chưa kết nối được' : 'Chưa xác nhận'}</p><p className="mt-2 text-xs text-gray-500">{source.text}</p></div>)}</div>

      <Card title="Bạn muốn xem ngày nào?">
        <div className="flex flex-col items-end gap-3 sm:flex-row">
          <Input label="Từ ngày (giờ Việt Nam)" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} disabled={busy} />
          <Input label="Đến ngày (giờ Việt Nam)" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} disabled={busy} />
          <Button onClick={() => void fetchReports()} isLoading={isLoading} disabled={isLoading || isSyncing || !!rangeError} className="w-full shrink-0 sm:w-auto" leftIcon={<RefreshCw aria-hidden="true" className="h-4 w-4" />}>Tra cứu bằng Apache Drill</Button>
        </div>
        {rangeError && <p role="alert" className="mt-2 text-xs text-rose-700">{rangeError}</p>}
        <p className="mt-3 text-xs leading-relaxed text-gray-600">Ngày báo cáo tính từ 00:00 đến trước 00:00 ngày kế tiếp theo giờ Việt Nam (UTC+7). Bạn không cần tự đổi ngày; tối đa 31 ngày mỗi lượt.</p>
        {rangeChanged && <p className="mt-2 text-xs font-medium text-amber-700">Bạn đã đổi khoảng ngày. Bấm tra cứu để cập nhật; bảng bên dưới vẫn là kết quả của lần tra cứu trước.</p>}
        {loadedRange && <p className="mt-2 text-xs text-gray-600">Khoảng ngày đã tra cứu: <strong>{formatReportDay(loadedRange.from)} – {formatReportDay(loadedRange.to)} (giờ Việt Nam)</strong>{latestReport ? ' · Nhận báo cáo lúc ' + formatDateTime(latestReport) : ''}.</p>}
      </Card>

      <div aria-live="polite" className="space-y-2">
        {isSyncing && <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">{syncAction}</p>}
        {isLoading ? <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">Drill đang đọc dữ liệu và tính báo cáo…</p> : (
          <>
            {reportErrors.length > 0 && <div role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
              <strong>Chưa lấy được đầy đủ báo cáo.</strong>
              <p className="mt-1 text-xs leading-relaxed">Truy vấn chưa thành công; chưa thể kết luận dữ liệu bị lệch. Xem lỗi và kiểm tra Drill/Cassandra.</p>
              <p className="mt-1 break-words text-xs">{reportErrors.join(' · ')}</p>
            </div>}
            {hasResults && <div className="flex flex-wrap items-center gap-2 text-xs text-gray-600"><Badge variant="info">Đã nhận kết quả từ Drill</Badge><span>Đây là kết quả truy vấn thật, không phải nhãn kết nối cố định.</span></div>}
            {astraPending != null && astraPending > 0 && <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-relaxed text-amber-900">Toàn hệ thống còn <strong>{astraPending} bản ghi chờ gửi sang Cassandra</strong>. Dữ liệu phân tích có thể chưa cập nhật đủ; con số này không chỉ riêng khoảng ngày đã chọn.</div>}
            {missingDates.length > 0 && <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-relaxed text-amber-900"><strong>{missingDates.length} ngày chưa xác nhận nguồn:</strong> {missingDates.join(', ')}. Chưa xác định được nguồn sự kiện đã đầy đủ hay chưa; kết quả hiện tại chưa chứng minh độ đầy đủ cho toàn khoảng ngày.</div>}
          </>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        {[{ label: 'Tiền đã thu', value: totalReceived, note: 'Từ sự kiện thanh toán đã ghi nhận trong Cassandra.' },
          { label: 'Tiền đã hoàn', value: totalRefunded, note: 'Chỉ tính khoản hoàn theo phiếu trả đã được xác nhận.' },
          { label: 'Tiền thu ròng', value: totalReceived - totalRefunded, note: 'Tiền đã thu trừ tiền đã hoàn; chưa trừ giá vốn và chi phí.' }].map((item) => (
          <Card key={item.label}><p className="text-xs font-semibold uppercase text-gray-600">{item.label}</p><p className="mt-2 text-xl font-bold text-gray-900">{isLoading ? '…' : cashFlow ? formatVND(item.value) : '—'}</p><p className="mt-2 text-xs leading-relaxed text-gray-500">{item.note}</p></Card>
        ))}
      </div>

      <Card title="Dòng tiền theo ngày" action={<Badge variant="info">Nguồn: {cashFlow?.sourceNames?.join(' · ') || 'Cassandra trực tiếp'}</Badge>}>
        <p className="mb-4 text-xs leading-relaxed text-gray-600">Drill cộng sự kiện PAYMENT_RECEIVED, trừ REFUND_CONFIRMED trực tiếp từ Cassandra. Bảng này chưa so sánh với MySQL.</p>
        {cashFlow && !isLoading ? <div className="overflow-x-auto">
          <table className="w-full text-left text-xs"><caption className="sr-only">Dòng tiền theo ngày Việt Nam từ sự kiện Cassandra trực tiếp</caption>
            <thead className="border-b border-gray-200 bg-gray-50 text-gray-600"><tr><th className="p-3">Ngày Việt Nam</th><th className="p-3 text-right">Tiền đã thu</th><th className="p-3 text-right">Tiền đã hoàn</th><th className="p-3 text-right">Tiền thu ròng</th></tr></thead>
            <tbody>{cashFlow.rows.length > 0 ? cashFlow.rows.map((row, index) => <tr key={index} className="border-b border-gray-100"><td className="p-3">{formatReportDay(row.event_date ?? row.report_date)}</td><td className="p-3 text-right text-blue-700">{formatVND(amountOf(row.collected_amount ?? row.total_received))}</td><td className="p-3 text-right text-rose-700">{formatVND(amountOf(row.refunded_amount ?? row.total_refunded))}</td><td className="p-3 text-right font-semibold">{formatVND(amountOf(row.net_cash_amount ?? row.net_revenue))}</td></tr>) : <tr><td colSpan={4} className="p-6 text-center text-gray-500">Truy vấn đã hoàn tất nhưng không có dòng dữ liệu.</td></tr>}</tbody>
          </table>
        </div> : <p className="py-4 text-sm text-gray-500">{isLoading ? 'Đang tổng hợp dòng tiền…' : cashError ? 'Chưa lấy được bảng dòng tiền. Xem thông báo phía trên.' : 'Chọn ngày và tra cứu để xem kết quả.'}</p>}
      </Card>

      <Card title="Đối soát: MySQL có khớp với Cassandra không?" action={reconciliation && reconciliation.rows.length > 0 && !isLoading ? <Badge variant={checkCount > 0 || missingDates.length > 0 ? 'warning' : 'success'}>{checkCount > 0 ? checkCount + ' ngày cần kiểm tra' : missingDates.length > 0 ? 'Khớp ở các ngày đã đối chiếu' : 'Các phép đối chiếu đang khớp'}</Badge> : undefined}>
        <p className="mb-3 text-xs leading-relaxed text-gray-600">MySQL là sổ giao dịch gốc. Cassandra lưu sự kiện đang có tại thời điểm truy vấn. Drill so cả <strong>số tiền và số giao dịch</strong>. Chênh lệch = MySQL − Cassandra: số dương là Cassandra ít hơn, số âm là Cassandra nhiều hơn.</p>
        <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3 text-xs leading-relaxed text-gray-600">Ví dụ minh họa: MySQL thu 100.000đ, Cassandra ghi 80.000đ → lệch +20.000đ. Có thể chưa đồng bộ hoặc sự kiện chưa được ghi nhận; cần kiểm tra trước khi kết luận thất thoát.</div>
        {reconciliation && !isLoading && reconciliation.rows.length > 0 ? <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs"><caption className="sr-only">So sánh tiền thu và hoàn giữa MySQL và Cassandra trực tiếp theo ngày Việt Nam</caption>
              <thead className="border-b border-gray-200 bg-gray-50 text-gray-600"><tr><th className="p-3 whitespace-nowrap">Ngày Việt Nam</th><th className="p-3">Tiền thu<br /><span className="font-normal">MySQL / Cassandra</span></th><th className="p-3 whitespace-nowrap">Lệch thu</th><th className="p-3">Tiền hoàn<br /><span className="font-normal">MySQL / Cassandra</span></th><th className="p-3 whitespace-nowrap">Lệch hoàn</th><th className="p-3 min-w-64">Kết quả và giải thích</th></tr></thead>
              <tbody>{reconciliation.rows.map((row, index) => <tr key={index} className="border-b border-gray-100 align-top">
                <td className="p-3 whitespace-nowrap font-medium">{formatReportDay(row.event_date)}</td>
                <td className="p-3"><MoneyPair mysql={row.mysql_collected_amount} astra={row.astra_collected_amount} mysqlCount={row.mysql_payment_count} astraCount={row.astra_payment_count} /></td>
                <td className="p-3 whitespace-nowrap"><Delta value={row.collected_delta} /></td>
                <td className="p-3"><MoneyPair mysql={row.mysql_refunded_amount} astra={row.astra_refunded_amount} mysqlCount={row.mysql_refund_count} astraCount={row.astra_refund_count} /></td>
                <td className="p-3 whitespace-nowrap"><Delta value={row.refunded_delta} /></td>
                <td className="p-3"><Badge variant={row.quality_status === 'PASS' ? 'success' : 'warning'}><span className="flex items-center gap-1">{row.quality_status === 'PASS' ? <ShieldCheck aria-hidden="true" className="h-3 w-3" /> : <AlertCircle aria-hidden="true" className="h-3 w-3" />}{row.quality_status === 'PASS' ? 'Khớp (PASS)' : 'Cần kiểm tra (CHECK)'}</span></Badge><p className="mt-2 leading-relaxed text-gray-600">{explainRow(row)}</p></td>
              </tr>)}</tbody>
            </table>
          </div>
          <details className="mt-4 rounded-lg border border-gray-200">
            <summary className="cursor-pointer rounded-lg px-3 py-2 text-xs font-semibold text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">Xem đối chiếu tổng tiền hàng trên đơn và các món</summary>
            <p className="px-3 py-2 text-xs leading-relaxed text-gray-600">Gross: so tiền hàng trước giảm giá trên hóa đơn POS với tổng đơn giá gốc × số lượng. Net: chỉ so tiền sau giảm giá ở đơn có đầy đủ giá net trên mọi dòng món; đơn thiếu dữ liệu (có thể gồm đơn cũ) được bỏ qua, không coi là đã kiểm tra. Lệch ròng có thể bù trừ; tổng trị tuyệt đối lệch từng đơn cho biết mức lệch không bù trừ. Khoản hoàn theo phiếu trả được đối chiếu riêng phía trên.</p>
            <div className="overflow-x-auto"><table className="w-full text-left text-xs">
              <thead className="bg-gray-50 text-gray-600"><tr>{['Ngày Việt Nam', 'Số đơn', 'Tiền hàng trên đơn (gross)', 'Tổng các món (gross)', 'Lệch gross', 'Đơn đã kiểm tra net', 'Đơn bỏ qua net', 'Lệch net (ròng)', 'Tổng trị tuyệt đối lệch net từng đơn'].map(label => <th key={label} className="p-3">{label}</th>)}</tr></thead>
              <tbody>{reconciliation.rows.map((row, index) => <tr key={index} className="border-t border-gray-100">
                <td className="p-3">{formatReportDay(row.event_date)}</td><td className="p-3">{row.mysql_order_count}</td>
                <td className="p-3 whitespace-nowrap">{formatVND(Number(row.mysql_order_subtotal))}</td>
                <td className="p-3 whitespace-nowrap">{row.mysql_item_gross_subtotal == null ? '—' : formatVND(Number(row.mysql_item_gross_subtotal))}</td>
                <td className="p-3 whitespace-nowrap"><Delta value={row.item_subtotal_delta} /></td>
                <td className="p-3">{row.net_checked_order_count ?? '—'}</td><td className="p-3">{row.net_skipped_order_count ?? '—'}</td>
                <td className="p-3 whitespace-nowrap">{row.net_subtotal_delta == null ? '—' : <Delta value={row.net_subtotal_delta} />}</td>
                <td className="p-3 whitespace-nowrap">{row.net_mismatch_amount == null ? '—' : formatVND(Number(row.net_mismatch_amount))}</td>
              </tr>)}</tbody>
            </table></div>
          </details>
          {checkCount > 0 && <p className="mt-3 text-xs leading-relaxed text-amber-800">Nếu có chênh lệch, gửi các sự kiện đang chờ rồi tra cứu lại đúng ngày. Nếu vẫn lệch, dùng SQL và mã truy vấn bên dưới để tìm nguyên nhân.</p>}
        </> : <p className="py-4 text-sm text-gray-500">{isLoading ? 'Drill đang so sánh hai nguồn…' : reconciliationError ? 'Chưa có kết quả đối soát. Xem thông báo phía trên.' : 'Không có dòng đối soát trong kết quả hiện tại.'}</p>}
        <p className="mt-3 text-xs text-gray-500">PASS xác nhận các phép đối chiếu đã thực hiện đang khớp; đơn bị bỏ qua kiểm tra net không được xác nhận. PASS không chứng minh toàn bộ nghiệp vụ hoặc mọi sự kiện đều đầy đủ.</p>
      </Card>

      <Card title="JSON payment log: đối chiếu từng khoản thanh toán" action={<Button variant="outline" size="sm" onClick={() => void fetchPaymentLog()} isLoading={paymentLogLoading} disabled={busy || paymentLogLoading || !!rangeError}>Tải đối chiếu JSON</Button>}>
        <p className="mb-3 text-xs leading-relaxed text-gray-600">JSON được tạo từ các khoản thanh toán đã commit trong MySQL qua POS_LOG; đây không phải nhật ký ngân hàng độc lập. Chỉ tải khi bấm nút, dùng cùng khoảng ngày Việt Nam đã chọn.</p>
        {paymentLogLoading && <p role="status" className="text-sm text-blue-700">Đang truy vấn JSON payment log…</p>}
        {paymentLogError && <p role="alert" className="text-sm text-amber-800">{paymentLogError}. Chưa có kết quả để kết luận đối chiếu.</p>}
        {paymentLog && <div className="space-y-3">
          <p className="text-xs text-gray-600">Đã truy vấn: {paymentLog.from} → {paymentLog.to} (giờ Việt Nam). Nguồn: {paymentLog.sourceNames?.join(' · ') || 'Chưa có thông tin'}.</p>
          {(paymentLog.from !== fromDate || paymentLog.to !== toDate) && <p role="status" className="text-xs text-amber-800">Khoảng ngày đã đổi. Bấm Tải đối chiếu JSON để cập nhật kết quả.</p>}
          <div className="text-xs"><Badge variant="warning">{paymentLog.missingUtcPartitions?.length ? 'Phạm vi chưa đầy đủ' : 'Chưa chứng minh đầy đủ nhật ký'}</Badge>
            <p className="mt-2 text-gray-600">Phân vùng UTC thiếu: {paymentLog.missingUtcPartitions === undefined ? 'Chưa có thông tin' : paymentLog.missingUtcPartitions.length ? paymentLog.missingUtcPartitions.join(', ') : 'Không có theo metadata backend'}. MATCH chỉ xác nhận từng bản ghi đã so sánh, không xác nhận toàn bộ khoảng ngày hoặc thời điểm ghi khớp nhau.</p>
          </div>
          <p className="text-xs text-gray-600">{paymentLog.rows.length} dòng{paymentLog.rowLimit ? ` · Giới hạn ${paymentLog.rowLimit} dòng` : ''}. {paymentLog.rowLimit && paymentLog.rows.length >= paymentLog.rowLimit ? 'Đã chạm giới hạn; kết quả có thể chưa bao gồm mọi bản ghi.' : ''}</p>
          <div className="overflow-x-auto"><table className="w-full text-left text-xs">
            <caption className="sr-only">Đối chiếu thanh toán MySQL và JSON theo payment_record_id</caption>
            <thead className="border-b bg-gray-50 text-gray-600"><tr><th className="p-3">Mã thanh toán</th><th className="p-3">Đơn MySQL / JSON</th><th className="p-3">Sự kiện MySQL / JSON</th><th className="p-3">Tiền MySQL / JSON</th><th className="p-3">Lệch tiền</th><th className="p-3">Thanh toán MySQL / JSON</th><th className="p-3">Thời điểm MySQL / JSON (UTC)</th><th className="p-3">Số log</th><th className="p-3">Đối chiếu bản ghi</th></tr></thead>
            <tbody>{paymentLog.rows.map((row) => <tr key={row.payment_record_id} className="border-b border-gray-100 align-top">
              <td className="p-3">{row.payment_record_id}</td>
              <td className="p-3">{row.mysql_order_id ?? '—'} / {row.log_order_id ?? '—'}</td>
              <td className="p-3">{row.mysql_event_type ?? '—'} / {row.log_event_type ?? '—'}</td>
              <td className="p-3 whitespace-nowrap">{row.mysql_amount == null ? '—' : formatVND(Number(row.mysql_amount))} / {row.log_amount == null ? '—' : formatVND(Number(row.log_amount))}</td>
              <td className="p-3 whitespace-nowrap"><Delta value={row.amount_delta} /></td>
              <td className="p-3">{row.mysql_payment_method ?? '—'} / {row.log_payment_method ?? '—'}</td>
              <td className="p-3">{row.mysql_created_at ?? '—'} / {row.log_event_time ?? '—'}</td>
              <td className="p-3">{row.log_count}</td>
              <td className="p-3"><Badge variant={row.comparison_status === 'MATCH' && paymentLog.missingUtcPartitions?.length === 0 ? 'info' : 'warning'}>{row.comparison_status === 'MATCH' ? 'Khớp bản ghi (MATCH)' : 'Cần kiểm tra (CHECK)'}</Badge></td>
            </tr>)}</tbody>
          </table>{paymentLog.rows.length === 0 && <p className="p-4 text-sm text-gray-500">Truy vấn trả về 0 dòng; không chứng minh nhật ký đầy đủ hoặc đối chiếu đạt.</p>}</div>
          <details className="rounded-lg border border-gray-200 p-3"><summary className="cursor-pointer text-xs font-semibold">Metadata và SQL truy vấn JSON</summary>
            <p className="mt-2 break-words text-xs">Nguồn đã đọc: {paymentLog.dataSources?.join(' · ') || 'Chưa có thông tin'}</p>
            {paymentLog.comparisonMeaning && <p className="mt-2 text-xs">{paymentLog.comparisonMeaning}</p>}
            <DrillReportExplanation title="SQL đối chiếu JSON payment log" report={{ ...paymentLog, dataSources: undefined }} description="Đối chiếu MySQL và JSON được tạo từ thanh toán đã commit; độc lập với báo cáo Cassandra phía trên." />
          </details>
        </div>}
      </Card>

      <details className="rounded-xl border border-gray-200 bg-white">
        <summary className="cursor-pointer rounded-xl px-5 py-4 text-sm font-semibold text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">Drill đã làm gì? Xem luồng xử lý và SQL thực tế</summary>
        <div className="space-y-4 border-t border-gray-100 p-5">
          <div className="grid gap-3 text-xs sm:grid-cols-3">
            <div className="rounded-lg bg-gray-50 p-3"><strong>1. Backend gửi SQL</strong><p className="mt-1 leading-relaxed text-gray-600">Khoảng ngày bạn chọn được đưa vào câu SQL cố định. Giao diện không tự cộng dữ liệu từ một trang đơn hàng.</p></div>
            <div className="rounded-lg bg-gray-50 p-3"><strong>2. Drill đọc và tính</strong><p className="mt-1 leading-relaxed text-gray-600">Dòng tiền đọc Cassandra trực tiếp. Đối soát đọc thêm MySQL và tính chênh lệch giữa hai nguồn.</p></div>
            <div className="rounded-lg bg-gray-50 p-3"><strong>3. Giao diện hiển thị</strong><p className="mt-1 leading-relaxed text-gray-600">Các bảng và thẻ tiền phía trên dùng kết quả Drill trả về, không phải số liệu minh họa.</p></div>
          </div>
          <p className="text-xs leading-relaxed text-gray-600">Drill truy vấn trực tiếp sự kiện hiện có trong Cassandra theo ngày Việt Nam. JSON payment log được truy vấn riêng bằng nút Tải đối chiếu JSON; hai báo cáo Cassandra không truy vấn JSON. Kết quả không chứng minh toàn bộ sự kiện đã được đồng bộ.</p>
          <DrillReportExplanation title="SQL báo cáo dòng tiền" report={cashFlow} description="Đọc trực tiếp sự kiện Cassandra theo ngày, cộng tiền thu và tiền hoàn để tính tiền thu ròng." />
          <DrillReportExplanation title="SQL đối soát nhiều nguồn" report={reconciliation} description="Đọc MySQL và Cassandra trực tiếp; so số giao dịch, tiền thu/hoàn và tổng tiền hàng của đơn với chi tiết món." />
          {sources.length === 0 && <p className="text-xs text-gray-500">Chưa có truy vấn thành công để xác nhận các nguồn đã đọc.</p>}
        </div>
      </details>

      <details className="rounded-xl border border-gray-200 bg-white">
        <summary className="cursor-pointer rounded-xl px-5 py-4 text-sm font-semibold text-gray-900">Outbox ASTRA và POS_LOG</summary>
        <div className="space-y-4 border-t border-gray-100 p-5">
          <p className="text-sm text-gray-600">Outbox được ghi cùng giao dịch MySQL. ASTRA gửi sự kiện sang Cassandra; POS_LOG ghi JSON payment log riêng. Không cần xuất file trước khi xem báo cáo Cassandra.</p>
          <p className="text-xs text-gray-600">Drill: {integrationStatus?.drillStatus ?? 'Chưa xác nhận'}.</p>
          {statusError && <p role="alert" className="text-xs text-amber-800">Chưa đọc được hàng đợi: {statusError}. Không coi số chưa xác nhận là 0.</p>}
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-lg border border-gray-200 p-3 text-xs"><strong className="flex items-center gap-1"><Database aria-hidden="true" className="h-3.5 w-3.5" />MySQL</strong><p className="mt-2">{integrationStatus?.mysqlConnected ? 'Đã đọc trạng thái hàng đợi.' : 'Chưa xác nhận kết nối.'}</p></div>
            <div className="rounded-lg border border-gray-200 p-3 text-xs"><strong>ASTRA: sự kiện Cassandra</strong><p className="mt-2">{astraPending ?? '—'} đang chờ / {astraSent ?? '—'} đã gửi</p></div>
            <div className="rounded-lg border border-gray-200 p-3 text-xs"><strong>POS_LOG: JSON payment log</strong><p className="mt-2">{posLogPending ?? '—'} đang chờ / {posLogSent ?? '—'} đã ghi</p><p className="mt-2">Nguồn riêng cho bảng đối chiếu JSON tải theo yêu cầu; không dùng trong báo cáo Cassandra.</p></div>
          </div>
          <Button variant="outline" size="sm" onClick={handleDispatch} disabled={busy} leftIcon={<Send aria-hidden="true" className="h-3.5 w-3.5" />}>Gửi dữ liệu đang chờ</Button>
          <p className="text-xs text-gray-600">Sau khi gửi, trang tra cứu lại khoảng ngày đang chọn. Số đã gửi không chứng minh dữ liệu lịch sử đã đầy đủ.</p>
        </div>
      </details>
    </div>
  );
};
