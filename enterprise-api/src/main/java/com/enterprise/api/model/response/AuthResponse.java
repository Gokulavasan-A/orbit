package com.enterprise.api.model.response;

import java.time.Instant;
import java.util.List;

/**
 * Body of login, refresh and change-password. The refresh token is never in the
 * body; it is set as an HttpOnly cookie. {@link #toString()} never prints the token.
 *
 * @param accessToken        JWT to send as {@code Authorization: Bearer <token>}
 * @param tokenType          always {@code Bearer}
 * @param expiresAt          access token expiry (UTC)
 * @param expiresIn          seconds until {@code expiresAt}
 * @param user               profile of the signed-in user
 * @param roles              role names
 * @param permissions        permission codes
 * @param mustChangePassword when true only /me, /change-password and /logout are allowed
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        long expiresIn,
        UserProfileResponse user,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword) {

    public static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "AuthResponse[accessToken=<redacted>, expiresAt=" + expiresAt + ", user=" + user
                + ", mustChangePassword=" + mustChangePassword + "]";
    }
}
