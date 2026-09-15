import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useAuth } from './auth/AuthContext';
import { LandingPage } from './pages/LandingPage';
import { AuthPage } from './pages/AuthPage';
import { DashboardPage } from './pages/DashboardPage';

function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <DashboardPage /> : <Navigate to="/login" replace />;
}

function AuthRedirect() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <Navigate to="/dashboard" replace /> : <AuthPage />;
}

export default function App() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  useEffect(() => {
    const handleUnauthorized = () => { logout(); navigate('/login', { replace: true }); };
    window.addEventListener('dronzer:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('dronzer:unauthorized', handleUnauthorized);
  }, [logout, navigate]);

  return <Routes>
    <Route path="/" element={<LandingPage />} />
    <Route path="/login" element={<AuthRedirect />} />
    <Route path="/register" element={<AuthRedirect />} />
    <Route path="/dashboard" element={<ProtectedRoute />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>;
}
