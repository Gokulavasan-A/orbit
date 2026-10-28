package com.enterprise.api.model.response;

/** One permission in the role editor. */
public record PermissionResponse(
        Long id,
        String code,
        String name,
        String description) {
}
