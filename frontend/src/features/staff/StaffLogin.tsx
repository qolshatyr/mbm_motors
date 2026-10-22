import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { request } from '../../shared/api/http';
import { Logo } from '../../shared/ui/Logo';

// Spring Security processes the form itself: POST /admin/login, then a redirect
// to /admin/clients or back here with ?error. The page only supplies the CSRF token.
export function StaffLogin() {
  const [params] = useSearchParams();
  const [token, setToken] = useState('');
  const [tokenError, setTokenError] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  useEffect(() => {
    document.title = 'Вход сотрудника — MBM AutoMotors';
    request<{ headerName: string; token: string }>('/api/csrf').then((value) => setToken(value.token), () => setTokenError(true));
  }, []);
  const failed = params.has('error');
  return <>
    <header className="site-header"><div className="page-width staff-header">
      <Link to="/" className="brand" aria-label="MBM AutoMotors — главная"><Logo tagline={false} /></Link>
      <span className="staff-tag">Служебный раздел</span>
    </div></header>
    <main id="main" tabIndex={-1} className="page-width main-content staff-login">
      <section className="login-card" aria-labelledby="staff-login-title">
        <h1 id="staff-login-title">Вход сотрудника</h1>
        <p className="login-lead">Учётные записи клиентов и заявки.</p>
        {failed && <p className="form-error" role="alert">Не удалось войти. Проверьте логин и пароль.</p>}
        {params.has('logout') && <p role="status">Вы вышли из служебного раздела.</p>}
        {tokenError && <p className="form-error" role="alert">Не удалось подготовить вход. Проверьте соединение и обновите страницу.</p>}
        <form className="login-form" action="/admin/login" method="post" onSubmit={() => setSubmitting(true)}>
          <input type="hidden" name="_csrf" value={token} />
          <div className="field"><label htmlFor="username">Логин</label><input id="username" name="username" autoComplete="username" autoCapitalize="none" spellCheck={false} required maxLength={64} aria-invalid={failed} /></div>
          <div className="field"><label htmlFor="password">Пароль</label><input id="password" name="password" type="password" autoComplete="current-password" required aria-invalid={failed} /></div>
          <button type="submit" className="button button-primary button-lg" disabled={!token || submitting}>{submitting ? 'Входим…' : 'Войти'}</button>
        </form>
        <Link to="/" className="login-help">Вернуться в каталог</Link>
      </section>
    </main>
  </>;
}
