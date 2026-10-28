import { Navigate, Outlet, useLocation } from 'react-router';
import { useAppSelector } from '../app/hooks';
import { RoutePaths } from '../constants/routes';
import { selectAuthStatus, selectMustChangePassword } from '../features/auth/authSlice';

/** Location state the login page reads to return the user where they were. */
export interface LoginLocationState {
  from?: string;
}

/**
 * Child routes need a signed-in user. Routing follows auth state: when the session ends (logout,
 * failed refresh, another tab), this sends the user to /login; while the password must be changed
 * it sends every route to /change-password.
 */
export function ProtectedRoute() {
  const status = useAppSelector(selectAuthStatus);
  const mustChangePassword = useAppSelector(selectMustChangePassword);
  const location = useLocation();

  if (status !== 'authenticated') {
    const state: LoginLocationState = { from: location.pathname + location.search };
    return <Navigate to={RoutePaths.login} replace state={state} />;
  }
  if (mustChangePassword && location.pathname !== RoutePaths.changePassword) {
    return <Navigate to={RoutePaths.changePassword} replace />;
  }
  return <Outlet />;
}
