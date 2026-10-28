package com.enterprise.api.model.response;

import java.time.Instant;

import com.enterprise.api.util.CorrelationId;

/**
 * Standard envelope for every API response.
 *
 * @param success       true when the request succeeded
 * @param data          payload on success, null on failure
 * @param error         error details on failure, null on success
 * @param correlationId correlation id of the request (also in the {@code X-Correlation-Id} header)
 * @param timestamp     server time (UTC) when the response was created
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, String correlationId, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, CorrelationId.current(), Instant.now());
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, error, CorrelationId.current(), Instant.now());
    }
}
