import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { fetchCheckout, fetchReceipt, forgetPendingKey, readPendingKey, rememberPendingKey, submitOrder } from '../../shared/api/checkout';
import type { CheckoutFields, OrderReceipt, OrderSubmission } from '../../shared/api/checkout';
import { ApiError } from '../../shared/api/http';
import { Button } from '../../shared/ui/Button';
import { Notice } from '../../shared/ui/Feedback';
import { useCartBusy } from '../cart/cartQueries';
import { useMe } from '../auth/session';
import { pickupText, warehouseMapUrl } from '../../shared/contacts';

export function Checkout({ onLockChange }: { onLockChange?: (locked: boolean) => void }) {
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
  const prefill = useRef<{ customerName: string; phone: string } | null>(null);
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
  useEffect(() => { onLockChange?.(uncertain); }, [uncertain, onLockChange]);
  const me = useMe();
  // Prefill once from the partner profile; never overwrite what the partner typed.
  useEffect(() => {
    if (prefill.current || !preview.data) return;
    const contact = { customerName: preview.data.contact?.customerName ?? me.data?.contactName ?? '', phone: preview.data.contact?.phone ?? me.data?.phone ?? '' };
    prefill.current = contact;
    setFields((old) => ({ ...old, customerName: old.customerName || contact.customerName, phone: old.phone || contact.phone }));
  }, [preview.data, me.data]);
  const data = preview.data;
  const locked = uncertain || busy || preview.isFetching || preview.isError;
  useEffect(() => {
    const field = errorToFocus.current;
    if (!field || locked) return;
    const frame = requestAnimationFrame(() => { (document.getElementById(field) ?? document.getElementById('checkout-error'))?.focus(); errorToFocus.current = null; });
    return () => cancelAnimationFrame(frame);
  }, [errors, locked]);
  useEffect(() => {
    const initial = prefill.current ?? { customerName: '', phone: '' };
    if (uncertain || (fields.customerName === initial.customerName && fields.phone === initial.phone && !fields.comment && !fields.consent)) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [fields, uncertain]);
  function fieldChanged<K extends keyof CheckoutFields>(key: K, value: CheckoutFields[K]) {
    setFields((old) => ({ ...old, [key]: value })); setErrors((old) => ({ ...old, [key]: '' }));
  }
  const demo = import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true';
  const prefilled = Boolean(prefill.current && (prefill.current.customerName || prefill.current.phone));
  return <aside className="checkout-panel" aria-labelledby="checkout-title">
    <h2 id="checkout-title" tabIndex={-1}>Оформление заявки</h2>
    <div className="pickup-box"><span className="pickup-dot" aria-hidden="true" /><div><p><strong>{pickupText}</strong></p><p className="muted">После подтверждения менеджером · <a href={warehouseMapUrl} target="_blank" rel="noopener noreferrer">Склад на карте</a></p></div></div>
    {message && <div id="checkout-error" tabIndex={-1}><Notice error>{message}</Notice></div>}
    {uncertain ? <section className="recovery-panel" aria-labelledby="recovery-title" aria-busy={recovering || busy}><h3 id="recovery-title" tabIndex={-1}>Проверка результата</h3><p>Не создавайте новую заявку, пока не проверен результат предыдущей отправки.</p>{recovering || busy ? <p role="status">{mutation.isPending ? 'Сохраняем заявку…' : 'Проверяем сохранение заявки…'}</p> : <><Button onClick={() => void checkResult()}>Проверить результат</Button>{submitted.current && <Button onClick={() => send(submitted.current!)}>Повторить ту же отправку</Button>}{!submitted.current && <><p>Данные формы не сохраняются в браузере. После перезагрузки повторить прежнюю отправку невозможно. Проверьте результат или начните новое оформление с подтверждением.</p><Button onClick={() => setRestartConfirmation(true)}>Начать новое оформление</Button></>}</>}</section> : <>
      {preview.isPending ? <div className="loading-state loading-compact" role="status"><span className="spinner" aria-hidden="true" />Проверяем корзину…</div> : preview.isError ? <Notice error><p>Не удалось проверить корзину. Отправка временно недоступна.</p><Button onClick={() => void preview.refetch()}>Обновить данные</Button></Notice> : data && <form className="checkout-form" aria-busy={busy} onSubmit={(event) => { event.preventDefault(); if (!data.canSubmit || locked || !data.revision || submitted.current) return; send({ idempotencyKey: data.idempotencyKey, revision: data.revision, customerName: fields.customerName, phone: fields.phone, comment: fields.comment, consent: String(fields.consent) }); }}>
        {demo && <p className="field-hint">Прототип: контакты не сохраняются и никуда не отправляются.</p>}
        {prefilled && me.data && <p className="prefill-note">Данные заполнены из кабинета {me.data.company}</p>}
        <div className="field"><label htmlFor="customerName">Имя</label><input id="customerName" name="customerName" autoComplete="name" required maxLength={80} placeholder="Как к вам обращаться" value={fields.customerName} disabled={locked} aria-invalid={Boolean(errors.customerName)} aria-describedby={errors.customerName ? 'customerName-error' : undefined} onChange={(event) => fieldChanged('customerName', event.target.value)} />{errors.customerName && <p id="customerName-error" className="field-error">{errors.customerName}</p>}</div>
        <div className="field"><label htmlFor="phone">Телефон</label><input id="phone" name="phone" type="tel" autoComplete="tel" required maxLength={32} placeholder="+7 7__ ___ __ __" value={fields.phone} disabled={locked} aria-invalid={Boolean(errors.phone)} aria-describedby={errors.phone ? 'phone-error' : 'phone-hint'} onChange={(event) => fieldChanged('phone', event.target.value)} /><p id="phone-hint" className="field-hint">Казахстанский номер: +7 и 10 цифр.</p>{errors.phone && <p id="phone-error" className="field-error">{errors.phone}</p>}</div>
        <div className="field"><label htmlFor="comment">Комментарий <span className="muted">(необязательно)</span></label><textarea id="comment" name="comment" rows={2} maxLength={1000} placeholder="Например, удобное время звонка" value={fields.comment} disabled={locked} aria-invalid={Boolean(errors.comment)} aria-describedby={errors.comment ? 'comment-error' : undefined} onChange={(event) => fieldChanged('comment', event.target.value)} />{errors.comment && <p id="comment-error" className="field-error">{errors.comment}</p>}</div>
        <div><label className="checkout-consent" htmlFor="consent"><input id="consent" name="consent" type="checkbox" required checked={fields.consent} disabled={locked} aria-invalid={Boolean(errors.consent)} aria-describedby={errors.consent ? 'consent-error' : undefined} onChange={(event) => fieldChanged('consent', event.target.checked)} /><span>Согласен на обработку указанных контактных данных для работы с этой заявкой.</span></label>{errors.consent && <p id="consent-error" className="field-error">{errors.consent}</p>}</div>
        {!data.canSubmit && <Notice error><p>Сохранение заявки пока недоступно.</p><ul>{data.issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul><p>Проверьте позиции в корзине.</p></Notice>}
        <dl className="checkout-sums"><div className="checkout-grand"><dt>Итого за {data.cart.items.reduce((sum, item) => sum + item.quantity, 0)} шт.</dt><dd>{data.cart.totalText}</dd></div></dl>
        <Button primary className="button-lg" type="submit" disabled={locked || !data.canSubmit}>Отправить заявку</Button>
        <p className="field-hint">Цены и наличие проверяются при сохранении. Оплата на сайте не предусмотрена; товар не резервируется до подтверждения.</p>
      </form>}
    </>}
    <dialog ref={restartDialog} className="confirmation-dialog" aria-labelledby="restart-title" aria-describedby="restart-description" onCancel={(event) => { event.preventDefault(); setRestartConfirmation(false); }}><h2 id="restart-title">Начать новое оформление?</h2><p id="restart-description">Результат предыдущей отправки не удалось определить. Заявка могла сохраниться. Новое оформление может создать отдельную заявку.</p><div className="dialog-actions"><button ref={restartCancel} className="button" onClick={() => setRestartConfirmation(false)}>Отмена</button><Button primary disabled={busy || recovering || Boolean(submitted.current)} onClick={restartCheckout}>Начать заново</Button></div></dialog>
  </aside>;
}
