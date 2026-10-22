import { ApiError, request } from './http';
import { postForm, resetCsrf } from './csrf';

export interface Partner { login: string; company: string; contactName: string | null; phone: string | null }

/** Returns null when nobody is signed in. */
export async function fetchMe(signal?: AbortSignal): Promise<Partner | null> {
  try { return await request<Partner>('/api/auth/me', { signal }); }
  catch (error) { if (error instanceof ApiError && error.status === 401) return null; throw error; }
}
export async function login(username: string, password: string): Promise<Partner> {
  try { return await postForm<Partner>('/api/auth/login', { username, password }); }
  finally { resetCsrf(); }
}
export async function logout(): Promise<void> {
  try { await postForm<void>('/api/auth/logout', {}); }
  finally { resetCsrf(); }
}
