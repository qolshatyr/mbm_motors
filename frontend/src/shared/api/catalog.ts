export interface Store { id: string; name: string }
export interface Product { id: string; name: string; sku: string | null; code: string | null; unit: string | null; priceText: string; stockText: string; imageUrl: string | null }
export interface CatalogPage { products: Product[]; query: string; storeId: string; storeName: string; stores: Store[]; page: number; totalPages: number; totalProducts: number; lastSyncText: string | null; stale: boolean }
export interface Stores { stores: Store[]; defaultStoreId: string }

export interface Detail { product: Product; offers: { storeId: string; storeName: string; priceText: string; stockText: string; canAdd: boolean }[]; selectedStore: string; lastSyncText: string | null; stale: boolean }
export const fetchStores = (signal: AbortSignal) => request<Stores>('/api/catalog/stores', { signal });
export const fetchProducts = (params: URLSearchParams, signal: AbortSignal) => request<CatalogPage>(`/api/catalog/products?${params}`, { signal });
export const fetchDetail = (id: string, store: string, signal: AbortSignal) => request<Detail>(`/api/catalog/products/${encodeURIComponent(id)}${store ? `?store=${encodeURIComponent(store)}` : ''}`, { signal });
import { request } from './http';
export { ApiError } from './http';
