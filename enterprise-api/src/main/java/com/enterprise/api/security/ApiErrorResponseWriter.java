package com.enterprise.api.security;

import java.io.IOException;

import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.model.response.ApiError;
import com.enterprise.api.model.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the {@link ApiResponse} envelope from security filters, which run before
 * Spring MVC and therefore never reach the {@code GlobalExceptionHandler}.
 */
@Component
public class ApiErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public ApiErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.failure(ApiError.of(code)));
    }
}
