import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { CartAction, CartItem } from '../../shared/api/cart';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/Button';
import { EmptyState, Notice } from '../../shared/ui/Feedback';
import { useCart, useCartAction, useCartBusy } from './cartQueries';

function Quantity({ item, disabled, update }: { item: CartItem; disabled: boolean; update: (value: string) => void }) {
  const [quantity, setQuantity] = useState(String(item.quantity));
  return <form className="quantity-form" onSubmit={(event) => { event.preventDefault(); if (/^\d+$/.test(quantity) && Number(quantity) >= 1 && Number(quantity) <= 999) update(quantity); }}>
    <label htmlFor={`quantity-${item.id}`}>Количество</label><div className="quantity-controls"><input id={`quantity-${item.id}`} name="quantity" type="number" inputMode="numeric" autoComplete="off" min="1" max="999" step="1" required value={quantity} disabled={disabled} onChange={(event) => { const value = event.target.value; event.target.setCustomValidity(/^\d+$/.test(value) && Number(value) >= 1 && Number(value) <= 999 ? '' : 'Укажите целое количество от 1 до 999.'); setQuantity(value); }} /><Button type="submit" disabled={disabled || quantity === String(item.quantity)}>Обновить</Button></div>
  </form>;
}

export function Cart() {
  const snapshot = useCart();
  const action = useCartAction();
  const busy = useCartBusy();
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
    if (!busy && origin.current && !origin.current.isConnected) {
      document.getElementById('basket-title')?.focus();
      origin.current = null;
    }
  }, [cart, busy]);
  const disabled = busy || snapshot.isFetching || snapshot.isError;
  function confirm() {
    if (!confirmation || disabled) return;
    action.mutate(confirmation.action);
    setConfirmation(null);
  }
  return <>
    <nav className="back-nav" aria-label="Навигация"><Link to={cart?.storeId ? `/?store=${encodeURIComponent(cart.storeId)}` : '/'}><Icon name="arrow" />Продолжить поиск</Link></nav>
    <nav className="purchase-steps" aria-label="Этапы покупки"><ol><li><Link to="/">Каталог</Link></li><li aria-current="step">Корзина</li><li><Link to="/checkout">Оформление</Link></li></ol></nav>
    <div className="heading-row"><h1 id="basket-title" tabIndex={-1}>Корзина</h1>{cart?.storeName && <span className="muted">{cart.storeName}</span>}</div>
    {action.isError && <Notice error>{action.error.message} Проверьте текущее состояние корзины перед повторным действием.</Notice>}
    {action.isSuccess && <Notice>{action.data.message || 'Корзина обновлена.'}</Notice>}
    {snapshot.isPending ? <div className="loading-state" role="status">Загружаем корзину…</div> : <>
      {snapshot.isError && <Notice error><p>Не удалось обновить корзину. Изменения временно недоступны.</p><Button disabled={busy} onClick={() => void snapshot.refetch()}>Обновить данные</Button></Notice>}
      {cart && (cart.items.length === 0 ? <EmptyState title="Корзина пока пуста"><p>Откройте карточку запчасти и добавьте товар из нужной точки.</p><Link className="button button-primary" to="/">Перейти в каталог</Link></EmptyState> : <div className="basket-layout" aria-busy={busy || snapshot.isFetching}>
        <div className="cart-lines">{cart.items.map((item) => <article key={item.id} className="cart-line"><div className="cart-item-copy"><h2><Link to={`/products/${encodeURIComponent(item.id)}?store=${encodeURIComponent(cart.storeId || '')}`}>{item.name}</Link></h2><div className="cart-line-meta"><p className="sku">{item.sku ? `Артикул: ${item.sku}` : 'Артикул не указан'}</p><p className="unit-price">{item.priceText} за {item.unit || 'единицу'}</p></div>{item.problem && <p className="item-problem">{item.problem}</p>}{item.priceChanged && <p className="item-problem">Цена изменилась. Проверьте новую цену и подтвердите её.</p>}{item.priceChanged && !item.problem && item.currentPrice !== null && <Button disabled={disabled} onClick={() => action.mutate({ action: 'confirm-price', fields: { productId: item.id, expectedPrice: item.currentPrice! } })}>Подтвердить новую цену</Button>}</div><div className="cart-item-actions"><Quantity key={`${item.id}-${item.quantity}`} item={item} disabled={disabled} update={(quantity) => action.mutate({ action: 'update', fields: { productId: item.id, quantity } })} /><strong className="cart-line-total">{item.totalText}</strong><button className="text-button" disabled={disabled} aria-label={`Удалить ${item.name} из корзины`} onClick={() => setConfirmation({ title: `Удалить «${item.name}» из корзины?`, action: { action: 'remove', fields: { productId: item.id } } })}>Удалить</button></div></article>)}</div>
        <aside className="cart-summary" aria-label="Итог корзины"><h2>Ваша корзина</h2><p className="cart-summary-store"><Icon name="location" />{cart.storeName}</p><p className="cart-summary-count">Товаров: {data.itemCount}</p><span>Итого по доступным позициям</span><strong>{cart.totalText}</strong>{cart.hasIssues && <p className="item-problem">Некоторые позиции требуют проверки. Обновите количество или удалите недоступные товары.</p>}<Link className="button button-primary checkout-link" to="/checkout">Оформить заявку<Icon name="arrow" /></Link><p className="summary-note">При оформлении проверим цену и наличие. Товары пока не резервируются.</p><Button disabled={disabled} onClick={() => setConfirmation({ title: 'Очистить корзину?', action: { action: 'clear', fields: {} } })}>Очистить корзину</Button></aside>
      </div>)}
      {cart && cart.items.length > 0 && <p className="flow-note">Корзина сохраняется на время сессии браузера и не резервирует товар. При оформлении проверим актуальную цену и наличие.</p>}
    </>}
    <dialog ref={dialog} className="confirmation-dialog" aria-labelledby="confirmation-title" onCancel={(event) => { event.preventDefault(); setConfirmation(null); }}><h2 id="confirmation-title">{confirmation?.title}</h2><p>Товары можно будет добавить снова из каталога.</p><div className="dialog-actions"><button ref={cancelButton} className="button" onClick={() => setConfirmation(null)}>Отмена</button><Button primary disabled={disabled} onClick={confirm}>{confirmation?.action.action === 'clear' ? 'Очистить' : 'Удалить'}</Button></div></dialog>
  </>;
}
