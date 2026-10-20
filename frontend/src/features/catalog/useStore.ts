import { useQuery } from '@tanstack/react-query';
import { fetchStores } from '../../shared/api/catalog';

/** The portal works with one warehouse. Its id comes from the server; no selector is shown. */
export function useDefaultStore() {
  const stores = useQuery({ queryKey: ['stores'], queryFn: ({ signal }) => fetchStores(signal), staleTime: 300000 });
  const id = stores.data?.defaultStoreId ?? '';
  return { id, name: stores.data?.stores.find((store) => store.id === id)?.name ?? null, query: stores };
}

export const pickupText = 'Самовывоз со склада MBM, Алматы';
