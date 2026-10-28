package com.enterprise.api.model.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Row of the users grid ({@code usp_User_GetPaged}). Field names match the grid
 * sort column ids accepted by the procedure.
 */
public record UserListItemResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        boolean isActive,
        boolean isLockedOut,
        Instant lastLoginOn,
        Instant createdOn,
        Instant modifiedOn,
        List<String> roles,
        UUID concurrencyStamp) {
}
