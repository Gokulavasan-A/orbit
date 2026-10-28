package com.enterprise.api.util;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * Correlation id of the current request or job, kept in the logging MDC so it
 * appears in every log line and can be passed to stored procedures as
 * {@code @CorrelationId}.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Restrictive on purpose: the value is echoed in headers and written to logs. */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
