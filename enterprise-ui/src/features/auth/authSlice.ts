import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { ApiError, type ApiErrorData } from '../../api/apiError';
import { authService } from '../../api/authService';
import type {
  AuthResponse,
  AuthUser,
  ChangePasswordRequest,
  LoginRequest,
  LogoutAllResponse,
  MeResponse,
  SessionEndReason,
} from '../../types/auth';
import { postAuthMessage } from './authChannel';

export type AuthStatus = 'bootstrapping' | 'authenticated' | 'unauthenticated';

export interface AuthState {
  status: AuthStatus;
  user: AuthUser | null;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
  /** JWT access token. Held here, in memory only: never persisted, never in web storage. */
  accessToken: string | null;
  /** Access token expiry (ISO-8601 UTC). */
  expiresAt: string | null;
  /** Why the last session ended; shown once on the login page. */
  sessionEndReason: SessionEndReason | null;
  /** Epoch ms of the last authenticated request the server saw (drives the idle timeout). */
  lastActivityAt: number | null;
}

export const initialAuthState: AuthState = {
  status: 'bootstrapping',
  user: null,
  roles: [],
  permissions: [],
  mustChangePassword: false,
  accessToken: null,
  expiresAt: null,
  sessionEndReason: null,
  lastActivityAt: null,
};

type ThunkConfig = { rejectValue: ApiErrorData };

function toRejectValue(error: unknown): ApiErrorData {
  return ApiError.from(error).toData();
}

export const login = createAsyncThunk<AuthResponse, LoginRequest, ThunkConfig>(
  'auth/login',
  async (request, { rejectWithValue }) => {
    try {
      return await authService.login(request);
    } catch (error) {
      return rejectWithValue(toRejectValue(error));
    }
  },
);

export const changePassword = createAsyncThunk<AuthResponse, ChangePasswordRequest, ThunkConfig>(
  'auth/changePassword',
  async (request, { rejectWithValue }) => {
    try {
      const auth = await authService.changePassword(request);
      // The change revoked the old session; other tabs must refresh onto the new one.
      postAuthMessage({ type: 'session-renewed' });
      return auth;
    } catch (error) {
      return rejectWithValue(toRejectValue(error));
    }
  },
);

export const loadProfile = createAsyncThunk<MeResponse, undefined, ThunkConfig>(
  'auth/loadProfile',
  async (_, { rejectWithValue }) => {
    try {
      return await authService.me();
    } catch (error) {
      return rejectWithValue(toRejectValue(error));
    }
  },
);

export const logoutAll = createAsyncThunk<LogoutAllResponse, undefined, ThunkConfig>(
  'auth/logoutAll',
  async (_, { rejectWithValue }) => {
    try {
      return await authService.logoutAll();
    } catch (error) {
      return rejectWithValue(toRejectValue(error));
    }
  },
);

/**
 * Signs out: calls POST /logout (errors ignored), clears local state and tells the other tabs.
 * `reason` is 'idle' when the idle timeout fired.
 */
export const logout = createAsyncThunk<undefined, { reason?: 'signedOut' | 'idle' } | undefined>(
  'auth/logout',
  async (arg, { dispatch }) => {
    const reason = arg?.reason ?? 'signedOut';
    try {
      await authService.logout();
    } catch {
      // The session is ended locally anyway; the server ends it by idle or absolute timeout.
    }
    dispatch(sessionEnded(reason));
    postAuthMessage({ type: 'logout', reason });
  },
);

function applyAuthResponse(state: AuthState, auth: AuthResponse): void {
  state.status = 'authenticated';
  state.user = auth.user;
  state.roles = auth.roles;
  state.permissions = auth.permissions;
  state.mustChangePassword = auth.mustChangePassword;
  state.accessToken = auth.accessToken;
  state.expiresAt = auth.expiresAt;
  state.sessionEndReason = null;
  state.lastActivityAt = Date.now();
}

const authSlice = createSlice({
  name: 'auth',
  initialState: initialAuthState,
  reducers: {
    /** Login, refresh or password change returned fresh tokens. */
    sessionEstablished(state, action: PayloadAction<AuthResponse>) {
      applyAuthResponse(state, action.payload);
    },
    /** Startup found no session (no or invalid refresh cookie). */
    bootstrapCompleted(state) {
      if (state.status === 'bootstrapping') {
        state.status = 'unauthenticated';
      }
    },
    /** Clears everything except the reason, which the login page shows. */
    sessionEnded(state, action: PayloadAction<SessionEndReason | null>) {
      const wasSignedIn = state.status === 'authenticated';
      Object.assign(state, initialAuthState, {
        status: 'unauthenticated' as const,
        // Keep the first reason (e.g. 'expired') when several failing requests end the session.
        sessionEndReason: wasSignedIn ? action.payload : (state.sessionEndReason ?? action.payload),
      });
    },
    sessionEndReasonCleared(state) {
      state.sessionEndReason = null;
    },
    /** 403 PASSWORD_CHANGE_REQUIRED: the routes switch to the change-password screen. */
    passwordChangeRequired(state) {
      if (state.status === 'authenticated') {
        state.mustChangePassword = true;
      }
    },
    /** The server saw an authenticated request (this tab or another one). */
    activityRecorded(state, action: PayloadAction<number>) {
      if (state.status === 'authenticated' && action.payload > (state.lastActivityAt ?? 0)) {
        state.lastActivityAt = action.payload;
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(login.fulfilled, (state, action) => applyAuthResponse(state, action.payload))
      .addCase(changePassword.fulfilled, (state, action) => applyAuthResponse(state, action.payload))
      .addCase(loadProfile.fulfilled, (state, action) => {
        state.user = action.payload.user;
        state.roles = action.payload.roles;
        state.permissions = action.payload.permissions;
        state.mustChangePassword = action.payload.mustChangePassword;
      });
  },
  selectors: {
    selectAuthStatus: (state) => state.status,
    selectIsAuthenticated: (state) => state.status === 'authenticated',
    selectCurrentUser: (state) => state.user,
    selectRoles: (state) => state.roles,
    selectPermissions: (state) => state.permissions,
    selectMustChangePassword: (state) => state.mustChangePassword,
    selectAccessToken: (state) => state.accessToken,
    selectSessionEndReason: (state) => state.sessionEndReason,
    selectLastActivityAt: (state) => state.lastActivityAt,
    selectHasPermission: (state, permission: string) => state.permissions.includes(permission),
  },
});

export const {
  sessionEstablished,
  bootstrapCompleted,
  sessionEnded,
  sessionEndReasonCleared,
  passwordChangeRequired,
  activityRecorded,
} = authSlice.actions;

export const {
  selectAuthStatus,
  selectIsAuthenticated,
  selectCurrentUser,
  selectRoles,
  selectPermissions,
  selectMustChangePassword,
  selectAccessToken,
  selectSessionEndReason,
  selectLastActivityAt,
  selectHasPermission,
} = authSlice.selectors;

export default authSlice.reducer;
export { authSlice };
