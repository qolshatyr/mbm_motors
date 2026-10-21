import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Route, Routes, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { fetchStaffDetail, fetchStaffPage } from '../../shared/api/staff';
import { ApiError, request } from '../../shared/api/http';
import { Button } from '../../shared/ui/Button';
import { EmptyState, Notice } from '../../shared/ui/Feedback';
import { Logo } from '../../shared/ui/Logo';
import { Clients } from './Clients';

const StaffAccess = createContext({ blocked: false, deny: () => {} });
const staffOptions = { gcTime: 0, staleTime: 0, retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false } as const;
const dateFormatter = new Intl.DateTimeFormat('ru-RU', { timeZone: 'Asia/Almaty', dateStyle: 'medium', timeStyle: 'short' });
function CreatedAt({ value }: { value: string }) {
  return <time dateTime={value}>{dateFormatter.format(new Date(value))} <span className="muted">(Алматы)</span></time>;
}
function ScopeNote() {
  return <Notice>Заявки сохранены на сайте. Они ещё не переданы в CloudShop и не подтверждены магазином. Товары не резервируются.</Notice>;
}
function AccessDenied() {
  return <Notice error><p>Войдите в служебный раздел, чтобы управлять клиентами и просматривать заявки. Если вход не помогает, обратитесь к администратору: доступ может быть отключён.</p><a className="button button-primary" href="/admin/login">Войти в служебный раздел</a></Notice>;
}
function useAuthError(error: Error | null) {
  const access = useContext(StaffAccess);
  const denied = error instanceof ApiError && (error.status === 401 || error.status === 403);
  useEffect(() => { if (denied) access.deny(); }, [denied, access.deny]);
  return denied || access.blocked;
}

