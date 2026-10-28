package com.enterprise.api.service;

import java.util.UUID;

/**
 * Server-side session check for every authenticated request. VALID results are
 * cached briefly ({@code app.security.auth.session.validation-cache-ttl}); every
 * revocation must evict, or the session stays usable until the entry expires.
 */
public interface ISessionValidationService {

    /** True when the session is VALID ({@code usp_Session_Validate}) and belongs to {@code userId}. */
    boolean isValid(UUID sessionId, long userId);

    void evictSession(UUID sessionId);

    /** Evicts every cached session of the user (logout-all, password change, token reuse). */
    void evictUser(long userId);
}
