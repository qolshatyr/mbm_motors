import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchProducts } from '../../shared/api/catalog';
import { Icon } from '../../shared/ui/Icon';
import { useCartQuantities } from '../cart/cartQueries';
import { ProductTile } from '../catalog/ProductRow';
import { pickupText, useDefaultStore } from '../catalog/useStore';
import { useMe } from '../auth/session';

export function Home() {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const me = useMe();
  const store = useDefaultStore();
  const quantities = useCartQuantities();
  const params = new URLSearchParams({ q: '', available: 'true', page: '0' });
  if (store.id) params.set('store', store.id);
  const first = useQuery({ queryKey: ['products', params.toString()], queryFn: ({ signal }) => fetchProducts(params, signal), enabled: Boolean(store.id), staleTime: 30000 });
  const items = first.data?.products.slice(0, 8) ?? [];
  return <>
    <section className="home-hero" aria-labelledby="home-title">
      <div className="home-hero-copy">
        <p className="hero-greeting">{me.data?.company}</p>
        <h1 id="home-title">Запчасти со склада в Алматы по оптовой цене</h1>
        <p className="hero-sub">Ищите по названию, артикулу или коду. Дефисы и пробелы не важны: 546102t000 найдёт 54610-2T000.</p>
        <form className="hero-search" role="search" onSubmit={(event) => { event.preventDefault(); const q = query.trim(); navigate(q ? `/search?q=${encodeURIComponent(q)}` : '/search'); }}>
          <label htmlFor="home-query" className="sr-only">Поиск по названию, артикулу или коду</label>
          <Icon name="search" />
          <input id="home-query" type="search" name="q" autoComplete="off" spellCheck={false} maxLength={120} placeholder="Например, 26300-35505 или фильтр масляный…" value={query} onChange={(event) => setQuery(event.target.value)} />
          <button type="submit" className="button button-primary">Найти</button>
        </form>
        <p className="hero-pickup"><Icon name="location" />{pickupText}</p>
      </div>
      <nav className="home-links" aria-label="Быстрые действия">
        <Link to="/quick-order" className="home-link home-link-dark"><strong>Быстрый заказ</strong><span>Вставьте список артикулов с количеством — корзина соберётся сама</span></Link>
        <Link to="/search" className="home-link"><strong>Весь каталог</strong><span>Остатки и цены склада, фильтр «Только в наличии»</span></Link>
        <Link to="/account" className="home-link"><strong>Мои заявки</strong><span>История заказов и повтор в один клик</span></Link>
      </nav>
    </section>
    {(first.isPending || items.length > 0) && <section className="home-section" aria-labelledby="home-stock-title">
      <div className="section-heading"><h2 id="home-stock-title">В наличии на складе</h2><Link to="/search?available=true">Весь каталог</Link></div>
      {first.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем позиции…</div>
        : <ul className="tile-grid">{items.map((product) => <ProductTile key={product.id} product={product} storeId={first.data!.storeId} inCart={quantities.get(product.id) ?? 0} />)}</ul>}
    </section>}
  </>;
}
