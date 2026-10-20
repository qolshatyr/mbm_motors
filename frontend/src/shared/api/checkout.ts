import type { CartSnapshot } from './cart';
import { request } from './http';
import { postForm } from './csrf';

export interface CheckoutPreview { cart: CartSnapshot['cart']; revision: string | null; idempotencyKey: string; canSubmit: boolean; issues: string[] }
export interface OrderReceipt { number: string; status: 'RECEIVED'; storeName: string; itemCount: number; totalText: string; createdAt: string; message: string }
export interface CheckoutFields { customerName: string; phone: string; comment: string; consent: boolean }
export type OrderSubmission = Record<string, string>;
export const fetchCheckout = (signal?: AbortSignal) => request<CheckoutPreview>('/api/checkout', { signal });
export const fetchReceipt = (key: string, signal?: AbortSignal) => request<OrderReceipt>(`/api/orders/by-key/${encodeURIComponent(key)}`, { signal });
export const submitOrder = (fields: OrderSubmission) => postForm<OrderReceipt>('/api/orders', fields);

const pendingKey = 'mbm.pendingOrderKey';
export function readPendingKey() { try { return sessionStorage.getItem(pendingKey); } catch { return null; } }
export function rememberPendingKey(key: string) { try { sessionStorage.setItem(pendingKey, key); } catch { /* Recovery remains available while this page stays open. */ } }
export function forgetPendingKey() { try { sessionStorage.removeItem(pendingKey); } catch { /* Storage can be unavailable in private browsing. */ } }
