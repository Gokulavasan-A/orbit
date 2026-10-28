import axios, { AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse } from '../types/api';
import type { AuthResponse, CsrfResponse, SessionEndReason } from '../types/auth';
import { CORRELATION_ID_HEADER, newCorrelationId } from '../utils/correlationId';
import { env } from '../utils/env';
import { ApiError, ErrorCodes } from './apiError';
import { CSRF_HEADER, getCsrfToken, setCsrfToken } from './csrfToken';
import { decryptResponse, encryptRequest } from './encryption';
import { endpoints } from './endpoints';
import { createSessionCoordinator } from './refreshCoordinator';

/**
 * The single axios instance. Every cross-cutting HTTP concern lives here, in this order:
 *
 * Request:  correlation id -> Bearer token -> X-XSRF-TOKEN (state-changing methods) -> encryption slot
 * Response: encryption slot -> unwrap ApiResponse.data
 * Errors:   401 TOKEN_EXPIRED/TOKEN_INVALID -> one shared refresh, retry once
 *           401 other -> session ended (login page)
 *           403 CSRF_INVALID -> GET /csrf, retry once
 *           403 PASSWORD_CHANGE_REQUIRED -> change-password screen
 *           429 / 5xx / network -> toast (unless skipErrorToast)
 *
 * It rejects only with ApiError. On success, `response.data` is the unwrapped `ApiResponse.data`.
 */

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Do not send the Authorization header (/csrf, /login, /refresh). Also means a 401 is not a session end. */
    skipAuth?: boolean;
    /** Never run refresh-on-401 (/login, /refresh, /change-password: a 401 there is final). */
    skipRefresh?: boolean;
    /** The caller shows the error itself; no global toast for 429/5xx/network. */
    skipErrorToast?: boolean;
    /** Do not count this request in the global loading indicator. */
    background?: boolean;
    /** Internal: already retried after a refresh. */
    _authRetried?: boolean;
    /** Internal: already retried after a CSRF refetch. */
    _csrfRetried?: boolean;
    /** Internal: the access token this request was sent with. */
    _sentToken?: string | null;
  }
}

/** What the client needs from the app. Wired once in `app/apiBridge.ts`; no-ops until then. */
export interface ApiClientBridge {
  getAccessToken: () => string | null;
  /** A refresh succeeded: store the new tokens. */
  onRefreshed: (auth: AuthResponse) => void;
  /** The session is gone (refresh failed, revoked, reused): clear state; routes show the login page. */
  onSessionEnded: (reason: SessionEndReason) => void;
  /** 403 PASSWORD_CHANGE_REQUIRED: routes show the change-password page. */
  onPasswordChangeRequired: () => void;
  /** An authenticated request succeeded: the server has reset its idle timer. */
  onAuthenticatedActivity: () => void;
  showErrorToast: (message: string) => void;
  onRequestStart: () => void;
  onRequestEnd: () => void;
}

const noopBridge: ApiClientBridge = {
  getAccessToken: () => null,
  onRefreshed: () => undefined,
  onSessionEnded: () => undefined,
  onPasswordChangeRequired: () => undefined,
  onAuthenticatedActivity: () => undefined,
  showErrorToast: () => undefined,
  onRequestStart: () => undefined,
  onRequestEnd: () => undefined,
};

let bridge: ApiClientBridge = noopBridge;

export function configureApiClient(next: ApiClientBridge): void {
  bridge = next;
}

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  withCredentials: true,
  timeout: 30_000,
  headers: { Accept: 'application/json' },
});

const STATE_CHANGING_METHODS = new Set(['post', 'put', 'patch', 'delete']);

/** 401 codes that a refresh can fix. */
const REFRESHABLE_CODES = new Set<string>([ErrorCodes.TOKEN_EXPIRED, ErrorCodes.TOKEN_INVALID]);

// ---- CSRF -------------------------------------------------------------------------------------

let csrfInFlight: Promise<void> | null = null;

/** GET /csrf and keep the token in memory. Concurrent callers share one request. */
export function fetchCsrfToken(): Promise<void> {
  csrfInFlight ??= apiClient
    .get<CsrfResponse>(endpoints.auth.csrf, { skipAuth: true, background: true })
    .then((response) => setCsrfToken(response.data.token))
    .finally(() => {
      csrfInFlight = null;
    });
  return csrfInFlight;
}

