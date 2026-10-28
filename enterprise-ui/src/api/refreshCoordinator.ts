import type { AuthResponse } from '../types/auth';
import { ApiError, ErrorCodes } from './apiError';

/**
 * Refresh tokens are single-use: if the same refresh cookie is presented twice, the API treats
 * it as theft and signs out every session of the user (REFRESH_TOKEN_REUSED). This coordinator
 * guarantees that at most one refresh runs at a time:
 *
 * - in this tab: concurrent callers share one in-flight promise (single-flight);
 * - across tabs: the request runs inside a Web Lock, so another tab waits and then sends the
 *   already-rotated cookie;
 * - at startup: `bootstrap()` caches its promise, so React StrictMode's second effect run (or any
 *   concurrent caller) joins the first run instead of sending the cookie again.
 */
export interface SessionCoordinatorDeps {
  /** GET /csrf and store the token in memory. */
  fetchCsrf: () => Promise<void>;
  /** POST /refresh. Resolves with the new tokens; rejects with ApiError. */
  refresh: () => Promise<AuthResponse>;
  /** Web Lock name shared by all tabs of this origin. */
  lockName?: string;
}

export interface SessionCoordinator {
  /** Runs one refresh, or joins the one already running. */
  refresh: () => Promise<AuthResponse>;
  /**
   * App start: GET /csrf, then a silent refresh. Resolves with the session, or `null` when there
   * is none (401). Other failures reject. Called any number of times, it runs only once.
   */
  bootstrap: () => Promise<AuthResponse | null>;
  /** Forgets the cached bootstrap result (tests, or a later re-bootstrap). */
  reset: () => void;
}

const DEFAULT_LOCK_NAME = 'enterprise-auth-refresh';

/** Runs `task` while holding a cross-tab Web Lock, or directly where Web Locks are missing. */
async function withCrossTabLock<T>(name: string, task: () => Promise<T>): Promise<T> {
  // `navigator.locks` is missing in old browsers, in insecure contexts and in jsdom.
  const locks = typeof navigator === 'undefined' ? undefined : (navigator.locks as LockManager | undefined);
  if (!locks) {
    return task();
  }
  return locks.request(name, task);
}

export function createSessionCoordinator(deps: SessionCoordinatorDeps): SessionCoordinator {
  const lockName = deps.lockName ?? DEFAULT_LOCK_NAME;
  let refreshInFlight: Promise<AuthResponse> | null = null;
  let bootstrapPromise: Promise<AuthResponse | null> | null = null;

  const refresh = (): Promise<AuthResponse> => {
    refreshInFlight ??= withCrossTabLock(lockName, deps.refresh).finally(() => {
      refreshInFlight = null;
    });
    return refreshInFlight;
  };

  const bootstrap = (): Promise<AuthResponse | null> => {
    bootstrapPromise ??= (async () => {
      await deps.fetchCsrf();
      try {
        return await refresh();
      } catch (error) {
        const apiError = ApiError.from(error);
        // No (or no longer valid) refresh cookie: simply signed out.
        if (apiError.status === 401 || apiError.code === ErrorCodes.SESSION_INVALID) {
          return null;
        }
        throw apiError;
      }
    })();
    return bootstrapPromise;
  };

  const reset = () => {
    bootstrapPromise = null;
    refreshInFlight = null;
  };

  return { refresh, bootstrap, reset };
}
