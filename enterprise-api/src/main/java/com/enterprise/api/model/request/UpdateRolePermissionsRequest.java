package com.enterprise.api.model.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/roles/{id}/permissions}: the complete list of permission
 * ids the role should have. Last write wins.
 */
public record UpdateRolePermissionsRequest(
        @NotNull(message = "is required")
        @Size(max = MAX_PERMISSIONS, message = "must contain at most 200 permissions")
        List<@NotNull(message = "must not contain null") @Positive(message = "must contain permission ids") Long> permissionIds) {

    public static final int MAX_PERMISSIONS = 200;
}
