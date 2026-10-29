import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/http';
import { salesPhone } from '../../shared/contacts';
import { safeReturn, useLogin } from './session';

export function Login() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const loginMutation = useLogin();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const errorRef = useRef<HTMLParagraphElement>(null);
  const error = loginMutation.error;
  const message = error instanceof ApiError && error.status === 401 ? 'Неверный логин или пароль.' : error?.message;
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  return <div className="login-layout">
    <section className="login-card" aria-labelledby="login-title">
      <h1 id="login-title">Вход для оптовых клиентов</h1>
      <p className="login-lead">Сайт работает только для СТО и оптовых покупателей MBM по договору.</p>
      <form className="login-form" onSubmit={(event) => {
        event.preventDefault();
        if (loginMutation.isPending || !username.trim() || !password) return;
        loginMutation.mutate({ username: username.trim(), password }, { onSuccess: () => navigate(safeReturn(params.get('from')), { replace: true }) });
      }}>
        <div className="field"><label htmlFor="login-username">Логин</label><input id="login-username" name="username" autoComplete="username" autoCapitalize="none" spellCheck={false} required maxLength={64} placeholder="Логин от менеджера" value={username} onChange={(event) => setUsername(event.target.value)} aria-describedby={message ? 'login-error' : undefined} aria-invalid={Boolean(message)} /></div>
        <div className="field"><label htmlFor="login-password">Пароль</label><input id="login-password" name="password" type="password" autoComplete="current-password" required maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} aria-describedby={message ? 'login-error' : undefined} aria-invalid={Boolean(message)} /></div>
        {message && <p id="login-error" ref={errorRef} tabIndex={-1} className="form-error" role="alert">{message}</p>}
        <button type="submit" className="button button-primary button-lg" disabled={loginMutation.isPending}>{loginMutation.isPending ? 'Входим…' : 'Войти'}</button>
      </form>
      <p className="muted login-help">Нет логина или забыли пароль — обратитесь к своему менеджеру MBM.</p>
    </section>
    <section className="login-aside" aria-labelledby="login-aside-title">
      <h2 id="login-aside-title">Кабинет для СТО и оптовиков</h2>
      <p>Доступ выдаёт менеджер MBM. В кабинете:</p>
      <ul>
        <li>оптовые цены и остатки склада в Алматы</li>
        <li>заказ списком артикулов из Excel или мессенджера</li>
        <li>история заявок и повтор заказа в один клик</li>
      </ul>
      <p className="login-aside-contact">Стать клиентом: позвоните в отдел продаж MBM по номеру <a href={salesPhone.tel}>{salesPhone.text}</a> или <a href={salesPhone.whatsapp} target="_blank" rel="noopener noreferrer">напишите в WhatsApp</a>.</p>
    </section>
  </div>;
}
