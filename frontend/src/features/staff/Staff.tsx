import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Route, Routes, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { cancelStaffRequest, fetchStaffDetail, fetchStaffPage, reconcileRequest } from '../../shared/api/staff';
import type { StaffDetail } from '../../shared/api/staff';
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
  return <Notice>Заявки сохранены на сайте. Состояние передачи в CloudShop указано у каждой заявки; переданная заявка — непроведённая продажа, не подтверждённая магазином. Товары не резервируются.</Notice>;
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
    document.title = location.pathname === '/admin/clients' ? 'Клиенты — MBM AutoMotors' : location.pathname === '/admin/requests' ? 'Заявки — MBM AutoMotors' : 'Заявка — MBM AutoMotors';
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
      <Link to="/admin/requests" className="brand" aria-label="MBM AutoMotors — служебные заявки"><Logo tagline={false} /></Link>
      <span className="staff-tag">Администрирование</span><nav aria-label="Служебная навигация"><Link to="/admin/clients" aria-current={location.pathname === '/admin/clients' ? 'page' : undefined}>Клиенты</Link><Link to="/admin/requests" aria-current={location.pathname.startsWith('/admin/requests') ? 'page' : undefined}>Заявки</Link><a href="/">Каталог</a><form ref={form} action="/admin/logout" method="post"><input type="hidden" name="_csrf" value={logoutToken} /><Button type="button" disabled={loggingOut} onClick={() => void logout()}>{loggingOut ? 'Выходим…' : 'Выйти'}</Button></form></nav>
    </div></header>
    <main id="main" tabIndex={-1} className="page-width main-content staff-content">
      {logoutError && <Notice error>{logoutError}</Notice>}
      {loggingOut ? <><h1>Служебный раздел</h1><div className="loading-state" role="status">Завершаем служебную сессию…</div></> : blocked ? <><h1>Служебный раздел</h1><AccessDenied /></> : !visible ? <><h1>Служебный раздел</h1><div className="loading-state" role="status">При возвращении проверим доступ…</div></> : <Routes key={accessCheck}><Route path="/admin/clients" element={<Clients onDenied={deny} />} /><Route path="/admin/requests" element={<RequestList />} /><Route path="/admin/requests/:number" element={<RequestDetail />} /><Route path="*" element={<EmptyState title="Страница не найдена"><Link className="button" to="/admin/requests">К заявкам</Link></EmptyState>} /></Routes>}
    </main>
    <footer className="page-width footer"><span>MBM AutoMotors · Служебный раздел</span><span>Клиенты и заявки</span></footer>
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
      {result.data.items.length === 0 ? <EmptyState title={page === 0 ? 'Заявок пока нет' : 'На этой странице нет заявок'}><p>{page === 0 ? 'Здесь появятся заявки, сохранённые покупателями на сайте.' : 'Вернитесь на предыдущую или первую страницу.'}</p>{page > 0 && <Link className="button" to="/admin/requests">К первой странице</Link>}</EmptyState> : <ol className="staff-request-list">{result.data.items.map(item => <li key={item.number}><div className="staff-request-main"><Link to={`/admin/requests/${item.number}`} className="staff-number">Заявка №{item.number}</Link><span className="staff-status">Сохранена на сайте</span>{item.cancellation && <CancellationTag cancellation={item.cancellation} />}<span className="muted">{item.delivery?.message || 'Передача ещё не подключена.'}</span><CreatedAt value={item.createdAt} /></div><div className="staff-request-store"><span className="muted">Точка</span><span>{item.storeName}</span><span className="muted">Товаров: {item.itemCount}</span></div><strong className="staff-request-total">{item.totalText}</strong></li>)}</ol>}
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
      <section className="staff-overview" aria-labelledby="request-summary"><h2 id="request-summary">Сохранена на сайте</h2><dl className="staff-facts"><div><dt>Дата и время</dt><dd><CreatedAt value={result.data.createdAt} /></dd></div><div><dt>Точка</dt><dd>{result.data.storeName}</dd></div><div><dt>Передача</dt><dd>{result.data.delivery?.message || 'Передача ещё не подключена.'}</dd></div>{result.data.delivery?.reason && <div><dt>Причина</dt><dd className="code">{result.data.delivery.reason}</dd></div>}{result.data.delivery?.reconciliation && <div><dt>Сверка</dt><dd>{result.data.delivery.reconciliation.by}, <CreatedAt value={result.data.delivery.reconciliation.at} /></dd></div>}<div><dt>Товаров</dt><dd>{result.data.itemCount}</dd></div><div><dt>Сумма</dt><dd>{result.data.totalText}</dd></div></dl>{result.data.delivery?.state === 'UNKNOWN' && !result.data.delivery.reconciliation && <Reconciliation number={number} />}</section>
      <Cancellation number={number} detail={result.data} />
      <section className="staff-contact" aria-labelledby="request-contact"><h2 id="request-contact">Покупатель</h2><dl className="staff-facts"><div><dt>Имя</dt><dd>{result.data.customerName}</dd></div><div><dt>Телефон</dt><dd>{result.data.phone}</dd></div><div><dt>Комментарий</dt><dd className="staff-comment">{result.data.comment || 'Не указан'}</dd></div></dl></section>
      <section aria-labelledby="request-items"><h2 id="request-items">Состав заявки</h2><p className="flow-note">Цены, названия и количество сохранены в момент оформления.</p><ol className="staff-items">{result.data.items.map(item => <li key={item.position}><div><h3>{item.productName}</h3>{item.sku && <p className="sku">Артикул: {item.sku}</p>}<p className="muted">{item.quantity} {item.unit} × {item.priceText}</p></div><strong>{item.lineTotalText}</strong></li>)}</ol><div className="checkout-total"><span>Итого</span><strong>{result.data.totalText}</strong></div></section>
    </>}
  </>;
}

