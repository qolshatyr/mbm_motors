import { ApiError, request } from './http';

export interface CartItem { id: string; name: string; sku: string | null; quantity: number; unit: string | null; priceText: string; totalText: string; problem: string | null; priceChanged: boolean; currentPrice: string | null }
export interface CartSnapshot { cart: { storeId: string | null; storeName: string | null; items: CartItem[]; totalText: string; hasIssues: boolean }; itemCount: number; message: string | null }
export type CartAction = { action: 'add' | 'update' | 'remove' | 'clear' | 'confirm-price'; fields: Record<string, string> };
let csrf: { headerName: string; token: string } | null = null;
export const fetchCart = (signal: AbortSignal) => request<CartSnapshot>('/api/cart', { signal });
export async function changeCart({ action, fields }: CartAction): Promise<CartSnapshot> {
  const token = csrf ?? await request<{ headerName: string; token: string }>('/api/csrf');
  csrf = token;
  try {
    return await request<CartSnapshot>(`/api/cart/${action}`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', [token.headerName]: token.token }, body: new URLSearchParams(fields) });
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) csrf = null;
    throw error;
  }
}
