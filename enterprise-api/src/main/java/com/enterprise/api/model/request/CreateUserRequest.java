package com.enterprise.api.model.request;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/users}. There is no password: the API generates a
 * temporary one and returns it once. Lengths match the {@code Users} columns.
 */
public record CreateUserRequest(
        @NotBlank(message = "is required")
        @Size(min = 3, max = 100, message = "must be between 3 and 100 characters")
        @Pattern(regexp = "^[^@\\s]*$", message = "must not contain '@' or spaces")
        String username,

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

        @Size(max = UpdateUserRolesRequest.MAX_ROLES, message = "must contain at most 200 roles")
        List<@NotNull(message = "must not contain null") @Positive(message = "must contain role ids") Long> roleIds) {

    public CreateUserRequest {
        username = strip(username);
        email = strip(email);
        firstName = strip(firstName);
        lastName = strip(lastName);
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.strip();
        roleIds = roleIds == null ? List.of() : roleIds;
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
