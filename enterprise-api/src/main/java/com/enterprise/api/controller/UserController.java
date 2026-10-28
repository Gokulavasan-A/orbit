package com.enterprise.api.controller;

import java.net.URI;
import java.util.UUID;

import com.enterprise.api.model.request.CreateUserRequest;
import com.enterprise.api.model.request.UpdateUserRequest;
import com.enterprise.api.model.request.UpdateUserRolesRequest;
import com.enterprise.api.model.request.UserPageRequest;
import com.enterprise.api.model.response.ApiResponse;
import com.enterprise.api.model.response.PagedResponse;
import com.enterprise.api.model.response.TemporaryPasswordResponse;
import com.enterprise.api.model.response.UserDetailResponse;
import com.enterprise.api.model.response.UserListItemResponse;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.ClientIpResolver;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

/** User administration. The contract for the UI is in {@code docs/users-and-roles.md}. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final IUserService userService;
    private final ClientIpResolver clientIpResolver;

    public UserController(IUserService userService, ClientIpResolver clientIpResolver) {
        this.userService = userService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAuthority('PERM_USER_VIEW')")
    @Operation(summary = "One page of the users grid (server-side paging, sorting, search and filters)")
    public ApiResponse<PagedResponse<UserListItemResponse>> getPaged(@Valid @ModelAttribute UserPageRequest request) {
        return ApiResponse.ok(userService.getPaged(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_USER_VIEW')")
    @Operation(summary = "One user with its roles")
    public ApiResponse<UserDetailResponse> getById(@PathVariable long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_USER_CREATE')")
    @Operation(summary = "Create a user; returns the generated temporary password once")
    public ResponseEntity<ApiResponse<TemporaryPasswordResponse>> create(@Valid @RequestBody CreateUserRequest body,
                                                                         @AuthenticationPrincipal Jwt jwt,
                                                                         HttpServletRequest request) {
        TemporaryPasswordResponse created = userService.create(body, CurrentUser.from(jwt), clientInfo(request));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.userId()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_USER_EDIT')")
    @Operation(summary = "Update the profile and active flag (concurrencyStamp required)")
    public ApiResponse<UserDetailResponse> update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest body,
                                                  @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(userService.update(id, body, CurrentUser.from(jwt), clientInfo(request)));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('PERM_USER_EDIT')")
    @Operation(summary = "Set the user's complete role list")
    public ApiResponse<UserDetailResponse> updateRoles(@PathVariable long id,
                                                       @Valid @RequestBody UpdateUserRolesRequest body,
                                                       @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ApiResponse.ok(userService.updateRoles(id, body, CurrentUser.from(jwt), clientInfo(request)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERM_USER_EDIT')")
    @Operation(summary = "Reset the password; returns a new temporary password once")
    public ApiResponse<TemporaryPasswordResponse> resetPassword(@PathVariable long id, @AuthenticationPrincipal Jwt jwt,
                                                                HttpServletRequest request) {
        return ApiResponse.ok(userService.resetPassword(id, CurrentUser.from(jwt), clientInfo(request)));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('PERM_USER_EDIT')")
    @Operation(summary = "Clear the failed sign-in lockout")
    public ApiResponse<UserDetailResponse> unlock(@PathVariable long id, @AuthenticationPrincipal Jwt jwt,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(userService.unlock(id, CurrentUser.from(jwt), clientInfo(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_USER_DELETE')")
    @Operation(summary = "Soft-delete a user (concurrencyStamp optional)")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam(required = false) UUID concurrencyStamp,
                                    @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        userService.delete(id, concurrencyStamp, CurrentUser.from(jwt), clientInfo(request));
        return ApiResponse.ok(null);
    }

    private ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
