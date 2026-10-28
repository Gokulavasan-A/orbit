package com.enterprise.api.config.properties;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access token settings ({@code app.security.jwt.*}).
 * <p>
 * The signing key comes only from {@code JWT_SIGNING_KEY}. Its Base64 format and
 * minimum length (256 bits) are checked when the key bean is created (see
 * {@code JwtConfig}), not with a constraint here, because bind-validation failures
 * print the rejected value.
 *
 * @param signingKey     Base64 HMAC key for HS256 (at least 32 bytes once decoded)
 * @param issuer         {@code iss} claim, written and required
 * @param audience       {@code aud} claim, written and required
 * @param accessTokenTtl lifetime of an access token
 * @param clockSkew      tolerance for {@code exp}, {@code nbf} and {@code iat}
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank String signingKey,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration clockSkew) {

    /** Never prints the signing key. */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", accessTokenTtl=" + accessTokenTtl
                + ", clockSkew=" + clockSkew + ", signingKey=<redacted>]";
    }
}
