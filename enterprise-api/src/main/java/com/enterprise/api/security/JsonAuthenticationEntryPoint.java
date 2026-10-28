package com.enterprise.api.security;

import java.io.IOException;

import com.enterprise.api.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 as an {@code ApiResponse} envelope: {@code TOKEN_EXPIRED} for an expired access
 * token (the client should refresh), {@code TOKEN_INVALID} for any other bad token,
 * {@code UNAUTHORIZED} when no token was sent.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final int MAX_CAUSE_DEPTH = 10;

    private final ApiErrorResponseWriter writer;

    public JsonAuthenticationEntryPoint(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        ErrorCode code = classify(ex);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                code == ErrorCode.UNAUTHORIZED ? "Bearer" : "Bearer error=\"invalid_token\"");
        writer.write(response, code);
    }

    static ErrorCode classify(AuthenticationException ex) {
        if (!(ex instanceof OAuth2AuthenticationException)) {
            return ErrorCode.UNAUTHORIZED;
        }
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof JwtValidationException validation && validation.getErrors().stream()
                    .anyMatch(error -> JwtConfig.TOKEN_EXPIRED_ERROR.equals(error.getErrorCode()))) {
                return ErrorCode.TOKEN_EXPIRED;
            }
            current = current.getCause();
        }
        return ErrorCode.TOKEN_INVALID;
    }
}
