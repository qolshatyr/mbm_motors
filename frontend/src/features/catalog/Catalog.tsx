import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ApiError, fetchProducts, fetchStores } from '../../shared/api/catalog';
import { EmptyState, Notice } from '../../shared/ui/Feedback';
import { ProductCard } from './ProductCard';
import { Icon } from '../../shared/ui/Icon';
import { DemoCategories, DemoGuide } from '../../demo/Showcase';
const demo = (import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true');

export function Catalog() {
  const [search, setSearch] = useSearchParams();
  const query = search.get('q') ?? '';
  const category = demo ? search.get('category') ?? '' : '';
  const store = search.get('store') ?? '';
  const available = search.get('available') === 'true';
  const rawPage = search.get('page') ?? '0';
  const page = /^\d+$/.test(rawPage) ? Number(rawPage) : -1;
  const invalid = query.length > 120 || !Number.isSafeInteger(page) || page < 0 || page > 1000000;
  const [draft, setDraft] = useState(query);
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 639px)').matches);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const filterDialog = useRef<HTMLDialogElement>(null);
  const filterTrigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const media = window.matchMedia('(max-width: 639px)');
    const update = () => { setMobile(media.matches); if (!media.matches) setFiltersOpen(false); };
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);
  useEffect(() => {
    const dialog = filterDialog.current;
    if (!filtersOpen || !dialog) return;
    dialog.showModal();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { dialog.close(); document.body.style.overflow = previousOverflow; (filterTrigger.current ?? document.getElementById('store'))?.focus({ preventScroll: true }); };
  }, [filtersOpen, mobile]);
  useEffect(() => setDraft(query), [query]);
  const stores = useQuery({ queryKey: ['stores'], queryFn: ({ signal }) => fetchStores(signal), staleTime: 300000 });
  const params = new URLSearchParams({ q: query, available: String(available), page: String(page) });
  if (store) params.set('store', store);
  if (category) params.set('category', category);
  const products = useQuery({ queryKey: ['products', params.toString()], queryFn: ({ signal }) => fetchProducts(params, signal), enabled: !invalid, refetchInterval: 60000, staleTime: 0 });
  const data = products.data;
  const displayedPage = useRef(page);
  useEffect(() => {
    if (!data || data.page !== page || displayedPage.current === page) return;
    displayedPage.current = page;
    const heading = document.getElementById('results-title');
    heading?.focus({ preventScroll: true });
    heading?.scrollIntoView({ block: 'start', behavior: 'instant' });
  }, [data, page]);
  const effectiveStore = store || stores.data?.defaultStoreId || data?.storeId || '';
  function changeFilters(changes: Record<string, string>) {
    const next = new URLSearchParams(search);
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    next.delete('page');
    setSearch(next);
  }
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); changeFilters({ q: draft.trim() }); }
  function reset() { setDraft(''); setSearch({}); }
  function pageLink(value: number) { const next = new URLSearchParams(search); next.set('page', String(value)); return `/?${next}`; }
  const filters = <div className="filter-row"><div className="store-field"><label htmlFor="store">Магазин или склад</label><select name="store" id="store" value={effectiveStore} disabled={!stores.data} onChange={(event) => changeFilters({ store: event.target.value })}>{!stores.data && <option value="">{stores.isError ? 'Магазины недоступны' : 'Загружаем магазины…'}</option>}{stores.data?.stores.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div>
    <label className="available-filter"><input type="checkbox" name="available" checked={available} onChange={(event) => changeFilters({ available: event.target.checked ? 'true' : '' })} />Только в наличии</label><button className="text-button" type="button" onClick={reset}>Сбросить фильтры</button>
  </div>;
  return <>
    <section className="search-section" aria-labelledby="catalog-title">
      <div className="catalog-intro"><div className="catalog-heading"><h1 id="catalog-title">{demo ? 'Нужная деталь. Точный выбор.' : 'Найдите нужную деталь'}</h1><p>По артикулу, названию или коду товара</p></div><div className="catalog-caption">{demo ? <img className="demo-assembly" src="/demo-images/assembly.svg" alt="Схематическая иллюстрация тормозного диска" width="520" height="300" /> : <><Icon name="box" /><span>Запчасти в магазинах<br />и на складах MBM Motors</span></>}</div></div>
      <form onSubmit={submit} className="search-form" role="search">
        <div className="search-field"><label htmlFor="query">Поиск запчастей</label><div className="search-input-row"><Icon name="search" /><input id="query" name="q" type="search" autoComplete="off" spellCheck={false} placeholder="Например, 54610-2T000…" maxLength={120} value={draft} onChange={(event) => setDraft(event.target.value)} /><button className="button button-primary" type="submit">Найти</button></div></div>
        {mobile ? <div className="mobile-filter-bar"><button ref={filterTrigger} className="button filter-trigger" type="button" aria-haspopup="dialog" onClick={() => setFiltersOpen(true)}><Icon name="filter" />Фильтры{(store || available) && <span className="filter-count">{Number(Boolean(store)) + Number(available)}</span>}</button><span>{stores.data?.stores.find((item) => item.id === effectiveStore)?.name || 'Магазины и склады'}{available ? ' · В наличии' : ''}</span></div> : filters}
      </form>
    </section>
    {mobile && <dialog ref={filterDialog} className="filter-dialog" aria-labelledby="filter-title" onCancel={() => setFiltersOpen(false)} onClose={() => setFiltersOpen(false)}><div className="filter-dialog-heading"><h2 id="filter-title">Фильтры каталога</h2><button type="button" className="icon-button" aria-label="Закрыть фильтры" onClick={() => setFiltersOpen(false)}><Icon name="close" /></button></div><div className="filter-dialog-body">{stores.isError && <Notice error>Не удалось загрузить магазины. <button type="button" className="text-button" onClick={() => void stores.refetch()}>Повторить</button></Notice>}{filters}</div><div className="filter-dialog-footer"><button type="button" className="button button-primary" onClick={() => setFiltersOpen(false)}>Показать товары</button></div></dialog>}
    {stores.isError && <Notice error>Не удалось загрузить магазины. <button type="button" className="text-button" onClick={() => void stores.refetch()}>Повторить</button></Notice>}
    {demo && !query && !category && <DemoCategories />}
    {data && <div className={`freshness ${data.stale ? 'freshness-stale' : ''}`}><span className="status-dot" aria-hidden="true" /><span>{demo ? 'Пример ассортимента — цены, остатки и иллюстрации демонстрационные' : data.lastSyncText ? `Данные обновлены: ${data.lastSyncText}` : 'Каталог ещё не обновлялся'}</span>{data.stale && <p>Данные могут быть устаревшими. Уточните цену и наличие перед покупкой.</p>}</div>}
    <section aria-labelledby="results-title" aria-busy={products.isFetching}>
      <div className="results-heading"><div><h2 id="results-title" tabIndex={-1}>{demo && category ? ({filter: 'Фильтры', brake: 'Тормозная система', suspension: 'Подвеска', light: 'Свет и электрика', oil: 'Масла и жидкости', body: 'Кузовные детали'} as Record<string, string>)[category] || 'Каталог запчастей' : 'Каталог запчастей'}</h2><p>{data?.storeName || stores.data?.stores.find((item) => item.id === effectiveStore)?.name}</p></div>{data && <span>Найдено: {data.totalProducts.toLocaleString('ru-RU')}</span>}</div>
      {invalid ? <EmptyState title="Проверьте параметры поиска"><p>Используйте поиск до 120 символов и существующую страницу каталога.</p><button className="button" type="button" onClick={reset}>Сбросить фильтры</button></EmptyState> : products.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем каталог…</div> : products.isError ? <Notice error><p>{products.error instanceof ApiError ? products.error.message : 'Не удалось загрузить каталог. Проверьте соединение и попробуйте ещё раз.'}</p><div className="flex flex-wrap gap-3"><button type="button" className="button" onClick={() => void products.refetch()}>Повторить</button><button type="button" className="text-button" onClick={reset}>Сбросить фильтры</button></div></Notice> : data && <>
        <p className="refresh-status" role="status">{products.isFetching ? 'Обновляем данные…' : ''}</p>
        {data.products.length === 0 ? <EmptyState title="Товары не найдены"><p>Проверьте артикул или попробуйте более короткое название. Можно выбрать другую точку или снять фильтр наличия.</p><button className="button" type="button" onClick={reset}>Показать весь каталог</button></EmptyState> : <div className="product-grid">{data.products.map((product) => <ProductCard key={product.id} product={product} store={data.storeId} />)}</div>}
        {data.totalPages > 1 && <nav className="pagination" aria-label="Страницы каталога">{data.page > 0 ? <Link className="button" to={pageLink(data.page - 1)} rel="prev">Назад</Link> : <span className="button button-disabled" aria-disabled="true">Назад</span>}<span>Страница {data.page + 1} из {data.totalPages}</span>{data.page + 1 < data.totalPages ? <Link className="button" to={pageLink(data.page + 1)} rel="next">Далее</Link> : <span className="button button-disabled" aria-disabled="true">Далее</span>}</nav>}
      </>}
    </section>
    {demo && <DemoGuide />}
  </>;
}
