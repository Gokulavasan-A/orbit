package com.enterprise.api.exception;

/**
 * An expected, documented failure (validation, not found, conflict, ...).
 * {@link #getUserMessage()} is safe to return to the client; {@link #getMessage()}
 * may carry internal detail and is for logs only.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Integer sqlErrorNumber;
    private final String userMessage;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null, errorCode.getDefaultMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String userMessage) {
        this(errorCode, userMessage, null, userMessage, null);
    }

    public BusinessException(ErrorCode errorCode, String userMessage, Integer sqlErrorNumber,
                             String internalMessage, Throwable cause) {
        super(internalMessage, cause);
        this.errorCode = errorCode;
        this.sqlErrorNumber = sqlErrorNumber;
        this.userMessage = userMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Integer getSqlErrorNumber() {
        return sqlErrorNumber;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
