import { request } from './http';
import { postJson } from './csrf';

export type OrderStatus = 'RECEIVED' | 'SENT' | 'UNKNOWN' | 'CANCEL_REQUESTED' | 'CANCELLED';
export interface AccountOrder { number: string; createdAt: string; itemCount: number; totalText: string; status: OrderStatus; statusText: string }
export interface AccountOrderItem { productId: string; name: string; sku: string | null; unit: string; quantity: number; priceText: string; lineTotalText: string }
/** Prices are the ones saved when the order was placed. cancellable — no request to cancel yet. */
export interface AccountOrderDetail extends AccountOrder { cancelReason: string | null; cancellable: boolean; items: AccountOrderItem[] }
export const fetchOrders = (signal?: AbortSignal) => request<{ orders: AccountOrder[] }>('/api/account/orders', { signal });
export const fetchOrder = (number: string, signal?: AbortSignal) => request<AccountOrderDetail>(`/api/account/orders/${encodeURIComponent(number)}`, { signal });
export const requestCancel = (number: string, reason: string) => postJson<AccountOrderDetail>(`/api/account/orders/${encodeURIComponent(number)}/cancel-request`, { reason });
