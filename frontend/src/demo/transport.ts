import { ApiError } from '../shared/api/http';
import type { CatalogPage, Detail, Product } from '../shared/api/catalog';
import type { CartSnapshot } from '../shared/api/cart';
import type { CheckoutPreview, OrderReceipt } from '../shared/api/checkout';
import { demoCategories, demoOffer, demoProducts, demoStores, money } from './data';

interface Line { id: string; quantity: number }
interface DemoState { storeId: string | null; items: Line[]; version: number; receipts: Record<string, OrderReceipt> }
const storageKey = 'mbm.demo.session.v1';
const fresh = (): DemoState => ({ storeId: null, items: [], version: 0, receipts: {} });
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function restore(): DemoState {
  try {
    const value = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') as DemoState | null;
    if (!value || !Array.isArray(value.items) || !Number.isSafeInteger(value.version) || value.version < 0 || value.items.length > 50 || !value.receipts || typeof value.receipts !== 'object') return fresh();
    if (value.items.length && !demoStores.some((store) => store.id === value.storeId)) return fresh();
    if (new Set(value.items.map((line) => line.id)).size !== value.items.length) return fresh();
    for (const line of value.items) {
      const product = demoProducts.find((item) => item.id === line.id);
      if (!product || !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > demoOffer(product, value.storeId ?? '').stock) return fresh();
    }
    // Copy only public demo metadata. Never restore arbitrary fields or contacts.
    const receipts: Record<string, OrderReceipt> = {};
    for (const [key, receipt] of Object.entries(value.receipts).slice(-50)) {
      if (!uuid.test(key) || !receipt || !/^DEMO-\d+$/.test(receipt.number) || receipt.status !== 'RECEIVED' || !demoStores.some((store) => store.name === receipt.storeName) || !Number.isInteger(receipt.itemCount) || receipt.itemCount < 1 || typeof receipt.totalText !== 'string' || !/^\d[\d\s]* ₸$/.test(receipt.totalText) || typeof receipt.createdAt !== 'string' || !Number.isFinite(Date.parse(receipt.createdAt))) continue;
      receipts[key] = { number: receipt.number, status: 'RECEIVED', storeName: receipt.storeName, itemCount: receipt.itemCount, totalText: receipt.totalText, createdAt: receipt.createdAt, message: receiptMessage };
    }
    return { storeId: value.items.length ? value.storeId : null, items: value.items.map(({ id, quantity }) => ({ id, quantity })), version: value.version, receipts };
  } catch { return fresh(); }
}
const receiptMessage = 'Это демонстрационная заявка. Она не отправляется в магазин или CloudShop. Контактные данные не сохраняются.';
let state = restore();
let previewKey = crypto.randomUUID();
function save() {
  try { sessionStorage.setItem(storageKey, JSON.stringify(state)); } catch { /* Demo remains usable without browser storage. */ }
}
function changed() { state.version += 1; previewKey = crypto.randomUUID(); save(); }
function productById(id: string) {
  const product = demoProducts.find((item) => item.id === id);
  if (!product) throw new ApiError(404, 'Запчасть не найдена. Вернитесь в демонстрационный каталог.');
  return product;
}
function storeById(id: string) {
  const store = demoStores.find((item) => item.id === id);
  if (!store) throw new ApiError(400, 'Выберите одну из демонстрационных точек.');
  return store;
}
function publicProduct(id: string, storeId: string): Product {
  const item = productById(id);
  const offer = demoOffer(item, storeId);
  return { id: item.id, name: item.name, sku: item.sku, code: item.code, unit: item.unit, imageUrl: item.imageUrl, priceText: money(offer.price), stockText: offer.stock > 0 ? `${offer.stock} шт.` : 'Нет в наличии' };
}
function cart(message: string | null = null): CartSnapshot {
  const items = state.items.map((line) => {
    const product = productById(line.id);
    const offer = demoOffer(product, state.storeId ?? '');
    return { id: product.id, name: product.name, sku: product.sku, unit: product.unit, quantity: line.quantity, priceText: money(offer.price), totalText: money(offer.price * line.quantity), problem: null, priceChanged: false, currentPrice: null };
  });
  const total = state.items.reduce((sum, line) => sum + demoOffer(productById(line.id), state.storeId ?? '').price * line.quantity, 0);
  return { cart: { storeId: state.storeId, storeName: demoStores.find((store) => store.id === state.storeId)?.name ?? null, items, totalText: money(total), hasIssues: false }, itemCount: items.reduce((sum, item) => sum + item.quantity, 0), message };
}
function quantity(value: string | null) {
  if (!value || !/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) < 1 || Number(value) > 999) throw new ApiError(400, 'Укажите целое количество от 1 до 999.');
  return Number(value);
}
function body(options: RequestInit) {
  if (options.body instanceof URLSearchParams) return options.body;
  if (typeof options.body === 'string') return new URLSearchParams(options.body);
  throw new ApiError(400, 'Не удалось прочитать форму. Обновите страницу.');
}
function mutateCart(action: string, form: URLSearchParams) {
  if (action === 'clear') {
    state.items = []; state.storeId = null; changed(); return cart('Демонстрационная корзина очищена.');
  }
  const product = productById(form.get('productId') ?? '');
  const line = state.items.find((item) => item.id === product.id);
  if (action === 'remove') {
    state.items = state.items.filter((item) => item.id !== product.id);
    if (!state.items.length) state.storeId = null;
    changed(); return cart('Товар удалён из корзины.');
  }
  if (action === 'confirm-price') {
    if (!line || Number(form.get('expectedPrice')) !== demoOffer(product, state.storeId ?? '').price) throw new ApiError(409, 'Цена не совпадает. Обновите корзину.');
    return cart('Демонстрационная цена актуальна.');
  }
  if (action !== 'add' && action !== 'update') throw new ApiError(404, 'Такое действие недоступно в демонстрации.');
  if (action === 'update' && !line) throw new ApiError(404, 'Товар уже удалён. Обновите корзину.');
  const storeId = action === 'add' ? form.get('storeId') ?? '' : state.storeId ?? '';
  storeById(storeId);
  if (state.storeId && storeId !== state.storeId) throw new ApiError(409, 'В корзине уже есть товары из другой точки. Очистите корзину или выберите ту же точку.');
  const count = quantity(form.get('quantity') ?? (action === 'add' ? '1' : null));
  const next = action === 'add' ? (line?.quantity ?? 0) + count : count;
  const offer = demoOffer(product, storeId);
  if (next > 999 || offer.price <= 0 || offer.stock < next) throw new ApiError(409, `Доступно ${offer.stock} шт. Уменьшите количество или выберите другую точку.`);
  if (!line && state.items.length >= 50) throw new ApiError(409, 'В корзине не может быть больше 50 разных товаров.');
  if (line) line.quantity = next; else state.items.push({ id: product.id, quantity: next });
  state.storeId = storeId; changed();
  return cart(action === 'add' ? 'Товар добавлен в демонстрационную корзину.' : 'Количество обновлено.');
}
function checkout(): CheckoutPreview {
  const snapshot = cart();
  const issues = snapshot.itemCount ? [] : ['Корзина пуста. Добавьте товары из каталога.'];
  return { cart: snapshot.cart, revision: snapshot.itemCount ? `demo-${state.version}` : null, idempotencyKey: previewKey, canSubmit: issues.length === 0, issues };
}
function submit(form: URLSearchParams): OrderReceipt {
  const key = form.get('idempotencyKey') ?? '';
  if (!uuid.test(key)) throw new ApiError(400, 'Ссылка оформления устарела. Обновите страницу.');
  if (state.receipts[key]) return { ...state.receipts[key] };
  const errors: Record<string, string> = {};
  const name = form.get('customerName')?.trim() ?? '';
  const phone = (form.get('phone') ?? '').replace(/[\s()\-]/g, '');
  if (!name || name.length > 80 || /[\u0000-\u001f\u007f]/.test(name)) errors.customerName = 'Укажите имя длиной от 1 до 80 символов.';
  if (!/^\+7\d{10}$/.test(phone)) errors.phone = 'Укажите номер в формате +7 и 10 цифр.';
  const comment = form.get('comment') ?? '';
  if (comment.length > 1000 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(comment)) errors.comment = 'Комментарий должен содержать не более 1000 символов.';
  if (form.get('consent') !== 'true') errors.consent = 'Подтвердите согласие для проверки формы.';
  if (Object.keys(errors).length) throw new ApiError(400, 'Проверьте выделенные поля.', errors);
  const preview = checkout();
  if (!preview.canSubmit || form.get('revision') !== preview.revision) throw new ApiError(409, 'Корзина изменилась. Проверьте товары и повторите оформление.');
  const previousNumber = Math.max(0, ...Object.values(state.receipts).map((item) => Number(item.number.slice(5))));
  const receipt: OrderReceipt = { number: `DEMO-${String(previousNumber + 1).padStart(4, '0')}`, status: 'RECEIVED', storeName: preview.cart.storeName!, itemCount: cart().itemCount, totalText: preview.cart.totalText, createdAt: new Date().toISOString(), message: receiptMessage };
  // Only public receipt metadata is persisted. Form data ends here in memory.
  state.receipts[key] = receipt;
  const keys = Object.keys(state.receipts);
  if (keys.length > 50) delete state.receipts[keys[0]];
  state.items = []; state.storeId = null; changed();
  return { ...receipt };
}

