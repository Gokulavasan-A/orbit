package com.enterprise.api.security;

import java.io.IOException;

import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.service.ISessionValidationService;
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
 * Runs right after bearer token authentication: a signed, unexpired token is only
 * accepted while its server-side session ({@code sid}) is still VALID. Rejected with
 * 401 {@code SESSION_INVALID}. Not a bean; created by {@link SecurityConfig} so it is
 * not also registered as a servlet filter.
 */
public class SessionValidationFilter extends OncePerRequestFilter {

    private final ISessionValidationService sessionValidationService;
    private final ApiErrorResponseWriter errorWriter;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public SessionValidationFilter(ISessionValidationService sessionValidationService,
                                   ApiErrorResponseWriter errorWriter) {
        this.sessionValidationService = sessionValidationService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            CurrentUser user = CurrentUser.from(jwtAuthentication.getToken());
            if (!sessionValidationService.isValid(user.sessionId(), user.userId())) {
                contextHolder.clearContext();
                errorWriter.write(response, ErrorCode.SESSION_INVALID);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
