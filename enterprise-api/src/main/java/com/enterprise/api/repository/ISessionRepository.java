package com.enterprise.api.repository;

import java.util.UUID;

import com.enterprise.api.entity.RevocationResult;
import com.enterprise.api.entity.RoleSessionRevocation;
import com.enterprise.api.entity.SessionCleanupResult;
import com.enterprise.api.entity.SessionValidation;
import com.enterprise.api.entity.UserSession;

public interface ISessionRepository {

    /** {@code usp_Session_Create}: new session for an active user (50101 otherwise). */
    UserSession create(long userId, int absoluteTimeoutMinutes, String ipAddress, String userAgent);

    /** {@code usp_Session_Validate}: status of the session; revokes USER_INACTIVE / EXPIRED / IDLE sessions. */
    SessionValidation validate(UUID sessionId, int idleTimeoutMinutes);

    /** {@code usp_Session_Revoke}: revokes one session and its refresh tokens (50201 if unknown). */
    RevocationResult revoke(UUID sessionId, String reason);

    /** {@code usp_Session_RevokeAllForUser}: revokes all sessions of the user except {@code exceptSessionId}. */
    RevocationResult revokeAllForUser(long userId, String reason, UUID exceptSessionId);

    /**
     * {@code usp_Session_RevokeAllForRole}: revokes all sessions of every user holding the role
     * (50403 if the role is unknown) and returns those user ids for cache eviction.
     */
    RoleSessionRevocation revokeAllForRole(long roleId, String reason);

    /** {@code usp_Session_CleanupExpired}: purges sessions/tokens expired or revoked before the retention window. */
    SessionCleanupResult cleanupExpired(int retentionDays, int batchSize);
}
