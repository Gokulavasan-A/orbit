import type { ColDef, ValueFormatterParams } from 'ag-grid-community';
import { roleService } from '../../../api/roleService';
import { AppGrid, type AppGridSort } from '../../../components/common/AppGrid';
import { PageHeader } from '../../../components/common/PageHeader';
import type { PageQuery, PagedResponse } from '../../../types/api';
import type { RoleListItem } from '../rolesTypes';

const yesNo = (params: ValueFormatterParams<RoleListItem, boolean>) => (params.value ? 'Yes' : 'No');
const activeText = (params: ValueFormatterParams<RoleListItem, boolean>) =>
  params.value ? 'Active' : 'Inactive';

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const dateTime = (params: ValueFormatterParams<RoleListItem, string | null>) =>
  params.value ? dateFormat.format(new Date(params.value)) : '—';

/** `field` values are the sort column ids usp_Role_GetPaged accepts. */
const columns: ColDef<RoleListItem>[] = [
  { field: 'name', headerName: 'Name', minWidth: 160, flex: 1 },
  { field: 'description', headerName: 'Description', minWidth: 220, flex: 2 },
  { field: 'isSystem', headerName: 'System', width: 110, valueFormatter: yesNo },
  { field: 'isActive', headerName: 'Status', width: 120, valueFormatter: activeText },
  { field: 'userCount', headerName: 'Users', width: 110, type: 'numericColumn' },
  { field: 'permissionCount', headerName: 'Permissions', width: 140, type: 'numericColumn' },
  { field: 'modifiedOn', headerName: 'Last modified', width: 190, valueFormatter: dateTime },
];

const defaultSort: AppGridSort = { column: 'name', direction: 'ASC' };

// Module-level, so the function identity is stable and AppGrid does not reload on render.
const fetchRoles = (query: PageQuery): Promise<PagedResponse<RoleListItem>> => roleService.getPaged(query);

/** Read-only roles list (GET /api/v1/roles/paged; requires ROLE_VIEW). */
export default function RolesPage() {
  return (
    <>
      <PageHeader title="Roles" subtitle="Roles group the permissions that are granted to users." />
      <AppGrid<RoleListItem>
        ariaLabel="roles"
        columnDefs={columns}
        fetchPage={fetchRoles}
        getRowId={(role) => String(role.id)}
        defaultSort={defaultSort}
        searchPlaceholder="Search roles"
        emptyMessage="No roles match your search."
      />
    </>
  );
}
