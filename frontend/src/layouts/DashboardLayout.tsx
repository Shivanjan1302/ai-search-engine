import { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';

interface Props { active: string; onChange: (value: string) => void; children: ReactNode; }
export function DashboardLayout({ active, onChange, children }: Props) {
  const { logout } = useAuth();
  return <div className="app-shell"><aside className="sidebar"><a className="brand" href="/"><span className="brand-mark">D</span> dronzer</a><div className="sidebar-rule" /><p className="nav-label">WORKSPACE</p><nav><button className={active === 'overview' ? 'nav-item active' : 'nav-item'} onClick={() => onChange('overview')}>Overview</button><button className={active === 'documents' ? 'nav-item active' : 'nav-item'} onClick={() => onChange('documents')}>Documents</button><button className={active === 'ask' ? 'nav-item active' : 'nav-item'} onClick={() => onChange('ask')}>Ask Dronzer</button><button className={active === 'notes' ? 'nav-item active' : 'nav-item'} onClick={() => onChange('notes')}>Notes</button></nav><div className="sidebar-bottom"><span className="status-dot" /> API connected<button className="nav-item logout" onClick={logout}>Sign out</button></div></aside><main className="main-content"><header className="mobile-header"><a className="brand" href="/"><span className="brand-mark">D</span> dronzer</a><button className="text-button" onClick={logout}>Sign out</button></header>{children}</main></div>;
}
