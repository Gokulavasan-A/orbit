import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { sessionCoordinator } from '../api/axiosClient';
import { setCsrfToken } from '../api/csrfToken';
import { closeAuthChannel } from '../features/auth/authChannel';
import { server } from './server';

// jsdom has no ResizeObserver; AG Grid and MUI need it.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
if (!('ResizeObserver' in globalThis)) {
  Object.assign(globalThis, { ResizeObserver: ResizeObserverStub });
}

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  // Module-level client state must not leak between tests.
  setCsrfToken(null);
  sessionCoordinator.reset();
  closeAuthChannel();
});

afterAll(() => server.close());
