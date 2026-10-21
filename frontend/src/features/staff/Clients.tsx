import { useEffect, useRef, useState } from 'react';
import type { FormEvent, ReactNode, SyntheticEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../shared/api/http';
import { changeClientPassword, createClient, createClientCustomer, fetchClients, generatePassword, linkClientCustomer, loginFromCompany, setClientEnabled } from '../../shared/api/staff';
import type { NewClient, StaffClient } from '../../shared/api/staff';
import { Button } from '../../shared/ui/Button';
import { EmptyState, Notice } from '../../shared/ui/Feedback';

const dateFormatter = new Intl.DateTimeFormat('ru-RU', { timeZone: 'Asia/Almaty', dateStyle: 'medium', timeStyle: 'short' });
const clientsKey = ['staff', 'clients'];
const staffOptions = { gcTime: 0, staleTime: 0, retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false } as const;
const message = (error: unknown) => error instanceof ApiError ? error.message : 'Не удалось выполнить действие. Проверьте соединение и повторите.';

/** Credentials shown once: after creation or a new password. Never stored by the interface. */
interface Credentials { company: string; login: string; password: string; note?: string }

export function Clients({ onDenied }: { onDenied: () => void }) {
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { const timer = setTimeout(() => setSearch(query.trim()), 300); return () => clearTimeout(timer); }, [query]);
  const result = useQuery({ queryKey: [...clientsKey, search], queryFn: ({ signal }) => fetchClients(search, signal), ...staffOptions });
  const denied = result.error instanceof ApiError && (result.error.status === 401 || result.error.status === 403);
  useEffect(() => { if (denied) onDenied(); }, [denied, onDenied]);
  useEffect(() => { document.title = 'Клиенты — MBM Motors'; }, []);
  const count = result.data?.clients.length ?? 0;
  return <>
    <div className="clients-heading">
      <div><h1>Клиенты</h1><p className="muted">{result.data ? `Учётных записей: ${count}${count === 500 ? ' (показаны первые 500)' : ''}` : 'Учётные записи оптовых клиентов'}</p></div>
      <label className="clients-search"><span className="sr-only">Поиск по компании или логину</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} maxLength={120} placeholder="Поиск по компании или логину" autoComplete="off" /></label>
    </div>
    {error && <Notice error>{error}</Notice>}
    <div className="clients-layout">
      <section className="clients-list" aria-label="Список клиентов" aria-busy={result.isFetching}>
        {result.isPending ? <div className="loading-state" role="status">Загружаем клиентов…</div>
          : result.isError ? <Notice error><p>{message(result.error)}</p><Button onClick={() => void result.refetch()}>Повторить</Button></Notice>
          : result.data.clients.length === 0 ? <EmptyState title={search ? 'Ничего не найдено' : 'Клиентов пока нет'}><p>{search ? 'Проверьте название компании или логин.' : 'Создайте первую учётную запись в форме справа.'}</p></EmptyState>
          : <ul>{result.data.clients.map((client) => <ClientRow key={client.login} client={client} customerCreation={result.data.customerCreation} onCredentials={setCredentials} onError={setError} />)}</ul>}
      </section>
      <NewClientForm customerCreation={result.data?.customerCreation ?? false} onCreated={setCredentials} />
    </div>
    {credentials && <CredentialsDialog credentials={credentials} onClose={() => setCredentials(null)} />}
  </>;
}

function statusOf(client: StaffClient) {
  if (!client.enabled) return { text: 'Заблокирован', tone: 'neutral' };
  return client.lastLoginAt ? { text: 'Активен', tone: 'ok' } : { text: 'Не входил', tone: 'warn' };
}

function ClientRow({ client, customerCreation, onCredentials, onError }: { client: StaffClient; customerCreation: boolean; onCredentials: (value: Credentials) => void; onError: (value: string) => void }) {
  const queryClient = useQueryClient();
  const [confirm, setConfirm] = useState<'password' | 'block' | null>(null);
  const status = statusOf(client);
  const refresh = () => queryClient.invalidateQueries({ queryKey: clientsKey });
  const password = useMutation({
    mutationFn: async () => { const value = generatePassword(); await changeClientPassword(client.login, value); return value; },
    onSuccess: (value) => { onError(''); onCredentials({ company: client.company, login: client.login, password: value, note: 'Старый пароль больше не действует; открытые сессии клиента завершены.' }); },
    onError: (error) => onError(message(error)),
  });
  const block = useMutation({
    mutationFn: () => setClientEnabled(client.login, !client.enabled),
    onSuccess: () => { onError(''); void refresh(); },
    onError: (error) => onError(message(error)),
  });
  const busy = password.isPending || block.isPending;
  return <li className={`client-row ${client.enabled ? '' : 'is-blocked'}`}>
    <div className="client-main">
      <p className="client-title"><strong>{client.company}</strong><span className={`client-tag client-tag-${status.tone}`}>{status.text}</span></p>
      <p className="muted">{client.contactName} · {client.phone}{client.bin && <> · БИН {client.bin}</>}</p>
      <p className="client-meta"><span className="code">{client.login}</span><span>{client.lastLoginAt ? `Вход: ${dateFormatter.format(new Date(client.lastLoginAt))}` : 'Ещё не входил'}</span></p>
      <CustomerCard client={client} customerCreation={customerCreation} onError={onError} />
    </div>
    <div className="client-actions">
      <Button disabled={busy} onClick={() => setConfirm('password')}>Новый пароль</Button>
      <Button disabled={busy} onClick={() => client.enabled ? setConfirm('block') : block.mutate()}>{client.enabled ? 'Заблокировать' : 'Разблокировать'}</Button>
    </div>
    {confirm && <ConfirmDialog
      title={confirm === 'password' ? `Новый пароль для «${client.company}»?` : `Заблокировать «${client.company}»?`}
      text={confirm === 'password' ? 'Старый пароль перестанет работать, клиент выйдет со всех устройств. Новый пароль покажем один раз.' : 'Клиент сразу потеряет доступ к сайту. Заявки и история сохранятся; разблокировать можно в любой момент.'}
      action={confirm === 'password' ? 'Выдать новый пароль' : 'Заблокировать'}
      onCancel={() => setConfirm(null)}
      onConfirm={() => { setConfirm(null); if (confirm === 'password') password.mutate(); else block.mutate(); }} />}
  </li>;
}

