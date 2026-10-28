/**
 * Every API URL, relative to VITE_API_BASE_URL. Services and the axios client import URLs from
 * here only; never write an API path anywhere else.
 */
export const endpoints = {
  auth: {
    csrf: '/api/v1/auth/csrf',
    login: '/api/v1/auth/login',
    refresh: '/api/v1/auth/refresh',
    logout: '/api/v1/auth/logout',
    logoutAll: '/api/v1/auth/logout-all',
    me: '/api/v1/auth/me',
    changePassword: '/api/v1/auth/change-password',
  },
  roles: {
    all: '/api/v1/roles',
    paged: '/api/v1/roles/paged',
  },
} as const;
