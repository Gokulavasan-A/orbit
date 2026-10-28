package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.api.config.properties.PasswordPolicyProperties;
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.Test;

class PasswordPolicyServiceImplTest {

    private final PasswordPolicyServiceImpl policy = new PasswordPolicyServiceImpl(TestSecurityProperties.passwordPolicy());

    @Test
    void acceptsACompliantPassword() {
        assertThat(policy.validate("Str0ng#Passw0rd", "jdoe", "Old#Password123")).isEmpty();
    }

    @Test
    void requiresMinimumLength() {
        assertThat(policy.validate("Sh0rt#Pass", "jdoe", null)).containsExactly("must be at least 12 characters");
    }

    @Test
    void requiresEveryCharacterClass() {
        assertThat(policy.validate("alllowercase#123", "jdoe", null)).containsExactly("must contain an uppercase letter");
        assertThat(policy.validate("ALLUPPERCASE#123", "jdoe", null)).containsExactly("must contain a lowercase letter");
        assertThat(policy.validate("NoDigitsHere#Abc", "jdoe", null)).containsExactly("must contain a digit");
        assertThat(policy.validate("NoSpecial1234Abc", "jdoe", null)).containsExactly("must contain a special character");
    }

    @Test
    void rejectsTheUsernameInAnyCase() {
        assertThat(policy.validate("Admin#User1234", "admin#user1234", null))
                .containsExactly("must not be the same as the username");
    }

    @Test
    void rejectsTheCurrentPassword() {
        assertThat(policy.validate("Same#Password1", "jdoe", "Same#Password1"))
                .containsExactly("must be different from the current password");
    }

    @Test
    void rejectsSpacesAndOverlongPasswords() {
        assertThat(policy.validate("Has Space#1234", "jdoe", null)).containsExactly("must not contain spaces");
        assertThat(policy.validate("Aa1#" + "x".repeat(61), "jdoe", null)).containsExactly("must be at most 64 characters");
        // Only 27 characters, but 4 + 23 * 3 = 73 UTF-8 bytes: over the BCrypt limit of 72.
        String multiByte = "Aa1#" + "€".repeat(23);
        assertThat(policy.validate(multiByte, "jdoe", null)).containsExactly("must be at most 64 characters");
    }

    @Test
    void rulesCanBeSwitchedOff() {
        PasswordPolicyServiceImpl relaxed = new PasswordPolicyServiceImpl(
                new PasswordPolicyProperties(8, 64, false, false, false, false));
        assertThat(relaxed.validate("abcdefgh", "jdoe", null)).isEmpty();
    }
}
