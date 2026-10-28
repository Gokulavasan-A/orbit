package com.enterprise.api.entity;

import java.util.List;

/**
 * {@code usp_Session_RevokeAllForRole}: the users holding the role (their sessions
 * were revoked) and the revoke counts.
 */
public class RoleSessionRevocation {

    private List<Long> userIds = List.of();
    private int sessionsRevoked;
    private int tokensRevoked;

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds == null ? List.of() : List.copyOf(userIds);
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
