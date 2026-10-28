package com.enterprise.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/change-password}. The password policy itself is
 * checked by the service (it needs the username and the current password).
 * {@link #toString()} never prints the passwords.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "is required")
        @Size(max = 128, message = "must be at most 128 characters")
        String currentPassword,

        @NotBlank(message = "is required")
        @Size(max = 128, message = "must be at most 128 characters")
        String newPassword,

        @NotBlank(message = "is required")
        @Size(max = 128, message = "must be at most 128 characters")
        String confirmPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[<redacted>]";
    }
}
