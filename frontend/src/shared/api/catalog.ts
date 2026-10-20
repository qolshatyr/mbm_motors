export interface Store { id: string; name: string }
export interface Product { id: string; name: string; sku: string | null; code: string | null; unit: string | null; priceText: string; stockText: string; imageUrl: string | null }
export interface CatalogPage { products: Product[]; query: string; storeId: string; storeName: string; stores: Store[]; page: number; totalPages: number; totalProducts: number; lastSyncText: string | null; stale: boolean }
export interface Stores { stores: Store[]; defaultStoreId: string }

export class ApiError extends Error { constructor(public status: number, message: string) { super(message); } }
async function get<T>(url: string, signal: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal, credentials: 'same-origin', headers: { Accept: 'application/json' } });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ApiError(response.status, problem?.detail ?? 'Не удалось загрузить каталог. Попробуйте ещё раз.');
  }
  return response.json() as Promise<T>;
}
export const fetchStores = (signal: AbortSignal) => get<Stores>('/api/catalog/stores', signal);
export const fetchProducts = (params: URLSearchParams, signal: AbortSignal) => get<CatalogPage>(`/api/catalog/products?${params}`, signal);
