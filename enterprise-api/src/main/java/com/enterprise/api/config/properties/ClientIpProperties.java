package com.enterprise.api.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Client IP resolution ({@code app.security.client-ip.*}).
 *
 * @param trustedProxies IPs or CIDR ranges of reverse proxies / load balancers whose
 *                       {@code X-Forwarded-For} header is trusted; empty = use the remote address only
 */
@ConfigurationProperties(prefix = "app.security.client-ip")
public record ClientIpProperties(List<String> trustedProxies) {

    public ClientIpProperties {
        trustedProxies = trustedProxies == null ? List.of()
                : trustedProxies.stream().map(String::strip).filter(value -> !value.isEmpty()).toList();
    }
}