/** The CloudShop customer card that receives the client's orders. */
function CustomerCard({ client, customerCreation, onError }: { client: StaffClient; customerCreation: boolean; onError: (value: string) => void }) {
  const queryClient = useQueryClient();
  const [linking, setLinking] = useState(false);
  const [customerId, setCustomerId] = useState('');
  const [checked, setChecked] = useState(false);
  const done = () => { onError(''); setLinking(false); setCustomerId(''); setChecked(false); void queryClient.invalidateQueries({ queryKey: clientsKey }); };
  const link = useMutation({ mutationFn: () => linkClientCustomer(client.login, customerId.trim()), onSuccess: done, onError: (error) => onError(message(error)) });
  const create = useMutation({ mutationFn: () => createClientCustomer(client.login, checked), onSuccess: done, onError: (error) => onError(message(error)) });
  const { state } = client.customer;
  if (state === 'LINKED') return <p className="client-card client-card-ok">Карточка CloudShop привязана — заявки передаются.</p>;
  const unclear = state === 'UNKNOWN' || state === 'PENDING';
  return <div className={`client-card ${unclear ? 'client-card-warn' : ''}`}>
    <p>{unclear ? 'Ответ CloudShop на создание карточки неясен. Проверьте покупателей в CloudShop: если карточка есть — укажите её ID.' : 'Карточки CloudShop нет — заявки клиента сохраняются, но не передаются.'}</p>
    {linking ? <form className="client-card-form" onSubmit={(event) => { event.preventDefault(); link.mutate(); }}>
      <label><span className="sr-only">ID карточки покупателя CloudShop</span><input value={customerId} onChange={(event) => setCustomerId(event.target.value)} placeholder="ID карточки, 24 символа" maxLength={24} spellCheck={false} autoComplete="off" required /></label>
      <Button type="submit" disabled={link.isPending}>Привязать</Button>
      <Button type="button" onClick={() => setLinking(false)}>Отмена</Button>
    </form> : <div className="client-card-actions">
      <button type="button" className="link-button" onClick={() => setLinking(true)}>Указать ID карточки</button>
      {customerCreation && state !== 'PENDING' && (unclear
        ? <span className="client-card-retry"><label className="client-card-check"><input type="checkbox" checked={checked} onChange={(event) => setChecked(event.target.checked)} />В CloudShop карточки нет</label><button type="button" className="link-button" disabled={!checked || create.isPending} onClick={() => create.mutate()}>Создать заново</button></span>
        : <button type="button" className="link-button" disabled={create.isPending} onClick={() => create.mutate()}>{create.isPending ? 'Создаём…' : 'Создать карточку в CloudShop'}</button>)}
    </div>}
  </div>;
}

const emptyForm = (): NewClient => ({ company: '', bin: '', contactName: '', phone: '', login: '', password: generatePassword() });

