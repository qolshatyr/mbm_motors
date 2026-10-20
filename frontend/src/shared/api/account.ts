import { request } from './http';

export type OrderStatus = 'RECEIVED' | 'SENT' | 'UNKNOWN';
export interface AccountOrder { number: string; createdAt: string; itemCount: number; totalText: string; status: OrderStatus; statusText: string }
export interface AccountOrderDetail { number: string; items: { productId: string; name: string; sku: string | null; quantity: number }[] }
export const fetchOrders = (signal?: AbortSignal) => request<{ orders: AccountOrder[] }>('/api/account/orders', { signal });
export const fetchOrder = (number: string, signal?: AbortSignal) => request<AccountOrderDetail>(`/api/account/orders/${encodeURIComponent(number)}`, { signal });
