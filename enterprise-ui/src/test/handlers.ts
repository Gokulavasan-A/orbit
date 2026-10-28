import { HttpResponse } from 'msw';
import type { FieldErrorDetail } from '../types/api';
import type { AuthResponse } from '../types/auth';

/** Base URL the tests run against (vitest.config.ts sets VITE_API_BASE_URL). */
export const API = 'http://api.test';

export function okEnvelope(data: unknown, init?: ResponseInit) {
  return HttpResponse.json(
    { success: true, data, error: null, correlationId: 'corr-ok', timestamp: new Date().toISOString() },
    init,
  );
}

export function errorEnvelope(
  status: number,
  code: string,
  message = code,
  options: { fieldErrors?: FieldErrorDetail[]; headers?: Record<string, string> } = {},
) {
  return HttpResponse.json(
    {
      success: false,
      data: null,
      error: { code, message, fieldErrors: options.fieldErrors ?? [] },
      correlationId: 'corr-err',
      timestamp: new Date().toISOString(),
    },
    { status, headers: options.headers },
  );
}

export function authResponse(overrides: Partial<AuthResponse> = {}): AuthResponse {
  return {
    accessToken: 'access-token',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    expiresIn: 900,
    user: { id: 1, username: 'admin', email: 'admin@example.com', firstName: 'Ada', lastName: 'Admin' },
    roles: ['Admin'],
    permissions: ['ROLE_VIEW'],
    mustChangePassword: false,
    ...overrides,
  };
}

export const meResponse = (auth: AuthResponse = authResponse()) => ({
  user: auth.user,
  roles: auth.roles,
  permissions: auth.permissions,
  mustChangePassword: auth.mustChangePassword,
});
