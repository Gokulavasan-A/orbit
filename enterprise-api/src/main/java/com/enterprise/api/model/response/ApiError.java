package com.enterprise.api.model.response;

import java.util.List;

import com.enterprise.api.exception.ErrorCode;

/**
 * Error part of {@link ApiResponse}.
 *
 * @param code        stable key from {@link ErrorCode} (e.g. {@code ROLE_NOT_FOUND})
 * @param message     message safe to show to the user
 * @param fieldErrors per-field validation messages; empty when not a validation error
 */
public record ApiError(String code, String message, List<FieldErrorDetail> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiError of(ErrorCode code) {
        return new ApiError(code.name(), code.getDefaultMessage(), List.of());
    }

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, List.of());
    }
}
