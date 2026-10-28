package com.enterprise.api.security;

import java.time.Instant;

/**
 * A token value handed to the client (access token or raw refresh token) and its
 * expiry. {@link #toString()} never prints the value.
 */
public record IssuedToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[value=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
