package com.enterprise.api.model.response;

import java.time.Instant;
import java.util.List;

/**
 * Row of the roles grid ({@code usp_Role_GetPaged}). Field names match the grid
 * sort column ids accepted by the procedure.
 */
public record RoleListItemResponse(
        Long id,
        String name,
        String description,
        boolean isSystem,
        boolean isActive,
        Instant createdOn,
        Instant modifiedOn,
        int userCount,
        int permissionCount,
        List<String> permissions) {
}
