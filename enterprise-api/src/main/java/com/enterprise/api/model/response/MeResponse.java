package com.enterprise.api.model.response;

import java.util.List;

/**
 * Body of {@code GET /api/v1/auth/me}. Roles, permissions and the password-change
 * flag are the ones granted by the current access token.
 */
public record MeResponse(
        UserProfileResponse user,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword) {
}
