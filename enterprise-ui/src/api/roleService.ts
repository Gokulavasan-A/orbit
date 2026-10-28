import type { RoleListItem, RoleOption } from '../features/roles/rolesTypes';
import type { PageQuery, PagedResponse } from '../types/api';
import { apiClient } from './axiosClient';
import { endpoints } from './endpoints';

/** Calls to /api/v1/roles. Every endpoint needs the ROLE_VIEW permission. */
export const roleService = {
  /** One page of the roles grid. `sortColumn` is the grid column id (e.g. `userCount`). */
  async getPaged(query: PageQuery & { isActive?: boolean }): Promise<PagedResponse<RoleListItem>> {
    const response = await apiClient.get<PagedResponse<RoleListItem>>(endpoints.roles.paged, {
      params: query,
      background: true,
    });
    return response.data;
  },

  /** All roles, for lists and dropdowns. */
  async getAll(includeInactive = false): Promise<RoleOption[]> {
    const response = await apiClient.get<RoleOption[]>(endpoints.roles.all, { params: { includeInactive } });
    return response.data;
  },
};
