import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { AccountProfile, LoginResponse, User, RoleName } from '../types';
import { api } from '../api/client';

interface AuthContextType {
  user: User | null;
  role: RoleName | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<User>;
  updateProfile: (profile: AccountProfile) => User;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('auth_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const role = user?.role || null;
  const isAuthenticated = !!user && !!localStorage.getItem('access_token');

  const updateProfile = useCallback((profile: AccountProfile): User => {
    const current: User = { ...profile, id: profile.userId, active: true };
    localStorage.setItem('auth_user', JSON.stringify(current));
    setUser(current);
    return current;
  }, []);

  const refreshProfile = useCallback(async () => {
    return updateProfile(await api.get<AccountProfile>('/auth/me'));
  }, [updateProfile]);

  const logout = useCallback(async () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token'); // Dọn khóa còn lại từ phiên bản cũ.
    localStorage.removeItem('auth_user');
    setUser(null);
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null);
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    if (localStorage.getItem('access_token')) {
      refreshProfile().catch(() => {}).finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }

    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
    };
  }, [refreshProfile]);

  const login = async (username: string, password: string): Promise<LoginResponse> => {
    const res = await api.post<LoginResponse>('/auth/login', { username, password }, { skipAuth: true });
    
    localStorage.setItem('access_token', res.accessToken);
    localStorage.removeItem('refresh_token'); // Dọn khóa còn lại từ phiên bản cũ.

    const authUser: User = {
      id: res.userId,
      username: res.username,
      role: res.role,
      active: true,
    };

    localStorage.setItem('auth_user', JSON.stringify(authUser));
    setUser(authUser);
    await refreshProfile().catch(() => {});

    return res;
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        role,
        isAuthenticated,
        isLoading,
        login,
        logout,
        refreshProfile,
        updateProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
