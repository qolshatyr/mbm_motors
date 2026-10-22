import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { carName, catalogHref, fetchDetail, fetchProducts, priceUnknown } from '../../shared/api/catalog';
import type { Classification } from '../../shared/api/catalog';
import { Button } from '../../shared/ui/Button';
import { Notice } from '../../shared/ui/Feedback';
import { QtyStepper } from '../../shared/ui/QtyStepper';
import { useToast } from '../../shared/ui/Toast';
import { useCartAction, useCartBusy, useCartQuantities } from '../cart/cartQueries';
import { PartImage, productHref } from '../catalog/AddControl';
import { pickupText, useDefaultStore } from '../catalog/useStore';

export function ProductDetail() {
  const { id = '' } = useParams();
  const store = useDefaultStore();
  const detail = useQuery({ queryKey: ['product', id, store.id], queryFn: ({ signal }) => fetchDetail(id, store.id, signal), enabled: Boolean(store.id), refetchInterval: 60000 });
  const action = useCartAction();
  const busy = useCartBusy();
  const toast = useToast();
  const inCart = useCartQuantities().get(id) ?? 0;
  const [quantity, setQuantity] = useState(1);
  useEffect(() => { document.title = detail.data ? `${detail.data.product.name} — MBM Automotors` : 'Запчасть — MBM Automotors'; }, [detail.data]);
  useEffect(() => { setQuantity(1); }, [id]);
  const data = detail.data;
  const offer = data?.offers.find((item) => item.storeId === (data.selectedStore || store.id)) ?? data?.offers[0];
  const unknownPrice = data ? (data.product.price === null || data.product.price <= 0) : offer ? priceUnknown(offer.priceText) : true;
  const stock = data?.product.availableQuantity ?? null;
  const canAdd = Boolean(offer?.canAdd) && !unknownPrice;
  const maxQuantity = Math.max(1, Math.min(999, stock ?? 999) - inCart);
  function add() {
    if (!offer || !canAdd || busy) return;
    action.mutate({ action: 'add', fields: { productId: id, storeId: offer.storeId, quantity: String(quantity) } }, {
      onSuccess: () => { toast({ text: `Добавлено: ${data!.product.name}, ${quantity} шт.`, action: { label: 'Открыть корзину', to: '/basket' } }); setQuantity(1); },
      onError: (error) => toast({ text: error.message, error: true }),
    });
  }
  return <>
    <nav className="breadcrumbs" aria-label="Вы здесь"><ol><li><Link to="/">Главная</Link></li><li><Link to="/search">Каталог</Link></li>{data?.classification.section && <li><Link to={catalogHref({ section: data.classification.section })}>{data.classification.section}</Link></li>}<li aria-current="page">{data?.product.name ?? 'Запчасть'}</li></ol></nav>
    {detail.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем запчасть…</div>
      : detail.isError ? <Notice error><p>{detail.error.message}</p><Button onClick={() => void detail.refetch()}>Повторить</Button></Notice>
      : data && <div className="product-layout">
        <PartImage src={data.product.imageUrl} className="part-image-detail" />
        <div className="product-main">
          <div>{data.classification.brands.length > 0 && <p className="product-brand-label">{data.classification.brands.join(', ')}</p>}<h1 id="product-title" className="product-title">{data.product.name}</h1></div>
          <dl className="product-facts">
            <div><dt>Артикул</dt><dd className="code">{data.product.sku || 'Не указан'}</dd></div>
            <div><dt>Код товара</dt><dd className="code">{data.product.code || 'Не указан'}</dd></div>
            <div><dt>Единица</dt><dd>{data.product.unit || 'Не указана'}</dd></div>
          </dl>
          <section className="buy-panel" aria-labelledby="buy-title" aria-busy={detail.isFetching}>
            <h2 id="buy-title" className="sr-only">Цена и наличие</h2>
            <div className="buy-panel-head">
              <div><p className="buy-label">Оптовая цена</p><p className={`buy-price ${unknownPrice ? 'price-unknown' : ''}`}>{unknownPrice ? 'Цена уточняется' : offer?.priceText}</p></div>
              <div className="buy-stock"><p className={`stock ${stock ? 'stock-in' : /^нет/i.test(offer?.stockText ?? '') ? 'stock-out' : 'stock-unknown'}`}>{offer?.stockText ?? 'Нет данных о наличии'}</p><p className="muted">{pickupText}</p></div>
            </div>
            {canAdd ? <div className="buy-actions">
              <QtyStepper size="lg" label="Количество для добавления" value={quantity} disabled={busy} canIncrease={quantity < maxQuantity} onChange={(next) => setQuantity(Math.max(1, Math.min(next, maxQuantity)))} />
              <button type="button" className="button button-primary button-lg buy-add" disabled={busy || detail.isFetching} onClick={add}>{busy ? 'Добавляем…' : inCart ? `Добавить ещё · в корзине ${inCart}` : 'В корзину'}</button>
            </div> : <p className="buy-blocked">{unknownPrice ? 'Цена этой позиции уточняется. Пока цена не указана, её нельзя добавить в заявку — уточните у менеджера.' : offer?.unavailabilityReason || 'Сейчас эту позицию нельзя добавить в заявку. Уточните наличие у менеджера.'}</p>}
            <p className="buy-note">{data.lastSyncText ? `Наличие обновлено: ${data.lastSyncText}. ` : ''}Наличие не является резервом. Менеджер подтвердит заявку.</p>
            {data.stale && <Notice error>Данные о наличии устарели. Добавление временно недоступно.</Notice>}
          </section>
          {inCart > 0 && <p className="muted">В корзине: {inCart} шт. <Link to="/basket">Открыть корзину</Link></p>}
          {data.classification.fitment.length > 0 && <section className="fitment" aria-labelledby="fitment-title">
            <h2 id="fitment-title" className="fitment-title">Применимость</h2>
            <ul className="fitment-list">{data.classification.fitment.flatMap((item) => item.models.length ? item.models.map((model) => carName(item.make, model)) : [item.make]).map((car) => <li key={car}>{car}</li>)}</ul>
          </section>}
        </div>
      </div>}
    {data && <MoreInSection id={id} classification={data.classification} storeId={store.id} />}
  </>;
}

