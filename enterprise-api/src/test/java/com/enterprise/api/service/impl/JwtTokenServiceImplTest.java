package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.enterprise.api.config.properties.JwtProperties;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.security.IssuedToken;
import com.enterprise.api.security.JwtConfig;
import com.enterprise.api.security.TokenClaims;
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class JwtTokenServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final TokenClaims CLAIMS = new TokenClaims(42L, SESSION_ID, List.of("Admin"),
            List.of("ROLE_VIEW", "USER_VIEW"), true);

    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtProperties properties = TestSecurityProperties.jwt();
    private SecretKey key;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() {
        key = jwtConfig.jwtSigningKey(properties);
        encoder = jwtConfig.jwtEncoder(key);
    }

    @Test
    void issuesTokenWithAllRequiredClaims() {
        IssuedToken token = service(NOW).issueAccessToken(CLAIMS);
        Jwt jwt = decoder(NOW).decode(token.value());

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("enterprise-api");
        assertThat(jwt.getAudience()).containsExactly("enterprise-ui");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(CurrentUser.from(jwt)).isEqualTo(new CurrentUser(42L, SESSION_ID, List.of("Admin"),
                List.of("ROLE_VIEW", "USER_VIEW"), true));
    }

    @Test
    void everyTokenGetsItsOwnJti() {
        JwtDecoder decoder = decoder(NOW);
        String first = decoder.decode(service(NOW).issueAccessToken(CLAIMS).value()).getId();
        String second = decoder.decode(service(NOW).issueAccessToken(CLAIMS).value()).getId();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void mapsRolesAndPermissionsToAuthorities() {
        Jwt jwt = decoder(NOW).decode(service(NOW).issueAccessToken(CLAIMS).value());
        JwtAuthenticationConverter converter = jwtConfig.jwtAuthenticationConverter();

        // Spring Security 7 also adds the FACTOR_BEARER authentication-factor authority.
        assertThat(converter.convert(jwt).getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .filteredOn(authority -> !authority.startsWith("FACTOR_"))
                .containsExactlyInAnyOrder("ROLE_Admin", "PERM_ROLE_VIEW", "PERM_USER_VIEW");
        assertThat(converter.convert(jwt).getName()).isEqualTo("42");
    }

    @Test
    void expiredTokenIsRejectedWithTheExpiredErrorCode() {
        String token = service(NOW).issueAccessToken(CLAIMS).value();

        assertThatThrownBy(() -> decoder(NOW.plus(Duration.ofMinutes(16))).decode(token))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(ex -> assertThat(((JwtValidationException) ex).getErrors())
                        .anyMatch(error -> JwtConfig.TOKEN_EXPIRED_ERROR.equals(error.getErrorCode())));
        // Within the 30 s clock skew it is still accepted.
        assertThat(decoder(NOW.plus(Duration.ofMinutes(15)).plusSeconds(20)).decode(token)).isNotNull();
    }

    @Test
    void wrongIssuerOrAudienceIsRejected() {
        String token = service(NOW).issueAccessToken(CLAIMS).value();
        JwtProperties otherIssuer = new JwtProperties(properties.signingKey(), "someone-else", properties.audience(),
                properties.accessTokenTtl(), properties.clockSkew());
        JwtProperties otherAudience = new JwtProperties(properties.signingKey(), properties.issuer(), "other-app",
                properties.accessTokenTtl(), properties.clockSkew());

        assertThatThrownBy(() -> decoder(otherIssuer, NOW).decode(token)).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder(otherAudience, NOW).decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        String token = service(NOW).issueAccessToken(CLAIMS).value();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("\"42\"", "\"1\"");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
                + "." + parts[2];

        assertThatThrownBy(() -> decoder(NOW).decode(tampered)).isInstanceOf(BadJwtException.class);

        JwtProperties otherKey = new JwtProperties(Base64.getEncoder().encodeToString(new byte[48]),
                properties.issuer(), properties.audience(), properties.accessTokenTtl(), properties.clockSkew());
        String foreign = new JwtTokenServiceImpl(jwtConfig.jwtEncoder(jwtConfig.jwtSigningKey(otherKey)), otherKey,
                Clock.fixed(NOW, ZoneOffset.UTC)).issueAccessToken(CLAIMS).value();
        assertThatThrownBy(() -> decoder(NOW).decode(foreign)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void signingKeyMustBeBase64AndAtLeast256Bits() {
        JwtProperties shortKey = new JwtProperties(Base64.getEncoder().encodeToString(new byte[31]), "i", "a",
                Duration.ofMinutes(15), Duration.ZERO);
        JwtProperties notBase64 = new JwtProperties("not base64 !!", "i", "a", Duration.ofMinutes(15), Duration.ZERO);

        assertThatThrownBy(() -> jwtConfig.jwtSigningKey(shortKey))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> jwtConfig.jwtSigningKey(notBase64))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("not base64");
        assertThat(properties.toString()).doesNotContain(properties.signingKey());
    }

    private JwtTokenServiceImpl service(Instant now) {
        return new JwtTokenServiceImpl(encoder, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private JwtDecoder decoder(Instant now) {
        return decoder(properties, now);
    }

    private JwtDecoder decoder(JwtProperties props, Instant now) {
        return jwtConfig.jwtDecoder(key, props, Clock.fixed(now, ZoneOffset.UTC));
    }
}
