package com.enterprise.api.model.response;

/** A role assigned to a user. An inactive role grants no permissions. */
public record UserRoleResponse(
        Long id,
        String name,
        boolean isActive) {
}
