package com.enterprise.api.config.properties;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CORS whitelist ({@code app.security.cors.*}, env {@code APP_CORS_ORIGINS} as a
 * comma-separated list). Wildcards are rejected at startup because credentials
 * (the refresh cookie) are allowed.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("app.security.cors.allowed-origins must list explicit origins, never '*'");
        }
    }
}
