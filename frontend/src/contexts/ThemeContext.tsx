import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

/** light: giấy ngà · dark: terminal đen · playful: kem pastel viền đậm (neo-brutalism). */
export type Theme = 'light' | 'dark' | 'playful';

export const THEMES: Theme[] = ['light', 'dark', 'playful'];

const ThemeContext = createContext<{ theme: Theme; toggle: () => void; setTheme: (t: Theme) => void } | null>(null);

/** Chế độ đã được index.html đặt sẵn (class "dark" / "playful" trên <html>); ở đây chỉ đọc lại và cho đổi. */
const current = (): Theme => {
  const cl = document.documentElement.classList;
  return cl.contains('dark') ? 'dark' : cl.contains('playful') ? 'playful' : 'light';
};

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(current);

  useEffect(() => {
    const cl = document.documentElement.classList;
    cl.toggle('dark', theme === 'dark');
    cl.toggle('playful', theme === 'playful');
    try { localStorage.setItem('theme', theme); } catch { /* chế độ riêng tư: chỉ không nhớ được lựa chọn */ }
  }, [theme]);

  const setTheme = useCallback((t: Theme) => setThemeState(t), []);
  /** Xoay vòng sáng → tối → vui tươi. */
  const toggle = useCallback(() => setThemeState(t => THEMES[(THEMES.indexOf(t) + 1) % THEMES.length]), []);
  return <ThemeContext.Provider value={{ theme, toggle, setTheme }}>{children}</ThemeContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme phải nằm trong ThemeProvider');
  return ctx;
}
