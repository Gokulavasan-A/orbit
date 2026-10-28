package com.enterprise.api.model.response;

/**
 * Body of {@code GET /api/v1/auth/csrf}: the raw token (same value as the
 * {@code XSRF-TOKEN} cookie) and the header to send it in.
 */
public record CsrfTokenResponse(String headerName, String token) {
}
