import type { ReactNode } from 'react';
import { usePermission } from '../../hooks/usePermission';

export interface PermissionGateProps {
  /** Permission code, e.g. `Permissions.ROLE_MANAGE`. */
  permission: string;
  children: ReactNode;
  /** Rendered without the permission. @default null */
  fallback?: ReactNode;
}

/**
 * Renders `children` only when the user has `permission`. This only hides UI: the API enforces
 * every permission anyway.
 */
export function PermissionGate({ permission, children, fallback = null }: PermissionGateProps) {
  return usePermission(permission) ? children : fallback;
}
