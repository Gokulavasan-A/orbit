package com.enterprise.api.controller;

import java.util.List;

import com.enterprise.api.model.response.ApiResponse;
import com.enterprise.api.model.response.PermissionGroupResponse;
import com.enterprise.api.service.IPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions")
@PreAuthorize("hasAuthority('PERM_ROLE_VIEW')")
public class PermissionController {

    private final IPermissionService permissionService;

    public PermissionController(IPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @Operation(summary = "Active permissions grouped by module, for the role editor")
    public ApiResponse<List<PermissionGroupResponse>> getGroupedByModule() {
        return ApiResponse.ok(permissionService.getGroupedByModule());
    }
}
