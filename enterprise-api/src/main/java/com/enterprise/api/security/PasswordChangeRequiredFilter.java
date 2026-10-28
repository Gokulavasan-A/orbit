package com.enterprise.api.security;

import java.io.IOException;

import com.enterprise.api.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * While the access token carries {@code pwd_change = true}, only
 * {@code GET /me}, {@code POST /change-password} and {@code POST /logout} are allowed;
 * every other authenticated request gets 403 {@code PASSWORD_CHANGE_REQUIRED}.
 * Anonymous endpoints (csrf, login, refresh) are not affected.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final ApiErrorResponseWriter errorWriter;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public PasswordChangeRequiredFilter(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && Boolean.TRUE.equals(jwtAuthentication.getToken().getClaimAsBoolean(TokenClaims.PASSWORD_CHANGE))
                && !AuthPaths.ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED.matches(request)) {
            errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }
}
