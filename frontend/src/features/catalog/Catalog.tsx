import { useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ApiError, catalogSorts, fetchProducts } from '../../shared/api/catalog';
import type { CatalogSort } from '../../shared/api/catalog';
import { EmptyState, Notice } from '../../shared/ui/Feedback';
import { useCartQuantities } from '../cart/cartQueries';
import { ProductRow } from './ProductRow';
import { pickupText, useDefaultStore } from './useStore';

export function Catalog() {
  const [search, setSearch] = useSearchParams();
  const query = search.get('q') ?? '';
  const available = search.get('available') === 'true';
  const rawSort = search.get('sort') ?? 'name';
  const sort: CatalogSort = catalogSorts.some((item) => item.value === rawSort) ? rawSort as CatalogSort : 'name';
  const rawPage = search.get('page') ?? '0';
  const page = /^\d+$/.test(rawPage) ? Number(rawPage) : -1;
  const invalid = query.length > 120 || !Number.isSafeInteger(page) || page < 0 || page > 1000000;
  const store = useDefaultStore();
  const quantities = useCartQuantities();
  const params = new URLSearchParams({ q: query, available: String(available), page: String(page) });
  if (store.id) params.set('store', store.id);
  if (sort !== 'name') params.set('sort', sort);
  const products = useQuery({ queryKey: ['products', params.toString()], queryFn: ({ signal }) => fetchProducts(params, signal), enabled: !invalid && Boolean(store.id), refetchInterval: 60000, staleTime: 0 });
  const data = products.data;
  const displayedPage = useRef(page);
  useEffect(() => {
    if (!data || data.page !== page || displayedPage.current === page) return;
    displayedPage.current = page;
    const heading = document.getElementById('results-title');
    heading?.focus({ preventScroll: true });
    heading?.scrollIntoView({ block: 'start', behavior: 'instant' });
  }, [data, page]);
  function change(changes: Record<string, string>) {
    const next = new URLSearchParams(search);
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    next.delete('page');
    setSearch(next);
  }
  function reset() { setSearch({}); }
  function pageLink(value: number) { const next = new URLSearchParams(search); next.set('page', String(value)); return `/search?${next}`; }
  const hasFilters = Boolean(query) || available || sort !== 'name';
  const storeError = store.query.isError;
  return <>
    <nav className="breadcrumbs" aria-label="Вы здесь"><ol><li><Link to="/">Главная</Link></li><li aria-current="page">Каталог</li></ol></nav>
    <h1 className="page-title">{query ? <>Поиск: «{query}»</> : 'Каталог запчастей'}</h1>
    <div className="catalog-layout">
      <aside className="catalog-sidebar" aria-label="Фильтры каталога">
        <button type="button" role="switch" aria-checked={available} className="switch-row" onClick={() => change({ available: available ? '' : 'true' })}>
          <span>Только в наличии</span><span className="switch" aria-hidden="true"><span /></span>
        </button>
        <div className="sidebar-facts">
          <p><strong>{store.name ?? 'Склад'}</strong><br />{pickupText}</p>
          {data && <p className={data.stale ? 'stale-text' : undefined}>{data.lastSyncText ? `Наличие обновлено: ${data.lastSyncText}` : 'Каталог ещё не обновлялся'}{data.stale && '. Данные могут быть устаревшими — добавление в корзину временно недоступно.'}</p>}
        </div>
        {hasFilters && <button type="button" className="button button-block" onClick={reset}>Сбросить фильтры</button>}
      </aside>
      <section className="catalog-results" aria-labelledby="results-title" aria-busy={products.isFetching}>
        <div className="results-toolbar">
          <h2 id="results-title" tabIndex={-1} className="results-count">{data ? <>Найдено: <b>{data.totalProducts.toLocaleString('ru-RU')}</b></> : 'Результаты'}</h2>
          <fieldset className="segmented"><legend className="sr-only">Сортировка</legend>
            {catalogSorts.map((item) => <label key={item.value} className={sort === item.value ? 'is-active' : undefined}><input type="radio" name="sort" value={item.value} checked={sort === item.value} onChange={() => change({ sort: item.value === 'name' ? '' : item.value })} />{item.label}</label>)}
          </fieldset>
        </div>
        <p className="sr-only" role="status">{products.isFetching ? 'Обновляем данные…' : ''}</p>
        {storeError ? <Notice error><p>Не удалось загрузить данные склада.</p><button type="button" className="button" onClick={() => void store.query.refetch()}>Повторить</button></Notice>
          : invalid ? <EmptyState title="Проверьте параметры поиска"><p>Используйте запрос до 120 символов и существующую страницу каталога.</p><button className="button" type="button" onClick={reset}>Показать весь каталог</button></EmptyState>
          : products.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем каталог…</div>
          : products.isError ? <Notice error><p>{products.error instanceof ApiError ? products.error.message : 'Не удалось загрузить каталог.'}</p><div className="actions"><button type="button" className="button" onClick={() => void products.refetch()}>Повторить</button><button type="button" className="button" onClick={reset}>Сбросить фильтры</button></div></Notice>
          : data && <>
            {data.products.length === 0 ? <div className="empty-panel"><h3>Товары не найдены</h3><p>Проверьте артикул или попробуйте более короткое название. Можно снять фильтр наличия или отправить список артикулов через быстрый заказ.</p><div className="actions"><button type="button" className="button button-dark" onClick={reset}>Показать весь каталог</button><Link to="/quick-order" className="button">Быстрый заказ</Link></div></div>
              : <ul className="product-list">{data.products.map((product) => <ProductRow key={product.id} product={product} storeId={data.storeId} inCart={quantities.get(product.id) ?? 0} />)}</ul>}
            {data.totalPages > 1 && <nav className="pagination" aria-label="Страницы каталога">{data.page > 0 ? <Link className="button" to={pageLink(data.page - 1)} rel="prev">Назад</Link> : <span className="button button-disabled" aria-disabled="true">Назад</span>}<span>Страница {data.page + 1} из {data.totalPages}</span>{data.page + 1 < data.totalPages ? <Link className="button" to={pageLink(data.page + 1)} rel="next">Далее</Link> : <span className="button button-disabled" aria-disabled="true">Далее</span>}</nav>}
          </>}
      </section>
    </div>
  </>;
}