// ---- Refresh ----------------------------------------------------------------------------------

async function postRefresh(): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>(endpoints.auth.refresh, undefined, {
    skipAuth: true,
    skipRefresh: true,
    skipErrorToast: true,
    background: true,
  });
  bridge.onRefreshed(response.data);
  return response.data;
}

/** Single-flight, cross-tab-safe refresh and the StrictMode-safe startup bootstrap. */
export const sessionCoordinator = createSessionCoordinator({
  fetchCsrf: fetchCsrfToken,
  refresh: postRefresh,
});

// ---- Interceptors -----------------------------------------------------------------------------

async function onRequest(config: InternalAxiosRequestConfig): Promise<InternalAxiosRequestConfig> {
  const headers = AxiosHeaders.from(config.headers);
  headers.set(CORRELATION_ID_HEADER, newCorrelationId());

  if (config.skipAuth) {
    headers.delete('Authorization');
  } else {
    const token = bridge.getAccessToken();
    config._sentToken = token;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    } else {
      headers.delete('Authorization');
    }
  }

  if (STATE_CHANGING_METHODS.has((config.method ?? 'get').toLowerCase())) {
    if (!getCsrfToken()) {
      await fetchCsrfToken();
    }
    const csrf = getCsrfToken();
    if (csrf) {
      headers.set(CSRF_HEADER, csrf);
    }
  }

  config.headers = headers;
  if (!config.background) {
    bridge.onRequestStart();
  }
  return encryptRequest(config);
}

function finishRequest(config: InternalAxiosRequestConfig | undefined): void {
  if (config && !config.background) {
    bridge.onRequestEnd();
  }
}

function onResponse(response: AxiosResponse): AxiosResponse {
  finishRequest(response.config);
  const decrypted = decryptResponse(response);
  const envelope = decrypted.data as ApiResponse<unknown> | undefined;
  if (decrypted.config.headers.has('Authorization')) {
    bridge.onAuthenticatedActivity();
  }
  // Unwrap the envelope: callers read `response.data` as the payload.
  decrypted.data =
    envelope && typeof envelope === 'object' && 'success' in envelope ? envelope.data : envelope;
  return decrypted;
}

async function onResponseError(error: unknown): Promise<AxiosResponse> {
  const config: InternalAxiosRequestConfig | undefined = axios.isAxiosError(error) ? error.config : undefined;
  finishRequest(config);
  const apiError = ApiError.from(error);
  if (!config) {
    throw apiError;
  }

  if (apiError.status === 401 && !config.skipAuth) {
    if (REFRESHABLE_CODES.has(apiError.code) && !config.skipRefresh && !config._authRetried) {
      config._authRetried = true;
      // Another request already refreshed while this one was in flight: just retry with the new token.
      if (bridge.getAccessToken() === config._sentToken) {
        try {
          await sessionCoordinator.refresh();
        } catch {
          bridge.onSessionEnded('expired');
          throw apiError;
        }
      }
      return apiClient.request(config);
    }
    // SESSION_INVALID, REFRESH_TOKEN_REUSED, UNAUTHORIZED, or a second 401: final.
    bridge.onSessionEnded(apiError.code === ErrorCodes.SESSION_INVALID ? 'revoked' : 'expired');
    throw apiError;
  }

  if (apiError.status === 403 && apiError.code === ErrorCodes.CSRF_INVALID && !config._csrfRetried) {
    config._csrfRetried = true;
    setCsrfToken(null);
    await fetchCsrfToken();
    return apiClient.request(config);
  }

  if (apiError.status === 403 && apiError.code === ErrorCodes.PASSWORD_CHANGE_REQUIRED) {
    bridge.onPasswordChangeRequired();
    throw apiError;
  }

  if (!config.skipErrorToast) {
    if (apiError.status === 429) {
      bridge.showErrorToast(
        apiError.retryAfterSeconds
          ? `Too many requests. Please try again in ${apiError.retryAfterSeconds} seconds.`
          : apiError.message,
      );
    } else if (apiError.status >= 500 || apiError.status === 0) {
      bridge.showErrorToast(
        apiError.correlationId
          ? `${apiError.message} (Reference: ${apiError.correlationId})`
          : apiError.message,
      );
    }
  }
  throw apiError;
}

apiClient.interceptors.request.use(onRequest);
apiClient.interceptors.response.use(onResponse, onResponseError);
