package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import com.enterprise.api.config.properties.PasswordPolicyProperties;
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorImplTest {

    private final PasswordPolicyServiceImpl policyService =
            new PasswordPolicyServiceImpl(TestSecurityProperties.passwordPolicy());
    private final TemporaryPasswordGeneratorImpl generator =
            new TemporaryPasswordGeneratorImpl(TestSecurityProperties.passwordPolicy(), policyService);

    @Test
    void everyGeneratedPasswordMeetsThePolicy() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String password = generator.generate("jdoe");

            assertThat(policyService.validate(password, "jdoe", null)).as(password).isEmpty();
            assertThat(password).hasSize(TemporaryPasswordGeneratorImpl.DEFAULT_LENGTH);
            assertThat(password).doesNotContainAnyWhitespaces();
            assertThat(password.chars()).as("no look-alike characters").noneMatch(c -> "0O1lI".indexOf(c) >= 0);
            seen.add(password);
        }
        assertThat(seen).as("no repeats").hasSize(1000);
    }

    @Test
    void containsEveryCharacterClass() {
        String password = generator.generate("jdoe");

        assertThat(password).containsAnyOf(chars(TemporaryPasswordGeneratorImpl.UPPER));
        assertThat(password).containsAnyOf(chars(TemporaryPasswordGeneratorImpl.LOWER));
        assertThat(password).containsAnyOf(chars(TemporaryPasswordGeneratorImpl.DIGITS));
        assertThat(password).containsAnyOf(chars(TemporaryPasswordGeneratorImpl.SPECIAL));
    }

    @Test
    void lengthFollowsALongerPolicyMinimum() {
        PasswordPolicyProperties longer = new PasswordPolicyProperties(24, 64, true, true, true, true);
        TemporaryPasswordGeneratorImpl longGenerator =
                new TemporaryPasswordGeneratorImpl(longer, new PasswordPolicyServiceImpl(longer));

        assertThat(longGenerator.generate("jdoe")).hasSize(24);
    }

    @Test
    void lengthIsCappedAtThePolicyMaximum() {
        PasswordPolicyProperties shortMax = new PasswordPolicyProperties(8, 12, true, true, true, true);
        TemporaryPasswordGeneratorImpl shortGenerator =
                new TemporaryPasswordGeneratorImpl(shortMax, new PasswordPolicyServiceImpl(shortMax));

        assertThat(shortGenerator.generate("jdoe")).hasSize(12);
    }

    @Test
    void failsWhenNoPasswordCanSatisfyThePolicy() {
        TemporaryPasswordGeneratorImpl impossible = new TemporaryPasswordGeneratorImpl(
                TestSecurityProperties.passwordPolicy(), (password, username, current) -> java.util.List.of("never"));

        assertThatThrownBy(() -> impossible.generate("jdoe")).isInstanceOf(IllegalStateException.class);
    }

    private static String[] chars(String characters) {
        return characters.chars().mapToObj(c -> String.valueOf((char) c)).toArray(String[]::new);
    }
}
