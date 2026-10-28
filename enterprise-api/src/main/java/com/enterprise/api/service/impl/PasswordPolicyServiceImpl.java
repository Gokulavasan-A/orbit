package com.enterprise.api.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.enterprise.api.config.properties.PasswordPolicyProperties;
import com.enterprise.api.service.IPasswordPolicyService;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicyServiceImpl implements IPasswordPolicyService {

    /** BCrypt ignores everything after 72 bytes (and Spring's encoder rejects longer input). */
    static final int BCRYPT_MAX_BYTES = 72;

    private final PasswordPolicyProperties policy;

    public PasswordPolicyServiceImpl(PasswordPolicyProperties policy) {
        this.policy = policy;
    }

    @Override
    public List<String> validate(String newPassword, String username, String currentPassword) {
        List<String> violations = new ArrayList<>();
        String password = newPassword == null ? "" : newPassword;
        int length = password.codePointCount(0, password.length());

        if (length < policy.minLength()) {
            violations.add("must be at least " + policy.minLength() + " characters");
        }
        if (length > policy.maxLength() || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            violations.add("must be at most " + policy.maxLength() + " characters");
        }
        if (policy.requireUppercase() && password.codePoints().noneMatch(Character::isUpperCase)) {
            violations.add("must contain an uppercase letter");
        }
        if (policy.requireLowercase() && password.codePoints().noneMatch(Character::isLowerCase)) {
            violations.add("must contain a lowercase letter");
        }
        if (policy.requireDigit() && password.codePoints().noneMatch(Character::isDigit)) {
            violations.add("must contain a digit");
        }
        if (policy.requireSpecial() && password.codePoints().noneMatch(PasswordPolicyServiceImpl::isSpecial)) {
            violations.add("must contain a special character");
        }
        if (password.codePoints().anyMatch(Character::isWhitespace)) {
            violations.add("must not contain spaces");
        }
        if (username != null && !username.isBlank() && password.equalsIgnoreCase(username.strip())) {
            violations.add("must not be the same as the username");
        }
        if (currentPassword != null && password.equals(currentPassword)) {
            violations.add("must be different from the current password");
        }
        return violations;
    }

    private static boolean isSpecial(int codePoint) {
        return !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint)
                && !Character.isISOControl(codePoint);
    }
}
