package com.enterprise.api.service;

/** Bucket4j rate limits ({@code app.security.rate-limit.*}). */
public interface IRateLimitService {

    enum Limit {
        LOGIN_PER_IP,
        LOGIN_PER_USERNAME,
        REFRESH_PER_IP,
        AUTHENTICATED_PER_USER,
        ANONYMOUS_PER_IP
    }

    /**
     * @param allowed           true when a token was consumed
     * @param retryAfterSeconds when not allowed: seconds until the next token (at least 1)
     */
    record Decision(boolean allowed, long retryAfterSeconds) {

        public static final Decision ALLOWED = new Decision(true, 0);
    }

    /** Consumes one token from the bucket of {@code limit} for {@code key} (IP, normalized username or user id). */
    Decision tryConsume(Limit limit, String key);
}
