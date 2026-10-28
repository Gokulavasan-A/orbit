package com.enterprise.api.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code usp_RefreshToken_Get}. {@code status} is one of {@code ACTIVE},
 * {@code EXPIRED}, {@code REVOKED}, {@code REUSED}, {@code SESSION_INVALID}.
 * Token hashes are not mapped: the API never needs them back.
 */
public class RefreshTokenRecord {

    public static final String ACTIVE = "ACTIVE";
    public static final String REUSED = "REUSED";
    public static final String SESSION_INVALID = "SESSION_INVALID";

    private Long id;
    private UUID sessionId;
    private Long userId;
    private Instant expiresOn;
    private Instant sessionExpiresOn;
    private boolean sessionRevoked;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(Instant expiresOn) {
        this.expiresOn = expiresOn;
    }

    public Instant getSessionExpiresOn() {
        return sessionExpiresOn;
    }

    public void setSessionExpiresOn(Instant sessionExpiresOn) {
        this.sessionExpiresOn = sessionExpiresOn;
    }

    public boolean isSessionRevoked() {
        return sessionRevoked;
    }

    public void setSessionRevoked(boolean sessionRevoked) {
        this.sessionRevoked = sessionRevoked;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
