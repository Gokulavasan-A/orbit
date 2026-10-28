package com.enterprise.api.repository.impl;

import java.sql.Types;
import java.util.UUID;

import com.enterprise.api.entity.RevocationResult;
import com.enterprise.api.entity.RoleSessionRevocation;
import com.enterprise.api.entity.SessionCleanupResult;
import com.enterprise.api.entity.SessionValidation;
import com.enterprise.api.entity.UserSession;
import com.enterprise.api.exception.TechnicalException;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import com.enterprise.api.repository.support.StoredProcedureResult;
import com.enterprise.api.util.SqlRowUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepositoryImpl implements ISessionRepository {

    private static final String RESULT = "result";
    private static final String USER_IDS = "userIds";

    private static final RowMapper<Long> USER_ID_MAPPER = (rs, rowNum) -> rs.getLong("UserId");

    private static final RowMapper<UserSession> SESSION_MAPPER = (rs, rowNum) -> {
        UserSession session = new UserSession();
        session.setSessionId(SqlRowUtils.getUuid(rs, "SessionId"));
        session.setCreatedOn(SqlRowUtils.getInstant(rs, "CreatedOn"));
        session.setExpiresOn(SqlRowUtils.getInstant(rs, "ExpiresOn"));
        return session;
    };

    private static final RowMapper<SessionValidation> VALIDATION_MAPPER = (rs, rowNum) -> {
        SessionValidation validation = new SessionValidation();
        validation.setSessionId(SqlRowUtils.getUuid(rs, "SessionId"));
        validation.setUserId(SqlRowUtils.getLong(rs, "UserId"));
        validation.setStatus(rs.getString("Status"));
        validation.setExpiresOn(SqlRowUtils.getInstant(rs, "ExpiresOn"));
        validation.setLastActivityOn(SqlRowUtils.getInstant(rs, "LastActivityOn"));
        return validation;
    };

    private static final RowMapper<RevocationResult> REVOCATION_MAPPER = (rs, rowNum) -> {
        RevocationResult result = new RevocationResult();
        result.setSessionsRevoked(rs.getInt("SessionsRevoked"));
        result.setTokensRevoked(rs.getInt("TokensRevoked"));
        return result;
    };

    private static final RowMapper<SessionCleanupResult> CLEANUP_MAPPER = (rs, rowNum) -> {
        SessionCleanupResult result = new SessionCleanupResult();
        result.setSessionsDeleted(rs.getInt("SessionsDeleted"));
        result.setTokensDeleted(rs.getInt("TokensDeleted"));
        return result;
    };

    private final StoredProcedureExecutor executor;

    public SessionRepositoryImpl(StoredProcedureExecutor executor) {
        this.executor = executor;
    }

    @Override
    public UserSession create(long userId, int absoluteTimeoutMinutes, String ipAddress, String userAgent) {
        return executor.call("usp_Session_Create")
                .in("UserId", Types.BIGINT, userId)
                .in("AbsoluteTimeoutMinutes", Types.INTEGER, absoluteTimeoutMinutes)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .in("UserAgent", Types.NVARCHAR, userAgent)
                .resultSet(RESULT, SESSION_MAPPER)
                .execute()
                .<UserSession>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_Session_Create returned no row", null, null));
    }

    @Override
    public SessionValidation validate(UUID sessionId, int idleTimeoutMinutes) {
        return executor.call("usp_Session_Validate")
                .in("SessionId", Types.NVARCHAR, SqlRowUtils.toSqlUuid(sessionId))
                .in("IdleTimeoutMinutes", Types.INTEGER, idleTimeoutMinutes)
                .resultSet(RESULT, VALIDATION_MAPPER)
                .execute()
                .<SessionValidation>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_Session_Validate returned no row", null, null));
    }

    @Override
    public RevocationResult revoke(UUID sessionId, String reason) {
        return executor.call("usp_Session_Revoke")
                .in("SessionId", Types.NVARCHAR, SqlRowUtils.toSqlUuid(sessionId))
                .in("Reason", Types.NVARCHAR, reason)
                .resultSet(RESULT, REVOCATION_MAPPER)
                .execute()
                .<RevocationResult>firstRow(RESULT)
                .orElseGet(RevocationResult::new);
    }

    @Override
    public RevocationResult revokeAllForUser(long userId, String reason, UUID exceptSessionId) {
        return executor.call("usp_Session_RevokeAllForUser")
                .in("UserId", Types.BIGINT, userId)
                .in("Reason", Types.NVARCHAR, reason)
                .in("ExceptSessionId", Types.NVARCHAR, SqlRowUtils.toSqlUuid(exceptSessionId))
                .resultSet(RESULT, REVOCATION_MAPPER)
                .execute()
                .<RevocationResult>firstRow(RESULT)
                .orElseGet(RevocationResult::new);
    }

    @Override
    public RoleSessionRevocation revokeAllForRole(long roleId, String reason) {
        StoredProcedureResult result = executor.call("usp_Session_RevokeAllForRole")
                .in("RoleId", Types.BIGINT, roleId)
                .in("Reason", Types.NVARCHAR, reason)
                .resultSet(USER_IDS, USER_ID_MAPPER)
                .resultSet(RESULT, REVOCATION_MAPPER)
                .execute();
        RoleSessionRevocation revocation = new RoleSessionRevocation();
        revocation.setUserIds(result.rows(USER_IDS));
        result.<RevocationResult>firstRow(RESULT).ifPresent(counts -> {
            revocation.setSessionsRevoked(counts.getSessionsRevoked());
            revocation.setTokensRevoked(counts.getTokensRevoked());
        });
        return revocation;
    }

    @Override
    public SessionCleanupResult cleanupExpired(int retentionDays, int batchSize) {
        return executor.call("usp_Session_CleanupExpired")
                .in("RetentionDays", Types.INTEGER, retentionDays)
                .in("BatchSize", Types.INTEGER, batchSize)
                .resultSet(RESULT, CLEANUP_MAPPER)
                .execute()
                .<SessionCleanupResult>firstRow(RESULT)
                .orElseGet(SessionCleanupResult::new);
    }
}
