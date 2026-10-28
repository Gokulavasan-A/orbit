package com.enterprise.api.config.properties;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bucket4j limits ({@code app.security.rate-limit.*}). Buckets are kept in memory
 * (Caffeine), so the limits apply per instance; see {@code docs/security.md}.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @Min(1) long maxBuckets,
        @Valid @NotNull Limit loginPerIp,
        @Valid @NotNull Limit loginPerUsername,
        @Valid @NotNull Limit refreshPerIp,
        @Valid @NotNull Limit authenticatedPerUser,
        @Valid @NotNull Limit anonymousPerIp) {

    /** {@code capacity} requests per {@code period}, refilled greedily. */
    public record Limit(@Min(1) long capacity, @NotNull Duration period) {
    }
}
