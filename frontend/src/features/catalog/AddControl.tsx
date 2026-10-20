import { Link } from 'react-router-dom';
import type { Product } from '../../shared/api/catalog';
import { availability } from '../../shared/api/catalog';
import { QtyStepper } from '../../shared/ui/QtyStepper';
import { useToast } from '../../shared/ui/Toast';
import { useCartAction, useCartBusy } from '../cart/cartQueries';

export const productHref = (id: string) => `/products/${encodeURIComponent(id)}`;

/** «В корзину», a quantity stepper once the item is in the cart, or the reason it cannot be ordered. */
export function AddControl({ product, storeId, inCart, size = 'md' }: { product: Product; storeId: string; inCart: number; size?: 'sm' | 'md' }) {
  const action = useCartAction();
  const busy = useCartBusy();
  const toast = useToast();
  const state = availability(product);
  const stock = product.availableQuantity;
  const disabled = busy || !storeId;
  function run(kind: 'add' | 'update' | 'remove', quantity?: number, announce?: string) {
    const fields: Record<string, string> = { productId: product.id };
    if (kind === 'add') { fields.storeId = storeId; fields.quantity = String(quantity ?? 1); }
    if (kind === 'update') fields.quantity = String(quantity);
    action.mutate({ action: kind, fields }, {
      onSuccess: () => { if (announce) toast({ text: announce, action: { label: 'Открыть корзину', to: '/basket' } }); },
      onError: (error) => toast({ text: error.message, error: true }),
    });
  }
  if (inCart > 0) return <QtyStepper size={size} label={`Количество «${product.name}» в корзине`} value={inCart} min={0} disabled={disabled} canIncrease={stock === null || inCart < stock}
    onChange={(next) => next <= 0 ? run('remove', undefined, `Убрано из корзины: ${product.name}`) : next > inCart && next - inCart === 1 ? run('add', 1) : run('update', next)} />;
  if (state === 'ok') return <button type="button" className={`button button-primary add-button add-button-${size}`} disabled={disabled} aria-label={`В корзину: ${product.name}`} onClick={() => run('add', 1, `Добавлено: ${product.name}`)}>В корзину</button>;
  const reason = state === 'price' ? 'цена уточняется' : state === 'stock' ? 'нет на складе' : 'наличие уточняется';
  return <Link to={productHref(product.id)} className={`button add-button add-button-${size} add-blocked`} aria-label={`Подробнее: ${product.name}, ${reason}`}>Подробнее</Link>;
}

export function stockTone(product: Product) {
  const state = availability(product);
  return state === 'stock' ? 'stock-out' : state === 'ok' ? 'stock-in' : 'stock-unknown';
}

export function PartImage({ src, className }: { src: string | null; className: string }) {
  return <span className={`part-image ${className}`} aria-hidden="true">{src ? <img src={src} alt="" loading="lazy" onError={(event) => { event.currentTarget.hidden = true; }} /> : <PartGlyph />}</span>;
}
function PartGlyph() {
  return <svg viewBox="0 0 48 48" width="40" height="40" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><circle cx="24" cy="24" r="15" /><circle cx="24" cy="24" r="5" /><path d="M24 9v6M24 33v6M9 24h6M33 24h6" /></svg>;
}
