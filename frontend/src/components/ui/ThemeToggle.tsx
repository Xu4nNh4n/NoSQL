import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown, Moon, Sparkles, Sun } from 'lucide-react';
import { THEMES, useTheme, type Theme } from '../../contexts/ThemeContext';

const META: Record<Theme, { label: string; hint: string; Icon: typeof Sun }> = {
  light: { label: 'Sáng', hint: 'Giấy ngà, nhấn vàng', Icon: Sun },
  dark: { label: 'Tối', hint: 'Terminal đen', Icon: Moon },
  playful: { label: 'Vui tươi', hint: 'Pastel, viền đậm', Icon: Sparkles },
};

/** Nút chọn giao diện: bấm mở menu Sáng / Tối / Vui tươi. */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const { theme, setTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('mousedown', onDown); document.removeEventListener('keydown', onKey); };
  }, [open]);

  const { Icon, label } = META[theme];
  return (
    <div ref={ref} className={`relative ${className}`}>
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        title="Đổi giao diện"
        aria-label={`Giao diện: ${label}. Bấm để đổi`}
        aria-haspopup="menu"
        aria-expanded={open}
        className="ui-chip inline-flex h-9 items-center gap-2 rounded-md border border-gray-300 bg-white px-2.5 text-xs text-gray-700 hover:border-gray-400 hover:text-gray-900 cursor-pointer"
      >
        <Icon className="h-4 w-4" />
        <span className="hidden sm:inline">{label}</span>
        <ChevronDown className={`h-3.5 w-3.5 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div role="menu" aria-label="Chọn giao diện"
          className="ui-pop absolute right-0 z-50 mt-2 w-52 overflow-hidden rounded-lg border border-gray-200 bg-white p-1 shadow-lg">
          {THEMES.map(t => {
            const m = META[t];
            const active = t === theme;
            return (
              <button key={t} type="button" role="menuitemradio" aria-checked={active}
                onClick={() => { setTheme(t); setOpen(false); }}
                className={`flex w-full cursor-pointer items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-xs ${active ? 'bg-gray-100 font-semibold text-gray-900' : 'text-gray-700 hover:bg-gray-50'}`}>
                <span className={`theme-swatch theme-swatch-${t}`} aria-hidden="true"><m.Icon className="h-3.5 w-3.5" /></span>
                <span className="flex-1">
                  <span className="block">{m.label}</span>
                  <span className="block text-[10px] font-normal text-gray-500">{m.hint}</span>
                </span>
                {active && <Check className="h-4 w-4 text-gray-900" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
