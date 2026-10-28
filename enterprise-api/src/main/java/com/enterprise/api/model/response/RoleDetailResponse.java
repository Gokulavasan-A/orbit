package com.enterprise.api.model.response;

import java.time.Instant;
import java.util.List;

/**
 * One role for the edit screen ({@code usp_Role_GetById}).
 *
 * @param rowVersion      Base64 of the 8-byte RowVersion, sent back on update/delete
 * @param permissionIds   assigned permission ids (used by {@code PUT /roles/{id}/permissions})
 * @param permissionCodes the same permissions as codes, ordered by code
 */
public record RoleDetailResponse(
        Long id,
        String name,
        String description,
        boolean isSystem,
        boolean isActive,
        int userCount,
        Instant createdOn,
        Instant modifiedOn,
        String rowVersion,
        List<Long> permissionIds,
        List<String> permissionCodes) {
}
