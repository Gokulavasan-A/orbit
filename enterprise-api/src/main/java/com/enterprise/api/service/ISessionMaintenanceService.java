package com.enterprise.api.service;

import com.enterprise.api.entity.SessionCleanupResult;

public interface ISessionMaintenanceService {

    /** Purges expired/revoked sessions and refresh tokens older than the configured retention. */
    SessionCleanupResult cleanupExpiredSessions();
}
