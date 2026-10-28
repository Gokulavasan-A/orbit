import '@fontsource-variable/inter';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { connectApiClient } from './app/apiBridge';
import { setupStore } from './app/store';

const store = setupStore();
connectApiClient(store);

const root = document.getElementById('root');
if (!root) {
  throw new Error('Missing #root element.');
}

createRoot(root).render(
  <StrictMode>
    <App store={store} />
  </StrictMode>,
);