export function Staff() {
  const client = useQueryClient();
  const location = useLocation();
  const [blocked, setBlocked] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutToken, setLogoutToken] = useState('');
  const [logoutError, setLogoutError] = useState('');
  const [visible, setVisible] = useState(document.visibilityState !== 'hidden');
  const [accessCheck, setAccessCheck] = useState(0);
  const form = useRef<HTMLFormElement>(null);
  const previousPath = useRef(location.pathname + location.search);
  const deny = useCallback(() => {
    setBlocked(true);
    void client.cancelQueries({ queryKey: ['staff'] });
    client.removeQueries({ queryKey: ['staff'] });
  }, [client]);
  useEffect(() => {
    if (blocked || loggingOut) document.getElementById('main')?.focus({ preventScroll: true });
  }, [blocked, loggingOut]);
  useEffect(() => {
    document.title = location.pathname === '/admin/clients' ? 'Клиенты — MBM Motors' : location.pathname === '/admin/requests' ? 'Заявки — MBM Motors' : 'Заявка — MBM Motors';
    const nextPath = location.pathname + location.search;
    if (previousPath.current !== nextPath) {
      document.getElementById('main')?.focus({ preventScroll: true });
      window.scrollTo(0, 0);
      previousPath.current = nextPath;
    }
  }, [location.pathname, location.search]);
  useEffect(() => {
    const hide = () => { setBlocked(true); void client.cancelQueries({ queryKey: ['staff'] }); client.removeQueries({ queryKey: ['staff'] }); };
    const restore = (event: PageTransitionEvent) => { if (event.persisted) window.location.reload(); };
    window.addEventListener('pagehide', hide);
    window.addEventListener('pageshow', restore);
    return () => { window.removeEventListener('pagehide', hide); window.removeEventListener('pageshow', restore); client.removeQueries({ queryKey: ['staff'] }); };
  }, [client]);
  useEffect(() => {
    // Contacts live only in a visible document. Returning from another tab/window
    // or reconnecting remounts the protected route and checks the server session.
    const clear = () => {
      void client.cancelQueries({ queryKey: ['staff'] });
      client.removeQueries({ queryKey: ['staff'] });
    };
    const recheck = () => {
      if (document.visibilityState === 'hidden') return;
      clear();
      setVisible(true);
      setAccessCheck(current => current + 1);
    };
    const visibility = () => {
      if (document.visibilityState === 'hidden') { setVisible(false); clear(); }
      else recheck();
    };
    document.addEventListener('visibilitychange', visibility);
    window.addEventListener('focus', recheck);
    window.addEventListener('online', recheck);
    return () => {
      document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('focus', recheck);
      window.removeEventListener('online', recheck);
    };
  }, [client]);
  async function logout() {
    setLoggingOut(true);
    setLogoutError('');
    await client.cancelQueries({ queryKey: ['staff'] });
    client.removeQueries({ queryKey: ['staff'] });
    try {
      const token = await request<{ headerName: string; token: string }>('/api/csrf');
      setLogoutToken(token.token);
    } catch {
      setLogoutError('Не удалось подготовить выход. Проверьте соединение и повторите.');
      setLoggingOut(false);
      setBlocked(true);
    }
  }
  useEffect(() => { if (logoutToken && loggingOut) form.current?.submit(); }, [logoutToken, loggingOut]);
  return <StaffAccess.Provider value={{ blocked, deny }}>
    <a href="#main" className="skip-link">Перейти к содержимому</a>
    <header className="site-header"><div className="page-width staff-header">
      <Link to="/admin/requests" className="brand" aria-label="MBM Motors — служебные заявки"><Logo tagline={false} /></Link>
      <span className="staff-tag">Администрирование</span><nav aria-label="Служебная навигация"><Link to="/admin/clients" aria-current={location.pathname === '/admin/clients' ? 'page' : undefined}>Клиенты</Link><Link to="/admin/requests" aria-current={location.pathname.startsWith('/admin/requests') ? 'page' : undefined}>Заявки</Link><a href="/">Каталог</a><form ref={form} action="/admin/logout" method="post"><input type="hidden" name="_csrf" value={logoutToken} /><Button type="button" disabled={loggingOut} onClick={() => void logout()}>{loggingOut ? 'Выходим…' : 'Выйти'}</Button></form></nav>
    </div></header>
    <main id="main" tabIndex={-1} className="page-width main-content staff-content">
      {logoutError && <Notice error>{logoutError}</Notice>}
      {loggingOut ? <><h1>Служебный раздел</h1><div className="loading-state" role="status">Завершаем служебную сессию…</div></> : blocked ? <><h1>Служебный раздел</h1><AccessDenied /></> : !visible ? <><h1>Служебный раздел</h1><div className="loading-state" role="status">При возвращении проверим доступ…</div></> : <Routes key={accessCheck}><Route path="/admin/clients" element={<Clients onDenied={deny} />} /><Route path="/admin/requests" element={<RequestList />} /><Route path="/admin/requests/:number" element={<RequestDetail />} /><Route path="*" element={<EmptyState title="Страница не найдена"><Link className="button" to="/admin/requests">К заявкам</Link></EmptyState>} /></Routes>}
    </main>
    <footer className="page-width footer"><span>MBM Motors · Служебный раздел</span><span>Клиенты и заявки</span></footer>
  </StaffAccess.Provider>;
}

function RequestList() {
  const [params, setParams] = useSearchParams();
  const rawPage = params.get('page') ?? '0';
  const validPage = /^\d{1,6}$/.test(rawPage) && Number(rawPage) <= 100000;
  const page = validPage ? Number(rawPage) : 0;
  const access = useContext(StaffAccess);
  const result = useQuery({ queryKey: ['staff', 'requests', page], queryFn: ({ signal }) => fetchStaffPage(page, signal), enabled: validPage && !access.blocked, ...staffOptions });
  const denied = useAuthError(result.error);
  function move(next: number) { const updated = new URLSearchParams(params); updated.set('page', String(next)); setParams(updated); }
  return <><div className="heading-row staff-heading"><div><h1>Заявки</h1><p>Новые сначала. По 20 заявок на странице.</p></div><Button disabled={!validPage || result.isFetching || denied} onClick={() => void result.refetch()}>Обновить</Button></div><ScopeNote />
    {!validPage ? <Notice error><p>Номер страницы должен быть от 0 до 100000.</p><Link className="button" to="/admin/requests">К первой странице</Link></Notice> : denied ? <AccessDenied /> : result.isFetching || result.isPending ? <div className="loading-state" role="status">Загружаем заявки…</div> : result.isError ? <Notice error>Не удалось загрузить заявки. Проверьте соединение и нажмите «Обновить».</Notice> : <>
      <p className="staff-result-count">Всего заявок: {result.data.totalItems}</p>
      {result.data.items.length === 0 ? <EmptyState title={page === 0 ? 'Заявок пока нет' : 'На этой странице нет заявок'}><p>{page === 0 ? 'Здесь появятся заявки, сохранённые покупателями на сайте.' : 'Вернитесь на предыдущую или первую страницу.'}</p>{page > 0 && <Link className="button" to="/admin/requests">К первой странице</Link>}</EmptyState> : <ol className="staff-request-list">{result.data.items.map(item => <li key={item.number}><div className="staff-request-main"><Link to={`/admin/requests/${item.number}`} className="staff-number">Заявка №{item.number}</Link><span className="staff-status">Сохранена на сайте</span><span className="muted">{item.delivery?.message || 'Передача ещё не подключена.'}</span><CreatedAt value={item.createdAt} /></div><div className="staff-request-store"><span className="muted">Точка</span><span>{item.storeName}</span><span className="muted">Товаров: {item.itemCount}</span></div><strong className="staff-request-total">{item.totalText}</strong></li>)}</ol>}
      <nav className="pagination staff-pagination" aria-label="Страницы заявок"><Button disabled={page === 0} onClick={() => move(Math.max(0, page - 1))} aria-label="Предыдущая страница заявок">Назад</Button><span>Страница {page + 1}{result.data.totalPages > 0 ? ` из ${result.data.totalPages}` : ''}</span><Button disabled={page >= result.data.totalPages - 1 || page >= 100000} onClick={() => move(page + 1)} aria-label="Следующая страница заявок">Далее</Button></nav>
    </>}
  </>;
}

