import { ApiError } from '../shared/api/http';
import { readFilter } from '../shared/api/catalog';
import type { CatalogFilter, CatalogPage, Detail, FacetValue, Product } from '../shared/api/catalog';
import type { DemoProduct } from './data';
import type { CartSnapshot } from '../shared/api/cart';
import type { CheckoutPreview, OrderReceipt } from '../shared/api/checkout';
import type { AccountOrder, AccountOrderDetail } from '../shared/api/account';
import { demoPartner, demoProducts, demoStore, money } from './data';

interface Line { id: string; quantity: number }
interface DemoOrder { order: AccountOrder; items: Line[] }
interface DemoState { signedIn: boolean; items: Line[]; version: number; receipts: Record<string, OrderReceipt>; orders: DemoOrder[] }
const storageKey = 'mbm.demo.session.v2';
const fresh = (): DemoState => ({ signedIn: false, items: [], version: 0, receipts: {}, orders: [] });
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const receiptMessage = 'Это демонстрационная заявка. Она не отправляется в магазин или CloudShop. Контактные данные не сохраняются.';
const findProduct = (id: string) => demoProducts.find((item) => item.id === id);
const validLines = (lines: unknown): lines is Line[] => Array.isArray(lines) && lines.length <= 50 && lines.every((line) => line && typeof line === 'object' && findProduct((line as Line).id) && Number.isInteger((line as Line).quantity) && (line as Line).quantity >= 1 && (line as Line).quantity <= 999);

