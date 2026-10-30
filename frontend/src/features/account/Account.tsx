import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Partner } from '../../shared/api/auth';
import { fetchOrder, fetchOrders, requestCancel } from '../../shared/api/account';
import type { AccountOrder } from '../../shared/api/account';
import { Notice } from '../../shared/ui/Feedback';
import { useToast } from '../../shared/ui/Toast';
import { useLogout } from '../auth/session';
import { useBulkAdd, useCartBusy } from '../cart/cartQueries';
import type { BulkResult } from '../cart/cartQueries';
import { useDefaultStore } from '../catalog/useStore';

const dateFormatter = new Intl.DateTimeFormat('ru-RU', { timeZone: 'Asia/Almaty', day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const statusTone: Record<AccountOrder['status'], string> = { RECEIVED: 'tag-pending', SENT: 'tag-ok', UNKNOWN: 'tag-warn', CANCEL_REQUESTED: 'tag-warn', CANCELLED: 'tag-muted' };
export function Account({ partner }: { partner: Partner }) {
  const orders = useQuery({ queryKey: ['account', 'orders'], queryFn: ({ signal }) => fetchOrders(signal), refetchInterval: 60000 });
  const logout = useLogout();
  const navigate = useNavigate();
  const client = useQueryClient();
  const toast = useToast();
  const store = useDefaultStore();
  const busy = useCartBusy();
  const bulkAdd = useBulkAdd();
  const [repeating, setRepeating] = useState<string | null>(null);
  const [result, setResult] = useState<{ number: string; outcome: BulkResult } | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  async function repeat(number: string) {
    if (repeating || busy || !store.id) return;
    setRepeating(number); setResult(null);
    try {
      const detail = await client.fetchQuery({ queryKey: ['account', 'order', number], queryFn: ({ signal }) => fetchOrder(number, signal), staleTime: 0 });
      const outcome = await bulkAdd(detail.items.map((item) => ({ productId: item.productId, name: item.name, quantity: item.quantity })), store.id);
      if (outcome.failed.length === 0) { toast({ text: `Заявка ${number}: позиции добавлены в корзину` }); navigate('/basket'); }
      else setResult({ number, outcome });
    } catch (error) {
      toast({ text: error instanceof Error ? error.message : 'Не удалось загрузить состав заявки.', error: true });
    } finally { setRepeating(null); }
  }
  return <>
    <div className="account-head">
      <div><p className="muted">Личный кабинет</p><h1 className="page-title">{partner.company}</h1><p className="muted">{[partner.contactName, partner.phone].filter(Boolean).join(', ')}</p></div>
      <div className="account-actions"><span className="tag tag-ok tag-lg">Оптовая цена</span><button type="button" className="button" disabled={logout.isPending} onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/login', { replace: true }) })}>{logout.isPending ? 'Выходим…' : 'Выйти'}</button></div>
    </div>
    {logout.isError && <Notice error>Не удалось выйти. Проверьте соединение и попробуйте ещё раз.</Notice>}
    <section aria-labelledby="orders-title" className="account-orders" aria-busy={orders.isFetching}>
      <h2 id="orders-title" className="section-title">Мои заявки</h2>
      {result && <Notice error><p>Заявка {result.number}: добавлено позиций {result.outcome.added.length}. Не удалось добавить:</p><ul>{result.outcome.failed.map((item) => <li key={item.productId}>{item.name} — {item.reason}</li>)}</ul><p><Link to="/basket">Открыть корзину</Link></p></Notice>}
      {orders.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем заявки…</div>
        : orders.isError ? <Notice error><p>{orders.error.message}</p><button type="button" className="button" onClick={() => void orders.refetch()}>Повторить</button></Notice>
        : orders.data.orders.length === 0 ? <div className="empty-panel"><h3>Заявок пока нет</h3><p>Соберите корзину в каталоге или списком артикулов — заявки появятся здесь.</p><div className="actions"><Link to="/search" className="button button-primary">Перейти в каталог</Link><Link to="/quick-order" className="button">Быстрый заказ</Link></div></div>
        : <ul className="order-list">{orders.data.orders.map((order) => <li key={order.number} className="order-row">
          <div className="order-main"><p><strong>{order.number}</strong><span className={`tag ${statusTone[order.status] ?? 'tag-muted'}`}>{order.statusText}</span></p><p className="muted"><time dateTime={order.createdAt}>{dateFormatter.format(new Date(order.createdAt))}</time>, {order.itemCount} шт.</p></div>
          <p className="order-total">{order.totalText}</p>
          <div className="order-actions"><button type="button" className="button" aria-expanded={open === order.number} aria-controls={`order-${order.number}`} onClick={() => setOpen(open === order.number ? null : order.number)}>{open === order.number ? 'Скрыть состав' : 'Состав'}</button>
          <button type="button" className="button" disabled={Boolean(repeating) || busy || !store.id} aria-label={`Повторить заказ ${order.number}`} onClick={() => void repeat(order.number)}>{repeating === order.number ? 'Добавляем…' : 'Повторить заказ'}</button></div>
          {open === order.number && <OrderDetails number={order.number} />}
        </li>)}</ul>}
    </section>
  </>;
}

/** Saved items and prices of one order, and the partner's request to cancel it. */
function OrderDetails({ number }: { number: string }) {
  const detail = useQuery({ queryKey: ['account', 'order', number], queryFn: ({ signal }) => fetchOrder(number, signal) });
  return <div id={`order-${number}`} className="order-details">
    {detail.isPending ? <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />Загружаем состав…</div>
      : detail.isError ? <Notice error><p>{detail.error.message}</p><button type="button" className="button" onClick={() => void detail.refetch()}>Повторить</button></Notice>
      : <>
        <ul className="order-items">{detail.data.items.map((item, index) => <li key={index}><div><p className="order-item-name">{item.name}</p>{item.sku && <p className="sku">Артикул: {item.sku}</p>}<p className="muted">{item.quantity} {item.unit} × {item.priceText}</p></div><strong>{item.lineTotalText}</strong></li>)}</ul>
        <p className="muted">Цены — на момент оформления заявки.</p>
        {detail.data.status === 'CANCEL_REQUESTED' && <Notice><p>Вы попросили отменить заявку{detail.data.cancelReason ? `: «${detail.data.cancelReason}»` : ''}. Менеджер отменит её и свяжется с вами.</p></Notice>}
        {detail.data.status === 'CANCELLED' && <Notice><p>Заявка отменена.</p></Notice>}
        {detail.data.cancellable && <CancelRequest number={number} />}
      </>}
  </div>;
}

function CancelRequest({ number }: { number: string }) {
  const client = useQueryClient();
  const toast = useToast();
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');
  const send = useMutation({
    mutationFn: () => requestCancel(number, reason.trim()),
    onSuccess: (detail) => {
      client.setQueryData(['account', 'order', number], detail);
      void client.invalidateQueries({ queryKey: ['account', 'orders'] });
      toast({ text: `Заявка ${number}: запрос на отмену отправлен` });
    },
    onError: () => { void client.invalidateQueries({ queryKey: ['account', 'order', number] }); },
  });
  if (!asking) return <button type="button" className="button order-cancel" onClick={() => setAsking(true)}>Попросить отменить</button>;
  return <form className="order-cancel-form" onSubmit={(event) => { event.preventDefault(); if (!send.isPending) send.mutate(); }}>
    <p>Менеджер отменит заявку в магазине и свяжется с вами. Если заказ уже собран, он сообщит об этом.</p>
    <label className="field"><span className="field-label">Причина, если хотите</span><textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={2} /></label>
    {send.isError && <p className="form-error" role="alert">{send.error.message}</p>}
    <div className="actions"><button type="submit" className="button button-primary" disabled={send.isPending}>{send.isPending ? 'Отправляем…' : 'Отправить запрос'}</button><button type="button" className="button" disabled={send.isPending} onClick={() => { setAsking(false); send.reset(); }}>Не отменять</button></div>
  </form>;
}
