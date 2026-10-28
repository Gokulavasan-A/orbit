package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.enterprise.api.service.IRateLimitService.Decision;
import com.enterprise.api.service.IRateLimitService.Limit;
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.Test;

class RateLimitServiceImplTest {

    @Test
    void allowsCapacityThenRejectsWithRetryAfter() {
        RateLimitServiceImpl service = new RateLimitServiceImpl(
                TestSecurityProperties.rateLimit(true, 3, Duration.ofMinutes(1)));

        for (int i = 0; i < 3; i++) {
            assertThat(service.tryConsume(Limit.LOGIN_PER_IP, "10.0.0.1").allowed()).isTrue();
        }
        Decision rejected = service.tryConsume(Limit.LOGIN_PER_IP, "10.0.0.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void bucketsAreSeparatedByLimitAndKey() {
        RateLimitServiceImpl service = new RateLimitServiceImpl(
                TestSecurityProperties.rateLimit(true, 1, Duration.ofMinutes(1)));

        assertThat(service.tryConsume(Limit.LOGIN_PER_IP, "10.0.0.1").allowed()).isTrue();
        assertThat(service.tryConsume(Limit.LOGIN_PER_IP, "10.0.0.1").allowed()).isFalse();
        assertThat(service.tryConsume(Limit.LOGIN_PER_IP, "10.0.0.2").allowed()).isTrue();
        assertThat(service.tryConsume(Limit.REFRESH_PER_IP, "10.0.0.1").allowed()).isTrue();
    }

    @Test
    void disabledLimitsAlwaysAllow() {
        RateLimitServiceImpl service = new RateLimitServiceImpl(
                TestSecurityProperties.rateLimit(false, 1, Duration.ofMinutes(1)));

        for (int i = 0; i < 5; i++) {
            assertThat(service.tryConsume(Limit.ANONYMOUS_PER_IP, "10.0.0.1").allowed()).isTrue();
        }
    }
}
