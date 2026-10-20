import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { fetchCheckout, fetchReceipt, forgetPendingKey, readPendingKey, rememberPendingKey, submitOrder } from '../../shared/api/checkout';
import type { CheckoutFields, OrderReceipt, OrderSubmission } from '../../shared/api/checkout';
import { ApiError } from '../../shared/api/http';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/Button';
import { Notice } from '../../shared/ui/Feedback';
import { useCartBusy } from '../cart/cartQueries';

export function Checkout() {
  const navigate = useNavigate();
  const client = useQueryClient();
  const busy = useCartBusy();
  const [pendingKey, setPendingKey] = useState(readPendingKey);
  const [recovering, setRecovering] = useState(Boolean(pendingKey));
  const [uncertain, setUncertain] = useState(Boolean(pendingKey));
  const [message, setMessage] = useState('');
  const [restartConfirmation, setRestartConfirmation] = useState(false);
  const restartDialog = useRef<HTMLDialogElement>(null);
  const restartCancel = useRef<HTMLButtonElement>(null);
  const restartOrigin = useRef<HTMLElement | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [fields, setFields] = useState<CheckoutFields>({ customerName: '', phone: '', comment: '', consent: false });
  const submitted = useRef<OrderSubmission | null>(null);
  const sending = useRef(false);
  const errorToFocus = useRef<string | null>(null);
  const preview = useQuery({ queryKey: ['checkout'], queryFn: ({ signal }) => fetchCheckout(signal), enabled: !busy && !uncertain, refetchOnWindowFocus: !uncertain, refetchInterval: uncertain || busy ? false : 60000 });

  function showReceipt(receipt: OrderReceipt, key: string) {
    client.setQueryData(['receipt', key], receipt);
    if (readPendingKey() === key) forgetPendingKey();
    navigate(`/checkout/success?key=${encodeURIComponent(key)}`, { replace: true });
  }
  useEffect(() => {
    if (!pendingKey) return;
    const controller = new AbortController();
    fetchReceipt(pendingKey, controller.signal).then((receipt) => showReceipt(receipt, pendingKey)).catch((error: unknown) => {
      if (controller.signal.aborted) return;
      setMessage(error instanceof ApiError && error.status === 404 ? 'Сохранённая заявка пока не найдена. Проверьте результат ещё раз перед повторной отправкой.' : 'Не удалось проверить результат. Восстановите соединение и повторите проверку.');
    }).finally(() => { if (!controller.signal.aborted) setRecovering(false); });
    return () => controller.abort();
    // Only restore on entry. Subsequent checks are explicit user actions.
  }, []);

  async function checkResult() {
    if (!pendingKey || recovering || busy) return;
    setRecovering(true);
    try { showReceipt(await fetchReceipt(pendingKey), pendingKey); }
    catch (error) { setMessage(error instanceof ApiError && error.status === 404 ? 'Сохранённая заявка пока не найдена. Повторная проверка безопасна.' : 'Не удалось проверить результат. Попробуйте проверить ещё раз после восстановления соединения.'); }
    finally { setRecovering(false); }
  }
  useEffect(() => {
    if (restartConfirmation) { restartOrigin.current = document.activeElement as HTMLElement; restartDialog.current?.showModal(); restartCancel.current?.focus(); }
    else if (restartDialog.current?.open) { restartDialog.current.close(); if (restartOrigin.current?.isConnected) restartOrigin.current.focus(); else document.getElementById('checkout-title')?.focus(); }
  }, [restartConfirmation]);
  function restartCheckout() {
    if (busy || recovering || submitted.current) return;
    forgetPendingKey(); setPendingKey(null); submitted.current = null; sending.current = false;
    setMessage(''); setErrors({}); setUncertain(false); setRestartConfirmation(false);
    void client.invalidateQueries({ queryKey: ['checkout'] });
    void client.invalidateQueries({ queryKey: ['cart'] });
  }
  const mutation = useMutation({
    mutationKey: ['cart', 'checkout'], scope: { id: 'cart' }, retry: false,
    mutationFn: submitOrder,
    onMutate: async () => { await client.cancelQueries({ queryKey: ['cart'] }); await client.cancelQueries({ queryKey: ['checkout'] }); },
    onSuccess: async (receipt, payload) => {
      await client.cancelQueries({ queryKey: ['cart'] });
      await client.invalidateQueries({ queryKey: ['cart'], refetchType: 'none' });
      client.removeQueries({ queryKey: ['checkout'] });
      showReceipt(receipt, payload.idempotencyKey);
    },
    onSettled: () => { sending.current = false; },
    onError: async (error, payload) => {
      if (error instanceof ApiError && (error.status === 400 || error.status === 403)) {
        forgetPendingKey(); setPendingKey(null); submitted.current = null; setUncertain(false);
        errorToFocus.current = Object.keys(error.errors)[0] || 'checkout-error';
        setErrors(error.errors); setMessage(error.message);
        return;
      }
      // A lost response may follow a committed order. Never automatically repeat this POST.
      try {
        const receipt = await fetchReceipt(payload.idempotencyKey);
        await client.invalidateQueries({ queryKey: ['cart'], refetchType: 'none' });
        showReceipt(receipt, payload.idempotencyKey);
      } catch (recoveryError) {
        if (error instanceof ApiError && error.status === 409 && recoveryError instanceof ApiError && recoveryError.status === 404) {
          forgetPendingKey(); setPendingKey(null); submitted.current = null; setUncertain(false); setMessage(error.message);
          await client.invalidateQueries({ queryKey: ['checkout'] });
          await client.invalidateQueries({ queryKey: ['cart'], refetchType: 'none' });
        } else {
          setUncertain(true); setMessage('Ответ на сохранение не получен. Проверьте результат. Если заявка не найдена, можно вручную повторить ту же отправку.');
        }
      }
    },
  });
  function send(payload: OrderSubmission) {
    if (sending.current || busy || mutation.isPending || recovering) return;
    sending.current = true;
    submitted.current = payload; rememberPendingKey(payload.idempotencyKey); setPendingKey(payload.idempotencyKey);
    setUncertain(true); setMessage(''); setErrors({}); mutation.mutate(payload);
  }
  useEffect(() => { if (uncertain) document.getElementById('recovery-title')?.focus(); }, [uncertain]);
  const data = preview.data;
  const locked = uncertain || busy || preview.isFetching || preview.isError;
  useEffect(() => {
    const field = errorToFocus.current;
    if (!field || locked) return;
    const frame = requestAnimationFrame(() => { (document.getElementById(field) ?? document.getElementById('checkout-error'))?.focus(); errorToFocus.current = null; });
    return () => cancelAnimationFrame(frame);
  }, [errors, locked]);
  useEffect(() => {
    if (uncertain || (!fields.customerName && !fields.phone && !fields.comment && !fields.consent)) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [fields, uncertain]);
  function fieldChanged<K extends keyof CheckoutFields>(key: K, value: CheckoutFields[K]) {
    setFields((old) => ({ ...old, [key]: value })); setErrors((old) => ({ ...old, [key]: '' }));
  }
  return <>
    <nav className="back-nav" aria-label="Навигация"><Link to="/basket"><Icon name="arrow" />Вернуться в корзину</Link></nav>
    <nav className="purchase-steps" aria-label="Этапы покупки"><ol><li><Link to="/">Каталог</Link></li><li><Link to="/basket">Корзина</Link></li><li aria-current="step">Оформление</li></ol></nav>
    <div className="heading-row"><div><h1 id="checkout-title" tabIndex={-1}>Оформление заявки</h1><p>Проверьте товары и укажите контактные данные.</p></div></div>
    <p className="checkout-intro">Заявка сохраняется на сайте. Передача в магазин и подтверждение заказа пока недоступны. Товары не резервируются.</p>
    {message && <div id="checkout-error" tabIndex={-1}><Notice error>{message}</Notice></div>}
    {uncertain ? <section className="recovery-panel" aria-labelledby="recovery-title" aria-busy={recovering || busy}><h2 id="recovery-title" tabIndex={-1}>Проверка результата</h2><p>Не создавайте новую заявку, пока не проверен результат предыдущей отправки.</p>{recovering || busy ? <p role="status">{mutation.isPending ? 'Сохраняем заявку…' : 'Проверяем сохранение заявки…'}</p> : <><Button onClick={() => void checkResult()}>Проверить результат</Button>{submitted.current && <Button onClick={() => send(submitted.current!)}>Повторить ту же отправку</Button>}{!submitted.current && <><p>Данные формы не сохраняются в браузере. После перезагрузки повторить прежнюю отправку невозможно. Проверьте результат или начните новое оформление с подтверждением.</p><Button onClick={() => setRestartConfirmation(true)}>Начать новое оформление</Button></>}</>}</section> : <>
      {preview.isPending ? <div className="loading-state" role="status">Проверяем корзину…</div> : preview.isError ? <Notice error><p>Не удалось проверить корзину. Отправка временно недоступна.</p><Button onClick={() => void preview.refetch()}>Обновить данные</Button></Notice> : data && <div className="checkout-layout">
        {(import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true') && <Notice>Проверка прототипа: используйте вымышленное имя и номер +77000000000. Контакты не сохраняются и никуда не отправляются.</Notice>}<form className="checkout-form" aria-busy={busy} onSubmit={(event) => { event.preventDefault(); if (!data.canSubmit || locked || !data.revision || submitted.current) return; send({ idempotencyKey: data.idempotencyKey, revision: data.revision, customerName: fields.customerName, phone: fields.phone, comment: fields.comment, consent: String(fields.consent) }); }}>
          <div className="checkout-section-heading"><h2>Ваши контакты</h2><p className="muted">Укажите данные для работы с заявкой.</p></div>
          <div className="checkout-field-grid">
          <div className="checkout-field"><label htmlFor="customerName">Имя</label><input id="customerName" name="customerName" autoComplete="name" required maxLength={80} value={fields.customerName} disabled={locked} aria-invalid={Boolean(errors.customerName)} aria-describedby={errors.customerName ? 'customerName-error' : undefined} onChange={(event) => fieldChanged('customerName', event.target.value)} />{errors.customerName && <p id="customerName-error" className="field-error">{errors.customerName}</p>}</div>
          <div className="checkout-field"><label htmlFor="phone">Телефон</label><input id="phone" name="phone" type="tel" autoComplete="tel" required maxLength={32} value={fields.phone} disabled={locked} aria-invalid={Boolean(errors.phone)} aria-describedby={errors.phone ? 'phone-error' : 'phone-hint'} onChange={(event) => fieldChanged('phone', event.target.value)} /><p id="phone-hint" className="field-hint">Казахстанский номер: +7 и 10 цифр.</p>{errors.phone && <p id="phone-error" className="field-error">{errors.phone}</p>}</div>
          </div>
          <div className="checkout-field"><label htmlFor="comment">Комментарий <span className="muted">(необязательно)</span></label><textarea id="comment" name="comment" rows={4} maxLength={1000} value={fields.comment} disabled={locked} aria-invalid={Boolean(errors.comment)} aria-describedby={errors.comment ? 'comment-error' : undefined} onChange={(event) => fieldChanged('comment', event.target.value)} />{errors.comment && <p id="comment-error" className="field-error">{errors.comment}</p>}</div>
          <div><label className="checkout-consent" htmlFor="consent"><input id="consent" name="consent" type="checkbox" required checked={fields.consent} disabled={locked} aria-invalid={Boolean(errors.consent)} aria-describedby={errors.consent ? 'consent-error' : undefined} onChange={(event) => fieldChanged('consent', event.target.checked)} /><span>Согласен на обработку указанных контактных данных для работы с этой заявкой.</span></label>{errors.consent && <p id="consent-error" className="field-error">{errors.consent}</p>}</div>
          {!data.canSubmit && <Notice error><p>Сохранение заявки пока недоступно.</p><ul>{data.issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul><p>Вернитесь в корзину, чтобы проверить позиции. Попробуйте позже или уточните возможность самовывоза в магазине.</p></Notice>}
          <Button primary type="submit" disabled={locked || !data.canSubmit}>Сохранить заявку<Icon name="arrow" /></Button>
        </form>
        <aside className="checkout-summary" aria-labelledby="checkout-summary-title"><h2 id="checkout-summary-title">Ваша заявка</h2><p className="checkout-summary-store"><Icon name="location" />{data.cart.storeName || 'Точка не выбрана'}</p>{data.cart.items.length === 0 ? <p>Корзина пуста. Добавьте товары из каталога.</p> : <ul className="checkout-items">{data.cart.items.map((item) => <li key={item.id}><span>{item.name}<small>{item.quantity} {item.unit || 'шт.'} × {item.priceText}</small>{item.problem && <span className="item-problem">{item.problem}</span>}{item.priceChanged && <span className="item-problem">Цена требует подтверждения в корзине.</span>}</span><strong>{item.totalText}</strong></li>)}</ul>}<div className="checkout-total"><span>Итого</span><strong>{data.cart.totalText}</strong></div><p className="summary-note">Цены и наличие проверяются при сохранении. Оплата на сайте не предусмотрена.</p></aside>
      </div>}
    </>}
    <dialog ref={restartDialog} className="confirmation-dialog" aria-labelledby="restart-title" aria-describedby="restart-description" onCancel={(event) => { event.preventDefault(); setRestartConfirmation(false); }}><h2 id="restart-title">Начать новое оформление?</h2><p id="restart-description">Результат предыдущей отправки не удалось определить. Заявка могла сохраниться. Новое оформление может создать отдельную заявку.</p><div className="dialog-actions"><button ref={restartCancel} className="button" onClick={() => setRestartConfirmation(false)}>Отмена</button><Button primary disabled={busy || recovering || Boolean(submitted.current)} onClick={restartCheckout}>Начать заново</Button></div></dialog>
  </>;
}
