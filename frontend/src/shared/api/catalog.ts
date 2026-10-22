import { request } from './http';
export { ApiError } from './http';

export interface Store { id: string; name: string }
/** Texts are for display; `canAdd`, `price` and `availableQuantity` drive add/blocked decisions. */
export interface Product { id: string; name: string; sku: string | null; code: string | null; unit: string | null; priceText: string; stockText: string; imageUrl: string | null; brand: string | null; canAdd: boolean; price: number | null; availableQuantity: number | null }
export interface CatalogPage { products: Product[]; query: string; storeId: string; storeName: string; stores: Store[]; page: number; totalPages: number; totalProducts: number; lastSyncText: string | null; stale: boolean; filter: CatalogFilter; facets: Facets }
export interface Stores { stores: Store[]; defaultStoreId: string }
export interface Offer { storeId: string; storeName: string; priceText: string; stockText: string; canAdd: boolean; unavailabilityReason: string | null }
export interface Detail { product: Product; offers: Offer[]; selectedStore: string; lastSyncText: string | null; stale: boolean; classification: Classification }

/** CloudShop tags: section and subsection, car make and model, part brands. */
export interface CatalogFilter { section: string | null; subsection: string | null; make: string | null; model: string | null; brands: string[] }
export interface FacetValue { name: string; count: number }
/** Options of each filter counted with all other filters applied; subsections and models only for a chosen section or make. */
export interface Facets { sections: FacetValue[]; subsections: FacetValue[]; makes: FacetValue[]; models: FacetValue[]; brands: FacetValue[] }
export interface Fitment { make: string; models: string[] }
export interface Classification { section: string | null; subsection: string | null; brands: string[]; fitment: Fitment[] }

/** URL search parameters of the catalog filters; the API takes the same names. */
export const filterKeys = ['section', 'subsection', 'make', 'model'] as const;
export function readFilter(search: URLSearchParams): CatalogFilter {
  const value = (key: string) => search.get(key)?.trim() || null;
  return { section: value('section'), subsection: value('subsection'), make: value('make'), model: value('model'), brands: [...new Set(search.getAll('brand').map((item) => item.trim()).filter(Boolean))] };
}
export function writeFilter(params: URLSearchParams, filter: CatalogFilter) {
  filterKeys.forEach((key) => { const value = filter[key]; if (value) params.set(key, value); else params.delete(key); });
  params.delete('brand');
  filter.brands.forEach((brand) => params.append('brand', brand));
}
/** «Hyundai Sonata» for a make with its model, the make alone otherwise. */
export const carName = (make: string, model?: string | null) => model ? `${make} ${model}` : make;
export const catalogHref = (filter: Partial<CatalogFilter>) => { const params = new URLSearchParams(); writeFilter(params, { section: null, subsection: null, make: null, model: null, brands: [], ...filter }); const text = params.toString(); return text ? `/search?${text}` : '/search'; };

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
