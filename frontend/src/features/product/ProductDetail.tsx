import { useEffect } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchDetail } from '../../shared/api/catalog';
import { Button } from '../../shared/ui/Button';
import { Icon } from '../../shared/ui/Icon';
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
    <nav className="back-nav" aria-label="Навигация"><Link to={`/?store=${encodeURIComponent(data?.selectedStore || store)}`}><Icon name="arrow" />Вернуться в каталог</Link></nav>
    {detail.isPending ? <div className="loading-state" role="status">Загружаем запчасть…</div> : detail.isError ? <Notice error><p>{detail.error.message}</p><Button onClick={() => void detail.refetch()}>Повторить</Button></Notice> : data && <>
      <div className="detail-layout">
      <section className="detail-product" aria-labelledby="product-title" aria-busy={detail.isFetching}>
        <div className="detail-image"><img src={data.product.imageUrl ?? '/images/part-placeholder.svg'} alt="" width="320" height="320" onError={(event) => { if (event.currentTarget.src.endsWith('/images/part-placeholder.svg')) event.currentTarget.style.visibility = 'hidden'; else event.currentTarget.src = '/images/part-placeholder.svg'; }} /></div>
        <div className="detail-copy"><h1 id="product-title">{data.product.name}</h1></div>
      </section>
      <section className="purchase-panel" aria-labelledby="offers-title"><div className="purchase-panel-heading"><h2 id="offers-title">Цена и наличие</h2><p className="muted">Выберите точку для покупки</p></div>
      <div className={`freshness ${data.stale ? 'freshness-stale' : ''}`}><span className="status-dot" aria-hidden="true" /><span>{data.lastSyncText ? `Данные обновлены: ${data.lastSyncText}` : 'Каталог ещё не обновлялся'}</span>{data.stale && <p>Данные о наличии устарели. Добавление временно недоступно.</p>}</div>
      {action.isError && <Notice error>{action.error.message}</Notice>}
      {action.isSuccess && <Notice>{action.data.message || 'Товар добавлен в корзину.'} <Link to="/basket">Открыть корзину</Link></Notice>}
      <div className="offer-list">{data.offers.map((offer) => <article key={offer.storeId} className={`offer-row ${offer.storeId === data.selectedStore ? 'selected-offer' : ''}`}><div className="offer-location"><h3><Icon name="location" />{offer.storeName}</h3><p className="offer-stock">{offer.stockText}</p></div><strong className="offer-price">{offer.priceText}</strong>{offer.canAdd ? <Button primary disabled={busy || detail.isFetching || detail.isError} aria-label={`Добавить из ${offer.storeName} в корзину`} onClick={() => action.mutate({ action: 'add', fields: { productId: id, storeId: offer.storeId, quantity: '1' } })}>{busy ? 'Подождите…' : 'В корзину'}</Button> : <span className="offer-unavailable">{offer.unavailabilityReason || 'Добавление недоступно'}</span>}</article>)}</div><p className="flow-note">Одна корзина относится к одной точке. Наличие не является резервом.</p></section>
      <section className="detail-characteristics" aria-labelledby="specs-title"><h2 id="specs-title">Характеристики</h2><dl className="product-specs"><div><dt>Артикул</dt><dd>{data.product.sku || 'Не указан'}</dd></div><div><dt>Код товара</dt><dd>{data.product.code || 'Не указан'}</dd></div><div><dt>Единица измерения</dt><dd>{data.product.unit || 'Не указана'}</dd></div></dl><p className="detail-guidance">Проверьте артикул перед покупкой. Выберите точку с подходящей ценой и наличием.</p></section>
      </div>
    </>}
  </>;
}
