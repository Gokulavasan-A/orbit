package com.enterprise.api.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.enterprise.api.entity.RefreshTokenRecord;

/** Refresh tokens are identified only by the SHA-256 hex hash of their value. */
public interface IRefreshTokenRepository {

    /**
     * {@code usp_RefreshToken_Save}: stores a new token; with {@code replacesTokenHash}
     * it atomically rotates (50204 when the old token was already used).
     *
     * @return the stored expiry (capped at the session's expiry)
     */
    Instant save(UUID sessionId, long userId, String tokenHash, Instant expiresOn, String createdByIp,
                 String replacesTokenHash);

    /** {@code usp_RefreshToken_Get}: token state; empty if the hash is unknown. */
    Optional<RefreshTokenRecord> findByHash(String tokenHash);

    /** {@code usp_RefreshToken_Revoke}: returns the number of tokens revoked (0 = already revoked). */
    int revoke(String tokenHash, String reason);
}
