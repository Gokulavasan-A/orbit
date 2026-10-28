package com.enterprise.api.entity;

/** Row of {@code usp_Session_CleanupExpired}. */
public class SessionCleanupResult {

    private int sessionsDeleted;
    private int tokensDeleted;

    public int getSessionsDeleted() {
        return sessionsDeleted;
    }

    public void setSessionsDeleted(int sessionsDeleted) {
        this.sessionsDeleted = sessionsDeleted;
    }

    public int getTokensDeleted() {
        return tokensDeleted;
    }

    public void setTokensDeleted(int tokensDeleted) {
        this.tokensDeleted = tokensDeleted;
    }
}
