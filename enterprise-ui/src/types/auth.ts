export interface AuthUser {
  id: number;
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
}

/** `data` of POST /login, /refresh and /change-password. */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** ISO-8601 UTC. */
  expiresAt: string;
  expiresIn: number;
  user: AuthUser;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
}

/** `data` of GET /me. */
export interface MeResponse {
  user: AuthUser;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
}

/** `data` of GET /csrf. */
export interface CsrfResponse {
  headerName: string;
  token: string;
}

/** `data` of POST /logout-all. */
export interface LogoutAllResponse {
  sessionsRevoked: number;
}

/**
 * Why the session ended, shown on the login page:
 * expired (refresh failed), revoked (signed out elsewhere, deactivated, password changed),
 * idle (UI idle timeout), signedOut (the user signed out, here or in another tab).
 */
export type SessionEndReason = 'expired' | 'revoked' | 'idle' | 'signedOut';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}
