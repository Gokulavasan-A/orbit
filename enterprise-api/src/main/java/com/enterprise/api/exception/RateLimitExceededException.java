package com.enterprise.api.exception;

/**
 * A Bucket4j limit was hit inside a service (the per-username login limit). The
 * HTTP layer answers 429 {@link ErrorCode#RATE_LIMITED} with a {@code Retry-After} header.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
