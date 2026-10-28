package com.enterprise.api.async;

import com.enterprise.api.service.ISessionMaintenanceService;
import com.enterprise.api.util.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges expired/revoked sessions and refresh tokens on the configured cron
 * ({@code app.session-cleanup.*}). The procedure is idempotent and deletes in
 * small batches, so overlapping runs on several instances are harmless.
 */
@Component
@ConditionalOnProperty(prefix = "app.session-cleanup", name = "enabled", havingValue = "true")
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);

    private final ISessionMaintenanceService sessionMaintenanceService;

    public SessionCleanupJob(ISessionMaintenanceService sessionMaintenanceService) {
        this.sessionMaintenanceService = sessionMaintenanceService;
    }

    @Scheduled(cron = "${app.session-cleanup.cron}", zone = "${app.session-cleanup.zone}")
    public void run() {
        MDC.put(CorrelationId.MDC_KEY, "job-session-cleanup-" + CorrelationId.newId());
        try {
            sessionMaintenanceService.cleanupExpiredSessions();
        } catch (RuntimeException ex) {
            log.error("Session cleanup failed", ex);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
