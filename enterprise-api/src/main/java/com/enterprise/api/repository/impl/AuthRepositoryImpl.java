package com.enterprise.api.repository.impl;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

import com.enterprise.api.entity.LoginAttemptResult;
import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.repository.IAuthRepository;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import com.enterprise.api.repository.support.StoredProcedureResult;
import com.enterprise.api.util.SqlRowUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepositoryImpl implements IAuthRepository {

    private static final String USER = "user";
    private static final String ROLES = "roles";
    private static final String PERMISSIONS = "permissions";
    private static final String RESULT = "result";

    private static final RowMapper<LoginUser> USER_MAPPER = (rs, rowNum) -> {
        LoginUser user = new LoginUser();
        user.setId(rs.getLong("Id"));
        user.setUsername(rs.getString("Username"));
        user.setEmail(rs.getString("Email"));
        user.setPasswordHash(rs.getString("PasswordHash"));
        user.setFirstName(rs.getString("FirstName"));
        user.setLastName(rs.getString("LastName"));
        user.setActive(rs.getBoolean("IsActive"));
        user.setFailedLoginCount(rs.getInt("FailedLoginCount"));
        user.setLockoutUntil(SqlRowUtils.getInstant(rs, "LockoutUntil"));
        user.setLockedOut(rs.getBoolean("IsLockedOut"));
        user.setMustChangePassword(rs.getBoolean("MustChangePassword"));
        user.setLastLoginOn(SqlRowUtils.getInstant(rs, "LastLoginOn"));
        return user;
    };

    private static final RowMapper<String> ROLE_NAME_MAPPER = (rs, rowNum) -> rs.getString("RoleName");
    private static final RowMapper<String> PERMISSION_CODE_MAPPER = (rs, rowNum) -> rs.getString("PermissionCode");

    private static final RowMapper<LoginAttemptResult> ATTEMPT_MAPPER = (rs, rowNum) -> {
        LoginAttemptResult result = new LoginAttemptResult();
        result.setFailedLoginCount(rs.getInt("FailedLoginCount"));
        result.setLockoutUntil(SqlRowUtils.getInstant(rs, "LockoutUntil"));
        result.setLockedOut(rs.getBoolean("IsLockedOut"));
        return result;
    };

    private final StoredProcedureExecutor executor;

    public AuthRepositoryImpl(StoredProcedureExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Optional<LoginUser> findForLogin(String usernameOrEmail) {
        StoredProcedureResult result = executor.call("usp_Auth_GetUserForLogin")
                .in("UsernameOrEmail", Types.NVARCHAR, usernameOrEmail)
                .resultSet(USER, USER_MAPPER)
                .resultSet(ROLES, ROLE_NAME_MAPPER)
                .resultSet(PERMISSIONS, PERMISSION_CODE_MAPPER)
                .execute();
        Optional<LoginUser> user = result.firstRow(USER);
        user.ifPresent(found -> {
            List<String> roles = result.rows(ROLES);
            List<String> permissions = result.rows(PERMISSIONS);
            found.setRoles(roles);
            found.setPermissions(permissions);
        });
        return user;
    }

    @Override
    public LoginAttemptResult recordFailedLogin(long userId, int maxFailedAttempts, int lockoutMinutes) {
        return executor.call("usp_Auth_RecordFailedLogin")
                .in("UserId", Types.BIGINT, userId)
                .in("MaxFailedAttempts", Types.INTEGER, maxFailedAttempts)
                .in("LockoutMinutes", Types.INTEGER, lockoutMinutes)
                .resultSet(RESULT, ATTEMPT_MAPPER)
                .execute()
                .<LoginAttemptResult>firstRow(RESULT)
                .orElseGet(LoginAttemptResult::new);
    }

    @Override
    public void recordSuccessfulLogin(long userId) {
        executor.call("usp_Auth_RecordSuccessfulLogin")
                .in("UserId", Types.BIGINT, userId)
                .execute();
    }
}
