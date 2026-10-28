package com.enterprise.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.enterprise.api.config.properties.ClientIpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver noProxies = new ClientIpResolver(new ClientIpProperties(List.of()));
    private final ClientIpResolver withProxies = new ClientIpResolver(
            new ClientIpProperties(List.of("10.0.0.0/8", "192.168.1.5")));

    @Test
    void usesTheRemoteAddressWithoutTrustedProxies() {
        assertThat(noProxies.resolve(request("203.0.113.7", "198.51.100.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoresForwardedForFromAnUntrustedPeer() {
        assertThat(withProxies.resolve(request("203.0.113.7", "198.51.100.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    void takesTheFirstUntrustedHopFromTheRight() {
        // Client spoofs 1.1.1.1; the real client is 198.51.100.1, added by our proxy chain.
        assertThat(withProxies.resolve(request("10.1.2.3", "1.1.1.1, 198.51.100.1, 192.168.1.5")))
                .isEqualTo("198.51.100.1");
    }

    @Test
    void fallsBackToTheRemoteAddressForMalformedHeaders() {
        assertThat(withProxies.resolve(request("10.1.2.3", "evil.example.com"))).isEqualTo("10.1.2.3");
        assertThat(withProxies.resolve(request("10.1.2.3", null))).isEqualTo("10.1.2.3");
    }

    @Test
    void supportsIpv6() {
        assertThat(withProxies.resolve(request("10.1.2.3", "2001:db8::1"))).isEqualTo("2001:db8::1");
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader(ClientIpResolver.FORWARDED_FOR, forwardedFor);
        }
        return request;
    }
}
