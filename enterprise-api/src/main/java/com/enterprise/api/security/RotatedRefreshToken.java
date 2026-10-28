package com.enterprise.api.security;

import java.util.UUID;

import com.enterprise.api.entity.LoginUser;

/**
 * A successful refresh-token rotation: the current user data (roles, permissions,
 * MustChangePassword) for the new access token, the session and the new refresh token.
 */
public record RotatedRefreshToken(LoginUser user, UUID sessionId, IssuedToken refreshToken) {
}
