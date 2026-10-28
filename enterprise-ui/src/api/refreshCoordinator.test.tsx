import { screen } from '@testing-library/react';
import { delay, http } from 'msw';
import { StrictMode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthBootstrap } from '../features/auth/components/AuthBootstrap';
import { API, authResponse, errorEnvelope, meResponse, okEnvelope } from '../test/handlers';
import { renderWithProviders } from '../test/renderWithProviders';
import { server } from '../test/server';
import { ApiError } from './apiError';
import { authService } from './authService';
import { endpoints } from './endpoints';
import { createSessionCoordinator } from './refreshCoordinator';

let counts: { csrf: number; refresh: number; me: number };

function useAuthApi(refresh: () => Response = () => okEnvelope(authResponse())) {
  server.use(
    http.get(`${API}${endpoints.auth.csrf}`, async () => {
      counts.csrf += 1;
      await delay(10);
      return okEnvelope({ headerName: 'X-XSRF-TOKEN', token: 'csrf-1' });
    }),
    http.post(`${API}${endpoints.auth.refresh}`, async () => {
      counts.refresh += 1;
      await delay(20);
      return refresh();
    }),
    http.get(`${API}${endpoints.auth.me}`, () => {
      counts.me += 1;
      return okEnvelope(meResponse());
    }),
  );
}

beforeEach(() => {
  counts = { csrf: 0, refresh: 0, me: 0 };
});

describe('session bootstrap', () => {
  it('sends /csrf and /refresh once when bootstrap runs twice concurrently', async () => {
    useAuthApi();

    const [first, second] = await Promise.all([authService.bootstrap(), authService.bootstrap()]);

    expect(counts.csrf).toBe(1);
    expect(counts.refresh).toBe(1);
    expect(first).toEqual(second);
    expect(first?.accessToken).toBe('access-token');
  });

  it('sends the refresh cookie once under React StrictMode (effects run twice)', async () => {
    useAuthApi();

    const { store } = renderWithProviders(
      <StrictMode>
        <AuthBootstrap>
          <p>app ready</p>
        </AuthBootstrap>
      </StrictMode>,
    );

    expect(await screen.findByText('app ready')).toBeInTheDocument();
    await vi.waitFor(() => expect(counts.me).toBe(1));
    expect(counts.csrf).toBe(1);
    expect(counts.refresh).toBe(1);
    expect(store.getState().auth.status).toBe('authenticated');
  });

  it('resolves null (signed out) when the refresh cookie is missing', async () => {
    useAuthApi(() => errorEnvelope(401, 'SESSION_INVALID'));

    const { store } = renderWithProviders(
      <AuthBootstrap>
        <p>app ready</p>
      </AuthBootstrap>,
    );

    expect(await screen.findByText('app ready')).toBeInTheDocument();
    expect(store.getState().auth.status).toBe('unauthenticated');
    expect(store.getState().auth.sessionEndReason).toBeNull();
    expect(counts.me).toBe(0);
  });
});

describe('createSessionCoordinator', () => {
  it('shares one in-flight refresh and allows a new one afterwards', async () => {
    const refresh = vi.fn(async () => {
      await delay(10);
      return authResponse();
    });
    const coordinator = createSessionCoordinator({ fetchCsrf: () => Promise.resolve(), refresh });

    await Promise.all([coordinator.refresh(), coordinator.refresh(), coordinator.refresh()]);
    expect(refresh).toHaveBeenCalledTimes(1);

    await coordinator.refresh();
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it('rejects bootstrap on non-401 failures so the app can offer a retry', async () => {
    const coordinator = createSessionCoordinator({
      fetchCsrf: () => Promise.resolve(),
      refresh: () =>
        Promise.reject(
          new ApiError({
            status: 0,
            code: 'NETWORK_ERROR',
            message: 'offline',
            fieldErrors: [],
            correlationId: null,
            retryAfterSeconds: null,
          }),
        ),
    });

    await expect(coordinator.bootstrap()).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
  });
});
