import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, HashRouter } from 'react-router-dom';
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@fontsource/golos-text/cyrillic-400.css';
import '@fontsource/golos-text/latin-400.css';
import '@fontsource/golos-text/cyrillic-500.css';
import '@fontsource/golos-text/latin-500.css';
import '@fontsource/golos-text/cyrillic-600.css';
import '@fontsource/golos-text/latin-600.css';
import '@fontsource/golos-text/cyrillic-700.css';
import '@fontsource/golos-text/latin-700.css';
import '@fontsource/manrope/cyrillic-700.css';
import '@fontsource/manrope/latin-700.css';
import '@fontsource-variable/archivo/wdth.css';
import './styles/theme.css';
import { App } from './app/App';
import { ApiError } from './shared/api/http';
import { ToastProvider } from './shared/ui/Toast';

/** A 401 from any partner endpoint means the session ended: re-check who is signed in. */
function sessionEnded(error: unknown, key: readonly unknown[] | undefined) {
  if (!(error instanceof ApiError) || error.status !== 401 || !key) return;
  if (key[0] === 'me' || key[0] === 'staff') return;
  void client.invalidateQueries({ queryKey: ['me'] });
}
const client: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: (error, query) => sessionEnded(error, query.queryKey) }),
  mutationCache: new MutationCache({ onError: (error, _variables, _context, mutation) => sessionEnded(error, mutation.options.mutationKey) }),
  defaultOptions: { queries: { retry: false, staleTime: 0, refetchOnWindowFocus: true } },
});
const Router = import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true' ? HashRouter : BrowserRouter;

createRoot(document.getElementById('root')!).render(
  <StrictMode><QueryClientProvider client={client}><Router><ToastProvider><App /></ToastProvider></Router></QueryClientProvider></StrictMode>,
);
