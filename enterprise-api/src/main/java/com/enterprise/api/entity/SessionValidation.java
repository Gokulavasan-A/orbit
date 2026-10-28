package com.enterprise.api.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code usp_Session_Validate}. {@code status} is one of {@code VALID},
 * {@code NOT_FOUND}, {@code REVOKED}, {@code USER_INACTIVE}, {@code EXPIRED}, {@code IDLE}.
 */
public class SessionValidation {

    public static final String VALID = "VALID";

    private UUID sessionId;
    private Long userId;
    private String status;
    private Instant expiresOn;
    private Instant lastActivityOn;

    public boolean isValid() {
        return VALID.equals(status);
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(Instant expiresOn) {
        this.expiresOn = expiresOn;
    }

    public Instant getLastActivityOn() {
        return lastActivityOn;
    }

    public void setLastActivityOn(Instant lastActivityOn) {
        this.lastActivityOn = lastActivityOn;
    }
}
