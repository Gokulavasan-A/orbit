package com.enterprise.api.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Response security headers ({@code app.security.headers.*}).
 *
 * @param hstsEnabled       send Strict-Transport-Security (prod only; written on HTTPS requests)
 * @param hstsMaxAge        HSTS max-age
 * @param permissionsPolicy value of the Permissions-Policy header
 */
@Validated
@ConfigurationProperties(prefix = "app.security.headers")
public record SecurityHeadersProperties(
        boolean hstsEnabled,
        @NotNull Duration hstsMaxAge,
        @NotBlank String permissionsPolicy) {
}
