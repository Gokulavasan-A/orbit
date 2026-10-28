package com.enterprise.api.model.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/users/{id}/roles}: the complete list of role ids the
 * user should have (empty removes every role). Last write wins.
 */
public record UpdateUserRolesRequest(
        @NotNull(message = "is required")
        @Size(max = MAX_ROLES, message = "must contain at most 200 roles")
        List<@NotNull(message = "must not contain null") @Positive(message = "must contain role ids") Long> roleIds) {

    public static final int MAX_ROLES = 200;
}
