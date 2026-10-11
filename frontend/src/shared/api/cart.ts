import { request } from './http';
import { postForm } from './csrf';

export interface CartItem { id: string; name: string; sku: string | null; quantity: number; unit: string | null; priceText: string; totalText: string; problem: string | null; priceChanged: boolean; currentPrice: string | null }
export interface CartSnapshot { cart: { storeId: string | null; storeName: string | null; items: CartItem[]; totalText: string; hasIssues: boolean }; itemCount: number; message: string | null }
export type CartAction = { action: 'add' | 'update' | 'remove' | 'clear' | 'confirm-price'; fields: Record<string, string> };
export const fetchCart = (signal: AbortSignal) => request<CartSnapshot>('/api/cart', { signal });
export const changeCart = ({ action, fields }: CartAction) => postForm<CartSnapshot>(`/api/cart/${action}`, fields);
