import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
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

function GoogleCallback() {
  const navigate = useNavigate();
  const location = useLocation();
  const { completeGoogleLogin } = useAuth();
  const [error, setError] = useState('');
  const exchangeStarted = useRef(false);

  useEffect(() => {
    if (new URLSearchParams(location.search).get('oauth') === 'failed') {
      setError('Google sign-in was cancelled or could not be completed.');
      return;
    }
    // The callback cookie is single use: make sure the exchange happens exactly once
    // even when the effect runs twice (React StrictMode) or is re-rendered.
    if (exchangeStarted.current) return;
    exchangeStarted.current = true;

    completeGoogleLogin().then(() => navigate('/dashboard', { replace: true })).catch(() => {
      setError('Google sign-in could not be completed.');
    });
  }, [completeGoogleLogin, location.search, navigate]);

  return <main className="auth-page"><div className="auth-card"><p className="eyebrow">Authentication</p><h1>{error || 'Completing sign-in...'}</h1>{error && <button className="button button-dark" onClick={() => navigate('/login')}>Back to login</button>}</div></main>;
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
    <Route path="/auth/callback" element={<GoogleCallback />} />
    <Route path="/dashboard" element={<ProtectedRoute />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>;
}
