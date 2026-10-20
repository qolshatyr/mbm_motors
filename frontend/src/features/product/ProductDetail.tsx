import { useEffect } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchDetail } from '../../shared/api/catalog';
import { Button } from '../../shared/ui/Button';
import { Notice } from '../../shared/ui/Feedback';
import { useCartAction, useCartBusy } from '../cart/cartQueries';

export function ProductDetail() {
  const { id = '' } = useParams();
  const [search] = useSearchParams();
  const store = search.get('store') ?? '';
  const detail = useQuery({ queryKey: ['product', id, store], queryFn: ({ signal }) => fetchDetail(id, store, signal), refetchInterval: 60000 });
  const action = useCartAction();
  const busy = useCartBusy();
  useEffect(() => { document.title = detail.data ? `${detail.data.product.name} — MBM Motors` : 'Запчасть — MBM Motors'; }, [detail.data, id, store]);
  useEffect(() => { action.reset(); }, [id, store]);
  const data = detail.data;
  return <>
    <nav className="back-nav" aria-label="Навигация"><Link to={`/?store=${encodeURIComponent(data?.selectedStore || store)}`}>Вернуться в каталог</Link></nav>
    {detail.isPending ? <div className="loading-state" role="status">Загружаем запчасть…</div> : detail.isError ? <Notice error><p>{detail.error.message}</p><Button onClick={() => void detail.refetch()}>Повторить</Button></Notice> : data && <>
      <section className="detail-product" aria-labelledby="product-title" aria-busy={detail.isFetching}>
        <div className="detail-image"><img src={data.product.imageUrl ?? '/images/part-placeholder.svg'} alt="" width="320" height="320" onError={(event) => { if (event.currentTarget.src.endsWith('/images/part-placeholder.svg')) event.currentTarget.style.visibility = 'hidden'; else event.currentTarget.src = '/images/part-placeholder.svg'; }} /></div>
        <div><h1 id="product-title">{data.product.name}</h1><dl className="product-specs"><div><dt>Артикул</dt><dd>{data.product.sku || 'Не указан'}</dd></div><div><dt>Код товара</dt><dd>{data.product.code || 'Не указан'}</dd></div><div><dt>Единица измерения</dt><dd>{data.product.unit || 'Не указана'}</dd></div></dl><p className="muted">Сравните цену и наличие в каждой точке.</p></div>
      </section>
      <div className={`freshness ${data.stale ? 'freshness-stale' : ''}`}><span className="status-dot" aria-hidden="true" /><span>{data.lastSyncText ? `Данные обновлены: ${data.lastSyncText}` : 'Каталог ещё не обновлялся'}</span>{data.stale && <p>Данные о наличии устарели. Добавление временно недоступно.</p>}</div>
      {action.isError && <Notice error>{action.error.message}</Notice>}
      {action.isSuccess && <Notice>{action.data.message || 'Товар добавлен в корзину.'} <Link to="/basket">Открыть корзину</Link></Notice>}
      <section aria-labelledby="offers-title"><h2 id="offers-title">Цена и наличие</h2><div className="offer-list">{data.offers.map((offer) => <article key={offer.storeId} className={`offer-row ${offer.storeId === data.selectedStore ? 'selected-offer' : ''}`}><div><h3>{offer.storeName}</h3><p className="muted">{offer.stockText}</p></div><strong>{offer.priceText}</strong>{offer.canAdd ? <Button primary disabled={busy || detail.isFetching || detail.isError} aria-label={`Добавить из ${offer.storeName} в корзину`} onClick={() => action.mutate({ action: 'add', fields: { productId: id, storeId: offer.storeId, quantity: '1' } })}>{busy ? 'Подождите…' : 'В корзину'}</Button> : <span className="muted">{offer.unavailabilityReason || 'Добавление недоступно'}</span>}</article>)}</div><p className="flow-note">Одна корзина относится к одной точке. Наличие не является резервом.</p></section>
    </>}
  </>;
}
