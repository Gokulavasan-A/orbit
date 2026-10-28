package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.enterprise.api.entity.SessionValidation;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionValidationServiceImplTest {

    private static final UUID SESSION_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID SESSION_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @Mock
    private ISessionRepository sessionRepository;

    private SessionValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SessionValidationServiceImpl(sessionRepository, TestSecurityProperties.auth());
    }

    @Test
    void validResultIsCachedAndPassesTheIdleTimeout() {
        when(sessionRepository.validate(SESSION_A, 30)).thenReturn(validation(SESSION_A, 1L, "VALID"));

        assertThat(service.isValid(SESSION_A, 1L)).isTrue();
        assertThat(service.isValid(SESSION_A, 1L)).isTrue();

        verify(sessionRepository, times(1)).validate(SESSION_A, 30);
    }

    @Test
    void invalidResultsAreNeverCached() {
        when(sessionRepository.validate(SESSION_A, 30)).thenReturn(validation(SESSION_A, 1L, "IDLE"));

        assertThat(service.isValid(SESSION_A, 1L)).isFalse();
        assertThat(service.isValid(SESSION_A, 1L)).isFalse();

        verify(sessionRepository, times(2)).validate(SESSION_A, 30);
    }

    @Test
    void sessionOfAnotherUserIsRejected() {
        when(sessionRepository.validate(SESSION_A, 30)).thenReturn(validation(SESSION_A, 2L, "VALID"));

        assertThat(service.isValid(SESSION_A, 1L)).isFalse();
    }

    @Test
    void evictSessionForcesANewCheck() {
        when(sessionRepository.validate(SESSION_A, 30))
                .thenReturn(validation(SESSION_A, 1L, "VALID"), validation(SESSION_A, 1L, "REVOKED"));

        assertThat(service.isValid(SESSION_A, 1L)).isTrue();
        service.evictSession(SESSION_A);
        assertThat(service.isValid(SESSION_A, 1L)).isFalse();
    }

    @Test
    void evictUserDropsOnlyThatUsersSessions() {
        when(sessionRepository.validate(SESSION_A, 30)).thenReturn(validation(SESSION_A, 1L, "VALID"));
        when(sessionRepository.validate(SESSION_B, 30)).thenReturn(validation(SESSION_B, 2L, "VALID"));
        service.isValid(SESSION_A, 1L);
        service.isValid(SESSION_B, 2L);

        service.evictUser(1L);
        service.isValid(SESSION_A, 1L);
        service.isValid(SESSION_B, 2L);

        verify(sessionRepository, times(2)).validate(SESSION_A, 30);
        verify(sessionRepository, times(1)).validate(SESSION_B, 30);
    }

    private static SessionValidation validation(UUID sessionId, long userId, String status) {
        SessionValidation validation = new SessionValidation();
        validation.setSessionId(sessionId);
        validation.setUserId(userId);
        validation.setStatus(status);
        return validation;
    }
}
