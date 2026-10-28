package com.enterprise.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Refresh token values: 256 random bits, Base64url without padding (43 chars). Only
 * the SHA-256 hash (64 lowercase hex chars, the {@code CHAR(64)} column format) is
 * stored. Neither value is ever logged.
 */
public final class RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern WELL_FORMED = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenCodec() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Rejects anything that cannot be a token before it reaches the database. */
    public static boolean isWellFormed(String token) {
        return token != null && WELL_FORMED.matcher(token).matches();
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
