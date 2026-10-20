import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { CartAction } from '../../shared/api/cart';
import { Button } from '../../shared/ui/Button';
import { Notice } from '../../shared/ui/Feedback';
import { QtyStepper } from '../../shared/ui/QtyStepper';
import { Checkout } from '../checkout/Checkout';
import { readPendingKey } from '../../shared/api/checkout';
import { PartImage, productHref } from '../catalog/AddControl';
import { useCart, useCartAction, useCartBusy } from './cartQueries';

export function Cart({ checkout = false }: { checkout?: boolean }) {
  const snapshot = useCart();
  const action = useCartAction();
  const busy = useCartBusy();
  const [orderLocked, setOrderLocked] = useState(false);
  const [confirmation, setConfirmation] = useState<{ title: string; action: CartAction } | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const cancelButton = useRef<HTMLButtonElement>(null);
  const origin = useRef<HTMLElement | null>(null);
  const data = snapshot.data;
  const cart = data?.cart;
  useEffect(() => {
    if (confirmation) { origin.current = document.activeElement as HTMLElement; dialog.current?.showModal(); cancelButton.current?.focus(); }
    else if (dialog.current?.open) { dialog.current.close(); if (origin.current?.isConnected) origin.current.focus(); else document.getElementById('basket-title')?.focus(); }
  }, [confirmation]);
  useEffect(() => {
    if (!busy && origin.current && !origin.current.isConnected) { document.getElementById('basket-title')?.focus(); origin.current = null; }
  }, [cart, busy]);
  useEffect(() => { if (checkout) document.getElementById('checkout-title')?.scrollIntoView({ block: 'start' }); }, [checkout, Boolean(cart)]);
  // While a submitted order awaits its result, the cart must not change under it.
  const disabled = busy || snapshot.isFetching || snapshot.isError || orderLocked;
  function confirm() {
    if (!confirmation || disabled) return;
    action.mutate(confirmation.action);
    setConfirmation(null);
  }
  const empty = cart && cart.items.length === 0;
  return <>
    <h1 id="basket-title" tabIndex={-1} className="page-title">Корзина {data && data.itemCount > 0 && <span className="title-count">{data.itemCount} шт.</span>}</h1>
    {action.isError && <Notice error>{action.error.message} Проверьте текущее состояние корзины перед повторным действием.</Notice>}
    {snapshot.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем корзину…</div> : <>
      {snapshot.isError && <Notice error><p>Не удалось обновить корзину. Изменения временно недоступны.</p><Button disabled={busy} onClick={() => void snapshot.refetch()}>Обновить данные</Button></Notice>}
      <div className={`basket-layout ${empty && !orderLocked && !readPendingKey() ? 'basket-empty' : ''}`}>
        {cart && <div className="basket-lines">
          {cart.items.length === 0 ? <div className="empty-panel"><h2>Корзина пока пуста</h2><p>Найдите деталь по артикулу или соберите корзину списком.</p><div className="actions"><Link className="button button-primary" to="/search">Перейти в каталог</Link><Link className="button" to="/quick-order">Быстрый заказ</Link></div></div> : <>
            <ul className="cart-lines" aria-busy={busy || snapshot.isFetching}>{cart.items.map((item) => <li key={item.id} className="cart-line">
              <PartImage src={null} className="part-image-cart" />
              <div className="cart-line-copy">
                <h2><Link to={productHref(item.id)}>{item.name}</Link></h2>
                <p className="muted">{item.sku ? <span className="code">{item.sku}</span> : 'Артикул не указан'} · {item.priceText} / {item.unit || 'шт.'}</p>
                {item.problem && <p className="item-problem">{item.problem}</p>}
                {item.priceChanged && <p className="item-problem">Цена изменилась. Проверьте новую цену и подтвердите её.</p>}
                {item.priceChanged && !item.problem && item.currentPrice !== null && <Button disabled={disabled} onClick={() => action.mutate({ action: 'confirm-price', fields: { productId: item.id, expectedPrice: item.currentPrice! } })}>Подтвердить новую цену</Button>}
              </div>
              <div className="cart-line-actions">
                <QtyStepper size="sm" label={`Количество «${item.name}»`} value={item.quantity} disabled={disabled} onChange={(quantity) => action.mutate({ action: 'update', fields: { productId: item.id, quantity: String(quantity) } })} />
                <div className="cart-line-total"><strong>{item.totalText}</strong><button type="button" className="text-button" disabled={disabled} aria-label={`Удалить ${item.name} из корзины`} onClick={() => setConfirmation({ title: `Удалить «${item.name}» из корзины?`, action: { action: 'remove', fields: { productId: item.id } } })}>Удалить</button></div>
              </div>
            </li>)}</ul>
            {cart.hasIssues && <Notice error>Некоторые позиции требуют проверки. Обновите количество или удалите недоступные товары.</Notice>}
            <div className="basket-foot"><p className="muted">Корзина не резервирует товар. Количество ограничено остатком на складе.</p><button type="button" className="text-button" disabled={disabled} onClick={() => setConfirmation({ title: 'Очистить корзину?', action: { action: 'clear', fields: {} } })}>Очистить корзину</button></div>
          </>}
        </div>}
        {(!cart || cart.items.length > 0 || orderLocked || readPendingKey()) && <Checkout onLockChange={setOrderLocked} />}
      </div>
    </>}
    <dialog ref={dialog} className="confirmation-dialog" aria-labelledby="confirmation-title" onCancel={(event) => { event.preventDefault(); setConfirmation(null); }}><h2 id="confirmation-title">{confirmation?.title}</h2><p>Товары можно будет добавить снова из каталога.</p><div className="dialog-actions"><button ref={cancelButton} className="button" onClick={() => setConfirmation(null)}>Отмена</button><Button primary disabled={disabled} onClick={confirm}>{confirmation?.action.action === 'clear' ? 'Очистить' : 'Удалить'}</Button></div></dialog>
  </>;
}
