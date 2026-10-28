import { useAppSelector } from '../app/hooks';
import { selectPermissions } from '../features/auth/authSlice';

/** True when the signed-in user has the permission code (e.g. `ROLE_VIEW`). */
export function usePermission(permission: string): boolean {
  return useAppSelector(selectPermissions).includes(permission);
}

/** True when the user has at least one of the codes (true for an empty list). */
export function useAnyPermission(permissions: readonly string[]): boolean {
  const granted = useAppSelector(selectPermissions);
  return permissions.length === 0 || permissions.some((p) => granted.includes(p));
}
