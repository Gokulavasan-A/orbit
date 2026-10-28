package com.enterprise.api.security;

/**
 * Caller details stored with sessions, refresh tokens and audit rows, already
 * trimmed to the column sizes.
 *
 * @param ipAddress client IP from {@link ClientIpResolver} (max 45 chars)
 * @param userAgent User-Agent header (max 512 chars), may be null
 */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_IP = 45;
    private static final int MAX_USER_AGENT = 512;

    public ClientInfo {
        ipAddress = truncate(ipAddress, MAX_IP);
        userAgent = truncate(userAgent, MAX_USER_AGENT);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
