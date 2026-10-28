package com.enterprise.api.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Database call settings ({@code app.db.*}).
 *
 * @param defaultQueryTimeoutSeconds query timeout for every stored procedure call
 *                                   that does not set its own
 */
@Validated
@ConfigurationProperties(prefix = "app.db")
public record DbProperties(@Min(1) @Max(600) int defaultQueryTimeoutSeconds) {
}
