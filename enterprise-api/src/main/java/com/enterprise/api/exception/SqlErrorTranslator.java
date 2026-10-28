package com.enterprise.api.exception;

import java.sql.SQLException;

import org.springframework.stereotype.Component;

/**
 * Turns a failed stored procedure call into a {@link BusinessException} (documented
 * {@code THROW 5xxxx} codes) or a {@link TechnicalException} (everything else).
 * <p>
 * Only {@link ErrorCode#INVALID_ARGUMENT} (50003) passes the procedure's own message
 * to the client: those messages are authored validation texts in the procedures.
 * Every other code uses the fixed message from {@link ErrorCode}.
 */
@Component
public class SqlErrorTranslator {

    static final int MAX_USER_MESSAGE_LENGTH = 300;
    private static final int MAX_CAUSE_DEPTH = 20;

    public RuntimeException translate(String procedureName, Throwable failure) {
        SQLException sqlException = findSqlException(failure);
        if (sqlException == null) {
            return new TechnicalException("Call to " + procedureName + " failed", null, failure);
        }

        int number = sqlException.getErrorCode();
        String internalMessage = procedureName + " raised " + number + ": " + sqlException.getMessage();

        return ErrorCode.fromSqlErrorNumber(number)
                .<RuntimeException>map(code -> new BusinessException(code, userMessage(code, sqlException),
                        number, internalMessage, failure))
                .orElseGet(() -> new TechnicalException(internalMessage, number, failure));
    }

    static SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException sql) {
                return sql;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String userMessage(ErrorCode code, SQLException sqlException) {
        if (code == ErrorCode.INVALID_ARGUMENT) {
            String message = sqlException.getMessage();
            if (message != null && !message.isBlank()) {
                message = message.strip();
                return message.length() > MAX_USER_MESSAGE_LENGTH ? message.substring(0, MAX_USER_MESSAGE_LENGTH) : message;
            }
        }
        return code.getDefaultMessage();
    }
}
