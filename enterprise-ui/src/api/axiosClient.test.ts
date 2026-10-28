import { delay, http } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { connectApiClient } from '../app/apiBridge';
import { setupStore, type AppStore } from '../app/store';
import { signedInState } from '../test/renderWithProviders';
import { API, authResponse, errorEnvelope, okEnvelope } from '../test/handlers';
import { server } from '../test/server';
import { ApiError } from './apiError';
import { authService } from './authService';
import { apiClient } from './axiosClient';
import { endpoints } from './endpoints';

const RESOURCE = `${API}${endpoints.roles.all}`;

let store: AppStore;
let counts: { csrf: number; refresh: number; resource: number };

function useCsrf(tokens: string[] = ['csrf-1']) {
  server.use(
    http.get(`${API}${endpoints.auth.csrf}`, () => {
      const token = tokens[Math.min(counts.csrf, tokens.length - 1)]!;
      counts.csrf += 1;
      return okEnvelope({ headerName: 'X-XSRF-TOKEN', token });
    }),
  );
}

function useRefresh(respond: () => Response | Promise<Response>) {
  server.use(
    http.post(`${API}${endpoints.auth.refresh}`, async () => {
      counts.refresh += 1;
      await delay(20);
      return respond();
    }),
  );
}

/** 401 TOKEN_EXPIRED for the old token, 200 for `validToken`. */
function useResource(validToken = 'new-token') {
  server.use(
    http.get(RESOURCE, ({ request }) => {
      counts.resource += 1;
      return request.headers.get('Authorization') === `Bearer ${validToken}`
        ? okEnvelope([{ id: 1, name: 'Admin' }])
        : errorEnvelope(401, 'TOKEN_EXPIRED');
    }),
  );
}

beforeEach(() => {
  counts = { csrf: 0, refresh: 0, resource: 0 };
  store = setupStore(signedInState('old-token'));
  connectApiClient(store);
});

describe('axiosClient', () => {
  it('unwraps the ApiResponse envelope and sends a correlation id', async () => {
    let correlationId: string | null = null;
    server.use(
      http.get(RESOURCE, ({ request }) => {
        correlationId = request.headers.get('X-Correlation-Id');
        return okEnvelope([{ id: 7 }]);
      }),
    );

    const response = await apiClient.get<{ id: number }[]>(endpoints.roles.all);

    expect(response.data).toEqual([{ id: 7 }]);
    expect(correlationId).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('runs one refresh for concurrent 401s and retries every queued request', async () => {
    useCsrf();
    useRefresh(() => okEnvelope(authResponse({ accessToken: 'new-token' })));
    useResource('new-token');

    const results = await Promise.all([
      apiClient.get(endpoints.roles.all),
      apiClient.get(endpoints.roles.all),
      apiClient.get(endpoints.roles.all),
    ]);

    expect(results.every((r) => Array.isArray(r.data))).toBe(true);
    expect(counts.refresh).toBe(1);
    expect(counts.resource).toBe(6); // 3 failed + 3 retried
    expect(store.getState().auth.accessToken).toBe('new-token');
  });

  it('retries a request only once after a refresh', async () => {
    useCsrf();
    useRefresh(() => okEnvelope(authResponse({ accessToken: 'new-token' })));
    useResource('never-valid');

    await expect(apiClient.get(endpoints.roles.all)).rejects.toMatchObject({ code: 'TOKEN_EXPIRED' });

    expect(counts.refresh).toBe(1);
    expect(counts.resource).toBe(2);
    expect(store.getState().auth.status).toBe('unauthenticated');
  });

  it('ends the session with "expired" when the refresh fails', async () => {
    useCsrf();
    useRefresh(() => errorEnvelope(401, 'SESSION_INVALID'));
    useResource('new-token');

    const error: unknown = await apiClient.get(endpoints.roles.all).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(counts.refresh).toBe(1);
    const auth = store.getState().auth;
    expect(auth.status).toBe('unauthenticated');
    expect(auth.accessToken).toBeNull();
    expect(auth.sessionEndReason).toBe('expired');
  });

  it('does not refresh on a 401 from /login and sends no Authorization header there', async () => {
    let authorization: string | null = 'unset';
    useCsrf();
    useRefresh(() => okEnvelope(authResponse()));
    server.use(
      http.post(`${API}${endpoints.auth.login}`, ({ request }) => {
        authorization = request.headers.get('Authorization');
        return errorEnvelope(401, 'INVALID_CREDENTIALS', 'Invalid username or password.');
      }),
    );

    await expect(authService.login({ username: 'admin', password: 'wrong' })).rejects.toMatchObject({
      status: 401,
      code: 'INVALID_CREDENTIALS',
    });
    expect(counts.refresh).toBe(0);
    expect(authorization).toBeNull();
    expect(store.getState().auth.status).toBe('authenticated');
  });

  it('refetches the CSRF token once and retries on 403 CSRF_INVALID', async () => {
    useCsrf(['csrf-old', 'csrf-new']);
    const sentTokens: (string | null)[] = [];
    server.use(
      http.post(`${API}${endpoints.auth.logoutAll}`, ({ request }) => {
        const token = request.headers.get('X-XSRF-TOKEN');
        sentTokens.push(token);
        return token === 'csrf-new' ? okEnvelope({ sessionsRevoked: 2 }) : errorEnvelope(403, 'CSRF_INVALID');
      }),
    );

    const result = await authService.logoutAll();

    expect(result).toEqual({ sessionsRevoked: 2 });
    expect(counts.csrf).toBe(2);
    expect(sentTokens).toEqual(['csrf-old', 'csrf-new']);
  });

  it('sends X-XSRF-TOKEN only on state-changing methods', async () => {
    useCsrf(['csrf-1']);
    const seen: Record<string, string | null> = {};
    server.use(
      http.get(RESOURCE, ({ request }) => {
        seen.GET = request.headers.get('X-XSRF-TOKEN');
        return okEnvelope([]);
      }),
      http.post(`${API}${endpoints.auth.logout}`, ({ request }) => {
        seen.POST = request.headers.get('X-XSRF-TOKEN');
        return okEnvelope(null);
      }),
    );

    await apiClient.get(endpoints.roles.all);
    await authService.logout();

    expect(seen.GET).toBeNull();
    expect(seen.POST).toBe('csrf-1');
  });

  it('routes to change-password on 403 PASSWORD_CHANGE_REQUIRED', async () => {
    server.use(http.get(RESOURCE, () => errorEnvelope(403, 'PASSWORD_CHANGE_REQUIRED')));

    await expect(apiClient.get(endpoints.roles.all)).rejects.toMatchObject({
      code: 'PASSWORD_CHANGE_REQUIRED',
    });

    expect(store.getState().auth.mustChangePassword).toBe(true);
  });

  it('toasts 429 with Retry-After and 500 with the correlation id', async () => {
    server.use(
      http.get(RESOURCE, () =>
        errorEnvelope(429, 'RATE_LIMITED', 'Too many requests.', { headers: { 'Retry-After': '42' } }),
      ),
    );
    await expect(apiClient.get(endpoints.roles.all)).rejects.toMatchObject({ retryAfterSeconds: 42 });

    server.use(http.get(RESOURCE, () => errorEnvelope(500, 'INTERNAL_ERROR', 'Unexpected error.')));
    await expect(apiClient.get(endpoints.roles.all)).rejects.toMatchObject({ status: 500 });

    const messages = store.getState().ui.toasts.map((t) => t.message);
    expect(messages).toEqual([
      'Too many requests. Please try again in 42 seconds.',
      'Unexpected error. (Reference: corr-err)',
    ]);
  });
});
