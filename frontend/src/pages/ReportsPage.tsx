import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { BarChart3, ShieldCheck, ArrowRight, History } from 'lucide-react';
import { CashFlowReportsPage } from './CashFlowReportsPage';
import { OperationalAnalyticsPage } from './OperationalAnalyticsPage';
import { StockHistoryPage } from './StockHistoryPage';

export const ReportsPage = () => {
  const { role } = useAuth();
  const [section, setSection] = useState<'OPERATIONS' | 'CASH' | 'STOCK'>('OPERATIONS');
  return <div className="space-y-6">
    <header><p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Apache Drill · báo cáo đa nguồn</p>
      <h1 className="mt-1 text-2xl font-bold text-gray-900">Đối soát & Big Data</h1>
      <p className="mt-2 text-sm text-gray-600">Xem tình hình bán hàng, tồn kho và kiểm tra tiền thu/hoàn giữa các nguồn dữ liệu.</p></header>
    <div className="grid gap-3 rounded-xl border border-gray-200 bg-white p-4 md:grid-cols-4">
      {[['MySQL', 'Hóa đơn, tiền thu/hoàn và lô hàng gốc'], ['JSON payment log', 'Đối chiếu riêng theo yêu cầu; tạo từ thanh toán đã commit'], ['Cassandra', 'Drill đọc trực tiếp sự kiện hiện có theo ngày Việt Nam'], ['Apache Drill', 'Đọc, kết hợp và tính kết quả báo cáo']].map(([name, text]) =>
        <div key={name} className="flex gap-2"><ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-blue-600" /><div><p className="text-sm font-semibold text-gray-900">{name}</p><p className="mt-1 text-xs leading-relaxed text-gray-500">{text}</p></div></div>)}
    </div>
    {role === 'ADMIN' && <div className="rounded-xl border border-gray-200 bg-white p-4">
      <p className="text-sm font-semibold text-gray-900">Giá nhập theo lô</p>
      <p className="mt-1 text-xs text-gray-500">Xem giá nhập thực tế đã lưu trên từng lô, chênh lệch với lô trước và giá trị nhập, tồn của lô qua Apache Drill.</p>
      <Link to="/purchase-costs" className="mt-3 inline-flex min-h-10 items-center gap-2 text-sm font-semibold text-blue-700 underline underline-offset-2">Xem giá nhập theo lô <ArrowRight className="h-4 w-4" aria-hidden="true" /></Link>
    </div>}
    <div className="grid gap-3 sm:grid-cols-3" role="tablist" aria-label="Chọn mục báo cáo">
      {[{ id: 'OPERATIONS' as const, icon: BarChart3, name: 'Bán hàng & tồn kho', text: 'Bán gì nhiều? Lô nào sắp hết hạn? Cần nhập thêm gì?', sources: 'MySQL' },
        { id: 'CASH' as const, icon: ShieldCheck, name: 'Dòng tiền & đối soát', text: 'Đã thu, hoàn bao nhiêu? Hai nguồn có khớp nhau không?', sources: 'MySQL + Cassandra trực tiếp' },
        { id: 'STOCK' as const, icon: History, name: 'Lịch sử kho', text: 'Tồn đầu, nhập, bán, nhận trả và tồn cuối theo ngày.', sources: 'Snapshot suy ra trong Cassandra' }].map(tab =>
        <button key={tab.id} id={'tab-' + tab.id} role="tab" aria-controls="report-panel" aria-selected={section === tab.id} onClick={() => setSection(tab.id)}
          className={'rounded-xl border p-4 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ' + (section === tab.id ? 'border-blue-600 bg-blue-50' : 'border-gray-200 bg-white hover:border-blue-300')}>
          <div className="flex items-center gap-2"><tab.icon className="h-5 w-5 text-blue-700" /><span className="font-semibold text-gray-900">{tab.name}</span></div>
          <p className="mt-2 text-sm text-gray-600">{tab.text}</p><p className="mt-2 text-xs font-medium text-blue-700">{tab.sources}</p>
        </button>)}
    </div>
    <section id="report-panel" role="tabpanel" aria-labelledby={'tab-' + section}>
      {section === 'OPERATIONS' ? <OperationalAnalyticsPage /> : section === 'CASH' ? <CashFlowReportsPage /> : <StockHistoryPage />}
    </section>
  </div>;
};
