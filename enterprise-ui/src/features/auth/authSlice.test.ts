import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { connectApiClient } from '../../app/apiBridge';
import { setupStore } from '../../app/store';
import { endpoints } from '../../api/endpoints';
import { API, authResponse, errorEnvelope, okEnvelope } from '../../test/handlers';
import { signedInState } from '../../test/renderWithProviders';
import { server } from '../../test/server';
import reducer, {
  activityRecorded,
  initialAuthState,
  login,
  logout,
  passwordChangeRequired,
  sessionEnded,
  sessionEstablished,
} from './authSlice';

const csrfHandler = http.get(`${API}${endpoints.auth.csrf}`, () =>
  okEnvelope({ headerName: 'X-XSRF-TOKEN', token: 'csrf-1' }),
);

describe('authSlice reducers', () => {
  it('stores user, roles, permissions and the token on sessionEstablished', () => {
    const auth = authResponse({ mustChangePassword: true });
    const state = reducer(initialAuthState, sessionEstablished(auth));

    expect(state).toMatchObject({
      status: 'authenticated',
      user: auth.user,
      roles: ['Admin'],
      permissions: ['ROLE_VIEW'],
      mustChangePassword: true,
      accessToken: 'access-token',
      sessionEndReason: null,
    });
    expect(state.lastActivityAt).toEqual(expect.any(Number));
  });

  it('clears everything on sessionEnded and keeps the first reason', () => {
    let state = reducer(initialAuthState, sessionEstablished(authResponse()));
    state = reducer(state, sessionEnded('expired'));
    state = reducer(state, sessionEnded('revoked'));

    expect(state).toEqual({ ...initialAuthState, status: 'unauthenticated', sessionEndReason: 'expired' });
  });

  it('sets mustChangePassword only for a signed-in user', () => {
    expect(reducer(initialAuthState, passwordChangeRequired()).mustChangePassword).toBe(false);
    const signedIn = reducer(initialAuthState, sessionEstablished(authResponse()));
    expect(reducer(signedIn, passwordChangeRequired()).mustChangePassword).toBe(true);
  });

  it('only moves lastActivityAt forward', () => {
    const signedIn = {
      ...reducer(initialAuthState, sessionEstablished(authResponse())),
      lastActivityAt: 1000,
    };
    expect(reducer(signedIn, activityRecorded(500)).lastActivityAt).toBe(1000);
    expect(reducer(signedIn, activityRecorded(2000)).lastActivityAt).toBe(2000);
  });
});

describe('authSlice thunks', () => {
  it('login: fulfilled stores the session', async () => {
    server.use(
      csrfHandler,
      http.post(`${API}${endpoints.auth.login}`, () =>
        okEnvelope(authResponse({ accessToken: 'login-token' })),
      ),
    );
    const store = setupStore();
    connectApiClient(store);

    await store.dispatch(login({ username: 'admin', password: 'secret' })).unwrap();

    expect(store.getState().auth.status).toBe('authenticated');
    expect(store.getState().auth.accessToken).toBe('login-token');
  });

  it('login: rejected carries the serializable API error', async () => {
    server.use(
      csrfHandler,
      http.post(`${API}${endpoints.auth.login}`, () =>
        errorEnvelope(423, 'ACCOUNT_LOCKED', 'This account is temporarily locked.'),
      ),
    );
    const store = setupStore();
    connectApiClient(store);

    const result = await store.dispatch(login({ username: 'admin', password: 'x' }));

    expect(login.rejected.match(result)).toBe(true);
    expect(result.payload).toMatchObject({ status: 423, code: 'ACCOUNT_LOCKED' });
    expect(store.getState().auth.status).toBe('bootstrapping');
  });

  it('logout: clears the session with reason signedOut even when the API call fails', async () => {
    server.use(
      csrfHandler,
      http.post(`${API}${endpoints.auth.logout}`, () => errorEnvelope(500, 'INTERNAL_ERROR')),
    );
    const store = setupStore(signedInState());
    connectApiClient(store);

    await store.dispatch(logout());

    expect(store.getState().auth).toMatchObject({
      status: 'unauthenticated',
      accessToken: null,
      sessionEndReason: 'signedOut',
    });
    expect(store.getState().ui.toasts).toEqual([]);
  });
});
