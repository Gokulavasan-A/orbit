package com.enterprise.api.model.response;

/** Body of {@code POST /api/v1/auth/logout-all}: how many other sessions were signed out. */
public record LogoutAllResponse(int sessionsRevoked) {
}
