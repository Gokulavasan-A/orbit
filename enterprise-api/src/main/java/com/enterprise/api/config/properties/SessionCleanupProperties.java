package com.enterprise.api.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Scheduled purge of expired/revoked sessions ({@code app.session-cleanup.*}),
 * passed to {@code usp_Session_CleanupExpired}.
 */
@Validated
@ConfigurationProperties(prefix = "app.session-cleanup")
public record SessionCleanupProperties(
        boolean enabled,
        @NotBlank String cron,
        @NotBlank String zone,
        @Min(0) int retentionDays,
        @Min(1) int batchSize) {
}
