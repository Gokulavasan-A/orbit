package com.enterprise.api.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller, read from a validated access token.
 *
 * @param userId                 {@code sub}
 * @param sessionId              {@code sid}
 * @param roles                  {@code roles}
 * @param permissions            {@code permissions}
 * @param passwordChangeRequired {@code pwd_change}
 */
public record CurrentUser(long userId, UUID sessionId, List<String> roles, List<String> permissions,
                          boolean passwordChangeRequired) {

    public CurrentUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /** The decoder has already required {@code sub} and {@code sid}, so this does not fail for valid tokens. */
    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                Long.parseLong(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(TokenClaims.SESSION_ID)),
                stringList(jwt, TokenClaims.ROLES),
                stringList(jwt, TokenClaims.PERMISSIONS),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(TokenClaims.PASSWORD_CHANGE)));
    }

    private static List<String> stringList(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }
}
