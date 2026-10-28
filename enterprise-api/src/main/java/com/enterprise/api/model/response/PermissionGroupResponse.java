package com.enterprise.api.model.response;

import java.util.List;

/** The active permissions of one module, ordered by code. */
public record PermissionGroupResponse(
        String module,
        List<PermissionResponse> permissions) {

    public PermissionGroupResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
