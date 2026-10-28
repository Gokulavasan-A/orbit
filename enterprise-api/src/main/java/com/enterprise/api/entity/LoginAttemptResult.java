package com.enterprise.api.entity;

import java.time.Instant;

/** Row of {@code usp_Auth_RecordFailedLogin}. */
public class LoginAttemptResult {

    private int failedLoginCount;
    private Instant lockoutUntil;
    private boolean lockedOut;

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public Instant getLockoutUntil() {
        return lockoutUntil;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    public void setLockedOut(boolean lockedOut) {
        this.lockedOut = lockedOut;
    }
}
