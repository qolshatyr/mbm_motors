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
    onSuccess: (partner) => { resetSessionData(client); client.setQueryData(meKey, partner); },
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: logout,
    retry: false,
    onSettled: async () => {
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
