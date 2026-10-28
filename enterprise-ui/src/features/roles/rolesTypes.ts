/** Row of GET /api/v1/roles/paged. Field names are the SP's sort column ids. */
export interface RoleListItem {
  id: number;
  name: string;
  description: string | null;
  isSystem: boolean;
  isActive: boolean;
  /** ISO-8601 UTC. */
  createdOn: string;
  modifiedOn: string | null;
  userCount: number;
  permissionCount: number;
  permissions: string[];
}

/** Item of GET /api/v1/roles. */
export interface RoleOption {
  id: number;
  name: string;
  description: string | null;
  isSystem: boolean;
  isActive: boolean;
  /** Base64 RowVersion, sent back on update/delete. */
  rowVersion: string;
}
