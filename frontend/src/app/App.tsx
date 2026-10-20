import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Catalog } from '../features/catalog/Catalog';
import { ProductDetail } from '../features/product/ProductDetail';
import { Cart } from '../features/cart/Cart';
import { Checkout } from '../features/checkout/Checkout';
import { Receipt } from '../features/checkout/Receipt';
import { useCart } from '../features/cart/cartQueries';
import { EmptyState } from '../shared/ui/Feedback';
import { Staff } from '../features/staff/Staff';
import { Icon } from '../shared/ui/Icon';
import { DemoFooter, DemoInformation } from '../demo/Showcase';
const demo = (import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true');

export function App() {
  const location = useLocation();
  return !demo && location.pathname.startsWith('/admin/') ? <Staff /> : <CustomerApp />;
}

function CustomerApp() {
  const location = useLocation();
  const previousPath = useRef(location.pathname);
  const cart = useCart();
  const navigate = useNavigate();
  const onCatalog = location.pathname === '/';
  const [headerQuery, setHeaderQuery] = useState('');
  function searchCatalog(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = new URLSearchParams();
    const store = new URLSearchParams(location.search).get('store');
    if (store) params.set('store', store);
    if (headerQuery.trim()) params.set('q', headerQuery.trim());
    navigate(`/?${params}`);
  }
  useEffect(() => {
    if (!location.pathname.startsWith('/products/')) document.title = demo && ['/about', '/pickup', '/how-to-order'].includes(location.pathname) ? 'Информация — MBM Motors' : location.pathname.startsWith('/checkout') ? 'Оформление заявки — MBM Motors' : location.pathname === '/basket' ? 'Корзина — MBM Motors' : location.pathname === '/' ? 'Каталог запчастей — MBM Motors' : 'Страница не найдена — MBM Motors';
    if (previousPath.current !== location.pathname) {
      document.getElementById('main')?.focus({ preventScroll: true });
      window.scrollTo(0, 0);
      previousPath.current = location.pathname;
    }
  }, [location.pathname]);
  return <>
    <a href="#main" className="skip-link" onClick={(event) => { if (demo) { event.preventDefault(); document.getElementById('main')?.focus(); document.getElementById('main')?.scrollIntoView({ behavior: 'instant' }); } }}>Перейти к содержимому</a>
    <header className="site-header">{demo && <div className="demo-banner"><div className="page-width"><strong>Прототип MBM Motors</strong><span>Тестовый ассортимент. Реальные заявки не отправляются.</span></div></div>}<div className="page-width header-inner">
      <Link to="/" className="brand" aria-label="MBM Motors — каталог запчастей"><span className="brand-symbol" aria-hidden="true"><span>MBM</span><span className="brand-symbol-rule" /></span><span className="brand-name">Motors<span className="brand-subtitle">Автозапчасти</span></span></Link>
      <nav className="header-nav" aria-label="Основная навигация"><Link to="/" className="catalog-nav-link" aria-current={onCatalog ? 'page' : undefined}>Каталог</Link>
        {onCatalog ? <button type="button" className="header-search-trigger" aria-label="Перейти к поиску запчастей" onClick={() => { const input = document.getElementById('query'); input?.focus(); input?.scrollIntoView({ block: 'center', behavior: 'instant' }); }}><Icon name="search" /><span>Поиск запчастей</span></button> : <form className="header-search" role="search" onSubmit={searchCatalog}><label htmlFor="header-query" className="sr-only">Поиск запчастей в каталоге</label><input id="header-query" name="q" type="search" autoComplete="off" spellCheck={false} maxLength={120} placeholder="Название или артикул…" value={headerQuery} onChange={(event) => setHeaderQuery(event.target.value)} /><button type="submit" aria-label="Искать в каталоге"><Icon name="search" /></button></form>}
        <Link to="/basket" className="cart-link" aria-current={location.pathname === '/basket' ? 'page' : undefined}><Icon name="cart" /><span>Корзина</span>{cart.data && !cart.isError && <span className="cart-count">{cart.data.itemCount}</span>}</Link>
      </nav>
    </div>{demo && <div className="demo-navigation"><nav className="page-width" aria-label="Информация для покупателя"><Link to="/how-to-order">Как заказать</Link><Link to="/pickup">Самовывоз</Link><Link to="/about">О магазине</Link><span>Демонстрация магазина автозапчастей</span></nav></div>}</header>
    <main id="main" tabIndex={-1} className="page-width main-content"><Routes><Route path="/" element={<Catalog />} /><Route path="/products/:id" element={<ProductDetail />} /><Route path="/basket" element={<Cart />} /><Route path="/checkout" element={<Checkout />} /><Route path="/checkout/success" element={<Receipt />} />{demo && <><Route path="/how-to-order" element={<DemoInformation page="purchase" />} /><Route path="/pickup" element={<DemoInformation page="pickup" />} /><Route path="/about" element={<DemoInformation page="about" />} /></>}<Route path="*" element={<EmptyState title="Страница не найдена"><p>Вернитесь в каталог, чтобы продолжить поиск.</p><Link className="button" to="/">Перейти в каталог</Link></EmptyState>} /></Routes></main>
    <footer className={demo ? "demo-site-footer" : "page-width footer"}>{demo ? <div className="page-width"><DemoFooter /></div> : <><Link to="/" className="footer-brand">MBM Motors</Link><span>Цены в тенге. Наличие по данным магазинов.</span></>}</footer>
  </>;
}
