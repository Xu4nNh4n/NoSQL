import type { DrillReportMetadata } from '../../types';
import { formatDateTime } from '../../utils/formatters';

interface Props {
  title: string;
  report: DrillReportMetadata | null;
  description: string;
}

export function DrillReportExplanation({ title, report, description }: Props) {
  return (
    <div className="space-y-3 rounded-lg border border-gray-200 bg-white p-4">
      <h4 className="text-sm font-semibold text-gray-900">{title}</h4>
      <p className="text-sm text-gray-600">{description}</p>
      {!report ? (
        <p className="text-xs text-gray-500">Tra cứu thành công để xem SQL và mã truy vấn thực tế của báo cáo này.</p>
      ) : (
        <>
          <dl className="grid grid-cols-1 gap-2 text-xs sm:grid-cols-2">
            {report.timeZone && <div><dt className="text-gray-500">Múi giờ báo cáo</dt><dd className="mt-1 font-medium text-gray-900">Giờ Việt Nam (UTC+7)</dd></div>}
            <div><dt className="text-gray-500">Kết quả truy vấn</dt><dd className="mt-1 font-medium text-gray-900">Đã nhận kết quả từ Drill</dd></div>
            <div><dt className="text-gray-500">Thời điểm nhận báo cáo</dt><dd className="mt-1 font-medium text-gray-900">{report.generatedAt ? formatDateTime(report.generatedAt) : 'Chưa có thông tin'}</dd></div>
            {report.requestElapsedMs !== undefined && <div><dt className="text-gray-500">Thời gian phản hồi Drill</dt><dd className="mt-1 font-medium text-gray-900">{report.requestElapsedMs} ms <span className="font-normal text-gray-500">(không phải benchmark chuẩn)</span></dd></div>}
            {report.dataset && <div><dt className="text-gray-500">Bộ dữ liệu</dt><dd className="mt-1 font-medium text-gray-900">{report.dataset === 'SIMULATED' ? 'Mô phỏng' : 'Vận hành'}</dd></div>}
            {report.sourceNames && <div className="sm:col-span-2"><dt className="text-gray-500">Nguồn dữ liệu</dt><dd className="mt-1 font-medium text-gray-900">{report.sourceNames.join(' · ')}</dd></div>}
            {report.stockAsOf && <div><dt className="text-gray-500">Ngày chụp tồn kho</dt><dd className="mt-1 font-medium text-gray-900">{report.stockAsOf}</dd></div>}
            <div className="sm:col-span-2"><dt className="text-gray-500">Mã truy vấn Drill</dt><dd className="mt-1 break-all font-mono text-gray-800">{report.queryIds?.length ? report.queryIds.join(' · ') : report.queryId || 'Drill chưa trả mã truy vấn'}</dd></div>
          </dl>
          {report.dataSources && report.dataSources.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <caption className="mb-2 text-left font-medium text-gray-700">Nguồn dữ liệu backend xác nhận đã truy vấn</caption>
                <thead className="border-b border-gray-200 text-gray-500"><tr><th className="py-2 pr-4">Nguồn</th><th className="py-2 pr-4">Ngày nguồn</th><th className="py-2 pr-4">Định dạng</th><th className="py-2">Thời điểm truy vấn</th></tr></thead>
                <tbody>{report.dataSources.map((source, index) => (
                  <tr key={source.reference ?? index} className="border-b border-gray-100">
                    <td className="py-2 pr-4">{source.sourceName ?? source.reference ?? source.format}</td>
                    <td className="py-2 pr-4 whitespace-nowrap">{source.dateVietnam ? source.dateVietnam + ' (Việt Nam)' : source.date ? source.date + ' (UTC)' : '—'}</td><td className="py-2 pr-4">{source.format}</td>
                    <td className="py-2">{source.queriedAt ? formatDateTime(source.queriedAt) : 'Chưa có thông tin'}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
          <details className="rounded-lg border border-gray-200">
            <summary className="cursor-pointer rounded-lg px-3 py-2 text-xs font-semibold text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">Xem SQL thực tế đã gửi tới Drill</summary>
            {report.sql ? (
              <pre className="max-h-80 overflow-auto border-t border-gray-200 bg-slate-950 p-4 text-xs leading-relaxed text-slate-100"><code>{report.sql}</code></pre>
            ) : <p className="p-3 text-xs text-gray-500">Backend chưa trả SQL. Sau khi cập nhật backend, tra cứu lại để xem.</p>}
          </details>
          {report.profilesUrl && /^https?:\/\//.test(report.profilesUrl) && (
            <p className="text-xs text-gray-600"><a href={report.profilesUrl} target="_blank" rel="noreferrer" className="font-semibold text-blue-700 underline underline-offset-2">Mở Drill Profiles</a> rồi tìm theo mã truy vấn phía trên để xem cách Drill thực hiện SQL.</p>
          )}
        </>
      )}
    </div>
  );
}
