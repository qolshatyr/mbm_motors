import { Catalog } from '../features/catalog/Catalog';

export function App() {
  return <>
    <a href="#main" className="skip-link">Перейти к содержимому</a>
    <header className="site-header"><div className="page-width flex items-center justify-between gap-4">
      <a href="/" className="brand" aria-label="MBM Motors — каталог запчастей"><span className="brand-mark" aria-hidden="true">M</span><span>MBM <span className="brand-light">Motors</span></span></a>
      <nav aria-label="Основная навигация"><a href="/cart" className="cart-link">Корзина <span aria-hidden="true">↗</span></a></nav>
    </div></header>
    <main id="main" className="page-width main-content"><Catalog /></main>
    <footer className="page-width footer"><span>MBM Motors</span><span>Цены в тенге. Наличие по данным магазинов.</span></footer>
  </>;
}
