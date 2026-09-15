import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { readableError } from '../utils/errors';

export function AuthPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const isRegister = location.pathname === '/register';
  const { login, register } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setNotice('');
    if (!email.trim() || !email.includes('@')) { setError('Enter a valid email address.'); return; }
    if (password.length < 6) { setError('Password must be at least 6 characters.'); return; }
    if (isRegister && password !== confirm) { setError('Passwords do not match.'); return; }
    setLoading(true);
    try {
      if (isRegister) { await register(email.trim(), password); setNotice('Account created. You can sign in now.'); navigate('/login'); }
      else { await login(email.trim(), password); navigate('/dashboard'); }
    } catch (requestError) { setError(readableError(requestError, 'The credentials were not accepted.')); }
    finally { setLoading(false); }
  }

  return <main className="auth-page"><Link className="brand auth-brand" to="/"><span className="brand-mark">D</span> dronzer</Link><div className="auth-card"><p className="eyebrow">{isRegister ? 'Start with your knowledge' : 'Welcome back'}</p><h1>{isRegister ? 'Create your workspace.' : 'Sign in to Dronzer.'}</h1><p className="muted">{isRegister ? 'Bring your documents into focus.' : 'Your source-grounded workspace is ready.'}</p>{error && <div className="alert alert-error" role="alert">{error}</div>}{notice && <div className="alert alert-success" role="status">{notice}</div>}<form onSubmit={submit}><label>Email address<input type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com" /></label><label>Password<input type="password" autoComplete={isRegister ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} placeholder="At least 6 characters" /></label>{isRegister && <label>Confirm password<input type="password" autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} placeholder="Repeat your password" /></label>}<button className="button button-primary full-width" disabled={loading}>{loading ? 'Working...' : isRegister ? 'Create account' : 'Sign in'} <span aria-hidden="true">→</span></button></form><p className="auth-switch">{isRegister ? 'Already have an account?' : 'New to Dronzer?'} <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Sign in' : 'Create an account'}</Link></p></div></main>;
}
