import React, { useState } from 'react';
import { formatVND } from '../../utils/formatters';

export interface DaySalesData {
  dayLabel: string;
  dateStr: string;
  revenue: number;
  orderCount: number;
}

interface NativeWeeklySalesChartProps {
  data: DaySalesData[];
  isLoading?: boolean;
}

export const NativeWeeklySalesChart: React.FC<NativeWeeklySalesChartProps> = ({
  data,
  isLoading = false,
}) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  if (isLoading) {
    return (
      <div className="h-64 flex items-center justify-center bg-gray-50/50 rounded-xl border border-dashed border-gray-200">
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <svg className="animate-spin h-5 w-5 text-blue-600" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Đang tải dữ liệu thu tiền...
        </div>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="h-64 flex flex-col items-center justify-center bg-gray-50/50 rounded-xl border border-dashed border-gray-200 text-sm text-gray-500">
        <p>Chưa có số liệu thu tiền trong khoảng ngày này</p>
      </div>
    );
  }

  const maxRevenue = Math.max(...data.map((d) => d.revenue), 1000000);
  const chartHeight = 180;
  const barWidth = 32;

  return (
    <div className="w-full relative">
      {/* Tooltip */}
      {hoveredIndex !== null && data[hoveredIndex] && (
        <div className="absolute top-2 right-4 bg-slate-900 text-white text-xs px-3 py-1.5 rounded-lg shadow-md z-10 pointer-events-none transition-all">
          <p className="font-semibold text-blue-400">{data[hoveredIndex].dayLabel} ({data[hoveredIndex].dateStr})</p>
          <p>Tiền đã thu: {formatVND(data[hoveredIndex].revenue)}</p>
          <p className="text-gray-300">Lượt thu tiền: {data[hoveredIndex].orderCount}</p>
        </div>
      )}

      {/* SVG Container */}
      <div className="w-full overflow-x-auto">
        <svg
          viewBox={`0 0 ${data.length * 80 + 40} ${chartHeight + 40}`}
          className="w-full h-56 select-none"
        >
          {/* Horizontal Grid lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
            const y = chartHeight - ratio * (chartHeight - 20) + 10;
            return (
              <g key={ratio}>
                <line
                  x1="10"
                  y1={y}
                  x2={data.length * 80 + 30}
                  y2={y}
                  stroke="#F1F5F9"
                  strokeWidth="1"
                  strokeDasharray={ratio === 0 ? '0' : '4 4'}
                />
              </g>
            );
          })}

          {/* Bars */}
          {data.map((item, index) => {
            const barHeight = Math.max((item.revenue / maxRevenue) * (chartHeight - 20), 0);
            const x = 30 + index * 80;
            const y = chartHeight - barHeight + 10;
            const isHovered = hoveredIndex === index;

            return (
              <g
                key={index}
                className="cursor-pointer transition-opacity"
                onMouseEnter={() => setHoveredIndex(index)}
                onMouseLeave={() => setHoveredIndex(null)}
              >
                {/* Background Hover highlight */}
                <rect
                  x={x - 14}
                  y="10"
                  width={barWidth + 28}
                  height={chartHeight + 25}
                  fill={isHovered ? '#EFF6FF' : 'transparent'}
                  rx="6"
                  className="transition-colors"
                />

                {/* The Bar */}
                <rect
                  x={x}
                  y={y}
                  width={barWidth}
                  height={barHeight}
                  fill={isHovered ? '#1D4ED8' : '#2563EB'}
                  rx="4"
                  className="transition-all duration-200"
                />

                {/* Day Label */}
                <text
                  x={x + barWidth / 2}
                  y={chartHeight + 30}
                  textAnchor="middle"
                  className={`text-[11px] font-medium ${
                    isHovered ? 'fill-blue-700 font-semibold' : 'fill-gray-500'
                  }`}
                >
                  {item.dayLabel}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
};
