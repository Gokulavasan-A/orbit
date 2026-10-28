package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.entity.RefreshTokenRecord;
import com.enterprise.api.entity.RevocationResult;
import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.repository.IAuthRepository;
import com.enterprise.api.repository.IRefreshTokenRepository;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.repository.IUserRepository;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.IssuedToken;
import com.enterprise.api.security.RefreshTokenCodec;
import com.enterprise.api.security.RotatedRefreshToken;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("0b6e5f0e-7c1a-4d2b-9e3f-4a5b6c7d8e9f");
    private static final long USER_ID = 7L;
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.9", "JUnit");

    @Mock
    private IRefreshTokenRepository refreshTokenRepository;
    @Mock
    private ISessionRepository sessionRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private IAuthRepository authRepository;
    @Mock
    private ISessionValidationService sessionValidationService;

    private RefreshTokenServiceImpl service;
    private String rawToken;
    private String hash;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(refreshTokenRepository, sessionRepository, userRepository,
                authRepository, sessionValidationService, TestSecurityProperties.auth(), Clock.fixed(NOW, ZoneOffset.UTC));
        rawToken = RefreshTokenCodec.generate();
        hash = RefreshTokenCodec.hash(rawToken);
    }

    @Test
    void issueStoresOnlyTheHash() {
        when(refreshTokenRepository.save(eq(SESSION_ID), eq(USER_ID), anyString(), eq(NOW.plusSeconds(8 * 3600)),
                eq("10.0.0.9"), isNull())).thenReturn(NOW.plusSeconds(3600));

        IssuedToken token = service.issue(SESSION_ID, USER_ID, CLIENT);

        ArgumentCaptor<String> storedHash = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository).save(eq(SESSION_ID), eq(USER_ID), storedHash.capture(), any(), any(), isNull());
        assertThat(RefreshTokenCodec.isWellFormed(token.value())).isTrue();
        assertThat(storedHash.getValue()).isEqualTo(RefreshTokenCodec.hash(token.value())).hasSize(64).isLowerCase();
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test
    void rotationReplacesTheOldHash() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(RefreshTokenRecord.ACTIVE)));
        when(userRepository.findById(USER_ID)).thenReturn(detail(true, false));
        when(authRepository.findForLogin("jdoe")).thenReturn(Optional.of(loginUser(true, false)));
        when(refreshTokenRepository.save(eq(SESSION_ID), eq(USER_ID), anyString(), any(), eq("10.0.0.9"), eq(hash)))
                .thenReturn(NOW.plusSeconds(100));

        RotatedRefreshToken rotated = service.rotate(rawToken, CLIENT);

        assertThat(rotated.sessionId()).isEqualTo(SESSION_ID);
        assertThat(rotated.user().getPermissions()).containsExactly("USER_VIEW");
        assertThat(rotated.refreshToken().value()).isNotEqualTo(rawToken);
        verify(sessionRepository, never()).revoke(any(), anyString());
    }

    @Test
    void reusedTokenRevokesAllSessionsOfTheUser() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(RefreshTokenRecord.REUSED)));
        when(sessionRepository.revokeAllForUser(USER_ID, RefreshTokenServiceImpl.REASON_REUSE, null))
                .thenReturn(new RevocationResult());

        assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.REFRESH_TOKEN_REUSED);

        verify(sessionValidationService).evictUser(USER_ID);
        verify(refreshTokenRepository, never()).save(any(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void concurrentReuseDuringRotationIsTreatedAsReuse() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(RefreshTokenRecord.ACTIVE)));
        when(userRepository.findById(USER_ID)).thenReturn(detail(true, false));
        when(authRepository.findForLogin("jdoe")).thenReturn(Optional.of(loginUser(true, false)));
        when(refreshTokenRepository.save(any(), anyLong(), anyString(), any(), any(), eq(hash)))
                .thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED));
        when(sessionRepository.revokeAllForUser(USER_ID, RefreshTokenServiceImpl.REASON_REUSE, null))
                .thenReturn(new RevocationResult());

        assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.REFRESH_TOKEN_REUSED);

        verify(sessionRepository).revokeAllForUser(USER_ID, RefreshTokenServiceImpl.REASON_REUSE, null);
        verify(sessionValidationService).evictUser(USER_ID);
    }

    @Test
    void sessionInvalidStatusRevokesTheSession() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(RefreshTokenRecord.SESSION_INVALID)));

        assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.SESSION_INVALID);

        verify(sessionRepository).revoke(SESSION_ID, RefreshTokenServiceImpl.REASON_SESSION_INVALID);
        verify(sessionValidationService).evictSession(SESSION_ID);
    }

    @Test
    void refreshRejectsInactiveUserAndRevokesSession() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(RefreshTokenRecord.ACTIVE)));
        when(userRepository.findById(USER_ID)).thenReturn(detail(false, false));

        assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.SESSION_INVALID);

        verifyRejected(RefreshTokenServiceImpl.REASON_USER_INACTIVE);
    }

    @Test
    void refreshRejectsDeletedUserAndRevokesSession() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(RefreshTokenRecord.ACTIVE)));
        when(userRepository.findById(USER_ID)).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.SESSION_INVALID);

        verifyRejected(RefreshTokenServiceImpl.REASON_USER_DELETED);
    }

    @Test
    void refreshRejectsLockedUserAndRevokesSession() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(RefreshTokenRecord.ACTIVE)));
        when(userRepository.findById(USER_ID)).thenReturn(detail(true, true));

        assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.SESSION_INVALID);

        verifyRejected(RefreshTokenServiceImpl.REASON_USER_LOCKED);
    }

    @Test
    void expiredOrRevokedTokensAreRejectedWithoutSideEffects() {
        for (String status : List.of("EXPIRED", "REVOKED")) {
            when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.of(stored(status)));
            assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.SESSION_INVALID);
        }
        verifyNoInteractions(sessionRepository, userRepository, sessionValidationService);
    }

    @Test
    void unknownOrMalformedTokensNeverReachTheSessionLogic() {
        when(refreshTokenRepository.findByHash(hash)).thenReturn(Optional.empty());

        assertError(() -> service.rotate(rawToken, CLIENT), ErrorCode.SESSION_INVALID);
        assertError(() -> service.rotate(null, CLIENT), ErrorCode.SESSION_INVALID);
        assertError(() -> service.rotate("'; DROP TABLE x --", CLIENT), ErrorCode.SESSION_INVALID);

        verify(refreshTokenRepository).findByHash(hash);
        verifyNoInteractions(sessionRepository, userRepository);
    }

    @Test
    void revokeIgnoresUnknownTokens() {
        when(refreshTokenRepository.revoke(hash, "PASSWORD_CHANGE"))
                .thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        service.revoke(rawToken, "PASSWORD_CHANGE");
        service.revoke(null, "PASSWORD_CHANGE");

        verify(refreshTokenRepository).revoke(hash, "PASSWORD_CHANGE");
    }

    private void verifyRejected(String reason) {
        verify(sessionRepository).revoke(SESSION_ID, reason);
        verify(sessionValidationService).evictSession(SESSION_ID);
        verify(refreshTokenRepository, never()).save(any(), anyLong(), anyString(), any(), any(), any());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(expected);
    }

    private static RefreshTokenRecord stored(String status) {
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setId(1L);
        record.setSessionId(SESSION_ID);
        record.setUserId(USER_ID);
        record.setStatus(status);
        return record;
    }

    private static UserDetail detail(boolean active, boolean lockedOut) {
        UserDetail detail = new UserDetail();
        detail.setId(USER_ID);
        detail.setUsername("jdoe");
        detail.setActive(active);
        detail.setLockedOut(lockedOut);
        return detail;
    }

    private static LoginUser loginUser(boolean active, boolean lockedOut) {
        LoginUser user = new LoginUser();
        user.setId(USER_ID);
        user.setUsername("jdoe");
        user.setActive(active);
        user.setLockedOut(lockedOut);
        user.setRoles(List.of("User"));
        user.setPermissions(List.of("USER_VIEW"));
        return user;
    }
}
