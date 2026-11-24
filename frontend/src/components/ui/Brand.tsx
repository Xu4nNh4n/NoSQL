export const BRAND_NAME = 'Siêu thị Uy Tín';

/** Logo mặt cười. Màu cố định nên nhìn giống nhau ở mọi giao diện và trên hóa đơn. */
export function BrandLogo({ className = 'h-8 w-8' }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} role="img" aria-label={`Logo ${BRAND_NAME}`}>
      <circle cx="16" cy="16" r="14" fill="#ffd84d" stroke="#1a1a1a" strokeWidth="2" />
      <ellipse cx="11.2" cy="12.8" rx="1.8" ry="2.4" fill="#1a1a1a" />
      <ellipse cx="20.8" cy="12.8" rx="1.8" ry="2.4" fill="#1a1a1a" />
      <path d="M8.5 18.5c1.6 4 4.2 5.8 7.5 5.8s5.9-1.8 7.5-5.8" fill="none" stroke="#1a1a1a" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  );
}
