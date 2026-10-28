package com.enterprise.api.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.enterprise.api.config.properties.AuthProperties;
import com.enterprise.api.entity.LoginAttemptResult;
import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.entity.RevocationResult;
import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.entity.UserSession;
import com.enterprise.api.exception.BusinessException;
import com.enterprise.api.exception.ErrorCode;
import com.enterprise.api.exception.FieldValidationException;
import com.enterprise.api.exception.RateLimitExceededException;
import com.enterprise.api.mapper.AuthMapper;
import com.enterprise.api.model.request.ChangePasswordRequest;
import com.enterprise.api.model.request.LoginRequest;
import com.enterprise.api.model.response.AuthResponse;
import com.enterprise.api.model.response.FieldErrorDetail;
import com.enterprise.api.model.response.LogoutAllResponse;
import com.enterprise.api.model.response.MeResponse;
import com.enterprise.api.repository.IAuthRepository;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.repository.IUserRepository;
import com.enterprise.api.security.AuthResult;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.CurrentUser;
import com.enterprise.api.security.IssuedToken;
import com.enterprise.api.security.RotatedRefreshToken;
import com.enterprise.api.security.TokenClaims;
import com.enterprise.api.service.IAuthService;
import com.enterprise.api.service.ILoginAuditService;
import com.enterprise.api.service.IPasswordPolicyService;
import com.enterprise.api.service.IRateLimitService;
import com.enterprise.api.service.IRefreshTokenService;
import com.enterprise.api.service.ISessionValidationService;
import com.enterprise.api.service.ITokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements IAuthService {

    static final String FAILURE_UNKNOWN_USER = "UNKNOWN_USER";
    static final String FAILURE_BAD_PASSWORD = "BAD_PASSWORD";
    static final String FAILURE_INACTIVE = "INACTIVE";
    static final String FAILURE_LOCKED = "LOCKED_OUT";
    static final String FAILURE_LOCKED_NOW = "BAD_PASSWORD_LOCKED";
    static final String FAILURE_RATE_LIMITED = "RATE_LIMITED";

    private static final int BCRYPT_MAX_BYTES = 72;

    private final IAuthRepository authRepository;
    private final ISessionRepository sessionRepository;
    private final IUserRepository userRepository;
    private final ITokenService tokenService;
    private final IRefreshTokenService refreshTokenService;
    private final ISessionValidationService sessionValidationService;
    private final IPasswordPolicyService passwordPolicyService;
    private final IRateLimitService rateLimitService;
    private final ILoginAuditService loginAuditService;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final AuthProperties authProperties;
    private final Clock clock;

    /** Compared against when the user does not exist, so the response time does not reveal it. */
    private final String dummyHash;

    public AuthServiceImpl(IAuthRepository authRepository, ISessionRepository sessionRepository,
                           IUserRepository userRepository, ITokenService tokenService,
                           IRefreshTokenService refreshTokenService, ISessionValidationService sessionValidationService,
                           IPasswordPolicyService passwordPolicyService, IRateLimitService rateLimitService,
                           ILoginAuditService loginAuditService, PasswordEncoder passwordEncoder, AuthMapper authMapper,
                           AuthProperties authProperties, Clock clock) {
        this.authRepository = authRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.sessionValidationService = sessionValidationService;
        this.passwordPolicyService = passwordPolicyService;
        this.rateLimitService = rateLimitService;
        this.loginAuditService = loginAuditService;
        this.passwordEncoder = passwordEncoder;
        this.authMapper = authMapper;
        this.authProperties = authProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public AuthResult login(LoginRequest request, ClientInfo client) {
        String username = request.username().strip();

        IRateLimitService.Decision decision = rateLimitService.tryConsume(
                IRateLimitService.Limit.LOGIN_PER_USERNAME, username.toLowerCase(Locale.ROOT));
        if (!decision.allowed()) {
            loginAuditService.record(null, username, false, FAILURE_RATE_LIMITED, client);
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }

        LoginUser user = authRepository.findForLogin(username).orElse(null);
        if (user == null) {
            passwordMatches(request.password(), dummyHash);
            loginAuditService.record(null, username, false, FAILURE_UNKNOWN_USER, client);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Rejected without checking the password, as usp_Auth_RecordFailedLogin expects.
        if (user.isLockedOut()) {
            loginAuditService.record(user.getId(), username, false, FAILURE_LOCKED, client);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        boolean passwordOk = passwordMatches(request.password(), user.getPasswordHash());

        if (!user.isActive()) {
            loginAuditService.record(user.getId(), username, false, FAILURE_INACTIVE, client);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordOk) {
            LoginAttemptResult attempt = recordFailedLogin(user.getId());
            if (attempt.isLockedOut()) {
                loginAuditService.record(user.getId(), username, false, FAILURE_LOCKED_NOW, client);
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            loginAuditService.record(user.getId(), username, false, FAILURE_BAD_PASSWORD, client);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        authRepository.recordSuccessfulLogin(user.getId());
        UserSession session = sessionRepository.create(user.getId(), minutes(authProperties.session().absoluteTimeout()),
                client.ipAddress(), client.userAgent());
        IssuedToken refreshToken = refreshTokenService.issue(session.getSessionId(), user.getId(), client);
        loginAuditService.record(user.getId(), username, true, null, client);

        return new AuthResult(authResponse(user, session.getSessionId(), user.isMustChangePassword()), refreshToken);
    }

    @Override
    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken, client);
        LoginUser user = rotated.user();
        return new AuthResult(authResponse(user, rotated.sessionId(), user.isMustChangePassword()),
                rotated.refreshToken());
    }

    @Override
    public void logout(CurrentUser user) {
        try {
            sessionRepository.revoke(user.sessionId(), "LOGOUT");
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.SESSION_NOT_FOUND) {
                throw ex;
            }
        } finally {
            sessionValidationService.evictSession(user.sessionId());
        }
    }

    @Override
    public LogoutAllResponse logoutAll(CurrentUser user) {
        RevocationResult result = sessionRepository.revokeAllForUser(user.userId(), "LOGOUT_ALL", user.sessionId());
        sessionValidationService.evictUser(user.userId());
        return new LogoutAllResponse(result.getSessionsRevoked());
    }

    @Override
    public MeResponse me(CurrentUser user) {
        UserDetail detail = userRepository.findById(user.userId());
        return new MeResponse(authMapper.toProfile(detail), user.roles(), user.permissions(),
                user.passwordChangeRequired());
    }

    @Override
    public AuthResult changePassword(CurrentUser current, ChangePasswordRequest request, String rawRefreshToken,
                                     ClientInfo client) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new FieldValidationException("confirmPassword", "must match the new password");
        }

        UserDetail detail = userRepository.findById(current.userId());
        LoginUser user = authRepository.findForLogin(detail.getUsername())
                .filter(found -> found.getId() != null && found.getId() == current.userId() && found.isActive())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_INVALID));
        if (user.isLockedOut()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordMatches(request.currentPassword(), user.getPasswordHash())) {
            if (recordFailedLogin(user.getId()).isLockedOut()) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new FieldValidationException("currentPassword", "is incorrect");
        }

        List<String> violations = passwordPolicyService.validate(request.newPassword(), user.getUsername(),
                request.currentPassword());
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations.stream()
                    .map(message -> new FieldErrorDetail("newPassword", message))
                    .toList());
        }

        userRepository.changePassword(user.getId(), passwordEncoder.encode(request.newPassword()),
                current.sessionId(), user.getId(), client.ipAddress());
        // Other sessions were revoked by the procedure; drop them from the cache now.
        sessionValidationService.evictUser(user.getId());

        refreshTokenService.revoke(rawRefreshToken, "PASSWORD_CHANGE");
        IssuedToken refreshToken = refreshTokenService.issue(current.sessionId(), user.getId(), client);
        return new AuthResult(authResponse(user, current.sessionId(), false), refreshToken);
    }

    private AuthResponse authResponse(LoginUser user, UUID sessionId, boolean mustChangePassword) {
        IssuedToken accessToken = tokenService.issueAccessToken(new TokenClaims(user.getId(), sessionId,
                user.getRoles(), user.getPermissions(), mustChangePassword));
        long expiresIn = Math.max(0, Duration.between(clock.instant(), accessToken.expiresAt()).toSeconds());
        return new AuthResponse(accessToken.value(), AuthResponse.BEARER, accessToken.expiresAt(), expiresIn,
                authMapper.toProfile(user), user.getRoles(), user.getPermissions(), mustChangePassword);
    }

    private LoginAttemptResult recordFailedLogin(long userId) {
        AuthProperties.Lockout lockout = authProperties.lockout();
        return authRepository.recordFailedLogin(userId, lockout.maxFailedAttempts(), minutes(lockout.duration()));
    }

    /**
     * BCrypt only reads 72 bytes and Spring's encoder rejects longer input; such a
     * password can never be valid, but still costs one comparison.
     */
    private boolean passwordMatches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES || hash == null) {
            passwordEncoder.matches("x", dummyHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private static int minutes(Duration duration) {
        return (int) Math.max(1, duration.toMinutes());
    }
}
