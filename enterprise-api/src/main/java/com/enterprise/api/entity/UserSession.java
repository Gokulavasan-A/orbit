package com.enterprise.api.entity;

import java.time.Instant;
import java.util.UUID;

/** Row of {@code usp_Session_Create}. */
public class UserSession {

    private UUID sessionId;
    private Instant createdOn;
    private Instant expiresOn;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Instant createdOn) {
        this.createdOn = createdOn;
    }

    public Instant getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(Instant expiresOn) {
        this.expiresOn = expiresOn;
    }
}
