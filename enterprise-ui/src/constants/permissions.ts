/**
 * Permission codes as the API returns them in `permissions` (seeded in enterprise-db V11).
 * The API turns them into `PERM_<code>` authorities; the UI uses the bare code.
 */
export const Permissions = {
  USER_VIEW: 'USER_VIEW',
  USER_CREATE: 'USER_CREATE',
  USER_EDIT: 'USER_EDIT',
  USER_DELETE: 'USER_DELETE',
  ROLE_VIEW: 'ROLE_VIEW',
  ROLE_MANAGE: 'ROLE_MANAGE',
} as const;

export type PermissionCode = (typeof Permissions)[keyof typeof Permissions];
