package com.enterprise.api.service.impl;

import java.security.SecureRandom;

import com.enterprise.api.config.properties.PasswordPolicyProperties;
import com.enterprise.api.service.IPasswordPolicyService;
import com.enterprise.api.service.ITemporaryPasswordGenerator;
import org.springframework.stereotype.Service;

/**
 * Generates temporary passwords with {@link SecureRandom}: at least one character of
 * every class (upper, lower, digit, special), no look-alike characters (0/O, 1/l/I),
 * shuffled, and checked against the password policy before it is returned.
 */
@Service
public class TemporaryPasswordGeneratorImpl implements ITemporaryPasswordGenerator {

    static final int DEFAULT_LENGTH = 16;
    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    static final String DIGITS = "23456789";
    static final String SPECIAL = "!@#$%^&*-_=+?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private static final String[] CLASSES = {UPPER, LOWER, DIGITS, SPECIAL};
    private static final int MAX_ATTEMPTS = 100;

    private final IPasswordPolicyService passwordPolicyService;
    private final SecureRandom random = new SecureRandom();
    private final int length;

    public TemporaryPasswordGeneratorImpl(PasswordPolicyProperties policy, IPasswordPolicyService passwordPolicyService) {
        this.passwordPolicyService = passwordPolicyService;
        this.length = Math.min(Math.max(DEFAULT_LENGTH, policy.minLength()), policy.maxLength());
    }

    @Override
    public String generate(String username) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = candidate();
            if (passwordPolicyService.validate(candidate, username, null).isEmpty()) {
                return candidate;
            }
        }
        // Only reachable with a policy that no generated password can satisfy.
        throw new IllegalStateException("Could not generate a temporary password that meets the password policy");
    }

    private String candidate() {
        char[] chars = new char[length];
        int i = 0;
        for (String characterClass : CLASSES) {
            chars[i++] = pick(characterClass);
        }
        while (i < length) {
            chars[i++] = pick(ALL);
        }
        // Fisher-Yates, so the guaranteed characters are not always at the start.
        for (int j = chars.length - 1; j > 0; j--) {
            int k = random.nextInt(j + 1);
            char tmp = chars[j];
            chars[j] = chars[k];
            chars[k] = tmp;
        }
        return new String(chars);
    }

    private char pick(String characters) {
        return characters.charAt(random.nextInt(characters.length()));
    }
}
