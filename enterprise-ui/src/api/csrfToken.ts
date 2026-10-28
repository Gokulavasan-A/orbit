/**
 * The CSRF token from `GET /csrf` (`data.token`), held in memory only. The API sets the
 * `XSRF-TOKEN` cookie as well, but that cookie belongs to the API host, so the UI uses the
 * response body instead (see security.md).
 */
let csrfToken: string | null = null;

export const CSRF_HEADER = 'X-XSRF-TOKEN';

export function getCsrfToken(): string | null {
  return csrfToken;
}

export function setCsrfToken(token: string | null): void {
  csrfToken = token;
}
