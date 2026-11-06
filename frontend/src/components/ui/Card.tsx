import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  title?: string;
  action?: React.ReactNode;
  headerAction?: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({ children, className = '', title, action, headerAction }) => {
  const rightAction = action || headerAction;
  return (
    <section className={`bg-white border border-gray-200 rounded-lg overflow-hidden ${className}`}>
      {title && (
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
          {rightAction && <div>{rightAction}</div>}
        </div>
      )}
      <div className="p-5">{children}</div>
    </section>
  );
};
