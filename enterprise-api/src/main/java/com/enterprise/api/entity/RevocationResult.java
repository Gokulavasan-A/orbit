package com.enterprise.api.entity;

/** Row of {@code usp_Session_Revoke} and {@code usp_Session_RevokeAllForUser}. */
public class RevocationResult {

    private int sessionsRevoked;
    private int tokensRevoked;

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
