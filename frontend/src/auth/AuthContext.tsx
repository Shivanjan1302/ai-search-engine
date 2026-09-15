import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../services/api';

interface AuthContextValue {
  token: string | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('dronzer_token'));

  useEffect(() => {
    const handleUnauthorized = () => setToken(null);
    window.addEventListener('dronzer:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('dronzer:unauthorized', handleUnauthorized);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    token,
    isAuthenticated: Boolean(token),
    login: async (email, password) => {
      const nextToken = await api.login(email, password);
      localStorage.setItem('dronzer_token', nextToken);
      setToken(nextToken);
    },
    register: async (email, password) => {
      await api.register(email, password);
    },
    logout: () => {
      localStorage.removeItem('dronzer_token');
      setToken(null);
    },
  }), [token]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
