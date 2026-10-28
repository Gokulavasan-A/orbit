package com.enterprise.api.model.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One user for the edit screen ({@code usp_User_GetById}). Never contains the password hash.
 *
 * @param concurrencyStamp sent back on update/delete; logins don't change it
 */
public record UserDetailResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        boolean isActive,
        boolean isLockedOut,
        boolean mustChangePassword,
        Instant lastLoginOn,
        Instant passwordChangedOn,
        Instant createdOn,
        Instant modifiedOn,
        List<UserRoleResponse> roles,
        UUID concurrencyStamp) {
}
