package com.enterprise.api.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Password policy for new passwords ({@code app.security.password-policy.*}).
 * BCrypt only uses the first 72 bytes, so longer passwords are always rejected
 * regardless of {@code maxLength}.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.password-policy")
public record PasswordPolicyProperties(
        @Min(8) @Max(72) int minLength,
        @Min(8) @Max(72) int maxLength,
        boolean requireUppercase,
        boolean requireLowercase,
        boolean requireDigit,
        boolean requireSpecial) {
}
