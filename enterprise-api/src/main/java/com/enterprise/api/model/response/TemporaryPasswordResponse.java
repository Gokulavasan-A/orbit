package com.enterprise.api.model.response;

/**
 * Result of creating a user or resetting a password. The temporary password is
 * returned only in this one response and is never stored or logged in plain text;
 * the user must change it at the first sign-in. {@link #toString()} never prints it.
 */
public record TemporaryPasswordResponse(
        Long userId,
        String username,
        String temporaryPassword) {

    @Override
    public String toString() {
        return "TemporaryPasswordResponse[userId=" + userId + ", username=" + username + ", temporaryPassword=<redacted>]";
    }
}
