import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { changeCart, fetchCart } from '../../shared/api/cart';

export function useCartBusy() { return useIsMutating({ mutationKey: ['cart'] }) > 0; }
export function useCart() {
  const busy = useCartBusy();
  return useQuery({ queryKey: ['cart'], queryFn: ({ signal }) => fetchCart(signal), enabled: !busy, refetchInterval: busy ? false : 60000 });
}
export function useCartAction() {
  const client = useQueryClient();
  return useMutation({
    mutationKey: ['cart'], scope: { id: 'cart' }, retry: false, mutationFn: changeCart,
    onMutate: () => client.cancelQueries({ queryKey: ['cart'] }),
    onSuccess: async (snapshot) => {
      await client.cancelQueries({ queryKey: ['cart'] });
      client.setQueryData(['cart'], snapshot);
      await client.invalidateQueries({ queryKey: ['product'] });
      await client.invalidateQueries({ queryKey: ['checkout'] });
    },
    onError: async () => {
      // A failed transport can still follow a completed write. Read the server; never replay the POST.
      await client.cancelQueries({ queryKey: ['cart'] });
      try { client.setQueryData(['cart'], await client.fetchQuery({ queryKey: ['cart'], queryFn: ({ signal }) => fetchCart(signal) })); } catch { /* The visible mutation error remains available. */ }
      await client.invalidateQueries({ queryKey: ['product'] });
      await client.invalidateQueries({ queryKey: ['checkout'] });
    },
  });
}
