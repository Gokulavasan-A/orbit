package com.enterprise.api.controller;

import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.model.request.ChangePasswordRequest;
import com.enterprise.api.model.request.LoginRequest;
import com.enterprise.api.model.response.ApiResponse;
import com.enterprise.api.model.response.AuthResponse;
import com.enterprise.api.model.response.CsrfTokenResponse;
import com.enterprise.api.model.response.LogoutAllResponse;
import com.enterprise.api.model.response.MeResponse;
import com.enterprise.api.security.AuthPaths;
import com.enterprise.api.security.AuthResult;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.ClientIpResolver;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.security.RefreshTokenCookies;
import com.enterprise.api.service.IAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Call order and cookie/header names are described in
 * {@code docs/security.md}. The refresh token only ever travels in the HttpOnly cookie.
 */
@RestController
@RequestMapping(AuthPaths.BASE)
@Tag(name = "Auth")
public class AuthController {

    private final IAuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final ClientIpResolver clientIpResolver;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(IAuthService authService, RefreshTokenCookies refreshTokenCookies,
                          ClientIpResolver clientIpResolver, CsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.refreshTokenCookies = refreshTokenCookies;
        this.clientIpResolver = clientIpResolver;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * Sets the {@code XSRF-TOKEN} cookie (if not already present) and returns the same
     * raw value, for clients that cannot read the cookie (UI and API on different hosts).
     */
    @GetMapping("/csrf")
    @SecurityRequirements
    @Operation(summary = "Get the CSRF token (also set as the XSRF-TOKEN cookie)")
    public ApiResponse<CsrfTokenResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        // The raw repository token (the request attribute "_csrf" holds a masked copy). The
        // deferred token is shared with CsrfFilter and caches its value, so the cookie is
        // written at most once per request.
        CsrfToken token;
        if (request.getAttribute(DeferredCsrfToken.class.getName()) instanceof DeferredCsrfToken deferred) {
            token = deferred.get();
        } else {
            token = csrfTokenRepository.loadToken(request);
            if (token == null) {
                token = csrfTokenRepository.generateToken(request);
                csrfTokenRepository.saveToken(token, request, response);
            }
        }
        return ApiResponse.ok(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in; returns the access token and sets the refresh cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest body,
                                                           HttpServletRequest request) {
        return withRefreshCookie(authService.login(body, clientInfo(request)));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate the refresh cookie and get a new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return withRefreshCookie(authService.refresh(refreshTokenCookies.read(request), clientInfo(request)));
        } catch (BusinessException ex) {
            // Kept on the response when the exception handler writes the error body.
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString());
            throw ex;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out the current session")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(CurrentUser.from(jwt));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .body(ApiResponse.ok(null));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Sign out all other sessions of the current user")
    public ApiResponse<LogoutAllResponse> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authService.logoutAll(CurrentUser.from(jwt)));
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user with the roles and permissions of the current token")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(authService.me(CurrentUser.from(jwt)));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the own password; returns fresh tokens and signs out other sessions")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                                                    @AuthenticationPrincipal Jwt jwt,
                                                                    HttpServletRequest request) {
        return withRefreshCookie(authService.changePassword(CurrentUser.from(jwt), body,
                refreshTokenCookies.read(request), clientInfo(request)));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    private ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
