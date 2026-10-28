package com.enterprise.api.config.properties;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Login, session and refresh-token policy ({@code app.security.auth.*}). The values
 * are passed to the auth and session procedures, which take policy as parameters.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.auth")
public record AuthProperties(
        @Valid @NotNull RefreshToken refreshToken,
        @Valid @NotNull Session session,
        @Valid @NotNull Lockout lockout,
        @Valid @NotNull Csrf csrf) {

    /**
     * @param ttl          refresh token lifetime, also the cookie max-age (capped at the session expiry by the DB)
     * @param cookieName   name of the HttpOnly refresh cookie
     * @param cookiePath   path the cookie is sent to (the auth endpoints only)
     * @param cookieSecure Secure attribute; keep true (browsers accept Secure cookies on http://localhost)
     */
    public record RefreshToken(
            @NotNull Duration ttl,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String cookieName,
            @NotBlank String cookiePath,
            boolean cookieSecure) {
    }

    /**
     * @param idleTimeout          passed to {@code usp_Session_Validate}
     * @param absoluteTimeout      passed to {@code usp_Session_Create}
     * @param validationCacheTtl   how long a VALID session check is cached
     * @param validationCacheSize  maximum number of cached session checks
     */
    public record Session(
            @NotNull Duration idleTimeout,
            @NotNull Duration absoluteTimeout,
            @NotNull Duration validationCacheTtl,
            @Min(1) long validationCacheSize) {
    }

    /**
     * @param maxFailedAttempts failed logins before the account is locked
     * @param duration          how long the lockout lasts
     */
    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration duration) {
    }

    /**
     * @param cookieDomain optional Domain attribute of the {@code XSRF-TOKEN} cookie
     *                     (e.g. {@code company.com} so app.company.com can read it); empty = host-only
     * @param cookieSecure Secure attribute of the {@code XSRF-TOKEN} cookie
     */
    public record Csrf(String cookieDomain, boolean cookieSecure) {
    }
}
