import { useEffect, useRef } from 'react';
import { Link, Route, Routes, useLocation } from 'react-router-dom';
import { Catalog } from '../features/catalog/Catalog';
import { ProductDetail } from '../features/product/ProductDetail';
import { Cart } from '../features/cart/Cart';
import { Checkout } from '../features/checkout/Checkout';
import { Receipt } from '../features/checkout/Receipt';
import { useCart } from '../features/cart/cartQueries';
import { EmptyState } from '../shared/ui/Feedback';

export function App() {
  const location = useLocation();
  const previousPath = useRef(location.pathname);
  const cart = useCart();
  useEffect(() => {
    if (!location.pathname.startsWith('/products/')) document.title = location.pathname.startsWith('/checkout') ? 'Оформление заявки — MBM Motors' : location.pathname === '/basket' ? 'Корзина — MBM Motors' : location.pathname === '/' ? 'Каталог запчастей — MBM Motors' : 'Страница не найдена — MBM Motors';
    if (previousPath.current !== location.pathname) {
      document.getElementById('main')?.focus({ preventScroll: true });
      window.scrollTo(0, 0);
      previousPath.current = location.pathname;
    }
  }, [location.pathname]);
  return <>
    <a href="#main" className="skip-link">Перейти к содержимому</a>
    <header className="site-header"><div className="page-width flex items-center justify-between gap-4">
      <Link to="/" className="brand" aria-label="MBM Motors — каталог запчастей"><span className="brand-mark" aria-hidden="true">M</span><span>MBM <span className="brand-light">Motors</span></span></Link>
      <nav aria-label="Основная навигация"><Link to="/basket" className="cart-link">Корзина{cart.data && !cart.isError ? ` (${cart.data.itemCount})` : ''}</Link></nav>
    </div></header>
    <main id="main" tabIndex={-1} className="page-width main-content"><Routes><Route path="/" element={<Catalog />} /><Route path="/products/:id" element={<ProductDetail />} /><Route path="/basket" element={<Cart />} /><Route path="/checkout" element={<Checkout />} /><Route path="/checkout/success" element={<Receipt />} /><Route path="*" element={<EmptyState title="Страница не найдена"><p>Вернитесь в каталог, чтобы продолжить поиск.</p><Link className="button" to="/">Перейти в каталог</Link></EmptyState>} /></Routes></main>
    <footer className="page-width footer"><span>MBM Motors</span><span>Цены в тенге. Наличие по данным магазинов.</span></footer>
  </>;
}
