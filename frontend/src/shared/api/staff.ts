import { request } from './http';

export interface StaffSummary {
  number: string;
  status: 'RECEIVED';
  createdAt: string;
  storeName: string;
  itemCount: number;
  totalText: string;
  delivery: { state: 'WAITING_CONFIGURATION' | 'NOT_PREPARED'; message: string };
}
export interface StaffPage {
  items: StaffSummary[];
  page: number;
  pageSize: 20;
  totalItems: number;
  totalPages: number;
}
export interface StaffDetail extends StaffSummary {
  customerName: string;
  phone: string;
  comment: string;
  items: { position: number; productName: string; sku: string | null; unit: string; quantity: number; priceText: string; lineTotalText: string }[];
}
export const fetchStaffPage = (page: number, signal: AbortSignal) => request<StaffPage>(`/admin/api/requests?page=${page}`, { signal });
export const fetchStaffDetail = (number: string, signal: AbortSignal) => request<StaffDetail>(`/admin/api/requests/${encodeURIComponent(number)}`, { signal });