/**
 * Manual check of an order whose delivery result is unknown. Staff look for «Заявка MBM-<number>»
 * in CloudShop sales and record what they found; the site never resends the order.
 */
function Reconciliation({ number }: { number: string }) {
  const client = useQueryClient();
  const access = useContext(StaffAccess);
  const [result, setResult] = useState<'FOUND' | 'NOT_CREATED' | ''>('');
  const [sale, setSale] = useState('');
  const saleValid = /^[1-9][0-9]{0,11}$/.test(sale.trim());
  const save = useMutation({
    mutationFn: () => reconcileRequest(number, result as 'FOUND' | 'NOT_CREATED', result === 'FOUND' ? Number(sale.trim()) : null),
    onSuccess: (detail: StaffDetail) => client.setQueryData(['staff', 'request', number], detail),
    onError: (error) => { if (error instanceof ApiError && (error.status === 401 || error.status === 403)) access.deny(); },
  });
  const ready = result === 'NOT_CREATED' || (result === 'FOUND' && saleValid);
  return <form className="staff-reconcile" onSubmit={(event) => { event.preventDefault(); if (ready) save.mutate(); }} aria-labelledby="reconcile-title">
    <h3 id="reconcile-title">Ручная сверка</h3>
    <p className="muted">Найдите в продажах CloudShop комментарий «Заявка MBM-{number}» и отметьте результат. Сайт заявку повторно не отправит.</p>
    <fieldset><legend className="sr-only">Результат сверки</legend>
      <label className="staff-reconcile-option"><input type="radio" name="reconcile" checked={result === 'FOUND'} onChange={() => setResult('FOUND')} />Продажа найдена в CloudShop</label>
      {result === 'FOUND' && <label className="field"><span className="field-label">Номер продажи</span><input className="code" value={sale} onChange={(event) => setSale(event.target.value)} inputMode="numeric" maxLength={12} placeholder="Например, 35825" aria-invalid={sale !== '' && !saleValid} autoComplete="off" required /></label>}
      <label className="staff-reconcile-option"><input type="radio" name="reconcile" checked={result === 'NOT_CREATED'} onChange={() => setResult('NOT_CREATED')} />Продажи в CloudShop нет</label>
    </fieldset>
    {save.isError && <p className="form-error" role="alert">{save.error instanceof ApiError ? save.error.message : 'Не удалось записать сверку. Проверьте соединение и повторите.'}</p>}
    <Button type="submit" primary disabled={!ready || save.isPending}>{save.isPending ? 'Сохраняем…' : 'Записать сверку'}</Button>
  </form>;
}

