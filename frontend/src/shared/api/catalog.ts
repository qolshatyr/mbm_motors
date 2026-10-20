import { request } from './http';
export { ApiError } from './http';

export interface Store { id: string; name: string }
/** Texts are for display; `canAdd`, `price` and `availableQuantity` drive add/blocked decisions. */
export interface Product { id: string; name: string; sku: string | null; code: string | null; unit: string | null; priceText: string; stockText: string; imageUrl: string | null; canAdd: boolean; price: number | null; availableQuantity: number | null }
export interface CatalogPage { products: Product[]; query: string; storeId: string; storeName: string; stores: Store[]; page: number; totalPages: number; totalProducts: number; lastSyncText: string | null; stale: boolean }
export interface Stores { stores: Store[]; defaultStoreId: string }
export interface Offer { storeId: string; storeName: string; priceText: string; stockText: string; canAdd: boolean; unavailabilityReason: string | null }
export interface Detail { product: Product; offers: Offer[]; selectedStore: string; lastSyncText: string | null; stale: boolean }

export type CatalogSort = 'name' | 'price' | 'stock';
export const catalogSorts: { value: CatalogSort; label: string }[] = [
  { value: 'name', label: 'По названию' },
  { value: 'price', label: 'Дешевле' },
  { value: 'stock', label: 'Больше на складе' },
];

export const fetchStores = (signal: AbortSignal) => request<Stores>('/api/catalog/stores', { signal });
export const fetchProducts = (params: URLSearchParams, signal: AbortSignal) => request<CatalogPage>(`/api/catalog/products?${params}`, { signal });
export const fetchDetail = (id: string, store: string, signal: AbortSignal) => request<Detail>(`/api/catalog/products/${encodeURIComponent(id)}${store ? `?store=${encodeURIComponent(store)}` : ''}`, { signal });

/** The server writes «Цена уточняется» for a missing or zero price. Such items cannot be ordered. */
export const priceUnknown = (priceText: string) => !/\d/.test(priceText) || /уточня/i.test(priceText) || /^0([,.]0+)?\s*₸$/.test(priceText.trim());

/** Parses «В наличии: 1 234 шт.» into 1234. Returns 0 for «Нет в наличии» and null when unknown. */
export function stockQuantity(stockText: string): number | null {
  if (/^нет/i.test(stockText.trim())) return 0;
  const match = stockText.match(/(\d[\d\s  ]*(?:[,.]\d+)?)/);
  if (!match) return null;
  const value = Number(match[1].replace(/[\s  ]/g, '').replace(',', '.'));
  return Number.isFinite(value) ? value : null;
}

export type Availability = 'ok' | 'price' | 'stock' | 'unknown';
/** Why a list row can or cannot go to the cart. The cart API still validates every add. */
export function availability(product: Pick<Product, 'price' | 'availableQuantity' | 'canAdd' | 'stockText'>): Availability {
  if (product.price === null || product.price <= 0) return 'price';
  if (product.canAdd && product.availableQuantity !== null) return 'ok';
  if (product.availableQuantity === null) return /^нет/i.test(product.stockText.trim()) ? 'stock' : 'unknown';
  return 'unknown';
}

/** Normalised sku/code: no spaces, dashes, punctuation or case. */
export const normalizeCode = (value: string | null | undefined) => (value ?? '').toLocaleLowerCase('ru-RU').replace(/[^\p{L}\p{N}]/gu, '');
