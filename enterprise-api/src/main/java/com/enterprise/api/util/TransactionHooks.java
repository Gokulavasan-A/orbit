package com.enterprise.api.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs work once the current Spring transaction has committed. */
public final class TransactionHooks {

    private TransactionHooks() {
    }

    /**
     * Runs {@code action} after the current transaction commits (not on rollback), or
     * immediately when no transaction is active.
     * <p>
     * Used for session-cache eviction: a request running at the same time can still read
     * the pre-commit state (READ_COMMITTED_SNAPSHOT) and put the session back in the cache
     * as VALID, so evicting only before the commit is not enough.
     */
    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
