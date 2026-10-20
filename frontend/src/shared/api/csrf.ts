import { ApiError, request } from './http';

let csrf: { headerName: string; token: string } | null = null;
export async function postForm<T>(url: string, fields: Record<string, string>): Promise<T> {
  const token = csrf ?? await request<{ headerName: string; token: string }>('/api/csrf');
  csrf = token;
  try {
    return await request<T>(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', [token.headerName]: token.token }, body: new URLSearchParams(fields) });
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) csrf = null;
    throw error;
  }
}
