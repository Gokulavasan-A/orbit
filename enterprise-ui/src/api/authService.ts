import type {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  LogoutAllResponse,
  MeResponse,
} from '../types/auth';
import { apiClient, sessionCoordinator } from './axiosClient';
import { endpoints } from './endpoints';

/**
 * Calls to /api/v1/auth. Startup (GET /csrf + silent refresh) and refresh-on-401 go through
 * `sessionCoordinator`, never directly, so the single-use refresh cookie is sent only once.
 */
export const authService = {
  async login(request: LoginRequest): Promise<AuthResponse> {
    // The login page shows 401/423/429 inline, so no global toast.
    const response = await apiClient.post<AuthResponse>(endpoints.auth.login, request, {
      skipAuth: true,
      skipRefresh: true,
      skipErrorToast: true,
    });
    return response.data;
  },

  /** Restores the session from the refresh cookie at app start; `null` when signed out. */
  bootstrap(): Promise<AuthResponse | null> {
    return sessionCoordinator.bootstrap();
  },

  /** Lets `bootstrap()` run again (the "Try again" button after a failed start). */
  resetBootstrap(): void {
    sessionCoordinator.reset();
  },

  /** One shared refresh (single-flight, cross-tab locked). */
  refresh(): Promise<AuthResponse> {
    return sessionCoordinator.refresh();
  },

  async me(): Promise<MeResponse> {
    const response = await apiClient.get<MeResponse>(endpoints.auth.me);
    return response.data;
  },

  async changePassword(request: ChangePasswordRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>(endpoints.auth.changePassword, request, {
      skipRefresh: true,
    });
    return response.data;
  },

  async logout(): Promise<void> {
    // State is cleared locally whatever happens, so failures are not shown.
    await apiClient.post(endpoints.auth.logout, undefined, { skipErrorToast: true });
  },

  /** Signs out every other session of the user; the current one stays. */
  async logoutAll(): Promise<LogoutAllResponse> {
    const response = await apiClient.post<LogoutAllResponse>(endpoints.auth.logoutAll);
    return response.data;
  },
};
