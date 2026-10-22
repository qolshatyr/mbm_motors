import { useEffect, useId, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import { availability, fetchProducts, normalizeCode } from '../../shared/api/catalog';
import type { Product } from '../../shared/api/catalog';
import { Notice } from '../../shared/ui/Feedback';
import { useToast } from '../../shared/ui/Toast';
import { useBulkAdd, useCartBusy, useCartQuantities } from '../cart/cartQueries';
import type { BulkResult } from '../cart/cartQueries';
import { useDefaultStore } from '../catalog/useStore';
import { productHref } from '../catalog/AddControl';

export const maxLines = 50;
export interface ParsedLine { line: number; input: string; key: string; query: string; quantity: number }

/** «артикул [кол-во]» per line. Separators: space, tab, «;» or «,». A trailing 1–3 digit token is the quantity. */
export function parseList(text: string): { lines: ParsedLine[]; extra: number } {
  const all = text.split(/\r?\n/).map((raw, index) => ({ raw: raw.trim(), line: index + 1 })).filter((item) => item.raw);
  const lines = all.slice(0, maxLines).map(({ raw, line }) => {
    const tokens = raw.split(/[\s;,]+/).filter(Boolean);
    let quantity = 1;
    if (tokens.length > 1 && /^\d{1,3}$/.test(tokens[tokens.length - 1])) quantity = Number(tokens.pop());
    const query = tokens.join('');
    return { line, input: tokens.join(' '), key: normalizeCode(query), query: query.slice(0, 120), quantity };
  });
  return { lines, extra: Math.max(0, all.length - maxLines) };
}

const money = (value: number) => `${value.toLocaleString('ru-RU', { maximumFractionDigits: 2 })} ₸`;

type Status = { tone: 'ok' | 'warn' | 'muted' | 'pending'; label: string; product?: Product; addQuantity?: number; sum?: number | null };
function status(line: ParsedLine, lookup: { isPending: boolean; isError: boolean; data?: { products: Product[] } }, inCart: (id: string) => number): Status {
  if (!line.key) return { tone: 'muted', label: 'Не найдено' };
  if (line.quantity < 1) return { tone: 'warn', label: 'Проверьте количество' };
  if (lookup.isPending) return { tone: 'pending', label: 'Ищем…' };
  if (lookup.isError) return { tone: 'warn', label: 'Ошибка поиска' };
  const product = lookup.data?.products.find((item) => normalizeCode(item.sku) === line.key || normalizeCode(item.code) === line.key);
  if (!product) return { tone: 'muted', label: 'Не найдено' };
  const state = availability(product);
  if (state === 'price') return { tone: 'warn', label: 'Цена уточняется', product };
  if (state === 'stock') return { tone: 'warn', label: 'Нет на складе', product };
  if (state === 'unknown') return { tone: 'warn', label: 'Наличие уточняется', product };
  const stock = Math.min(999, Math.floor(product.availableQuantity ?? 0));
  const already = inCart(product.id);
  const left = stock - already;
  if (left <= 0) return { tone: 'warn', label: `Уже в корзине: ${already}`, product };
  const take = Math.min(line.quantity, left);
  return { tone: 'ok', label: take < line.quantity ? `Есть ${take} из ${line.quantity}` : 'Найдено', product, addQuantity: take, sum: product.price! * take };
}

export function QuickOrder() {
  const navigate = useNavigate();
  const toast = useToast();
  const store = useDefaultStore();
  const busy = useCartBusy();
  const bulkAdd = useBulkAdd();
  const hintId = useId();
  const [text, setText] = useState('');
  const [checked, setChecked] = useState('');
  const [adding, setAdding] = useState(false);
  const [result, setResult] = useState<BulkResult | null>(null);
  useEffect(() => { const timer = window.setTimeout(() => setChecked(text), 600); return () => window.clearTimeout(timer); }, [text]);
  const parsed = useMemo(() => parseList(checked), [checked]);
  const lookups = useQueries({ queries: parsed.lines.map((line) => {
    const params = new URLSearchParams({ q: line.query, available: 'false', page: '0' });
    if (store.id) params.set('store', store.id);
    return { queryKey: ['products', params.toString(), 'line', line.line], queryFn: ({ signal }: { signal: AbortSignal }) => fetchProducts(params, signal), enabled: Boolean(store.id && line.key && line.quantity >= 1), staleTime: 60000 };
  }) });
  const quantities = useCartQuantities();
  const rows = parsed.lines.map((line, index) => ({ line, status: status(line, lookups[index], (id) => quantities.get(id) ?? 0) }));
  const ready = rows.filter((row) => row.status.tone === 'ok' && row.status.product && (row.status.addQuantity ?? 0) > 0);
  const total = ready.reduce((sum, row) => sum + (row.status.sum ?? 0), 0);
  const pending = rows.some((row) => row.status.tone === 'pending') || text !== checked;
  async function addAll() {
    if (!ready.length || adding || busy || pending) return;
    setAdding(true); setResult(null);
    const merged = new Map<string, { productId: string; name: string; quantity: number }>();
    ready.forEach(({ status: item }) => {
      const current = merged.get(item.product!.id);
      const cap = Math.min(999, Math.floor(item.product!.availableQuantity ?? 0)) - (quantities.get(item.product!.id) ?? 0);
      merged.set(item.product!.id, { productId: item.product!.id, name: item.product!.name, quantity: Math.min(cap, (current?.quantity ?? 0) + item.addQuantity!) });
    });
    const outcome = await bulkAdd([...merged.values()], store.id);
    setAdding(false);
    if (outcome.failed.length === 0) {
      toast({ text: `Добавлено в корзину позиций: ${outcome.added.length}` });
      navigate('/basket');
    } else setResult(outcome);
  }
  return <>
    <h1 className="page-title">Быстрый заказ</h1>
    <p className="page-lead">Вставьте список из Excel или мессенджера: артикул или код и количество, по одной позиции в строке. Не больше {maxLines} строк за раз.</p>
    <div className="quick-layout">
      <div className="quick-input">
        <label htmlFor="quick-list" className="field-label">Список артикулов</label>
        <textarea id="quick-list" rows={12} spellCheck={false} autoComplete="off" aria-describedby={hintId} placeholder={'54651-2T000 2\n26300 35505 4\n58101d7a00 1'} value={text} onChange={(event) => { setText(event.target.value); setResult(null); }} />
        <p id={hintId} className="field-hint">Формат строки: <span className="code">54651-2T000 2</span>. Разделители — пробел, табуляция, точка с запятой или запятая. Без количества добавим 1 шт.</p>
        {parsed.extra > 0 && <Notice error>В списке больше {maxLines} строк. Проверены первые {maxLines}; остальные {parsed.extra} отправьте следующим списком.</Notice>}
      </div>
      <section className="quick-results" aria-labelledby="quick-results-title" aria-busy={pending}>
        <h2 id="quick-results-title" className="sr-only">Результат проверки</h2>
        {rows.length === 0 ? <p className="quick-empty">Здесь появится результат: найдём каждую позицию на складе и покажем цену и наличие.</p>
          : <ul className="quick-rows">{rows.map(({ line, status: item }) => <li key={line.line} className="quick-row">
            <div className="quick-row-main">
              <p><span className="code">{item.product?.sku || line.input || `Строка ${line.line}`}</span><span className={`tag tag-${item.tone}`}>{item.label}</span></p>
              <p className="quick-row-name">{item.product ? <Link to={productHref(item.product.id)}>{item.product.name}</Link> : item.tone === 'pending' ? '' : 'Артикул не найден на складе'}</p>
            </div>
            <div className="quick-row-sum"><p>{item.sum != null ? money(item.sum) : '—'}</p><p className="muted">{item.tone === 'ok' && item.product ? `${item.addQuantity} шт. × ${item.product.priceText}` : `${line.quantity} шт.`}</p></div>
          </li>)}</ul>}
        <div className="quick-footer">
          <p role="status">Готово к добавлению: <b>{ready.length}</b> из {rows.length}</p>
          <button type="button" className="button button-primary button-lg" disabled={!ready.length || adding || busy || pending} onClick={() => void addAll()}>{adding ? 'Добавляем…' : `Добавить в корзину${total ? ` · ${money(total)}` : ''}`}</button>
        </div>
        {result && <Notice error><p>Добавлено позиций: {result.added.length}. Не удалось добавить:</p><ul>{result.failed.map((item) => <li key={item.productId}>{item.name} — {item.reason}</li>)}</ul><p><Link to="/basket">Открыть корзину</Link></p></Notice>}
      </section>
    </div>
  </>;
}
