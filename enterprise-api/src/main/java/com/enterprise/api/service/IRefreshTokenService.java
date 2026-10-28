package com.enterprise.api.service;

import java.util.UUID;

import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.IssuedToken;
import com.enterprise.api.security.RotatedRefreshToken;

/** Refresh token lifecycle. Raw values exist only in memory; the database stores SHA-256 hashes. */
public interface IRefreshTokenService {

    /** Creates and stores a new refresh token for the session. */
    IssuedToken issue(UUID sessionId, long userId, ClientInfo client);

    /**
     * Validates the presented token and rotates it.
     * <ul>
     *   <li>Reuse of a rotated token (status REUSED, or 50204 during rotation) revokes
     *       all sessions of the user: {@code REFRESH_TOKEN_REUSED} (401).</li>
     *   <li>A deleted, inactive or locked user, or an invalid session, revokes the
     *       session: {@code SESSION_INVALID} (401).</li>
     *   <li>Unknown, malformed, expired or revoked tokens: {@code SESSION_INVALID} (401).</li>
     * </ul>
     */
    RotatedRefreshToken rotate(String rawToken, ClientInfo client);

    /** Revokes the token if it is well-formed and known; never fails for unknown tokens. */
    void revoke(String rawToken, String reason);
}
