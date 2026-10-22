import { request } from './http';
import { postJson } from './csrf';

export interface StaffSummary {
  number: string;
  status: 'RECEIVED';
  createdAt: string;
  storeName: string;
  itemCount: number;
  totalText: string;
  delivery: StaffDelivery;
}
/** reason — why the attempt became UNKNOWN; reconciliation — the staff check in CloudShop. */
export interface StaffDelivery {
  state: 'WAITING_CONFIGURATION' | 'READY' | 'CUSTOMER_IN_FLIGHT' | 'CUSTOMER_CREATED' | 'SALE_IN_FLIGHT' | 'SENT' | 'UNKNOWN' | 'NOT_PREPARED';
  message: string;
  reason: string | null;
  reconciliation: { result: 'FOUND' | 'NOT_CREATED'; saleNumber: number | null; by: string; at: string } | null;
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
export const reconcileRequest = (number: string, result: 'FOUND' | 'NOT_CREATED', saleNumber: number | null) =>
  postJson<StaffDetail>(`/admin/api/requests/${encodeURIComponent(number)}/reconciliation`, { result, saleNumber });

/** NONE — no card, PENDING — request interrupted, UNKNOWN — unclear answer, LINKED — card attached. */
export type CustomerState = 'NONE' | 'PENDING' | 'UNKNOWN' | 'LINKED';
export interface StaffClient { login: string; company: string; contactName: string; phone: string; bin: string | null; enabled: boolean; createdAt: string; lastLoginAt: string | null; customer: { state: CustomerState; id: string | null } }
export interface StaffClients { clients: StaffClient[]; customerCreation: boolean }
export interface NewClient { login: string; company: string; contactName: string; phone: string; bin: string; password: string }
const clientPath = (login: string) => `/admin/api/clients/${encodeURIComponent(login)}`;
export const fetchClients = (query: string, signal: AbortSignal) => request<StaffClients>(`/admin/api/clients?q=${encodeURIComponent(query)}`, { signal });
export const createClient = (client: NewClient) => postJson<StaffClient>('/admin/api/clients', client);
export const changeClientPassword = (login: string, password: string) => postJson<void>(`${clientPath(login)}/password`, { password });
export const setClientEnabled = (login: string, enabled: boolean) => postJson<StaffClient>(`${clientPath(login)}/enabled`, { enabled });
export const linkClientCustomer = (login: string, customerId: string) => postJson<StaffClient>(`${clientPath(login)}/customer`, { customerId });
export const createClientCustomer = (login: string, checkedInCloudShop: boolean) => postJson<StaffClient>(`${clientPath(login)}/customer/create`, { checkedInCloudShop });

const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
/** 12 characters without look-alikes (0/O, 1/l/I), from the browser's cryptographic generator. */
export function generatePassword(length = 12) {
  const result: string[] = [];
  const limit = 256 - (256 % PASSWORD_ALPHABET.length);
  while (result.length < length) {
    for (const byte of crypto.getRandomValues(new Uint8Array(length * 2))) {
      if (byte < limit && result.length < length) result.push(PASSWORD_ALPHABET[byte % PASSWORD_ALPHABET.length]);
    }
  }
  return result.join('');
}

const TRANSLIT: Record<string, string> = { а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', е: 'e', ё: 'e', ж: 'zh', з: 'z', и: 'i', й: 'y', к: 'k', л: 'l', м: 'm', н: 'n', о: 'o', п: 'p', р: 'r', с: 's', т: 't', у: 'u', ф: 'f', х: 'h', ц: 'ts', ч: 'ch', ш: 'sh', щ: 'sch', ъ: '', ы: 'y', ь: '', э: 'e', ю: 'yu', я: 'ya', ә: 'a', ғ: 'g', қ: 'k', ң: 'n', ө: 'o', ұ: 'u', ү: 'u', һ: 'h', і: 'i' };
/** Login suggestion from the company name: «ТОО «Турбо Сервис»» → «turboservis». */
export function loginFromCompany(company: string) {
  return company.toLocaleLowerCase('ru-RU').split(/[\s«»"'().,-]+/).filter((word) => word && !['тоо', 'ип', 'сто', 'ао', 'too', 'ip'].includes(word)).join('')
    .split('').map((char) => TRANSLIT[char] ?? char).join('').replace(/[^a-z0-9]/g, '').slice(0, 20);
}
