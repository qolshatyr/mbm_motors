import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface ToastMessage { id: number; text: string; error?: boolean; action?: { label: string; to: string } }
type Show = (toast: Omit<ToastMessage, 'id'>) => void;
const ToastContext = createContext<Show>(() => {});
export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const counter = useRef(0);
  const show = useCallback<Show>((next) => {
    window.clearTimeout(timer.current);
    counter.current += 1;
    setToast({ ...next, id: counter.current });
  }, []);
  const hide = useCallback(() => { window.clearTimeout(timer.current); setToast(null); }, []);
  const schedule = useCallback(() => {
    window.clearTimeout(timer.current);
    if (toast) timer.current = window.setTimeout(() => setToast(null), toast.error ? 8000 : 5000);
  }, [toast]);
  useEffect(() => { schedule(); return () => window.clearTimeout(timer.current); }, [schedule]);
  return <ToastContext.Provider value={show}>
    {children}
    <div className="toast-region" aria-live={toast?.error ? 'assertive' : 'polite'} role={toast?.error ? 'alert' : 'status'}>
      {toast && <div key={toast.id} className={`toast ${toast.error ? 'toast-error' : ''}`} onMouseEnter={() => window.clearTimeout(timer.current)} onMouseLeave={schedule} onFocus={() => window.clearTimeout(timer.current)} onBlur={schedule}>
        <span className="toast-text">{toast.text}</span>
        {toast.action && <Link className="toast-action" to={toast.action.to} onClick={hide}>{toast.action.label}</Link>}
        <button type="button" className="toast-close" aria-label="Закрыть уведомление" onClick={hide}>×</button>
      </div>}
    </div>
  </ToastContext.Provider>;
}
