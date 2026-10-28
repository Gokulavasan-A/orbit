package com.enterprise.api.entity;

import java.time.Instant;

/** Row of {@code usp_User_ChangePassword} and {@code usp_User_ResetPassword}. */
public class PasswordChangeResult {

    private Long id;
    private byte[] rowVersion;
    private Instant passwordChangedOn;
    private int sessionsRevoked;
    private int tokensRevoked;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public byte[] getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(byte[] rowVersion) {
        this.rowVersion = rowVersion;
    }

    public Instant getPasswordChangedOn() {
        return passwordChangedOn;
    }

    public void setPasswordChangedOn(Instant passwordChangedOn) {
        this.passwordChangedOn = passwordChangedOn;
    }

    public int getSessionsRevoked() {
        return sessionsRevoked;
    }

    public void setSessionsRevoked(int sessionsRevoked) {
        this.sessionsRevoked = sessionsRevoked;
    }

    public int getTokensRevoked() {
        return tokensRevoked;
    }

    public void setTokensRevoked(int tokensRevoked) {
        this.tokensRevoked = tokensRevoked;
    }
}
