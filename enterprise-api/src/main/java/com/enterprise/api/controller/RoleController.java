package com.enterprise.api.controller;

import java.net.URI;
import java.util.List;

import com.enterprise.api.model.request.CreateRoleRequest;
import com.enterprise.api.model.request.RolePageRequest;
import com.enterprise.api.model.request.UpdateRolePermissionsRequest;
import com.enterprise.api.model.request.UpdateRoleRequest;
import com.enterprise.api.model.response.ApiResponse;
import com.enterprise.api.model.response.PagedResponse;
import com.enterprise.api.model.response.RoleDetailResponse;
import com.enterprise.api.model.response.RoleListItemResponse;
import com.enterprise.api.model.response.RoleResponse;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.ClientIpResolver;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.service.IRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles. Reads need {@code ROLE_VIEW} (class level); writes need {@code ROLE_MANAGE}
 * (method level, which replaces the class-level rule).
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles")
@PreAuthorize("hasAuthority('PERM_ROLE_VIEW')")
public class RoleController {

    private final IRoleService roleService;
    private final ClientIpResolver clientIpResolver;

    public RoleController(IRoleService roleService, ClientIpResolver clientIpResolver) {
        this.roleService = roleService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    @Operation(summary = "All roles, for lists and dropdowns")
    public ApiResponse<List<RoleResponse>> getAll(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(roleService.getAll(includeInactive));
    }

    @GetMapping("/paged")
    @Operation(summary = "One page of the roles grid (server-side paging, sorting and search)")
    public ApiResponse<PagedResponse<RoleListItemResponse>> getPaged(@Valid @ModelAttribute RolePageRequest request) {
        return ApiResponse.ok(roleService.getPaged(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One role with its permissions")
    public ApiResponse<RoleDetailResponse> getById(@PathVariable long id) {
        return ApiResponse.ok(roleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Create a role")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> create(@Valid @RequestBody CreateRoleRequest body,
                                                                  @AuthenticationPrincipal Jwt jwt,
                                                                  HttpServletRequest request) {
        RoleDetailResponse created = roleService.create(body, CurrentUser.from(jwt), clientInfo(request));
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Update name, description and active flag (rowVersion required)")
    public ApiResponse<RoleDetailResponse> update(@PathVariable long id, @Valid @RequestBody UpdateRoleRequest body,
                                                  @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(roleService.update(id, body, CurrentUser.from(jwt), clientInfo(request)));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Set the role's complete permission list")
    public ApiResponse<RoleDetailResponse> updatePermissions(@PathVariable long id,
                                                             @Valid @RequestBody UpdateRolePermissionsRequest body,
                                                             @AuthenticationPrincipal Jwt jwt,
                                                             HttpServletRequest request) {
        return ApiResponse.ok(roleService.updatePermissions(id, body, CurrentUser.from(jwt), clientInfo(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    @Operation(summary = "Soft-delete a role (system roles are refused; rowVersion optional)")
    public ApiResponse<Void> delete(@PathVariable long id,
                                    @RequestParam(required = false)
                                    @Pattern(regexp = UpdateRoleRequest.ROW_VERSION_PATTERN, message = "must be a row version")
                                    String rowVersion,
                                    @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        roleService.delete(id, rowVersion, CurrentUser.from(jwt), clientInfo(request));
        return ApiResponse.ok(null);
    }

    private ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
