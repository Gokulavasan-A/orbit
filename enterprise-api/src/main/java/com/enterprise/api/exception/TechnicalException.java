package com.enterprise.api.exception;

/**
 * An unexpected failure (unknown SQL error, connectivity, bug). Always returned
 * to the client as {@link ErrorCode#INTERNAL_ERROR} with a generic message; the
 * message and cause are for logs only.
 */
public class TechnicalException extends RuntimeException {

    private final Integer sqlErrorNumber;

    public TechnicalException(String message, Integer sqlErrorNumber, Throwable cause) {
        super(message, cause);
        this.sqlErrorNumber = sqlErrorNumber;
    }

    public Integer getSqlErrorNumber() {
        return sqlErrorNumber;
    }
}
