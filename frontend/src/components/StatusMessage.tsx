import type { ReactNode } from 'react';

export function StatusMessage({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'success' }) { return <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>{children}</div>; }
