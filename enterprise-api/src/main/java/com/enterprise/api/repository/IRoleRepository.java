package com.enterprise.api.repository;

import java.util.List;

import com.enterprise.api.entity.Role;
import com.enterprise.api.entity.RoleDetail;
import com.enterprise.api.entity.RoleListItem;
import com.enterprise.api.repository.support.PageQuery;
import com.enterprise.api.repository.support.PagedResult;

public interface IRoleRepository {

    /** {@code usp_Role_GetAll}: non-deleted roles ordered by name. */
    List<Role> findAll(boolean includeInactive);

    /** {@code usp_Role_GetPaged}; {@code isActive} null means all. */
    PagedResult<RoleListItem> findPaged(PageQuery query, Boolean isActive);

    /** {@code usp_Role_GetById}: the role and its permissions (50403 if not found or deleted). */
    RoleDetail findById(long id);

    /** {@code usp_Role_Create}: returns the new id (50402 name taken, 50501 unknown permission). */
    long create(String name, String description, boolean isActive, List<Long> permissionIds, long actorUserId,
                String ipAddress);

    /** {@code usp_Role_Update}: 50002 when {@code rowVersion} is stale, 50401 for system roles. */
    void update(long id, String name, String description, boolean isActive, byte[] rowVersion, long actorUserId,
                String ipAddress);

    /** {@code usp_Role_SoftDelete}: also removes the role from all users. {@code rowVersion} may be null. */
    void softDelete(long id, byte[] rowVersion, long actorUserId, String ipAddress);

    /** {@code usp_RolePermission_Assign}: no-op when already assigned. */
    void assignPermission(long roleId, long permissionId, long actorUserId, String ipAddress);

    /** {@code usp_RolePermission_Revoke}: no-op when not assigned; 50502 for ROLE_MANAGE on Admin. */
    void revokePermission(long roleId, long permissionId, long actorUserId, String ipAddress);
}
