import { Navigate, Outlet } from 'react-router';
import { RoutePaths } from '../constants/routes';
import { usePermission } from '../hooks/usePermission';

export interface PermissionRouteProps {
  /** Permission code the child routes need, e.g. `Permissions.ROLE_VIEW`. */
  permission: string;
}

/** Child routes need `permission`; without it the user sees the 403 page. */
export function PermissionRoute({ permission }: PermissionRouteProps) {
  return usePermission(permission) ? <Outlet /> : <Navigate to={RoutePaths.forbidden} replace />;
}