function NewClientForm({ customerCreation, onCreated }: { customerCreation: boolean; onCreated: (value: Credentials) => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<NewClient>(emptyForm);
  const [loginEdited, setLoginEdited] = useState(false);
  const [error, setError] = useState('');
  const [loginTaken, setLoginTaken] = useState(false);
  const create = useMutation({
    mutationFn: () => createClient(form),
    onSuccess: (client) => {
      const card = client.customer.state === 'LINKED' ? 'Карточка покупателя в CloudShop создана — заявки будут передаваться.'
        : client.customer.state === 'NONE' ? 'Карточка CloudShop не создавалась: заявки клиента сохраняются на сайте.'
        : 'Ответ CloudShop на создание карточки неясен — проверьте покупателей в CloudShop.';
      onCreated({ company: client.company, login: client.login, password: form.password, note: card });
      setForm(emptyForm()); setLoginEdited(false); setError(''); setLoginTaken(false);
      void queryClient.invalidateQueries({ queryKey: clientsKey });
    },
    onError: (failure) => { setLoginTaken(failure instanceof ApiError && failure.status === 409); setError(message(failure)); },
  });
  function update(field: keyof NewClient, value: string) {
    setForm((current) => ({ ...current, [field]: value, ...(field === 'company' && !loginEdited ? { login: loginFromCompany(value) } : {}) }));
    if (field === 'login') { setLoginEdited(true); setLoginTaken(false); }
  }
  const ready = form.company.trim() && form.contactName.trim() && form.phone.trim() && /^[a-z0-9._-]{3,64}$/.test(form.login);
  function submit(event: FormEvent) { event.preventDefault(); if (ready && !create.isPending) create.mutate(); }
  return <form className="client-form" onSubmit={submit} aria-labelledby="new-client-title">
    <h2 id="new-client-title">Новый клиент</h2>
    <Field label="Компания"><input value={form.company} onChange={(event) => update('company', event.target.value)} placeholder="ТОО «…»" maxLength={120} required /></Field>
    <div className="client-form-pair">
      <Field label="БИН"><input className="code" value={form.bin} onChange={(event) => update('bin', event.target.value)} placeholder="12 цифр" inputMode="numeric" maxLength={14} /></Field>
      <Field label="Телефон"><input type="tel" value={form.phone} onChange={(event) => update('phone', event.target.value)} placeholder="+7 7__ ___ __ __" maxLength={32} required /></Field>
    </div>
    <Field label="Контактное лицо"><input value={form.contactName} onChange={(event) => update('contactName', event.target.value)} maxLength={80} required /></Field>
    <div className="client-form-access">
      <Field label="Логин"><input className="code" value={form.login} onChange={(event) => update('login', event.target.value.toLowerCase())} placeholder="login" maxLength={64} spellCheck={false} autoComplete="off" aria-invalid={loginTaken} required /></Field>
      {loginTaken && <p className="client-form-error">Такой логин уже занят</p>}
      <div className="field"><span className="field-label">Пароль</span><div className="client-password"><output className="code" aria-label="Сгенерированный пароль">{form.password}</output><Button type="button" onClick={() => setForm((current) => ({ ...current, password: generatePassword() }))}>Другой</Button></div></div>
    </div>
    {error && !loginTaken && <Notice error>{error}</Notice>}
    <Button primary type="submit" className="button-lg" disabled={!ready || create.isPending}>{create.isPending ? 'Создаём…' : 'Создать учётную запись'}</Button>
    <p className="muted client-form-note">Логин и пароль покажем один раз после создания — передайте их клиенту.{customerCreation ? ' Карточка покупателя в CloudShop создаётся сразу.' : ' Создание карточек CloudShop выключено: заявки клиента будут сохраняться на сайте, пока карточку не привяжут.'}</p>
  </form>;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="field"><span className="field-label">{label}</span>{children}</label>;
}

function useModal(onClose: () => void) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const origin = document.activeElement as HTMLElement | null;
    dialog.current?.showModal();
    return () => origin?.focus();
  }, []);
  return { ref: dialog, onCancel: (event: SyntheticEvent) => { event.preventDefault(); onClose(); } };
}

function ConfirmDialog({ title, text, action, onConfirm, onCancel }: { title: string; text: string; action: string; onConfirm: () => void; onCancel: () => void }) {
  const modal = useModal(onCancel);
  return <dialog ref={modal.ref} className="confirmation-dialog" aria-labelledby="client-confirm-title" onCancel={modal.onCancel}>
    <h2 id="client-confirm-title">{title}</h2><p>{text}</p>
    <div className="dialog-actions"><Button autoFocus onClick={onCancel}>Отмена</Button><Button primary onClick={onConfirm}>{action}</Button></div>
  </dialog>;
}

function CredentialsDialog({ credentials, onClose }: { credentials: Credentials; onClose: () => void }) {
  const modal = useModal(onClose);
  const [copied, setCopied] = useState(false);
  const site = window.location.origin;
  const text = `Сайт: ${site}\nЛогин: ${credentials.login}\nПароль: ${credentials.password}`;
  async function copy() {
    try { await navigator.clipboard.writeText(text); setCopied(true); } catch { setCopied(false); }
  }
  return <dialog ref={modal.ref} className="confirmation-dialog credentials-dialog" aria-labelledby="credentials-title" onCancel={modal.onCancel}>
    <h2 id="credentials-title">Доступ: {credentials.company}</h2>
    <p>Передайте клиенту эти данные. Пароль больше не будет показан — при утере выдайте новый.</p>
    <dl className="credentials-list"><dt>Сайт</dt><dd className="code">{site}</dd><dt>Логин</dt><dd className="code">{credentials.login}</dd><dt>Пароль</dt><dd className="code">{credentials.password}</dd></dl>
    {credentials.note && <p className="credentials-note">{credentials.note}</p>}
    <div className="dialog-actions"><Button onClick={() => void copy()}>{copied ? 'Скопировано' : 'Скопировать'}</Button><Button primary autoFocus onClick={onClose}>Готово</Button></div>
  </dialog>;
}
