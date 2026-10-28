import { ThemeProvider } from '@mui/material/styles';
import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';
import { connectApiClient } from '../app/apiBridge';
import { setupStore, type AppStore, type RootState } from '../app/store';
import { initialAuthState } from '../features/auth/authSlice';
import { theme } from '../theme/theme';
import { authResponse } from './handlers';

interface Options extends Omit<RenderOptions, 'wrapper'> {
  preloadedState?: Partial<RootState>;
  store?: AppStore;
  /** Wrap in a MemoryRouter at this path. */
  route?: string;
}

/** Renders with Redux (connected to the axios client), the theme and optionally a router. */
export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { preloadedState, store = setupStore(preloadedState), route, ...renderOptions } = options;
  connectApiClient(store);

  function Wrapper({ children }: { children: ReactNode }) {
    const content = route ? <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter> : children;
    return (
      <Provider store={store}>
        <ThemeProvider theme={theme}>{content}</ThemeProvider>
      </Provider>
    );
  }
  return { store, ...render(ui, { wrapper: Wrapper, ...renderOptions }) };
}

/** Auth state of a signed-in user with the given token. */
export function signedInState(accessToken = 'access-token'): Partial<RootState> {
  const auth = authResponse({ accessToken });
  return {
    auth: {
      ...initialAuthState,
      status: 'authenticated',
      user: auth.user,
      roles: auth.roles,
      permissions: auth.permissions,
      accessToken,
      expiresAt: auth.expiresAt,
      lastActivityAt: Date.now(),
    },
  };
}
