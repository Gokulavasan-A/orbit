package com.enterprise.api.repository;

public interface ILoginAuditRepository {

    /** {@code usp_LoginAudit_Insert}: returns the new LoginAudit id. An unknown userId is stored as NULL. */
    Long insert(Long userId, String usernameAttempted, boolean success, String failureReason, String ipAddress,
                String userAgent);
}
