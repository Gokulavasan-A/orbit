package com.enterprise.api.repository.impl;

import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;

import com.enterprise.api.entity.Role;
import com.enterprise.api.entity.RoleDetail;
import com.enterprise.api.entity.RoleListItem;
import com.enterprise.api.entity.RolePermissionAssignment;
import com.enterprise.api.exception.TechnicalException;
import com.enterprise.api.repository.IRoleRepository;
import com.enterprise.api.repository.support.PageQuery;
import com.enterprise.api.repository.support.PagedResult;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import com.enterprise.api.repository.support.StoredProcedureResult;
import com.enterprise.api.util.SqlRowUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepositoryImpl implements IRoleRepository {

    private static final String ROWS = "rows";
    private static final String RESULT = "result";
    private static final String PERMISSIONS = "permissions";

    private static final RowMapper<Role> ROLE_MAPPER = (rs, rowNum) -> {
        Role role = new Role();
        role.setId(rs.getLong("Id"));
        role.setName(rs.getString("Name"));
        role.setDescription(rs.getString("Description"));
        role.setSystem(rs.getBoolean("IsSystem"));
        role.setActive(rs.getBoolean("IsActive"));
        role.setRowVersion(rs.getBytes("RowVersion"));
        return role;
    };

    private static final RowMapper<RoleListItem> LIST_ITEM_MAPPER = (rs, rowNum) -> {
        RoleListItem item = new RoleListItem();
        item.setId(rs.getLong("Id"));
        item.setName(rs.getString("Name"));
        item.setDescription(rs.getString("Description"));
        item.setSystem(rs.getBoolean("IsSystem"));
        item.setActive(rs.getBoolean("IsActive"));
        item.setCreatedOn(SqlRowUtils.getInstant(rs, "CreatedOn"));
        item.setModifiedOn(SqlRowUtils.getInstant(rs, "ModifiedOn"));
        item.setUserCount(rs.getInt("UserCount"));
        item.setPermissionCount(rs.getInt("PermissionCount"));
        item.setPermissions(rs.getString("Permissions"));
        return item;
    };

    private static final RowMapper<RoleDetail> DETAIL_MAPPER = (rs, rowNum) -> {
        RoleDetail role = new RoleDetail();
        role.setId(rs.getLong("Id"));
        role.setName(rs.getString("Name"));
        role.setDescription(rs.getString("Description"));
        role.setSystem(rs.getBoolean("IsSystem"));
        role.setActive(rs.getBoolean("IsActive"));
        role.setUserCount(rs.getInt("UserCount"));
        role.setCreatedOn(SqlRowUtils.getInstant(rs, "CreatedOn"));
        role.setModifiedOn(SqlRowUtils.getInstant(rs, "ModifiedOn"));
        role.setRowVersion(rs.getBytes("RowVersion"));
        return role;
    };

    private static final RowMapper<RolePermissionAssignment> PERMISSION_MAPPER = (rs, rowNum) -> {
        RolePermissionAssignment permission = new RolePermissionAssignment();
        permission.setPermissionId(rs.getLong("PermissionId"));
        permission.setPermissionCode(rs.getString("PermissionCode"));
        return permission;
    };

    private static final RowMapper<Long> ID_MAPPER = (rs, rowNum) -> rs.getLong("Id");

    private final StoredProcedureExecutor executor;

    public RoleRepositoryImpl(StoredProcedureExecutor executor) {
        this.executor = executor;
    }

    @Override
    public List<Role> findAll(boolean includeInactive) {
        return executor.call("usp_Role_GetAll")
                .in("IncludeInactive", Types.BIT, includeInactive)
                .resultSet(ROWS, ROLE_MAPPER)
                .execute()
                .rows(ROWS);
    }

    @Override
    public PagedResult<RoleListItem> findPaged(PageQuery query, Boolean isActive) {
        return executor.call("usp_Role_GetPaged")
                .in("PageNumber", Types.INTEGER, query.page())
                .in("PageSize", Types.INTEGER, query.pageSize())
                .in("SortColumn", Types.NVARCHAR, query.sortColumn())
                .in("SortDirection", Types.NVARCHAR, query.sortDirection())
                .in("SearchText", Types.NVARCHAR, query.searchText())
                .in("IsActive", Types.BIT, isActive)
                .pagedResultSet(ROWS, LIST_ITEM_MAPPER)
                .execute()
                .paged(ROWS);
    }

    @Override
    public RoleDetail findById(long id) {
        StoredProcedureResult result = executor.call("usp_Role_GetById")
                .in("Id", Types.BIGINT, id)
                .resultSet(RESULT, DETAIL_MAPPER)
                .resultSet(PERMISSIONS, PERMISSION_MAPPER)
                .execute();
        RoleDetail role = result.<RoleDetail>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_Role_GetById returned no row", null, null));
        role.setPermissions(result.rows(PERMISSIONS));
        return role;
    }

    @Override
    public long create(String name, String description, boolean isActive, List<Long> permissionIds,
                       long actorUserId, String ipAddress) {
        return executor.call("usp_Role_Create")
                .in("Name", Types.NVARCHAR, name)
                .in("Description", Types.NVARCHAR, description)
                .in("IsActive", Types.BIT, isActive)
                .in("PermissionIds", Types.NVARCHAR, toCsv(permissionIds))
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .resultSet(RESULT, ID_MAPPER)
                .execute()
                .<Long>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_Role_Create returned no row", null, null));
    }

    @Override
    public void update(long id, String name, String description, boolean isActive, byte[] rowVersion,
                       long actorUserId, String ipAddress) {
        // The (Id, RowVersion) result set is skipped; callers re-read the role.
        executor.call("usp_Role_Update")
                .in("Id", Types.BIGINT, id)
                .in("Name", Types.NVARCHAR, name)
                .in("Description", Types.NVARCHAR, description)
                .in("IsActive", Types.BIT, isActive)
                .in("RowVersion", Types.BINARY, rowVersion)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .execute();
    }

    @Override
    public void softDelete(long id, byte[] rowVersion, long actorUserId, String ipAddress) {
        executor.call("usp_Role_SoftDelete")
                .in("Id", Types.BIGINT, id)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("RowVersion", Types.BINARY, rowVersion)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .execute();
    }

    @Override
    public void assignPermission(long roleId, long permissionId, long actorUserId, String ipAddress) {
        executor.call("usp_RolePermission_Assign")
                .in("RoleId", Types.BIGINT, roleId)
                .in("PermissionId", Types.BIGINT, permissionId)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .execute();
    }

    @Override
    public void revokePermission(long roleId, long permissionId, long actorUserId, String ipAddress) {
        executor.call("usp_RolePermission_Revoke")
                .in("RoleId", Types.BIGINT, roleId)
                .in("PermissionId", Types.BIGINT, permissionId)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .execute();
    }

    private static String toCsv(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
