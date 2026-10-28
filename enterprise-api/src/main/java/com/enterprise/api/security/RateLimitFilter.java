package com.enterprise.api.security;

import java.io.IOException;

import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.service.IRateLimitService;
import com.enterprise.api.service.IRateLimitService.Decision;
import com.enterprise.api.service.IRateLimitService.Limit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-request limits: login and refresh per IP, authenticated requests per user,
 * everything else anonymous per IP. The per-username login limit is applied in
 * {@code AuthServiceImpl}, which knows the username. Runs after authentication, so
 * requests with an invalid token are rejected (401) before they are counted.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final IRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final ApiErrorResponseWriter errorWriter;
    private final RequestMatcher excluded;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    /** @param excluded requests that are never limited (health checks) */
    public RateLimitFilter(IRateLimitService rateLimitService, ClientIpResolver clientIpResolver,
                           ApiErrorResponseWriter errorWriter, RequestMatcher excluded) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.errorWriter = errorWriter;
        this.excluded = excluded;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return excluded.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Decision decision = decide(request);
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            errorWriter.write(response, ErrorCode.RATE_LIMITED);
            return;
        }
        chain.doFilter(request, response);
    }

    private Decision decide(HttpServletRequest request) {
        if (AuthPaths.LOGIN.matches(request)) {
            return rateLimitService.tryConsume(Limit.LOGIN_PER_IP, clientIpResolver.resolve(request));
        }
        if (AuthPaths.REFRESH.matches(request)) {
            return rateLimitService.tryConsume(Limit.REFRESH_PER_IP, clientIpResolver.resolve(request));
        }
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return rateLimitService.tryConsume(Limit.AUTHENTICATED_PER_USER, jwtAuthentication.getToken().getSubject());
        }
        return rateLimitService.tryConsume(Limit.ANONYMOUS_PER_IP, clientIpResolver.resolve(request));
    }
}
