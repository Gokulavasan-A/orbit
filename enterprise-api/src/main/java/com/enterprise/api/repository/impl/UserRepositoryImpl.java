package com.enterprise.api.repository.impl;

import java.sql.Types;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.enterprise.api.entity.PasswordChangeResult;
import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.entity.UserListItem;
import com.enterprise.api.entity.UserRoleAssignment;
import com.enterprise.api.exception.TechnicalException;
import com.enterprise.api.repository.IUserRepository;
import com.enterprise.api.repository.support.PageQuery;
import com.enterprise.api.repository.support.PagedResult;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import com.enterprise.api.repository.support.StoredProcedureResult;
import com.enterprise.api.util.SqlRowUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryImpl implements IUserRepository {

    private static final String RESULT = "result";
    private static final String ROLES = "roles";
    private static final String ROWS = "rows";

    private static final RowMapper<UserDetail> USER_MAPPER = (rs, rowNum) -> {
        UserDetail user = new UserDetail();
        user.setId(rs.getLong("Id"));
        user.setUsername(rs.getString("Username"));
        user.setEmail(rs.getString("Email"));
        user.setFirstName(rs.getString("FirstName"));
        user.setLastName(rs.getString("LastName"));
        user.setPhoneNumber(rs.getString("PhoneNumber"));
        user.setActive(rs.getBoolean("IsActive"));
        user.setLockedOut(rs.getBoolean("IsLockedOut"));
        user.setLastLoginOn(SqlRowUtils.getInstant(rs, "LastLoginOn"));
        user.setMustChangePassword(rs.getBoolean("MustChangePassword"));
        user.setPasswordChangedOn(SqlRowUtils.getInstant(rs, "PasswordChangedOn"));
        user.setCreatedOn(SqlRowUtils.getInstant(rs, "CreatedOn"));
        user.setModifiedOn(SqlRowUtils.getInstant(rs, "ModifiedOn"));
        user.setConcurrencyStamp(SqlRowUtils.getUuid(rs, "ConcurrencyStamp"));
        return user;
    };

    private static final RowMapper<UserRoleAssignment> ROLE_MAPPER = (rs, rowNum) -> {
        UserRoleAssignment role = new UserRoleAssignment();
        role.setRoleId(rs.getLong("RoleId"));
        role.setRoleName(rs.getString("RoleName"));
        role.setActive(rs.getBoolean("IsActive"));
        return role;
    };

    private static final RowMapper<UserListItem> LIST_ITEM_MAPPER = (rs, rowNum) -> {
        UserListItem item = new UserListItem();
        item.setId(rs.getLong("Id"));
        item.setUsername(rs.getString("Username"));
        item.setEmail(rs.getString("Email"));
        item.setFirstName(rs.getString("FirstName"));
        item.setLastName(rs.getString("LastName"));
        item.setPhoneNumber(rs.getString("PhoneNumber"));
        item.setActive(rs.getBoolean("IsActive"));
        item.setLockedOut(rs.getBoolean("IsLockedOut"));
        item.setLastLoginOn(SqlRowUtils.getInstant(rs, "LastLoginOn"));
        item.setCreatedOn(SqlRowUtils.getInstant(rs, "CreatedOn"));
        item.setModifiedOn(SqlRowUtils.getInstant(rs, "ModifiedOn"));
        item.setRoles(rs.getString("Roles"));
        item.setConcurrencyStamp(SqlRowUtils.getUuid(rs, "ConcurrencyStamp"));
        return item;
    };

    private static final RowMapper<PasswordChangeResult> PASSWORD_MAPPER = (rs, rowNum) -> {
        PasswordChangeResult result = new PasswordChangeResult();
        result.setId(rs.getLong("Id"));
        result.setRowVersion(rs.getBytes("RowVersion"));
        result.setPasswordChangedOn(SqlRowUtils.getInstant(rs, "PasswordChangedOn"));
        result.setSessionsRevoked(rs.getInt("SessionsRevoked"));
        result.setTokensRevoked(rs.getInt("TokensRevoked"));
        return result;
    };

    private static final RowMapper<Long> ID_MAPPER = (rs, rowNum) -> rs.getLong("Id");

    private static final RowMapper<UUID> STAMP_MAPPER = (rs, rowNum) -> SqlRowUtils.getUuid(rs, "ConcurrencyStamp");

    private final StoredProcedureExecutor executor;

    public UserRepositoryImpl(StoredProcedureExecutor executor) {
        this.executor = executor;
    }

    @Override
    public UserDetail findById(long id) {
        StoredProcedureResult result = executor.call("usp_User_GetById")
                .in("Id", Types.BIGINT, id)
                .resultSet(RESULT, USER_MAPPER)
                .resultSet(ROLES, ROLE_MAPPER)
                .execute();
        UserDetail user = result.<UserDetail>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_User_GetById returned no row", null, null));
        user.setRoles(result.rows(ROLES));
        return user;
    }

    @Override
    public PagedResult<UserListItem> findPaged(PageQuery query, Boolean isActive, Long roleId) {
        return executor.call("usp_User_GetPaged")
                .in("PageNumber", Types.INTEGER, query.page())
                .in("PageSize", Types.INTEGER, query.pageSize())
                .in("SortColumn", Types.NVARCHAR, query.sortColumn())
                .in("SortDirection", Types.NVARCHAR, query.sortDirection())
                .in("SearchText", Types.NVARCHAR, query.searchText())
                .in("IsActive", Types.BIT, isActive)
                .in("RoleId", Types.BIGINT, roleId)
                .pagedResultSet(ROWS, LIST_ITEM_MAPPER)
                .execute()
                .paged(ROWS);
    }

    @Override
    public long create(String username, String email, String passwordHash, String firstName, String lastName,
                       String phoneNumber, List<Long> roleIds, long actorUserId, String ipAddress) {
        return executor.call("usp_User_Create")
                .in("Username", Types.NVARCHAR, username)
                .in("Email", Types.NVARCHAR, email)
                .in("PasswordHash", Types.NVARCHAR, passwordHash)
                .in("FirstName", Types.NVARCHAR, firstName)
                .in("LastName", Types.NVARCHAR, lastName)
                .in("PhoneNumber", Types.NVARCHAR, phoneNumber)
                .in("IsActive", Types.BIT, true)
                .in("MustChangePassword", Types.BIT, true)
                .in("RoleIds", Types.NVARCHAR, toCsv(roleIds))
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .resultSet(RESULT, ID_MAPPER)
                .execute()
                .<Long>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_User_Create returned no row", null, null));
    }

    @Override
    public UUID update(long id, String email, String firstName, String lastName, String phoneNumber,
                       boolean isActive, UUID concurrencyStamp, long actorUserId, String ipAddress) {
        return executor.call("usp_User_Update")
                .in("Id", Types.BIGINT, id)
                .in("Email", Types.NVARCHAR, email)
                .in("FirstName", Types.NVARCHAR, firstName)
                .in("LastName", Types.NVARCHAR, lastName)
                .in("PhoneNumber", Types.NVARCHAR, phoneNumber)
                .in("IsActive", Types.BIT, isActive)
                .in("ConcurrencyStamp", Types.NVARCHAR, SqlRowUtils.toSqlUuid(concurrencyStamp))
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .resultSet(RESULT, STAMP_MAPPER)
                .execute()
                .<UUID>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_User_Update returned no row", null, null));
    }

    @Override
    public void softDelete(long id, UUID concurrencyStamp, long actorUserId, String ipAddress) {
        executor.call("usp_User_SoftDelete")
                .in("Id", Types.BIGINT, id)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("ConcurrencyStamp", Types.NVARCHAR, SqlRowUtils.toSqlUuid(concurrencyStamp))
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .execute();
    }

    @Override
    public PasswordChangeResult changePassword(long userId, String newPasswordHash, UUID currentSessionId,
                                               long actorUserId, String ipAddress) {
        return executor.call("usp_User_ChangePassword")
                .in("UserId", Types.BIGINT, userId)
                .in("NewPasswordHash", Types.NVARCHAR, newPasswordHash)
                .in("CurrentSessionId", Types.NVARCHAR, SqlRowUtils.toSqlUuid(currentSessionId))
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .resultSet(RESULT, PASSWORD_MAPPER)
                .execute()
                .<PasswordChangeResult>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_User_ChangePassword returned no row", null, null));
    }

    @Override
    public PasswordChangeResult resetPassword(long userId, String newPasswordHash, long actorUserId,
                                              String ipAddress) {
        return executor.call("usp_User_ResetPassword")
                .in("UserId", Types.BIGINT, userId)
                .in("NewPasswordHash", Types.NVARCHAR, newPasswordHash)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .resultSet(RESULT, PASSWORD_MAPPER)
                .execute()
                .<PasswordChangeResult>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_User_ResetPassword returned no row", null, null));
    }

    @Override
    public void unlock(long userId, long actorUserId, String ipAddress) {
        // The Id result set is not needed and is skipped.
        executor.call("usp_User_Unlock")
                .in("UserId", Types.BIGINT, userId)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .execute();
    }

    @Override
    public void assignRole(long userId, long roleId, long actorUserId, String ipAddress) {
        executor.call("usp_UserRole_Assign")
                .in("UserId", Types.BIGINT, userId)
                .in("RoleId", Types.BIGINT, roleId)
                .in("ActorUserId", Types.BIGINT, actorUserId)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .withCorrelationId()
                .execute();
    }

    @Override
    public void revokeRole(long userId, long roleId, long actorUserId, String ipAddress) {
        executor.call("usp_UserRole_Revoke")
                .in("UserId", Types.BIGINT, userId)
                .in("RoleId", Types.BIGINT, roleId)
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
