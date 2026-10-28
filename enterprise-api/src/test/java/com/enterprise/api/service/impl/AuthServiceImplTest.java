package com.enterprise.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.enterprise.api.entity.LoginAttemptResult;
import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.entity.PasswordChangeResult;
import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.entity.UserSession;
import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.exception.FieldValidationException;
import com.enterprise.api.exception.RateLimitExceededException;
import com.enterprise.api.mapper.AuthMapperImpl;
import com.enterprise.api.model.request.ChangePasswordRequest;
import com.enterprise.api.model.request.LoginRequest;
import com.enterprise.api.repository.IAuthRepository;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.repository.IUserRepository;
import com.enterprise.api.security.AuthResult;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.security.IssuedToken;
import com.enterprise.api.security.TokenClaims;
import com.enterprise.api.service.ILoginAuditService;
import com.enterprise.api.service.IRateLimitService;
import com.enterprise.api.service.IRefreshTokenService;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.service.ITokenService;
import com.enterprise.api.support.TestSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final String PASSWORD = "Correct#Horse9";
    private static final ClientInfo CLIENT = new ClientInfo("10.1.2.3", "JUnit");
    private static final UUID SESSION_ID = UUID.fromString("6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b");
    /** Low cost keeps the tests fast; production uses 12. */
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD_HASH = ENCODER.encode(PASSWORD);

    @Mock
    private IAuthRepository authRepository;
    @Mock
    private ISessionRepository sessionRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private ITokenService tokenService;
    @Mock
    private IRefreshTokenService refreshTokenService;
    @Mock
    private ISessionValidationService sessionValidationService;
    @Mock
    private IRateLimitService rateLimitService;
    @Mock
    private ILoginAuditService loginAuditService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(authRepository, sessionRepository, userRepository, tokenService,
                refreshTokenService, sessionValidationService,
                new PasswordPolicyServiceImpl(TestSecurityProperties.passwordPolicy()), rateLimitService,
                loginAuditService, ENCODER, new AuthMapperImpl(), TestSecurityProperties.auth(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(rateLimitService.tryConsume(any(), anyString())).thenReturn(IRateLimitService.Decision.ALLOWED);
        lenient().when(tokenService.issueAccessToken(any()))
                .thenReturn(new IssuedToken("access-token", NOW.plusSeconds(900)));
    }

    @Test
    void loginSucceedsAndIssuesTokens() {
        LoginUser user = user(true, false, false);
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user));
        when(sessionRepository.create(1L, 480, "10.1.2.3", "JUnit")).thenReturn(session());
        when(refreshTokenService.issue(SESSION_ID, 1L, CLIENT)).thenReturn(new IssuedToken("refresh", NOW.plusSeconds(28800)));

        AuthResult result = authService.login(new LoginRequest("  admin ", PASSWORD), CLIENT);

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(900);
        assertThat(result.response().user().username()).isEqualTo("admin");
        assertThat(result.response().roles()).containsExactly("Admin");
        assertThat(result.response().permissions()).containsExactly("ROLE_VIEW", "USER_VIEW");
        assertThat(result.response().mustChangePassword()).isFalse();
        assertThat(result.refreshToken().value()).isEqualTo("refresh");

        verify(authRepository).recordSuccessfulLogin(1L);
        verify(authRepository, never()).recordFailedLogin(anyLong(), anyInt(), anyInt());
        verify(loginAuditService).record(1L, "admin", true, null, CLIENT);
        ArgumentCaptor<TokenClaims> claims = ArgumentCaptor.forClass(TokenClaims.class);
        verify(tokenService).issueAccessToken(claims.capture());
        assertThat(claims.getValue()).isEqualTo(new TokenClaims(1L, SESSION_ID, List.of("Admin"),
                List.of("ROLE_VIEW", "USER_VIEW"), false));
    }

    @Test
    void wrongPasswordRecordsFailureAndReturnsGenericError() {
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user(true, false, false)));
        when(authRepository.recordFailedLogin(1L, 5, 15)).thenReturn(attempt(false));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "Wrong#Password1"), CLIENT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(authRepository).recordFailedLogin(1L, 5, 15);
        verify(authRepository, never()).recordSuccessfulLogin(anyLong());
        verify(sessionRepository, never()).create(anyLong(), anyInt(), any(), any());
        verify(loginAuditService).record(1L, "admin", false, AuthServiceImpl.FAILURE_BAD_PASSWORD, CLIENT);
    }

    @Test
    void unknownUserReturnsSameGenericError() {
        when(authRepository.findForLogin("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", PASSWORD), CLIENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(business.getUserMessage()).isEqualTo("Invalid username or password.");
                });

        verify(authRepository, never()).recordFailedLogin(anyLong(), anyInt(), anyInt());
        verify(loginAuditService).record(null, "ghost", false, AuthServiceImpl.FAILURE_UNKNOWN_USER, CLIENT);
    }

    @Test
    void lockedAccountIsRejectedWithoutCheckingThePassword() {
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user(true, true, false)));

        // Even the correct password is refused while locked.
        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", PASSWORD), CLIENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
                    assertThat(business.getUserMessage()).doesNotContainPattern("\\d");
                });

        verify(authRepository, never()).recordFailedLogin(anyLong(), anyInt(), anyInt());
        verify(authRepository, never()).recordSuccessfulLogin(anyLong());
        verify(loginAuditService).record(1L, "admin", false, AuthServiceImpl.FAILURE_LOCKED, CLIENT);
    }

    @Test
    void inactiveUserGetsGenericErrorEvenWithCorrectPassword() {
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user(false, false, false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", PASSWORD), CLIENT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(authRepository, never()).recordFailedLogin(anyLong(), anyInt(), anyInt());
        verify(sessionRepository, never()).create(anyLong(), anyInt(), any(), any());
        verify(loginAuditService).record(1L, "admin", false, AuthServiceImpl.FAILURE_INACTIVE, CLIENT);
    }

    @Test
    void failureThatReachesTheThresholdLocksTheAccount() {
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user(true, false, false)));
        when(authRepository.recordFailedLogin(1L, 5, 15)).thenReturn(attempt(true));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "Wrong#Password1"), CLIENT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(loginAuditService).record(1L, "admin", false, AuthServiceImpl.FAILURE_LOCKED_NOW, CLIENT);
    }

    @Test
    void usernameRateLimitStopsBeforeTheDatabase() {
        when(rateLimitService.tryConsume(IRateLimitService.Limit.LOGIN_PER_USERNAME, "admin"))
                .thenReturn(new IRateLimitService.Decision(false, 42));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ADMIN", PASSWORD), CLIENT))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting("retryAfterSeconds").isEqualTo(42L);

        verify(authRepository, never()).findForLogin(anyString());
    }

    @Test
    void mustChangePasswordFlowIssuesRestrictedThenFreshTokens() {
        LoginUser user = user(true, false, true);
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user));
        when(sessionRepository.create(1L, 480, "10.1.2.3", "JUnit")).thenReturn(session());
        when(refreshTokenService.issue(SESSION_ID, 1L, CLIENT)).thenReturn(new IssuedToken("refresh", NOW));

        AuthResult login = authService.login(new LoginRequest("admin", PASSWORD), CLIENT);
        assertThat(login.response().mustChangePassword()).isTrue();

        when(userRepository.findById(1L)).thenReturn(detail());
        when(userRepository.changePassword(eq(1L), anyString(), eq(SESSION_ID), eq(1L), eq("10.1.2.3")))
                .thenReturn(new PasswordChangeResult());
        CurrentUser current = new CurrentUser(1L, SESSION_ID, List.of("Admin"), List.of("ROLE_VIEW", "USER_VIEW"), true);

        AuthResult changed = authService.changePassword(current,
                new ChangePasswordRequest(PASSWORD, "Brand-New#Pass42", "Brand-New#Pass42"), "old-refresh", CLIENT);

        assertThat(changed.response().mustChangePassword()).isFalse();
        ArgumentCaptor<TokenClaims> claims = ArgumentCaptor.forClass(TokenClaims.class);
        verify(tokenService, org.mockito.Mockito.times(2)).issueAccessToken(claims.capture());
        assertThat(claims.getAllValues()).extracting(TokenClaims::passwordChangeRequired).containsExactly(true, false);

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(userRepository).changePassword(eq(1L), newHash.capture(), eq(SESSION_ID), eq(1L), eq("10.1.2.3"));
        assertThat(ENCODER.matches("Brand-New#Pass42", newHash.getValue())).isTrue();
        verify(sessionValidationService).evictUser(1L);
        verify(refreshTokenService).revoke("old-refresh", "PASSWORD_CHANGE");
    }

    @Test
    void changePasswordWithWrongCurrentPasswordIsAFieldErrorAndCountsAsFailure() {
        when(userRepository.findById(1L)).thenReturn(detail());
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user(true, false, true)));
        when(authRepository.recordFailedLogin(1L, 5, 15)).thenReturn(attempt(false));
        CurrentUser current = new CurrentUser(1L, SESSION_ID, List.of(), List.of(), true);

        assertThatThrownBy(() -> authService.changePassword(current,
                new ChangePasswordRequest("Not#ThePassword1", "Brand-New#Pass42", "Brand-New#Pass42"), null, CLIENT))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getFieldErrors())
                        .singleElement().extracting("field").isEqualTo("currentPassword"));

        verify(userRepository, never()).changePassword(anyLong(), anyString(), any(), anyLong(), any());
    }

    @Test
    void changePasswordEnforcesThePolicy() {
        when(userRepository.findById(1L)).thenReturn(detail());
        when(authRepository.findForLogin("admin")).thenReturn(Optional.of(user(true, false, true)));
        CurrentUser current = new CurrentUser(1L, SESSION_ID, List.of(), List.of(), true);

        assertThatThrownBy(() -> authService.changePassword(current,
                new ChangePasswordRequest(PASSWORD, "short", "short"), null, CLIENT))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getFieldErrors())
                        .allSatisfy(error -> assertThat(error.field()).isEqualTo("newPassword"))
                        .isNotEmpty());

        assertThatThrownBy(() -> authService.changePassword(current,
                new ChangePasswordRequest(PASSWORD, "Brand-New#Pass42", "Other-New#Pass42"), null, CLIENT))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).getFieldErrors())
                        .singleElement().extracting("field").isEqualTo("confirmPassword"));

        verify(userRepository, never()).changePassword(anyLong(), anyString(), any(), anyLong(), any());
    }

    @Test
    void logoutRevokesTheSessionAndEvictsTheCache() {
        CurrentUser current = new CurrentUser(1L, SESSION_ID, List.of(), List.of(), false);

        authService.logout(current);

        verify(sessionRepository).revoke(SESSION_ID, "LOGOUT");
        verify(sessionValidationService).evictSession(SESSION_ID);
    }

    @Test
    void logoutAllKeepsTheCurrentSession() {
        com.enterprise.api.entity.RevocationResult revoked = new com.enterprise.api.entity.RevocationResult();
        revoked.setSessionsRevoked(3);
        when(sessionRepository.revokeAllForUser(1L, "LOGOUT_ALL", SESSION_ID)).thenReturn(revoked);

        assertThat(authService.logoutAll(new CurrentUser(1L, SESSION_ID, List.of(), List.of(), false))
                .sessionsRevoked()).isEqualTo(3);
        verify(sessionValidationService).evictUser(1L);
        verify(sessionRepository, never()).revokeAllForUser(anyLong(), anyString(), isNull());
    }

    private static LoginUser user(boolean active, boolean lockedOut, boolean mustChangePassword) {
        LoginUser user = new LoginUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setEmail("admin@enterprise.local");
        user.setPasswordHash(PASSWORD_HASH);
        user.setFirstName("System");
        user.setLastName("Administrator");
        user.setActive(active);
        user.setLockedOut(lockedOut);
        user.setMustChangePassword(mustChangePassword);
        user.setRoles(List.of("Admin"));
        user.setPermissions(List.of("ROLE_VIEW", "USER_VIEW"));
        return user;
    }

    private static UserDetail detail() {
        UserDetail detail = new UserDetail();
        detail.setId(1L);
        detail.setUsername("admin");
        detail.setActive(true);
        return detail;
    }

    private static UserSession session() {
        UserSession session = new UserSession();
        session.setSessionId(SESSION_ID);
        session.setCreatedOn(NOW);
        session.setExpiresOn(NOW.plusSeconds(28800));
        return session;
    }

    private static LoginAttemptResult attempt(boolean lockedOut) {
        LoginAttemptResult attempt = new LoginAttemptResult();
        attempt.setFailedLoginCount(lockedOut ? 5 : 1);
        attempt.setLockedOut(lockedOut);
        return attempt;
    }
}
