package com.enterprise.api.exception;

import java.util.List;

import com.enterprise.api.model.response.FieldErrorDetail;

/**
 * Validation that only a service can do (current password check, password policy).
 * Answered like a Bean Validation failure: 400 {@link ErrorCode#VALIDATION_FAILED}
 * with field errors. Messages must be safe, fixed texts.
 */
public class FieldValidationException extends RuntimeException {

    private final List<FieldErrorDetail> fieldErrors;

    public FieldValidationException(List<FieldErrorDetail> fieldErrors) {
        super("Field validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public FieldValidationException(String field, String message) {
        this(List.of(new FieldErrorDetail(field, message)));
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
