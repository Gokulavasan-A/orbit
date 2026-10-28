package com.enterprise.api.model.response;

/**
 * Role for lists and dropdowns ({@code usp_Role_GetAll}).
 *
 * @param rowVersion Base64 of the 8-byte RowVersion, sent back on update/delete
 */
public record RoleResponse(
        Long id,
        String name,
        String description,
        boolean isSystem,
        boolean isActive,
        String rowVersion) {
}
