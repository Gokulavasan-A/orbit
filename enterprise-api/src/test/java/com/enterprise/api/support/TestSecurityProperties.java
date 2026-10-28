package com.enterprise.api.support;

import java.time.Duration;

import com.enterprise.api.config.properties.AuthProperties;
import com.enterprise.api.config.properties.JwtProperties;
import com.enterprise.api.config.properties.PasswordPolicyProperties;
import com.enterprise.api.config.properties.RateLimitProperties;

/** Property records with the production defaults, for unit tests. */
public final class TestSecurityProperties {

    /** TEST-ONLY key, same as application-test.yml. */
    public static final String SIGNING_KEY = "dGVzdC1vbmx5LWp3dC1zaWduaW5nLWtleS1mb3ItZW50ZXJwcmlzZS1hcGktdW5pdC10ZXN0cy0wMDAwMDA=";

    private TestSecurityProperties() {
    }

    public static JwtProperties jwt() {
        return new JwtProperties(SIGNING_KEY, "enterprise-api", "enterprise-ui", Duration.ofMinutes(15),
                Duration.ofSeconds(30));
    }

    public static AuthProperties auth() {
        return new AuthProperties(
                new AuthProperties.RefreshToken(Duration.ofHours(8), "refresh_token", "/api/v1/auth", true),
                new AuthProperties.Session(Duration.ofMinutes(30), Duration.ofHours(8), Duration.ofSeconds(15), 1000),
                new AuthProperties.Lockout(5, Duration.ofMinutes(15)),
                new AuthProperties.Csrf(null, true));
    }

    public static PasswordPolicyProperties passwordPolicy() {
        return new PasswordPolicyProperties(12, 64, true, true, true, true);
    }

    public static RateLimitProperties rateLimit(boolean enabled, long capacity, Duration period) {
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(capacity, period);
        return new RateLimitProperties(enabled, 1000, limit, limit, limit, limit, limit);
    }
}
