package com.enterprise.api.model.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/roles}. {@code isActive} defaults to true. */
public record CreateRoleRequest(
        @NotBlank(message = "is required")
        @Size(min = 2, max = 100, message = "must be between 2 and 100 characters")
        String name,

        @Size(max = 500, message = "must be at most 500 characters")
        String description,

        Boolean isActive,

        @Size(max = UpdateRolePermissionsRequest.MAX_PERMISSIONS, message = "must contain at most 200 permissions")
        List<@NotNull(message = "must not contain null") @Positive(message = "must contain permission ids") Long> permissionIds) {

    public CreateRoleRequest {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
        isActive = isActive == null ? Boolean.TRUE : isActive;
        permissionIds = permissionIds == null ? List.of() : permissionIds;
    }
}
