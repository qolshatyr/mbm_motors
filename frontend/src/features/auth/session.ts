import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import { fetchMe, login, logout } from '../../shared/api/auth';

export const meKey = ['me'] as const;
export function useMe() {
  return useQuery({ queryKey: meKey, queryFn: ({ signal }) => fetchMe(signal), staleTime: 60000, refetchOnWindowFocus: true });
}

/** Drops every cached customer response: they belong to the previous session. */
function resetSessionData(client: QueryClient) {
  client.removeQueries({ predicate: (query) => query.queryKey[0] !== 'me' && query.queryKey[0] !== 'staff' });
}

export function useLogin() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) => login(username, password),
    retry: false,
    onSuccess: (partner) => { leaving = false; resetSessionData(client); client.setQueryData(meKey, partner); },
  });
}

let leaving = false;

/**
 * Where a signed-out visitor of {@code path} is sent. After «Выйти» the page left is not a place to
 * return to, so the next sign-in opens the home page.
 */
export function signInPath(path: string) {
  if (leaving) return '/login';
  return `/login?from=${encodeURIComponent(path)}`;
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async () => { leaving = true; await logout(); },
    retry: false,
    onSettled: async (_data, error) => {
      if (error) leaving = false;
      resetSessionData(client);
      await client.invalidateQueries({ queryKey: meKey });
    },
  });
}

/** Accept only same-site paths for the post-login return. */
export function safeReturn(value: string | null) {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\') || value.startsWith('/login')) return '/';
  return value;
}
