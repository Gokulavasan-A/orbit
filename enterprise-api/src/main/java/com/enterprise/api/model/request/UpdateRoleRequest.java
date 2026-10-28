package com.enterprise.api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/roles/{id}}. Permissions are changed with
 * {@code PUT /api/v1/roles/{id}/permissions}.
 *
 * @param rowVersion Base64 of the 8-byte RowVersion from the last read
 */
public record UpdateRoleRequest(
        @NotBlank(message = "is required")
        @Size(min = 2, max = 100, message = "must be between 2 and 100 characters")
        String name,

        @Size(max = 500, message = "must be at most 500 characters")
        String description,

        @NotNull(message = "is required")
        Boolean isActive,

        @NotBlank(message = "is required")
        @Pattern(regexp = ROW_VERSION_PATTERN, message = "must be a row version")
        String rowVersion) {

    /** Base64 of exactly 8 bytes. */
    public static final String ROW_VERSION_PATTERN = "^[A-Za-z0-9+/]{11}=$";

    public UpdateRoleRequest {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
