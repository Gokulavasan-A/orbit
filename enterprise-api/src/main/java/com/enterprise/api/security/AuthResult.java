package com.enterprise.api.security;

import com.enterprise.api.model.response.AuthResponse;

/**
 * Outcome of login, refresh and change-password: the response body (with the access
 * token) and the new refresh token, which the controller puts in the HttpOnly cookie.
 */
public record AuthResult(AuthResponse response, IssuedToken refreshToken) {
}
