import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogHref, fetchProducts } from '../../shared/api/catalog';
import { useCartQuantities } from '../cart/cartQueries';
import { ProductTile } from '../catalog/ProductRow';
import { useDefaultStore } from '../catalog/useStore';

const photos = `${import.meta.env.BASE_URL}photos/`;
// Section photos: six from the v4 design, the rest from Unsplash; unknown sections keep the striped placeholder.
const sectionPhotos: Record<string, string> = {
  'Ходовая часть': 'suspension', 'Тормозная система': 'brakes', 'Фильтры и обслуживание': 'filters',
  'Двигатель': 'engine', 'Масла и технические жидкости': 'oils', 'Электрика и освещение': 'electrics',
  'Кузовные детали': 'body', 'Охлаждение и климат': 'cooling', 'Рулевое управление': 'steering', 'Трансмиссия': 'transmission',
};

function catalogParams(storeId: string | null, extra: Record<string, string> = {}) {
  const params = new URLSearchParams({ q: '', available: 'false', page: '0', ...extra });
  if (storeId) params.set('store', storeId);
  return params;
}

export function Home() {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const store = useDefaultStore();
  const quantities = useCartQuantities();
  // The whole catalog: its facets give the sections and makes with product counts.
  const all = catalogParams(store.id);
  const catalog = useQuery({ queryKey: ['products', all.toString()], queryFn: ({ signal }) => fetchProducts(all, signal), enabled: Boolean(store.id), staleTime: 30000 });
  const inStock = catalogParams(store.id, { available: 'true' });
  const first = useQuery({ queryKey: ['products', inStock.toString()], queryFn: ({ signal }) => fetchProducts(inStock, signal), enabled: Boolean(store.id), staleTime: 30000 });
  const items = first.data?.products.slice(0, 8) ?? [];
  const sections = catalog.data?.facets.sections ?? [];
  const makes = catalog.data?.facets.makes ?? [];
  return <>
    <section className="home-hero" aria-labelledby="home-title">
      <div className="home-hero-copy">
        <p className="hero-kicker">ЗАПЧАСТИ ДЛЯ КОРЕЙСКИХ АВТО</p>
        <h1 id="home-title">Нужная деталь со склада в Алматы</h1>
        <p className="hero-sub">Ищите по артикулу, коду или марке автомобиля. Оптовые цены и наличие склада — сразу в каталоге.</p>
      </div>
      <div className="home-hero-media"><img src={`${photos}hero.jpg`} alt="" /></div>
    </section>
    <div className="search-cards">
      <form className="search-card" role="search" aria-labelledby="by-sku-title" onSubmit={(event) => { event.preventDefault(); const q = query.trim(); navigate(q ? `/search?q=${encodeURIComponent(q)}` : '/search'); }}>
        <h2 id="by-sku-title" className="search-card-title">По артикулу</h2>
        <p className="search-card-hint">Дефисы и пробелы не важны: 546102t000 найдёт 54610-2T000</p>
        <div className="search-card-row">
          <label htmlFor="home-query" className="sr-only">Артикул, код или название</label>
          <input id="home-query" type="search" name="q" autoComplete="off" spellCheck={false} maxLength={120} placeholder="Артикул или код" value={query} onChange={(event) => setQuery(event.target.value)} />
          <button type="submit" className="button button-primary">Найти</button>
        </div>
      </form>
      <section className="search-card" aria-labelledby="by-make-title">
        <h2 id="by-make-title" className="search-card-title">По марке авто</h2>
        <p className="search-card-hint">Покажем детали, которые подходят к выбранной марке.</p>
        {makes.length > 0 ? <ul className="make-buttons">{makes.map((item) => <li key={item.name}><Link to={catalogHref({ make: item.name })} className="make-button">{item.name}<span>{item.count.toLocaleString('ru-RU')}</span></Link></li>)}</ul>
          : <p className="muted">{catalog.isPending ? 'Загружаем марки…' : 'Марки появятся после разметки каталога.'}</p>}
      </section>
    </div>
    {sections.length > 0 && <section className="home-section" aria-labelledby="home-categories-title">
      <div className="section-heading"><h2 id="home-categories-title">Категории</h2></div>
      <ul className="category-grid">{sections.map((item) => <li key={item.name}><Link to={catalogHref({ section: item.name })} className="category-tile">
        <span className="category-media" aria-hidden="true">{sectionPhotos[item.name] && <img src={`${photos}categories/${sectionPhotos[item.name]}.jpg`} alt="" loading="lazy" />}</span>
        <span className="category-name">{item.name}</span>
        <span className="category-count">{item.count.toLocaleString('ru-RU')} позиций</span>
      </Link></li>)}</ul>
    </section>}
    <section className="home-section b2b-panel" aria-labelledby="b2b-title">
      <div>
        <h2 id="b2b-title">Для СТО и оптовых покупателей</h2>
        <p>Оформляйте заявки списком артикулов, повторяйте прежние заказы из кабинета.</p>
      </div>
      <div className="b2b-list">
        <div className="b2b-item"><span aria-hidden="true">≡</span><div><strong>Заказ списком</strong><p>Вставьте артикулы с количеством — корзина соберётся сама</p></div></div>
        <div className="b2b-item"><span aria-hidden="true">↻</span><div><strong>Мои заявки</strong><p>История заказов и повтор в один клик</p></div></div>
        <div className="b2b-actions"><Link to="/quick-order" className="button button-light">Быстрый заказ</Link><Link to="/account" className="button button-outline-light">Кабинет</Link></div>
      </div>
    </section>
    {(first.isPending || items.length > 0) && <section className="home-section" aria-labelledby="home-stock-title">
      <div className="section-heading"><h2 id="home-stock-title">В наличии на складе</h2><Link to="/search?available=true">Весь каталог →</Link></div>
      {first.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем позиции…</div>
        : <ul className="tile-grid">{items.map((product) => <ProductTile key={product.id} product={product} storeId={first.data!.storeId} inCart={quantities.get(product.id) ?? 0} />)}</ul>}
    </section>}
  </>;
}
