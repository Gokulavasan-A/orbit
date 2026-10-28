package com.enterprise.api.model.request;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/users/{id}}. The username can't be changed.
 *
 * @param concurrencyStamp the value from the last read; a stale value gives 409 CONCURRENCY_CONFLICT
 */
public record UpdateUserRequest(
        @NotBlank(message = "is required")
        @Size(max = 256, message = "must be at most 256 characters")
        @Email(message = "must be a valid email address")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "must be a valid email address")
        String email,

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String firstName,

        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String lastName,

        @Size(max = 30, message = "must be at most 30 characters")
        @Pattern(regexp = "^[0-9+()\\-. ]*$", message = "may only contain digits, spaces and + ( ) - .")
        String phoneNumber,

        @NotNull(message = "is required")
        Boolean isActive,

        @NotNull(message = "is required")
        UUID concurrencyStamp) {

    public UpdateUserRequest {
        email = email == null ? null : email.strip();
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.strip();
    }
}