function RequestDetail() {
  const { number = '' } = useParams();
  const valid = /^[1-9][0-9]{0,18}$/.test(number);
  const access = useContext(StaffAccess);
  const result = useQuery({ queryKey: ['staff', 'request', number], queryFn: ({ signal }) => fetchStaffDetail(number, signal), enabled: valid && !access.blocked, ...staffOptions });
  const denied = useAuthError(result.error);
  return <><div className="back-nav"><Link to="/admin/requests">Все заявки</Link></div><div className="heading-row staff-heading"><h1>Заявка №{number}</h1><Button disabled={!valid || result.isFetching || denied} onClick={() => void result.refetch()}>Обновить</Button></div><ScopeNote />
    {!valid ? <Notice error>Некорректный номер заявки.</Notice> : denied ? <AccessDenied /> : result.isFetching || result.isPending ? <div className="loading-state" role="status">Загружаем заявку…</div> : result.isError ? <Notice error>{result.error instanceof ApiError && result.error.status === 404 ? 'Заявка не найдена. Проверьте номер или вернитесь к списку заявок.' : 'Не удалось загрузить заявку. Проверьте соединение и нажмите «Обновить».'}</Notice> : <>
      <section className="staff-overview" aria-labelledby="request-summary"><h2 id="request-summary">Сохранена на сайте</h2><dl className="staff-facts"><div><dt>Дата и время</dt><dd><CreatedAt value={result.data.createdAt} /></dd></div><div><dt>Точка</dt><dd>{result.data.storeName}</dd></div><div><dt>Передача</dt><dd>{result.data.delivery?.message || 'Передача ещё не подключена.'}</dd></div><div><dt>Товаров</dt><dd>{result.data.itemCount}</dd></div><div><dt>Сумма</dt><dd>{result.data.totalText}</dd></div></dl></section>
      <section className="staff-contact" aria-labelledby="request-contact"><h2 id="request-contact">Покупатель</h2><dl className="staff-facts"><div><dt>Имя</dt><dd>{result.data.customerName}</dd></div><div><dt>Телефон</dt><dd>{result.data.phone}</dd></div><div><dt>Комментарий</dt><dd className="staff-comment">{result.data.comment || 'Не указан'}</dd></div></dl></section>
      <section aria-labelledby="request-items"><h2 id="request-items">Состав заявки</h2><p className="flow-note">Цены, названия и количество сохранены в момент оформления.</p><ol className="staff-items">{result.data.items.map(item => <li key={item.position}><div><h3>{item.productName}</h3>{item.sku && <p className="sku">Артикул: {item.sku}</p>}<p className="muted">{item.quantity} {item.unit} × {item.priceText}</p></div><strong>{item.lineTotalText}</strong></li>)}</ol><div className="checkout-total"><span>Итого</span><strong>{result.data.totalText}</strong></div></section>
    </>}
  </>;
}
