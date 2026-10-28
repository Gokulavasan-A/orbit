package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.api.exception.TechnicalException;
import com.enterprise.api.repository.ILoginAuditRepository;
import com.enterprise.api.security.ClientInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginAuditServiceImplTest {

    @Mock
    private ILoginAuditRepository repository;

    @Test
    void writesTheAuditRow() {
        new LoginAuditServiceImpl(repository).record(5L, " jdoe ", false, "BAD_PASSWORD", new ClientInfo("10.0.0.1", "UA"));

        verify(repository).insert(5L, "jdoe", false, "BAD_PASSWORD", "10.0.0.1", "UA");
    }

    @Test
    void truncatesTheUsernameToTheColumnSize() {
        new LoginAuditServiceImpl(repository).record(null, "x".repeat(300), false, "UNKNOWN_USER", null);

        verify(repository).insert(null, "x".repeat(256), false, "UNKNOWN_USER", null, null);
    }

    @Test
    void neverPropagatesFailures() {
        when(repository.insert(any(), anyString(), anyBoolean(), any(), any(), any()))
                .thenThrow(new TechnicalException("down", null, null));

        assertThatCode(() -> new LoginAuditServiceImpl(repository).record(1L, "jdoe", true, null, null))
                .doesNotThrowAnyException();
    }
}
