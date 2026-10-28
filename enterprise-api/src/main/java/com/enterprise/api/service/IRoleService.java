package com.enterprise.api.service;

import java.util.List;

import com.enterprise.api.model.request.CreateRoleRequest;
import com.enterprise.api.model.request.RolePageRequest;
import com.enterprise.api.model.request.UpdateRolePermissionsRequest;
import com.enterprise.api.model.request.UpdateRoleRequest;
import com.enterprise.api.model.response.PagedResponse;
import com.enterprise.api.model.response.RoleDetailResponse;
import com.enterprise.api.model.response.RoleListItemResponse;
import com.enterprise.api.model.response.RoleResponse;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.CurrentUser;

public interface IRoleService {

    List<RoleResponse> getAll(boolean includeInactive);

    PagedResponse<RoleListItemResponse> getPaged(RolePageRequest request);

    RoleDetailResponse getById(long id);

    RoleDetailResponse create(CreateRoleRequest request, CurrentUser actor, ClientInfo client);

    /** Name, description and active flag; deactivating ends the sessions of the role's users. */
    RoleDetailResponse update(long id, UpdateRoleRequest request, CurrentUser actor, ClientInfo client);

    /** Sets the full permission list; removing any permission ends the sessions of the role's users. */
    RoleDetailResponse updatePermissions(long id, UpdateRolePermissionsRequest request, CurrentUser actor,
                                         ClientInfo client);

    /** Soft delete (system roles are refused); ends the sessions of the role's users. */
    void delete(long id, String rowVersion, CurrentUser actor, ClientInfo client);
}
