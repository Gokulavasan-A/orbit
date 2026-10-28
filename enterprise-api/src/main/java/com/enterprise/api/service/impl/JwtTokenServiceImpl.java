package com.enterprise.api.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.enterprise.api.config.properties.JwtProperties;
import com.enterprise.api.security.IssuedToken;
import com.enterprise.api.security.TokenClaims;
import com.enterprise.api.service.ITokenService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenServiceImpl implements ITokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenServiceImpl(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issueAccessToken(TokenClaims claims) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(Long.toString(claims.userId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.SESSION_ID, claims.sessionId().toString())
                .claim(TokenClaims.ROLES, claims.roles())
                .claim(TokenClaims.PERMISSIONS, claims.permissions())
                .claim(TokenClaims.PASSWORD_CHANGE, claims.passwordChangeRequired())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
