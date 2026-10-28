package com.enterprise.api.repository.impl;

import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.enterprise.api.entity.RefreshTokenRecord;
import com.enterprise.api.exception.TechnicalException;
import com.enterprise.api.repository.IRefreshTokenRepository;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import com.enterprise.api.util.SqlRowUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepositoryImpl implements IRefreshTokenRepository {

    private static final String RESULT = "result";

    private static final RowMapper<Instant> SAVE_MAPPER = (rs, rowNum) -> SqlRowUtils.getInstant(rs, "ExpiresOn");

    private static final RowMapper<RefreshTokenRecord> TOKEN_MAPPER = (rs, rowNum) -> {
        RefreshTokenRecord token = new RefreshTokenRecord();
        token.setId(rs.getLong("Id"));
        token.setSessionId(SqlRowUtils.getUuid(rs, "SessionId"));
        token.setUserId(rs.getLong("UserId"));
        token.setExpiresOn(SqlRowUtils.getInstant(rs, "ExpiresOn"));
        token.setSessionExpiresOn(SqlRowUtils.getInstant(rs, "SessionExpiresOn"));
        token.setSessionRevoked(rs.getBoolean("SessionIsRevoked"));
        token.setStatus(rs.getString("Status"));
        return token;
    };

    private static final RowMapper<Integer> REVOKE_MAPPER = (rs, rowNum) -> rs.getInt("TokensRevoked");

    private final StoredProcedureExecutor executor;

    public RefreshTokenRepositoryImpl(StoredProcedureExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Instant save(UUID sessionId, long userId, String tokenHash, Instant expiresOn, String createdByIp,
                        String replacesTokenHash) {
        return executor.call("usp_RefreshToken_Save")
                .in("SessionId", Types.NVARCHAR, SqlRowUtils.toSqlUuid(sessionId))
                .in("UserId", Types.BIGINT, userId)
                .in("TokenHash", Types.CHAR, tokenHash)
                .in("ExpiresOn", Types.TIMESTAMP, SqlRowUtils.toSqlUtc(expiresOn))
                .in("CreatedByIp", Types.NVARCHAR, createdByIp)
                .in("ReplacesTokenHash", Types.CHAR, replacesTokenHash)
                .resultSet(RESULT, SAVE_MAPPER)
                .execute()
                .<Instant>firstRow(RESULT)
                .orElseThrow(() -> new TechnicalException("usp_RefreshToken_Save returned no row", null, null));
    }

    @Override
    public Optional<RefreshTokenRecord> findByHash(String tokenHash) {
        return executor.call("usp_RefreshToken_Get")
                .in("TokenHash", Types.CHAR, tokenHash)
                .resultSet(RESULT, TOKEN_MAPPER)
                .execute()
                .firstRow(RESULT);
    }

    @Override
    public int revoke(String tokenHash, String reason) {
        return executor.call("usp_RefreshToken_Revoke")
                .in("TokenHash", Types.CHAR, tokenHash)
                .in("ReplacedByTokenHash", Types.CHAR, null)
                .in("Reason", Types.NVARCHAR, reason)
                .resultSet(RESULT, REVOKE_MAPPER)
                .execute()
                .<Integer>firstRow(RESULT)
                .orElse(0);
    }
}
