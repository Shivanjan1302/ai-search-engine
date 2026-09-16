import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../services/api';
import { isTokenLike } from './token';

interface AuthContextValue {
  token: string | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  completeGoogleLogin: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const TOKEN_STORAGE_KEY = 'dronzer_token';

function readStoredToken(): string | null {
  const stored = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (isTokenLike(stored)) return stored;
  if (stored) localStorage.removeItem(TOKEN_STORAGE_KEY);
  return null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(readStoredToken);

  const applyToken = useCallback((candidate: string) => {
    if (!isTokenLike(candidate)) {
      throw new Error('The server did not return a usable session token.');
    }
    localStorage.setItem(TOKEN_STORAGE_KEY, candidate);
    setToken(candidate);
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => setToken(null);
    window.addEventListener('dronzer:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('dronzer:unauthorized', handleUnauthorized);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await api.login(email, password);
    applyToken(response.token);
  }, [applyToken]);

  const register = useCallback(async (email: string, password: string) => {
    await api.register(email, password);
  }, []);

  const completeGoogleLogin = useCallback(async () => {
    applyToken(await api.oauthToken());
  }, [applyToken]);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    token,
    isAuthenticated: Boolean(token),
    login,
    register,
    completeGoogleLogin,
    logout,
  }), [token, login, register, completeGoogleLogin, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
