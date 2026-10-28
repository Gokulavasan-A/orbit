package com.enterprise.api.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.enterprise.api.config.properties.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * HS256 access tokens (Nimbus). The decoder requires a valid signature and
 * {@code exp}, {@code iat}, {@code iss}, {@code aud}, {@code sub} (numeric),
 * {@code sid} (UUID) and {@code jti}. Expired tokens are reported with the
 * {@link #TOKEN_EXPIRED_ERROR} error code so the entry point can answer
 * {@code TOKEN_EXPIRED} instead of {@code TOKEN_INVALID}.
 */
@Configuration
public class JwtConfig {

    public static final String TOKEN_EXPIRED_ERROR = "token_expired";
    static final int MIN_KEY_BYTES = 32;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Fails startup when the key is not Base64 or shorter than 256 bits. The value is never put in a message. */
    @Bean
    public SecretKey jwtSigningKey(JwtProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.signingKey().strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SIGNING_KEY (app.security.jwt.signing-key) is not valid Base64");
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SIGNING_KEY (app.security.jwt.signing-key) must decode to at least "
                    + MIN_KEY_BYTES + " bytes (256 bits)");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(validator(properties, clock));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName(JwtClaimNames.SUB);
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter());
        return converter;
    }

    static OAuth2TokenValidator<Jwt> validator(JwtProperties properties, Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(properties.clockSkew());
        timestamps.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(
                new ExpiryValidator(clock, properties.clockSkew()),
                timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.audience())),
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, value -> value != null),
                new JwtClaimValidator<Object>(JwtClaimNames.JTI, value -> value != null),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, JwtConfig::isUserId),
                new JwtClaimValidator<Object>(TokenClaims.SESSION_ID, JwtConfig::isUuid));
    }

    /** {@code roles} → {@code ROLE_<name>}, {@code permissions} → {@code PERM_<code>}. */
    static Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter() {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList(TokenClaims.ROLES);
            List<String> permissions = jwt.getClaimAsStringList(TokenClaims.PERMISSIONS);
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(TokenClaims.ROLE_PREFIX + role)));
            }
            if (permissions != null) {
                permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(TokenClaims.PERMISSION_PREFIX + code)));
            }
            return authorities;
        };
    }

    private static boolean isUserId(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            return Long.parseLong(text) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isUuid(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            UUID.fromString(text);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Requires {@code exp} and flags expiry with its own error code. */
    record ExpiryValidator(Clock clock, Duration clockSkew) implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The exp claim is required", null));
            }
            if (clock.instant().minus(clockSkew).isAfter(expiresAt)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(TOKEN_EXPIRED_ERROR, "The access token has expired", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
