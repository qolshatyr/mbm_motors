import { ApiError, request } from './http';

type Token = { headerName: string; token: string };
let csrf: Token | null = null;

/** The session id changes on login and logout, and the CSRF token changes with it. */
export function resetCsrf() { csrf = null; }

export async function postForm<T>(url: string, fields: Record<string, string>): Promise<T> {
  const token = csrf ?? await request<Token>('/api/csrf');
  csrf = token;
  try {
    return await request<T>(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', [token.headerName]: token.token }, body: new URLSearchParams(fields) });
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) csrf = null;
    throw error;
  }
}

export async function postJson<T>(url: string, body: unknown): Promise<T> {
  const token = csrf ?? await request<Token>('/api/csrf');
  csrf = token;
  try {
    return await request<T>(url, { method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify(body) });
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) csrf = null;
    throw error;
  }
}
