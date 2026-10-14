import React, { useState } from 'react';
import { CashFlowReportsPage } from './CashFlowReportsPage';
import { OperationalAnalyticsPage } from './OperationalAnalyticsPage';

type Section = 'OPERATIONS' | 'CASH';

export const ReportsPage: React.FC = () => {
  const [section, setSection] = useState<Section>('OPERATIONS');
  return <div className="space-y-5">
    <div className="flex flex-wrap gap-2" role="tablist" aria-label="Khu vực báo cáo">
      <button role="tab" aria-selected={section === 'OPERATIONS'} onClick={() => setSection('OPERATIONS')} className={`rounded-lg border px-4 py-2 text-sm font-semibold ${section === 'OPERATIONS' ? 'border-emerald-600 bg-emerald-50 text-emerald-800' : 'border-gray-200 bg-white text-gray-600'}`}>Phân tích vận hành</button>
      <button role="tab" aria-selected={section === 'CASH'} onClick={() => setSection('CASH')} className={`rounded-lg border px-4 py-2 text-sm font-semibold ${section === 'CASH' ? 'border-emerald-600 bg-emerald-50 text-emerald-800' : 'border-gray-200 bg-white text-gray-600'}`}>Dòng tiền & đối soát Astra</button>
    </div>
    {section === 'OPERATIONS' ? <OperationalAnalyticsPage /> : <CashFlowReportsPage />}
  </div>;
};