function CancellationTag({ cancellation }: { cancellation: NonNullable<StaffDetail['cancellation']> }) {
  return cancellation.cancelledAt ? <span className="tag tag-muted">Отменена</span> : <span className="tag tag-warn">Клиент просит отменить</span>;
}

/**
 * The partner's request to cancel and the staff mark. The site changes nothing in CloudShop: staff
 * remove the sale there first. Allowed once delivery has finished, so the order cannot be sent later.
 */
function Cancellation({ number, detail }: { number: string; detail: StaffDetail }) {
  const client = useQueryClient();
  const access = useContext(StaffAccess);
  const [confirming, setConfirming] = useState(false);
  const save = useMutation({
    mutationFn: () => cancelStaffRequest(number),
    onSuccess: (updated: StaffDetail) => { client.setQueryData(['staff', 'request', number], updated); void client.invalidateQueries({ queryKey: ['staff', 'requests'] }); },
    onError: (error) => { if (error instanceof ApiError && (error.status === 401 || error.status === 403)) access.deny(); },
  });
  const cancellation = detail.cancellation;
  const finished = !detail.delivery || detail.delivery.state === 'SENT' || detail.delivery.state === 'UNKNOWN' || detail.delivery.state === 'NOT_PREPARED';
  if (!cancellation && !confirming) return <section aria-labelledby="request-cancel"><h2 id="request-cancel">Отмена</h2><p className="muted">Клиент не просил отменить заявку.</p>{finished && <Button onClick={() => setConfirming(true)}>Отметить отменённой</Button>}</section>;
  return <section aria-labelledby="request-cancel"><h2 id="request-cancel">Отмена</h2>
    <dl className="staff-facts">
      {cancellation?.requestedAt && <div><dt>Клиент попросил</dt><dd><CreatedAt value={cancellation.requestedAt} /></dd></div>}
      {cancellation?.requestedAt && <div><dt>Причина</dt><dd className="staff-comment">{cancellation.reason || 'Не указана'}</dd></div>}
      {cancellation?.cancelledAt && <div><dt>Отменена</dt><dd>{cancellation.by}, <CreatedAt value={cancellation.cancelledAt} /></dd></div>}
    </dl>
    {!cancellation?.cancelledAt && (finished ? <div className="staff-reconcile">
      <p className="muted">Сначала удалите продажу с комментарием «Заявка MBM-{number}» в CloudShop. Сайт сам в CloudShop ничего не удаляет — отметка только сообщает клиенту, что заявка отменена.</p>
      {save.isError && <p className="form-error" role="alert">{save.error instanceof ApiError ? save.error.message : 'Не удалось записать отмену. Проверьте соединение и повторите.'}</p>}
      <div className="actions"><Button primary disabled={save.isPending} onClick={() => save.mutate()}>{save.isPending ? 'Сохраняем…' : 'Продажа удалена — отметить отменённой'}</Button>{!cancellation && <Button disabled={save.isPending} onClick={() => setConfirming(false)}>Не отменять</Button>}</div>
    </div> : <p className="muted">Заявка ещё передаётся в CloudShop. Отметить отмену можно после передачи или сверки.</p>)}
  </section>;
}