/** Up to four other products of the same subsection, or of the section when there is no subsection. */
function MoreInSection({ id, classification, storeId }: { id: string; classification: Classification; storeId: string | null }) {
  const { section, subsection } = classification;
  const params = new URLSearchParams({ q: '', available: 'true', page: '0' });
  if (storeId) params.set('store', storeId);
  if (section) params.set('section', section);
  if (section && subsection) params.set('subsection', subsection);
  const more = useQuery({ queryKey: ['products', params.toString()], queryFn: ({ signal }) => fetchProducts(params, signal), enabled: Boolean(section && storeId), staleTime: 30000 });
  const items = more.data?.products.filter((item) => item.id !== id).slice(0, 4) ?? [];
  if (!section || items.length === 0) return null;
  const name = subsection ?? section;
  return <section className="more-section" aria-labelledby="more-title">
    <div className="section-heading"><h2 id="more-title">Ещё в категории «{name}»</h2><Link to={catalogHref({ section, subsection })}>Все товары →</Link></div>
    <ul className="more-grid">{items.map((item) => <li key={item.id}><Link to={productHref(item.id)} className="more-card">
      <span className="more-sku code">{item.sku || 'Артикул не указан'}</span>
      <span className="more-name">{item.name}</span>
      <span className="more-foot"><span className={item.price ? 'more-price' : 'more-price more-price-unknown'}>{item.price ? item.priceText : 'Цена уточняется'}</span><span className="more-stock">{item.availableQuantity ? 'В наличии' : 'Наличие уточняется'}</span></span>
    </Link></li>)}</ul>
  </section>;
}
