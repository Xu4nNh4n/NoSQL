import { Moon, Sun } from 'lucide-react';
import { useTheme } from '../../contexts/ThemeContext';

/** Nút đổi chế độ tối/sáng, ô vuông viền mảnh như các nút "☆" / "⋯" của mẫu. */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const { theme, toggle } = useTheme();
  const dark = theme === 'dark';
  return (
    <button
      type="button"
      onClick={toggle}
      title={dark ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối'}
      aria-label={dark ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối'}
      aria-pressed={dark}
      className={`inline-flex h-9 items-center gap-2 rounded-md border border-gray-300 bg-white px-2.5 text-xs text-gray-700 hover:border-gray-400 hover:text-gray-900 cursor-pointer ${className}`}
    >
      {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
      <span className="hidden sm:inline">{dark ? 'Sáng' : 'Tối'}</span>
    </button>
  );
}
