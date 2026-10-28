import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';

/**
 * Slot for the optional AES-GCM payload encryption (root CLAUDE.md: key exchanged per session,
 * on top of mandatory TLS). Not built yet: both hooks pass the payload through unchanged.
 *
 * When it is built:
 * - `encryptRequest` runs as the LAST request interceptor, after auth and CSRF headers, and
 *   encrypts `config.data` for endpoints that opt in (e.g. a `encrypt: true` request flag).
 * - `decryptResponse` runs as the FIRST response step, before the ApiResponse envelope is
 *   unwrapped, and decrypts `response.data`.
 * - The per-session key lives in memory only, next to the access token.
 */
export function encryptRequest(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  return config;
}

export function decryptResponse(response: AxiosResponse): AxiosResponse {
  return response;
}