function restore(): DemoState {
  try {
    const value = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') as DemoState | null;
    if (!value || !Number.isSafeInteger(value.version) || value.version < 0 || !value.receipts || typeof value.receipts !== 'object' || !Array.isArray(value.orders)) return fresh();
    if (!validLines(value.items) || new Set(value.items.map((line) => line.id)).size !== value.items.length) return fresh();
    if (value.items.some((line) => line.quantity > findProduct(line.id)!.stock)) return fresh();
    // Copy only public demo metadata. Never restore arbitrary fields or contacts.
    const receipts: Record<string, OrderReceipt> = {};
    for (const [key, receipt] of Object.entries(value.receipts).slice(-50)) {
      if (!uuid.test(key) || !receipt || !/^DEMO-\d+$/.test(receipt.number) || receipt.status !== 'RECEIVED' || !Number.isInteger(receipt.itemCount) || receipt.itemCount < 1 || typeof receipt.totalText !== 'string' || !/^\d[\d\s]* ₸$/.test(receipt.totalText) || typeof receipt.createdAt !== 'string' || !Number.isFinite(Date.parse(receipt.createdAt))) continue;
      receipts[key] = { number: receipt.number, status: 'RECEIVED', storeName: demoStore.name, itemCount: receipt.itemCount, totalText: receipt.totalText, createdAt: receipt.createdAt, message: receiptMessage };
    }
    const orders = value.orders.slice(0, 50).filter((entry) => entry && entry.order && /^DEMO-\d+$/.test(entry.order.number) && validLines(entry.items) && typeof entry.order.createdAt === 'string' && Number.isFinite(Date.parse(entry.order.createdAt)))
      .map(({ order, items }) => ({ order: demoOrder(order.number, order.createdAt, items), items: items.map(({ id, quantity }) => ({ id, quantity })) }));
    return { signedIn: value.signedIn === true, items: value.items.map(({ id, quantity }) => ({ id, quantity })), version: value.version, receipts, orders };
  } catch { return fresh(); }
}
function demoOrder(number: string, createdAt: string, items: Line[]): AccountOrder {
  const total = items.reduce((sum, line) => sum + findProduct(line.id)!.price * line.quantity, 0);
  return { number, createdAt, itemCount: items.reduce((sum, line) => sum + line.quantity, 0), totalText: money(total), status: 'RECEIVED', statusText: 'Сохранена, ждёт передачи' };
}
let state = restore();
let previewKey = crypto.randomUUID();
function save() {
  try { sessionStorage.setItem(storageKey, JSON.stringify(state)); } catch { /* Demo remains usable without browser storage. */ }
}
function changed() { state.version += 1; previewKey = crypto.randomUUID(); save(); }
function productById(id: string) {
  const product = findProduct(id);
  if (!product) throw new ApiError(404, 'Запчасть не найдена. Вернитесь в демонстрационный каталог.');
  return product;
}
function requireStore(id: string | null) {
  if (id && id !== demoStore.id) throw new ApiError(400, 'Склад не найден.');
}
function publicProduct(id: string): Product {
  const { price, stock, classification, ...item } = productById(id);
  return { ...item, brand: classification.brands.join(', ') || null, canAdd: price > 0 && stock > 0, price: price > 0 ? price : null, availableQuantity: stock > 0 ? stock : null };
}
/** Same rules as the server: every filter except the facet's own one narrows its counts. */
function matches(item: DemoProduct, filter: CatalogFilter) {
  const { section, subsection, brands, fitment } = item.classification;
  return (!filter.section || section === filter.section) && (!filter.subsection || subsection === filter.subsection)
    && (!filter.make || fitment.some((car) => car.make === filter.make && (!filter.model || car.models.includes(filter.model))))
    && (!filter.brands.length || brands.some((brand) => filter.brands.includes(brand)));
}
function count(items: DemoProduct[], names: (item: DemoProduct) => string[]): FacetValue[] {
  const counts = new Map<string, number>();
  items.forEach((item) => new Set(names(item)).forEach((name) => counts.set(name, (counts.get(name) ?? 0) + 1)));
  return [...counts].map(([name, value]) => ({ name, count: value })).sort((a, b) => a.name.localeCompare(b.name, 'ru'));
}
function facets(items: DemoProduct[], filter: CatalogFilter) {
  const without = (changes: Partial<CatalogFilter>) => items.filter((item) => matches(item, { ...filter, ...changes }));
  return {
    sections: count(without({ section: null, subsection: null }), (item) => [item.classification.section ?? ''].filter(Boolean)),
    subsections: filter.section ? count(without({ subsection: null }), (item) => [item.classification.subsection ?? ''].filter(Boolean)) : [],
    makes: count(without({ make: null, model: null }), (item) => item.classification.fitment.map((car) => car.make)),
    models: filter.make ? count(without({ model: null }), (item) => item.classification.fitment.filter((car) => car.make === filter.make).flatMap((car) => car.models)) : [],
    brands: count(without({ brands: [] }), (item) => item.classification.brands),
  };
}
function cart(message: string | null = null): CartSnapshot {
  const items = state.items.map((line) => {
    const product = productById(line.id);
    return { id: product.id, name: product.name, sku: product.sku, unit: product.unit, quantity: line.quantity, priceText: money(product.price), totalText: money(product.price * line.quantity), problem: null, priceChanged: false, currentPrice: null };
  });
  const total = state.items.reduce((sum, line) => sum + productById(line.id).price * line.quantity, 0);
  return { cart: { storeId: items.length ? demoStore.id : null, storeName: items.length ? demoStore.name : null, items, totalText: money(total), hasIssues: false }, itemCount: items.reduce((sum, item) => sum + item.quantity, 0), message };
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
  if (action === 'clear') { state.items = []; changed(); return cart('Корзина очищена.'); }
  const product = productById(form.get('productId') ?? '');
  const line = state.items.find((item) => item.id === product.id);
  if (action === 'remove') { state.items = state.items.filter((item) => item.id !== product.id); changed(); return cart('Товар удалён из корзины.'); }
  if (action === 'confirm-price') {
    if (!line || Number(form.get('expectedPrice')) !== product.price) throw new ApiError(409, 'Цена не совпадает. Обновите корзину.');
    return cart('Цена актуальна.');
  }
  if (action !== 'add' && action !== 'update') throw new ApiError(404, 'Такое действие недоступно в демонстрации.');
  if (action === 'update' && !line) throw new ApiError(404, 'Товар уже удалён. Обновите корзину.');
  if (action === 'add') requireStore(form.get('storeId'));
  const count = quantity(form.get('quantity') ?? (action === 'add' ? '1' : null));
  const next = action === 'add' ? (line?.quantity ?? 0) + count : count;
  if (product.price <= 0) throw new ApiError(409, 'Цена уточняется. Эту позицию пока нельзя заказать.');
  if (next > 999 || product.stock < next) throw new ApiError(409, product.stock ? `На складе ${product.stock} шт. Уменьшите количество.` : 'Товара нет на складе.');
  if (!line && state.items.length >= 50) throw new ApiError(409, 'В корзине не может быть больше 50 разных товаров.');
  if (line) line.quantity = next; else state.items.push({ id: product.id, quantity: next });
  changed();
  return cart(action === 'add' ? 'Товар добавлен в корзину.' : 'Количество обновлено.');
}
function checkout(): CheckoutPreview {
  const snapshot = cart();
  const issues = snapshot.itemCount ? [] : ['Корзина пуста. Добавьте товары из каталога.'];
  return { cart: snapshot.cart, revision: snapshot.itemCount ? `demo-${state.version}` : null, idempotencyKey: previewKey, canSubmit: issues.length === 0, issues, contact: { customerName: demoPartner.contactName, phone: demoPartner.phone } };
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
  const previousNumber = Math.max(0, ...Object.values(state.receipts).map((item) => Number(item.number.slice(5))), ...state.orders.map((item) => Number(item.order.number.slice(5))));
  const number = `DEMO-${String(previousNumber + 1).padStart(4, '0')}`;
  const createdAt = new Date().toISOString();
  const receipt: OrderReceipt = { number, status: 'RECEIVED', storeName: demoStore.name, itemCount: cart().itemCount, totalText: preview.cart.totalText, createdAt, message: receiptMessage };
  // Only public receipt metadata is persisted. Form data ends here in memory.
  state.receipts[key] = receipt;
  const keys = Object.keys(state.receipts);
  if (keys.length > 50) delete state.receipts[keys[0]];
  const items = state.items.map(({ id, quantity: count }) => ({ id, quantity: count }));
  state.orders = [{ order: demoOrder(number, createdAt, items), items }, ...state.orders].slice(0, 50);
  state.items = []; changed();
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
  else if (method === 'GET' && path === '/api/auth/me') {
    if (!state.signedIn) throw new ApiError(401, 'Войдите в кабинет, чтобы продолжить.');
    response = { ...demoPartner };
  } else if (method === 'POST' && path === '/api/auth/login') {
    const form = body(options);
    if (!form.get('username')?.trim() || !form.get('password')) throw new ApiError(401, 'Неверный логин или пароль.');
    state.signedIn = true; save();
    response = { ...demoPartner };
  } else if (method === 'POST' && path === '/api/auth/logout') {
    state = { ...fresh(), receipts: state.receipts, orders: state.orders }; save();
    return undefined as T;
  } else if (!state.signedIn) throw new ApiError(401, 'Войдите в кабинет, чтобы продолжить.');
  else if (method === 'GET' && path === '/api/catalog/stores') response = { stores: [{ ...demoStore }], defaultStoreId: demoStore.id };
  else if (method === 'GET' && path === '/api/catalog/products') {
    requireStore(params.get('store'));
    const query = (params.get('q') ?? '').trim();
    const pageValue = params.get('page') ?? '0';
    if (query.length > 120 || !/^\d+$/.test(pageValue) || !Number.isSafeInteger(Number(pageValue)) || Number(pageValue) > 1000000) throw new ApiError(400, 'Проверьте параметры поиска.');
    const lower = query.toLocaleLowerCase('ru-RU');
    const compact = lower.replace(/[^\p{L}\p{N}]/gu, '');
    const filter = readFilter(params);
    if ((filter.subsection && !filter.section) || (filter.model && !filter.make)) throw new ApiError(400, 'Проверьте параметры поиска.');
    const searched = demoProducts.filter((item) => (!lower || `${item.name} ${item.sku} ${item.code}`.toLocaleLowerCase('ru-RU').includes(lower) || (compact && `${item.sku}`.toLocaleLowerCase('ru-RU').replace(/[^\p{L}\p{N}]/gu, '').includes(compact))) && (params.get('available') !== 'true' || item.stock > 0));
    const filtered = searched.filter((item) => matches(item, filter));
    const sort = params.get('sort');
    if (sort && !['name', 'price', 'stock'].includes(sort)) throw new ApiError(400, 'Неизвестная сортировка.');
    const sorted = [...filtered].sort(sort === 'price' ? (a, b) => (a.price || 1e12) - (b.price || 1e12) : sort === 'stock' ? (a, b) => b.stock - a.stock : (a, b) => a.name.localeCompare(b.name, 'ru'));
    const page = Number(pageValue);
    const size = 20;
    const totalPages = Math.ceil(sorted.length / size);
    if (page > 0 && page >= totalPages) throw new ApiError(404, 'Такой страницы каталога нет.');
    response = { products: sorted.slice(page * size, (page + 1) * size).map((item) => publicProduct(item.id)), query, storeId: demoStore.id, storeName: demoStore.name, stores: [{ ...demoStore }], page, totalPages, totalProducts: sorted.length, lastSyncText: 'демонстрационные данные', stale: false, filter, facets: facets(searched, filter) } satisfies CatalogPage;
  } else if (method === 'GET' && /^\/api\/catalog\/products\/[0-9a-f]{24}$/.test(path)) {
    const product = productById(path.split('/').at(-1)!);
    requireStore(params.get('store'));
    const canAdd = product.stock > 0 && product.price > 0;
    response = { product: publicProduct(product.id), offers: [{ storeId: demoStore.id, storeName: demoStore.name, priceText: product.priceText, stockText: product.stockText, canAdd, unavailabilityReason: canAdd ? null : product.price <= 0 ? 'Цена уточняется.' : 'Товара нет на складе.' }], selectedStore: demoStore.id, lastSyncText: 'демонстрационные данные', stale: false, classification: structuredClone(product.classification) } satisfies Detail;
  } else if (method === 'GET' && path === '/api/cart') response = cart();
  else if (method === 'POST' && /^\/api\/cart\/(add|update|remove|clear|confirm-price)$/.test(path)) response = mutateCart(path.split('/').at(-1)!, body(options));
  else if (method === 'GET' && path === '/api/checkout') response = checkout();
  else if (method === 'POST' && path === '/api/orders') response = submit(body(options));
  else if (method === 'GET' && path.startsWith('/api/orders/by-key/')) {
    const key = decodeURIComponent(path.slice('/api/orders/by-key/'.length));
    if (!uuid.test(key) || !state.receipts[key]) throw new ApiError(404, 'Демонстрационная заявка не найдена в этой вкладке.');
    response = { ...state.receipts[key] };
  } else if (method === 'GET' && path === '/api/account/orders') response = { orders: state.orders.map((entry) => ({ ...entry.order })) };
  else if (method === 'GET' && path.startsWith('/api/account/orders/')) {
    const number = decodeURIComponent(path.slice('/api/account/orders/'.length));
    const entry = state.orders.find((item) => item.order.number === number);
    if (!entry) throw new ApiError(404, 'Заявка не найдена.');
    response = { number, items: entry.items.map((line) => { const product = productById(line.id); return { productId: product.id, name: product.name, sku: product.sku, quantity: line.quantity }; }) } satisfies AccountOrderDetail;
  } else throw new ApiError(404, 'Этот раздел недоступен в демонстрационном прототипе. Вернитесь в каталог.');
  return structuredClone(response) as T;
}
