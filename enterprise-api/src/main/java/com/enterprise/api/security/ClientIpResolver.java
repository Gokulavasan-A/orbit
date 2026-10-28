package com.enterprise.api.security;

import java.util.List;
import java.util.regex.Pattern;

import com.enterprise.api.config.properties.ClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Client IP for rate limiting and audit. Uses the remote address; {@code X-Forwarded-For}
 * is read only when the remote address is a configured trusted proxy, walking the
 * list from the right and skipping trusted proxies, so a client cannot spoof its IP
 * by sending the header itself.
 */
@Component
public class ClientIpResolver {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    /** Literal IPv4/IPv6 only; anything else (hostnames, zone ids) is never resolved or trusted. */
    private static final Pattern IP_LITERAL = Pattern.compile("^(?=.*[.:])[0-9A-Fa-f:.]{2,45}$");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(ClientIpProperties properties) {
        this.trustedProxies = properties.trustedProxies().stream().map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !isTrusted(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].strip();
            if (!IP_LITERAL.matcher(hop).matches()) {
                return remoteAddress;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        // Every hop is a trusted proxy: the left-most one is the closest we know to the client.
        return hops[0].strip();
    }

    private boolean isTrusted(String address) {
        if (address == null || !IP_LITERAL.matcher(address).matches()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return false;
    }
}
