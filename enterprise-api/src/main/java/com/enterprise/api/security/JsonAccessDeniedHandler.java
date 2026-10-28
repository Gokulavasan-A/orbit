package com.enterprise.api.security;

import java.io.IOException;

import com.enterprise.api.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** 403 as an {@code ApiResponse} envelope: {@code CSRF_INVALID} for CSRF failures, otherwise {@code ACCESS_DENIED}. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter writer;

    public JsonAccessDeniedHandler(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writer.write(response, ex instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED);
    }
}
