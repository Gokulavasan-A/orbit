package com.enterprise.api.service;

import com.enterprise.api.security.ClientInfo;

public interface ILoginAuditService {

    /**
     * Writes a LoginAudit row in the background ({@code @Async}); failures are logged
     * and never reach the caller, so auditing cannot slow down or break a login.
     *
     * @param userId        null for an unknown username
     * @param failureReason null on success, otherwise a fixed code such as {@code BAD_PASSWORD}
     */
    void record(Long userId, String usernameAttempted, boolean success, String failureReason, ClientInfo client);
}
