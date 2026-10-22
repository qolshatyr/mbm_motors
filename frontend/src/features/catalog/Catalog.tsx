import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ApiError, carName, catalogSorts, fetchProducts, readFilter, writeFilter } from '../../shared/api/catalog';
import type { CatalogFilter, CatalogSort, FacetValue } from '../../shared/api/catalog';
import { EmptyState, Notice } from '../../shared/ui/Feedback';
import { useCartQuantities } from '../cart/cartQueries';
import { ProductRow } from './ProductRow';
import { pickupText, useDefaultStore } from './useStore';

const BRANDS_COLLAPSED = 8;

export function Catalog() {
  const [search, setSearch] = useSearchParams();
  const query = search.get('q') ?? '';
  const available = search.get('available') === 'true';
  const filter = readFilter(search);
  const rawSort = search.get('sort') ?? 'name';
  const sort: CatalogSort = catalogSorts.some((item) => item.value === rawSort) ? rawSort as CatalogSort : 'name';
  const rawPage = search.get('page') ?? '0';
  const page = /^\d+$/.test(rawPage) ? Number(rawPage) : -1;
  const tooLong = [filter.section, filter.subsection, filter.make, filter.model, ...filter.brands].some((value) => value && value.length > 120);
  const invalid = query.length > 120 || !Number.isSafeInteger(page) || page < 0 || page > 1000000 || tooLong || filter.brands.length > 20 || (Boolean(filter.subsection) && !filter.section) || (Boolean(filter.model) && !filter.make);
  const store = useDefaultStore();
  const quantities = useCartQuantities();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [allBrands, setAllBrands] = useState(false);
  const params = new URLSearchParams({ q: query, available: String(available), page: String(page) });
  if (store.id) params.set('store', store.id);
  if (sort !== 'name') params.set('sort', sort);
  writeFilter(params, filter);
  const products = useQuery({ queryKey: ['products', params.toString()], queryFn: ({ signal }) => fetchProducts(params, signal), enabled: !invalid && Boolean(store.id), refetchInterval: 60000, staleTime: 0, placeholderData: (previous) => previous });
  const data = products.data;
  const facets = data?.facets;
  const displayedPage = useRef(page);
  useEffect(() => {
    if (!data || data.page !== page || displayedPage.current === page) return;
    displayedPage.current = page;
    const heading = document.getElementById('results-title');
    heading?.focus({ preventScroll: true });
    heading?.scrollIntoView({ block: 'start', behavior: 'instant' });
  }, [data, page]);
  useEffect(() => {
    // Keep the chosen section and subsection visible in their horizontally scrolled chip rows.
    document.querySelectorAll<HTMLElement>('.chip-row .chip[aria-pressed=true]').forEach((chip) => {
      const row = chip.parentElement!;
      row.scrollLeft = chip.offsetLeft - row.offsetLeft - (row.clientWidth - chip.offsetWidth) / 2;
    });
  }, [filter.section, filter.subsection, facets]);
  function change(changes: Record<string, string>) {
    const next = new URLSearchParams(search);
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    next.delete('page');
    setSearch(next);
  }
  function changeFilter(changes: Partial<CatalogFilter>) {
    const next = new URLSearchParams(search);
    writeFilter(next, { ...filter, ...changes });
    next.delete('page');
    setSearch(next);
  }
  function toggleBrand(brand: string) {
    changeFilter({ brands: filter.brands.includes(brand) ? filter.brands.filter((item) => item !== brand) : [...filter.brands, brand] });
  }
  function pickCar(value: string) {
    const [make, model] = value ? JSON.parse(value) as [string, string | null] : [null, null];
    changeFilter({ make, model });
  }
  function reset() { setSearch({}); }
  function pageLink(value: number) { const next = new URLSearchParams(search); next.set('page', String(value)); return `/search?${next}`; }
  const hasFilters = Boolean(query) || available || sort !== 'name' || Boolean(filter.section || filter.make) || filter.brands.length > 0;
  const storeError = store.query.isError;
  const title = filter.subsection ?? filter.section ?? 'Каталог запчастей';
  // Selected options stay visible even when the other filters leave them without products.
  const keep = (values: FacetValue[] | undefined, selected: (string | null)[]) => {
    const list = [...(values ?? [])];
    selected.forEach((name) => { if (name && !list.some((item) => item.name === name)) list.push({ name, count: 0 }); });
    return list;
  };
  const sections = keep(facets?.sections, [filter.section]);
  const subsections = keep(facets?.subsections, [filter.subsection]);
  const brands = keep(facets?.brands, filter.brands);
  const shownBrands = allBrands || brands.length <= BRANDS_COLLAPSED + 2 ? brands
    : [...brands].sort((a, b) => Number(filter.brands.includes(b.name)) - Number(filter.brands.includes(a.name)) || b.count - a.count).slice(0, BRANDS_COLLAPSED);
  const makes = keep(facets?.makes, [filter.make]);
  const models = keep(facets?.models, [filter.model]);
  const carValue = filter.make ? JSON.stringify([filter.make, filter.model]) : '';
  return <>
    <nav className="breadcrumbs" aria-label="Вы здесь"><ol><li><Link to="/">Главная</Link></li>{filter.section ? <><li><Link to="/search">Каталог</Link></li><li aria-current="page">{filter.section}</li></> : <li aria-current="page">Каталог</li>}</ol></nav>
    <h1 className="page-title">{title}</h1>
    {filter.make && <section className="car-banner" aria-label="Выбранный автомобиль">
      <div className="car-banner-copy"><p className="car-banner-label">АВТОМОБИЛЬ</p><p className="car-banner-name">{carName(filter.make, filter.model)}</p></div>
      {data && <p className="car-banner-count">Подходит: {data.totalProducts.toLocaleString('ru-RU')} поз.</p>}
      <button type="button" className="button car-banner-reset" onClick={() => changeFilter({ make: null, model: null })}>Сбросить автомобиль</button>
    </section>}
    {sections.length > 0 && <nav className="chip-row" aria-label="Разделы каталога">
      <button type="button" className="chip" aria-pressed={!filter.section} onClick={() => changeFilter({ section: null, subsection: null })}>Все</button>
      {sections.map((item) => <button type="button" key={item.name} className="chip" aria-pressed={filter.section === item.name} onClick={() => changeFilter({ section: item.name, subsection: null })}>{item.name}</button>)}
    </nav>}
    {filter.section && subsections.length > 0 && <nav className="chip-row chip-row-sub" aria-label={`Подразделы: ${filter.section}`}>
      <button type="button" className="chip" aria-pressed={!filter.subsection} onClick={() => changeFilter({ subsection: null })}>Все в разделе</button>
      {subsections.map((item) => <button type="button" key={item.name} className="chip" aria-pressed={filter.subsection === item.name} onClick={() => changeFilter({ subsection: item.name })}>{item.name} <span className="chip-count">{item.count}</span></button>)}
    </nav>}
    <div className="catalog-layout">
      <aside id="catalog-filters" className="catalog-sidebar" data-open={filtersOpen} aria-label="Фильтры каталога">
        <button type="button" role="switch" aria-checked={available} className="switch-row" onClick={() => change({ available: available ? '' : 'true' })}>
          <span>Только в наличии</span><span className="switch" aria-hidden="true"><span /></span>
        </button>
        {brands.length > 0 && <fieldset className="filter-group">
          <legend className="filter-label">ПРОИЗВОДИТЕЛЬ</legend>
          <div className="check-list">
            {shownBrands.map((item) => <label key={item.name} className="check-row"><input type="checkbox" checked={filter.brands.includes(item.name)} onChange={() => toggleBrand(item.name)} /><span className="check-box" aria-hidden="true" /><span className="check-name">{item.name}</span><span className="check-count">{item.count}</span></label>)}
          </div>
          {shownBrands.length < brands.length ? <button type="button" className="link-button" onClick={() => setAllBrands(true)}>Показать все ({brands.length})</button>
            : allBrands && <button type="button" className="link-button" onClick={() => setAllBrands(false)}>Свернуть</button>}
        </fieldset>}
        {makes.length > 0 && <div className="filter-group">
          <label className="filter-label" htmlFor="car-select">АВТОМОБИЛЬ</label>
          <select id="car-select" className="filter-select" value={carValue} onChange={(event) => pickCar(event.target.value)}>
            <option value="">Любой</option>
            {makes.map((make) => make.name === filter.make && models.length > 0
              ? <optgroup key={make.name} label={make.name}>
                <option value={JSON.stringify([make.name, null])}>{make.name} — все модели ({make.count})</option>
                {models.map((model) => <option key={model.name} value={JSON.stringify([make.name, model.name])}>{carName(make.name, model.name)} ({model.count})</option>)}
              </optgroup>
              : <option key={make.name} value={JSON.stringify([make.name, null])}>{make.name} ({make.count})</option>)}
          </select>
          <p className="filter-note">Применимость взята из меток CloudShop.</p>
        </div>}
        <div className="sidebar-facts">
          <p><strong>{store.name ?? 'Склад'}</strong><br />{pickupText}</p>
          {data && <p className={data.stale ? 'stale-text' : undefined}>{data.lastSyncText ? `Наличие обновлено: ${data.lastSyncText}` : 'Каталог ещё не обновлялся'}{data.stale && '. Данные могут быть устаревшими — добавление в корзину временно недоступно.'}</p>}
        </div>
        {hasFilters && <button type="button" className="button button-block" onClick={reset}>Сбросить фильтры</button>}
      </aside>
      <section className="catalog-results" aria-labelledby="results-title" aria-busy={products.isFetching}>
        <div className="results-toolbar">
          <div className="results-heading">
            <button type="button" className="button button-dark filters-toggle" aria-expanded={filtersOpen} aria-controls="catalog-filters" onClick={() => setFiltersOpen((open) => !open)}>{filtersOpen ? 'Скрыть фильтры' : 'Фильтры'}</button>
            <h2 id="results-title" tabIndex={-1} className="results-count">{data ? <>Найдено: <b>{data.totalProducts.toLocaleString('ru-RU')}</b>{query && <> по запросу «{query}»</>}</> : 'Результаты'}</h2>
          </div>
          <fieldset className="segmented"><legend className="sr-only">Сортировка</legend>
            {catalogSorts.map((item) => <label key={item.value} className={sort === item.value ? 'is-active' : undefined}><input type="radio" name="sort" value={item.value} checked={sort === item.value} onChange={() => change({ sort: item.value === 'name' ? '' : item.value })} />{item.label}</label>)}
          </fieldset>
        </div>
        <p className="sr-only" role="status">{products.isFetching ? 'Обновляем данные…' : ''}</p>
        {storeError ? <Notice error><p>Не удалось загрузить данные склада.</p><button type="button" className="button" onClick={() => void store.query.refetch()}>Повторить</button></Notice>
          : invalid ? <EmptyState title="Проверьте параметры поиска"><p>Используйте запрос до 120 символов, существующую страницу и фильтры из списка.</p><button className="button" type="button" onClick={reset}>Показать весь каталог</button></EmptyState>
          : products.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем каталог…</div>
          : products.isError ? <Notice error><p>{products.error instanceof ApiError ? products.error.message : 'Не удалось загрузить каталог.'}</p><div className="actions"><button type="button" className="button" onClick={() => void products.refetch()}>Повторить</button><button type="button" className="button" onClick={reset}>Сбросить фильтры</button></div></Notice>
          : data && <>
            {data.products.length === 0 ? <div className="empty-panel"><h3>Товары не найдены</h3><p>Проверьте артикул или попробуйте более короткое название. Можно снять фильтры или отправить список артикулов через быстрый заказ.</p><div className="actions"><button type="button" className="button button-dark" onClick={reset}>Показать весь каталог</button><Link to="/quick-order" className="button">Быстрый заказ</Link></div></div>
              : <ul className="product-list">{data.products.map((product) => <ProductRow key={product.id} product={product} storeId={data.storeId} inCart={quantities.get(product.id) ?? 0} />)}</ul>}
            {data.totalPages > 1 && <nav className="pagination" aria-label="Страницы каталога">{data.page > 0 ? <Link className="button" to={pageLink(data.page - 1)} rel="prev">Назад</Link> : <span className="button button-disabled" aria-disabled="true">Назад</span>}<span>Страница {data.page + 1} из {data.totalPages}</span>{data.page + 1 < data.totalPages ? <Link className="button" to={pageLink(data.page + 1)} rel="next">Далее</Link> : <span className="button button-disabled" aria-disabled="true">Далее</span>}</nav>}
          </>}
      </section>
    </div>
  </>;
}
