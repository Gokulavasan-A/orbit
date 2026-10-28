package com.enterprise.api.exception;

import java.util.List;

import com.enterprise.api.model.response.ApiError;
import com.enterprise.api.model.response.ApiResponse;
import com.enterprise.api.model.response.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception that reaches a controller to the {@link ApiResponse}
 * envelope. Clients only ever see {@link ErrorCode} keys and safe messages;
 * internal detail goes to the log together with the correlation id (MDC).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_VALUE = "has an invalid value";

    /** {@code @Valid} on request bodies and {@code @ModelAttribute} records, incl. type mismatches. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toDetail)
                .toList();
        return validationFailed(fieldErrors);
    }

    /** Constraint annotations directly on controller method parameters. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationFailed(List.of(new FieldErrorDetail(ex.getName(), INVALID_VALUE)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationFailed(List.of(new FieldErrorDetail(ex.getParameterName(), "is required")));
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldValidation(FieldValidationException ex) {
        return validationFailed(ex.getFieldErrors());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        ErrorCode code = ErrorCode.RATE_LIMITED;
        return ResponseEntity.status(code.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(ApiResponse.failure(ApiError.of(code)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return respond(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.getDefaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getDefaultMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("{}: {}", code, ex.getMessage(), ex);
        } else {
            log.warn("{} (SQL {}): {}", code, ex.getSqlErrorNumber(), ex.getMessage());
        }
        return respond(code, ex.getUserMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getDefaultMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage());
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ApiResponse<Void>> handleTechnical(TechnicalException ex) {
        log.error("Technical failure (SQL {}): {}", ex.getSqlErrorNumber(), ex.getMessage(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private static FieldErrorDetail toDetail(FieldError error) {
        // Binding failures carry conversion exception text; never echo it.
        String message = error.isBindingFailure() ? INVALID_VALUE : error.getDefaultMessage();
        return new FieldErrorDetail(error.getField(), message);
    }

    private static ResponseEntity<ApiResponse<Void>> validationFailed(List<FieldErrorDetail> fieldErrors) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        ApiError error = new ApiError(code.name(), code.getDefaultMessage(), fieldErrors);
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(error));
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.failure(ApiError.of(code, message)));
    }
}
