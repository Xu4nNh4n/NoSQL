import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { RoleName, TokenResponse, User } from '../types';
import { api, REFRESH_KEY, TOKEN_KEY, USER_KEY } from '../api/client';

interface AuthContextType {
  user: User | null;
  role: RoleName | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function readUser(): User | null {
  try {
    const saved = localStorage.getItem(USER_KEY);
    return saved ? (JSON.parse(saved) as User) : null;
  } catch {
    return null;
  }
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUserState] = useState<User | null>(readUser);
  const [isLoading, setIsLoading] = useState(true);

  const setUser = useCallback((u: User) => {
    localStorage.setItem(USER_KEY, JSON.stringify(u));
    setUserState(u);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    if (refreshToken) {
      await api.post('/auth/logout', { refreshToken }, { skipAuth: true }).catch(() => undefined);
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    setUserState(null);
  }, []);

  useEffect(() => {
    const onUnauthorized = () => setUserState(null);
    window.addEventListener('auth:unauthorized', onUnauthorized);
    if (localStorage.getItem(TOKEN_KEY)) {
      api.get<User>('/auth/me').then(setUser).catch(() => undefined).finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
    return () => window.removeEventListener('auth:unauthorized', onUnauthorized);
  }, [setUser]);

  const login = async (username: string, password: string) => {
    const res = await api.post<TokenResponse>('/auth/login', { username, password }, { skipAuth: true });
    localStorage.setItem(TOKEN_KEY, res.accessToken);
    if (res.refreshToken) localStorage.setItem(REFRESH_KEY, res.refreshToken);
    else localStorage.removeItem(REFRESH_KEY);
    setUser(res.user);
    return res.user;
  };

  return (
    <AuthContext.Provider
      value={{ user, role: user?.role ?? null, isAuthenticated: !!user && !!localStorage.getItem(TOKEN_KEY), isLoading, login, logout, setUser }}
    >
      {children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
