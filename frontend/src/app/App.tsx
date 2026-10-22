import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Home } from '../features/home/Home';
import { Catalog } from '../features/catalog/Catalog';
import { ProductDetail } from '../features/product/ProductDetail';
import { QuickOrder } from '../features/quick/QuickOrder';
import { Cart } from '../features/cart/Cart';
import { Receipt } from '../features/checkout/Receipt';
import { Account } from '../features/account/Account';
import { Login } from '../features/auth/Login';
import { safeReturn, useMe } from '../features/auth/session';
import { useCart } from '../features/cart/cartQueries';
import { pickupText } from '../features/catalog/useStore';
import { EmptyState, Notice } from '../shared/ui/Feedback';
import { Staff } from '../features/staff/Staff';
import { StaffLogin } from '../features/staff/StaffLogin';
import { Icon } from '../shared/ui/Icon';
import { Logo } from '../shared/ui/Logo';
import type { Partner } from '../shared/api/auth';

const demo = import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true';

export function App() {
  const location = useLocation();
  if (demo || !location.pathname.startsWith('/admin/')) return <PartnerApp />;
  return location.pathname === '/admin/login' ? <StaffLogin /> : <Staff />;
}

const titles: Record<string, string> = {
  '/': 'Оптовый портал', '/search': 'Каталог запчастей', '/quick-order': 'Быстрый заказ', '/basket': 'Корзина',
  '/checkout': 'Оформление заявки', '/checkout/success': 'Заявка сохранена', '/account': 'Мои заявки', '/login': 'Вход для оптовых клиентов',
};

function PartnerApp() {
  const location = useLocation();
  const me = useMe();
  const previousPath = useRef(location.pathname);
  useEffect(() => {
    if (!location.pathname.startsWith('/products/')) document.title = `${titles[location.pathname] ?? 'Страница не найдена'} — MBM AutoMotors`;
    if (previousPath.current !== location.pathname) {
      document.getElementById('main')?.focus({ preventScroll: true });
      window.scrollTo(0, 0);
      previousPath.current = location.pathname;
    }
  }, [location.pathname]);
  const partner = me.data;
  const signedOut = me.isSuccess && partner === null;
  let content;
  if (me.isPending) content = <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Проверяем вход…</div>;
  else if (me.isError) content = <Notice error><p>Не удалось проверить вход. Проверьте соединение и попробуйте ещё раз.</p><button type="button" className="button" onClick={() => void me.refetch()}>Повторить</button></Notice>;
  else if (signedOut) content = location.pathname === '/login' ? <Login /> : <Navigate replace to={`/login?from=${encodeURIComponent(location.pathname + location.search)}`} />;
  else content = <Routes>
    <Route path="/" element={<Home />} />
    <Route path="/search" element={<Catalog />} />
    <Route path="/products/:id" element={<ProductDetail />} />
    <Route path="/quick-order" element={<QuickOrder />} />
    <Route path="/basket" element={<Cart />} />
    <Route path="/checkout" element={<Cart checkout />} />
    <Route path="/checkout/success" element={<Receipt />} />
    <Route path="/account" element={<Account partner={partner!} />} />
    <Route path="/login" element={<SignedInRedirect />} />
    <Route path="*" element={<EmptyState title="Страница не найдена"><p>Проверьте адрес или вернитесь в каталог.</p><Link className="button button-primary" to="/search">Перейти в каталог</Link></EmptyState>} />
  </Routes>;
  return <>
    <a href="#main" className="skip-link" onClick={(event) => { event.preventDefault(); const main = document.getElementById('main'); main?.focus(); main?.scrollIntoView({ behavior: 'instant' }); }}>Перейти к содержимому</a>
    {demo && <div className="demo-banner"><div className="page-width"><strong>Прототип MBM AutoMotors</strong><span>Тестовый ассортимент. Подходит любой логин. Заявки никуда не отправляются.</span></div></div>}
    <div className="top-strip"><div className="page-width top-strip-inner"><span><Icon name="location" />{pickupText}</span>{partner && <Link to="/quick-order">Быстрый заказ</Link>}</div></div>
    <Header partner={partner ?? null} />
    <main id="main" tabIndex={-1} className="page-width main-content">{content}</main>
    <footer className="site-footer"><div className="page-width footer-grid">
      <div><div className="footer-brand"><Logo dark /></div><p>Оптовый портал для СТО и оптовых покупателей. {pickupText}.</p></div>
      {partner && <nav aria-label="Разделы портала"><Link to="/search">Каталог</Link><Link to="/quick-order">Быстрый заказ</Link><Link to="/basket">Корзина</Link><Link to="/account">Мои заявки</Link></nav>}
      <p>Цены оптовые, в тенге. Наличие — по данным склада, товар не резервируется до подтверждения заявки.</p>
    </div></footer>
  </>;
}

function SignedInRedirect() {
  const [params] = useSearchParams();
  return <Navigate replace to={safeReturn(params.get('from'))} />;
}

function Brand() {
  return <Link to="/" className="portal-brand" aria-label="MBM AutoMotors — главная"><Logo /></Link>;
}

function Header({ partner }: { partner: Partner | null }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const urlQuery = location.pathname === '/search' ? params.get('q') ?? '' : '';
  const [query, setQuery] = useState(urlQuery);
  useEffect(() => setQuery(urlQuery), [urlQuery]);
  const cart = useCart(Boolean(partner));
  if (!partner) return <header className="portal-header"><div className="page-width portal-header-inner"><Brand /></div></header>;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = new URLSearchParams();
    if (query.trim()) next.set('q', query.trim());
    navigate(`/search${next.size ? `?${next}` : ''}`);
  }
  const count = cart.data && !cart.isError ? cart.data.itemCount : null;
  const initial = (partner.company.replace(/[^\p{L}\p{N}]/gu, ' ').trim().split(/\s+/).find((word) => !/^(сто|тоо|ип|ооо)$/i.test(word)) ?? partner.login).slice(0, 1).toLocaleUpperCase('ru-RU');
  return <header className="portal-header"><div className="page-width portal-header-inner">
    <Brand />
    <Link to="/search" className="header-catalog" aria-current={location.pathname === '/search' ? 'page' : undefined}>Каталог</Link>
    <form className="header-search" role="search" onSubmit={submit}>
      <label htmlFor="header-query" className="sr-only">Поиск по названию, артикулу или коду</label>
      <Icon name="search" />
      <input id="header-query" name="q" type="search" autoComplete="off" spellCheck={false} maxLength={120} placeholder="Название, артикул или код…" value={query} onChange={(event) => setQuery(event.target.value)} />
      <button type="submit" className="header-search-submit">Найти</button>
    </form>
    <Link to="/account" className="account-badge" aria-current={location.pathname === '/account' ? 'page' : undefined} aria-label={`Кабинет: ${partner.company}`} title={partner.company}><span aria-hidden="true">{initial}</span></Link>
    <Link to="/basket" className="header-cart" aria-current={location.pathname === '/basket' ? 'page' : undefined} aria-label={count === null ? 'Корзина' : `Корзина: ${count} шт., ${cart.data!.cart.totalText}`}>
      <span className="header-cart-count" aria-hidden="true">{count ?? '–'}</span><span className="header-cart-total" aria-hidden="true">{cart.data && !cart.isError ? cart.data.cart.totalText : 'Корзина'}</span>
    </Link>
  </div></header>;
}
