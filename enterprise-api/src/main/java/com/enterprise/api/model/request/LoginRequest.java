package com.enterprise.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/login}. {@code username} also accepts the email
 * address. {@link #toString()} never prints the password.
 */
public record LoginRequest(
        @NotBlank(message = "is required")
        @Size(max = 256, message = "must be at most 256 characters")
        String username,

        @NotBlank(message = "is required")
        @Size(max = 128, message = "must be at most 128 characters")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=<redacted>]";
    }
}
