package com.enterprise.api.exception;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

/**
 * Stable error keys returned in {@code ApiResponse.error.code}, with their HTTP
 * status and a message that is safe to show to users.
 * <p>
 * Codes with a SQL error number mirror {@code enterprise-db/docs/error-codes.md}
 * ({@code THROW 5xxxx}). Keep the two in sync when the database adds a code.
 */
public enum ErrorCode {

    // --- API-level ---------------------------------------------------------
    VALIDATION_FAILED(null, HttpStatus.BAD_REQUEST, "One or more fields are invalid."),
    BAD_REQUEST(null, HttpStatus.BAD_REQUEST, "The request could not be read."),
    UNAUTHORIZED(null, HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(null, HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    NOT_FOUND(null, HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(null, HttpStatus.METHOD_NOT_ALLOWED, "This HTTP method is not supported for this resource."),
    RATE_LIMITED(null, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait and try again."),

    // --- API-level: authentication and security ------------------------------
    INVALID_CREDENTIALS(null, HttpStatus.UNAUTHORIZED, "Invalid username or password."),
    ACCOUNT_LOCKED(null, HttpStatus.LOCKED,
            "This account is temporarily locked because of too many failed sign-in attempts. Please try again later."),
    TOKEN_EXPIRED(null, HttpStatus.UNAUTHORIZED, "Your access token has expired."),
    TOKEN_INVALID(null, HttpStatus.UNAUTHORIZED, "The access token is invalid."),
    PASSWORD_CHANGE_REQUIRED(null, HttpStatus.FORBIDDEN, "You must change your password before continuing."),
    CSRF_INVALID(null, HttpStatus.FORBIDDEN, "The CSRF token is missing or invalid. Reload and try again."),
    INTERNAL_ERROR(null, HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please quote the correlation id when reporting it."),

    // --- API-level: user management (checked before the database is called) ----
    SELF_DEACTIVATE_NOT_ALLOWED(null, HttpStatus.BAD_REQUEST, "You cannot deactivate your own account."),
    SELF_DELETE_NOT_ALLOWED(null, HttpStatus.BAD_REQUEST, "You cannot delete your own account."),
    SELF_PASSWORD_RESET_NOT_ALLOWED(null, HttpStatus.BAD_REQUEST,
            "You cannot reset your own password. Use change password instead."),

    // --- 50000-50099 general ------------------------------------------------
    CONCURRENCY_CONFLICT(50002, HttpStatus.CONFLICT, "The record was modified by someone else. Reload and try again."),
    INVALID_ARGUMENT(50003, HttpStatus.BAD_REQUEST, "The request contains an invalid or missing value."),
    LAST_ACTIVE_ADMIN(50004, HttpStatus.CONFLICT, "This would remove the last active administrator."),

    // --- 50100-50199 auth ---------------------------------------------------
    AUTH_USER_INACTIVE(50101, HttpStatus.UNAUTHORIZED, "This account cannot sign in."),

    // --- 50200-50299 session/token -------------------------------------------
    SESSION_NOT_FOUND(50201, HttpStatus.NOT_FOUND, "The session was not found."),
    SESSION_INVALID(50202, HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again."),
    REFRESH_TOKEN_NOT_FOUND(50203, HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again."),
    REFRESH_TOKEN_REUSED(50204, HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again."),
    SESSION_USER_MISMATCH(50205, HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again."),

    // --- 50300-50399 user ---------------------------------------------------
    USERNAME_EXISTS(50301, HttpStatus.CONFLICT, "This username is already taken."),
    EMAIL_EXISTS(50302, HttpStatus.CONFLICT, "This email address is already in use."),
    USER_NOT_FOUND(50303, HttpStatus.NOT_FOUND, "The user was not found."),
    CANNOT_MODIFY_SELF(50304, HttpStatus.CONFLICT, "You cannot deactivate or delete your own account."),

    // --- 50400-50499 role ---------------------------------------------------
    SYSTEM_ROLE_PROTECTED(50401, HttpStatus.CONFLICT, "System roles cannot be renamed, deactivated or deleted."),
    ROLE_NAME_EXISTS(50402, HttpStatus.CONFLICT, "A role with this name already exists."),
    ROLE_NOT_FOUND(50403, HttpStatus.NOT_FOUND, "The role was not found."),

    // --- 50500-50599 assignment/permission ------------------------------------
    PERMISSION_NOT_FOUND(50501, HttpStatus.NOT_FOUND, "One or more permissions were not found."),
    ADMIN_ROLE_MANAGE_REQUIRED(50502, HttpStatus.CONFLICT, "ROLE_MANAGE cannot be removed from the Admin role.");

    private static final Map<Integer, ErrorCode> BY_SQL_ERROR = Arrays.stream(values())
            .filter(code -> code.sqlErrorNumber != null)
            .collect(Collectors.toUnmodifiableMap(code -> code.sqlErrorNumber, Function.identity()));

    private final Integer sqlErrorNumber;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(Integer sqlErrorNumber, HttpStatus status, String defaultMessage) {
        this.sqlErrorNumber = sqlErrorNumber;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public static Optional<ErrorCode> fromSqlErrorNumber(int sqlErrorNumber) {
        return Optional.ofNullable(BY_SQL_ERROR.get(sqlErrorNumber));
    }

    public Integer getSqlErrorNumber() {
        return sqlErrorNumber;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
