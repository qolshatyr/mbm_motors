import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { changeCart, fetchCart } from '../../shared/api/cart';

export function useCartBusy() { return useIsMutating({ mutationKey: ['cart'] }) > 0; }
export function useCart(enabled = true) {
  const busy = useCartBusy();
  return useQuery({ queryKey: ['cart'], queryFn: ({ signal }) => fetchCart(signal), enabled: enabled && !busy, refetchInterval: busy || !enabled ? false : 60000 });
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

/** Quantity in the cart per product id. */
export function useCartQuantities() {
  const cart = useCart();
  const map = new Map<string, number>();
  cart.data?.cart.items.forEach((item) => map.set(item.id, item.quantity));
  return map;
}

export interface BulkLine { productId: string; name: string; quantity: number }
export interface BulkResult { added: BulkLine[]; failed: (BulkLine & { reason: string })[] }
/** Adds lines one by one through the regular cart API. Never repeats a failed add. */
export function useBulkAdd() {
  const action = useCartAction();
  return async function addAll(lines: BulkLine[], storeId: string): Promise<BulkResult> {
    const result: BulkResult = { added: [], failed: [] };
    for (const line of lines) {
      try {
        await action.mutateAsync({ action: 'add', fields: { productId: line.productId, storeId, quantity: String(line.quantity) } });
        result.added.push(line);
      } catch (error) {
        result.failed.push({ ...line, reason: error instanceof Error ? error.message : 'Не удалось добавить позицию.' });
      }
    }
    return result;
  };
}
