package com.enterprise.api.security;

import java.util.List;
import java.util.UUID;

/**
 * Content of an access token besides the standard claims ({@code iss}, {@code aud},
 * {@code iat}, {@code exp}, {@code jti}).
 *
 * @param userId                 {@code sub}
 * @param sessionId              {@code sid}, the server-side UserSession id
 * @param roles                  {@code roles} (role names, become {@code ROLE_<name>})
 * @param permissions            {@code permissions} (codes, become {@code PERM_<code>})
 * @param passwordChangeRequired {@code pwd_change}
 */
public record TokenClaims(long userId, UUID sessionId, List<String> roles, List<String> permissions,
                          boolean passwordChangeRequired) {

    public static final String SESSION_ID = "sid";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String PASSWORD_CHANGE = "pwd_change";

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String PERMISSION_PREFIX = "PERM_";

    public TokenClaims {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