/** Browser-only transport. An unknown route fails closed and never reaches fetch. */
export async function demoRequest<T>(url: string, options: RequestInit = {}): Promise<T> {
  options.signal?.throwIfAborted();
  if (!url.startsWith('/api/')) throw new ApiError(403, 'В демонстрации внешние запросы и служебный раздел отключены.');
  const parsed = new URL(url, window.location.origin);
  const path = parsed.pathname;
  const params = parsed.searchParams;
  const method = (options.method ?? 'GET').toUpperCase();
  let response: unknown;
  if (method === 'GET' && path === '/api/csrf') response = { headerName: 'X-DEMO-CSRF', token: 'browser-local-only' };
  else if (method === 'GET' && path === '/api/catalog/stores') response = { stores: demoStores.map((store) => ({ ...store })), defaultStoreId: demoStores[0].id };
  else if (method === 'GET' && path === '/api/catalog/products') {
    const store = storeById(params.get('store') || demoStores[0].id);
    const query = (params.get('q') ?? '').trim();
    const pageValue = params.get('page') ?? '0';
    if (query.length > 120 || !/^\d+$/.test(pageValue) || !Number.isSafeInteger(Number(pageValue)) || Number(pageValue) > 1000000) throw new ApiError(400, 'Проверьте параметры поиска.');
    const queryLower = query.toLocaleLowerCase('ru-RU');
    const category = params.get('category');
    if (category && !demoCategories.some((item) => item.id === category)) throw new ApiError(400, 'Категория не найдена. Сбросьте фильтры.');
    const filtered = demoProducts.filter((item) => (!category || item.category === category) && (!queryLower || `${item.name} ${item.sku} ${item.code}`.toLocaleLowerCase('ru-RU').includes(queryLower)) && (params.get('available') !== 'true' || demoOffer(item, store.id).stock > 0));
    const page = Number(pageValue);
    const size = 12;
    response = { products: filtered.slice(page * size, (page + 1) * size).map((item) => publicProduct(item.id, store.id)), query, storeId: store.id, storeName: store.name, stores: demoStores, page, totalPages: Math.ceil(filtered.length / size), totalProducts: filtered.length, lastSyncText: 'демонстрационные данные', stale: false } satisfies CatalogPage;
  } else if (method === 'GET' && /^\/api\/catalog\/products\/[0-9a-f]{24}$/.test(path)) {
    const id = path.split('/').at(-1)!;
    const product = productById(id);
    const store = storeById(params.get('store') || demoStores[0].id);
    response = { product: publicProduct(id, store.id), offers: demoStores.map((point) => {
      const offer = demoOffer(product, point.id);
      return { storeId: point.id, storeName: point.name, priceText: money(offer.price), stockText: offer.stock > 0 ? `${offer.stock} шт.` : 'Нет в наличии', canAdd: offer.stock > 0 && offer.price > 0, unavailabilityReason: offer.stock ? null : 'В этой демонстрационной точке товар отсутствует.' };
    }), selectedStore: store.id, lastSyncText: 'демонстрационные данные', stale: false } satisfies Detail;
  } else if (method === 'GET' && path === '/api/cart') response = cart();
  else if (method === 'POST' && /^\/api\/cart\/(add|update|remove|clear|confirm-price)$/.test(path)) response = mutateCart(path.split('/').at(-1)!, body(options));
  else if (method === 'GET' && path === '/api/checkout') response = checkout();
  else if (method === 'POST' && path === '/api/orders') response = submit(body(options));
  else if (method === 'GET' && path.startsWith('/api/orders/by-key/')) {
    const key = decodeURIComponent(path.slice('/api/orders/by-key/'.length));
    if (!uuid.test(key) || !state.receipts[key]) throw new ApiError(404, 'Демонстрационная заявка не найдена в этой вкладке.');
    response = { ...state.receipts[key] };
  } else throw new ApiError(404, 'Этот раздел недоступен в демонстрационном прототипе. Вернитесь в каталог.');
  return structuredClone(response) as T;
}
