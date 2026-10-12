export class ApiError extends Error {
  constructor(public status: number, message: string, public errors: Record<string, string> = {}) { super(message); }
}

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  if (import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true') {
    const { demoRequest } = await import('../../demo/transport');
    return demoRequest<T>(url, options);
  }
  const response = await fetch(url, { ...options, credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json', ...options.headers } });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ApiError(response.status, problem?.detail ?? 'Не удалось выполнить запрос. Проверьте соединение и обновите данные.', problem?.errors ?? {});
  }
  return response.json() as Promise<T>;
}
