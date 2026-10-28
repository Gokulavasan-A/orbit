package com.enterprise.api.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.enterprise.api.config.properties.AuthProperties;
import com.enterprise.api.entity.LoginUser;
import com.enterprise.api.entity.RefreshTokenRecord;
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
import com.enterprise.api.service.IRefreshTokenService;
import com.enterprise.api.service.ISessionValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements the refresh flow from {@code enterprise-db/docs/stored-procedures.md}. */
@Service
public class RefreshTokenServiceImpl implements IRefreshTokenService {

    static final String REASON_REUSE = "REFRESH_TOKEN_REUSE";
    static final String REASON_SESSION_INVALID = "SESSION_INVALID";
    static final String REASON_USER_DELETED = "USER_DELETED";
    static final String REASON_USER_INACTIVE = "USER_INACTIVE";
    static final String REASON_USER_LOCKED = "USER_LOCKED";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    private final IRefreshTokenRepository refreshTokenRepository;
    private final ISessionRepository sessionRepository;
    private final IUserRepository userRepository;
    private final IAuthRepository authRepository;
    private final ISessionValidationService sessionValidationService;
    private final AuthProperties authProperties;
    private final Clock clock;

    public RefreshTokenServiceImpl(IRefreshTokenRepository refreshTokenRepository, ISessionRepository sessionRepository,
                                   IUserRepository userRepository, IAuthRepository authRepository,
                                   ISessionValidationService sessionValidationService, AuthProperties authProperties,
                                   Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.authRepository = authRepository;
        this.sessionValidationService = sessionValidationService;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(UUID sessionId, long userId, ClientInfo client) {
        String token = RefreshTokenCodec.generate();
        Instant expiresOn = refreshTokenRepository.save(sessionId, userId, RefreshTokenCodec.hash(token),
                requestedExpiry(), client.ipAddress(), null);
        return new IssuedToken(token, expiresOn);
    }

    @Override
    public RotatedRefreshToken rotate(String rawToken, ClientInfo client) {
        if (!RefreshTokenCodec.isWellFormed(rawToken)) {
            throw sessionInvalid();
        }
        String oldHash = RefreshTokenCodec.hash(rawToken);
        RefreshTokenRecord stored = refreshTokenRepository.findByHash(oldHash).orElseThrow(this::sessionInvalid);

        switch (stored.getStatus()) {
            case RefreshTokenRecord.ACTIVE -> {
                // continue below
            }
            case RefreshTokenRecord.REUSED -> throw reuseDetected(stored.getUserId());
            case RefreshTokenRecord.SESSION_INVALID -> throw rejectSession(stored, REASON_SESSION_INVALID);
            default -> throw sessionInvalid(); // EXPIRED, REVOKED
        }

        LoginUser user = loadActiveUser(stored);

        String newToken = RefreshTokenCodec.generate();
        Instant expiresOn;
        try {
            expiresOn = refreshTokenRepository.save(stored.getSessionId(), stored.getUserId(),
                    RefreshTokenCodec.hash(newToken), requestedExpiry(), client.ipAddress(), oldHash);
        } catch (BusinessException ex) {
            throw switch (ex.getErrorCode()) {
                // A concurrent request rotated the same token first.
                case REFRESH_TOKEN_REUSED -> reuseDetected(stored.getUserId());
                case SESSION_NOT_FOUND, SESSION_INVALID, REFRESH_TOKEN_NOT_FOUND, SESSION_USER_MISMATCH ->
                        sessionInvalid();
                default -> ex;
            };
        }
        return new RotatedRefreshToken(user, stored.getSessionId(), new IssuedToken(newToken, expiresOn));
    }

    @Override
    public void revoke(String rawToken, String reason) {
        if (!RefreshTokenCodec.isWellFormed(rawToken)) {
            return;
        }
        try {
            refreshTokenRepository.revoke(RefreshTokenCodec.hash(rawToken), reason);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.REFRESH_TOKEN_NOT_FOUND) {
                throw ex;
            }
        }
    }

    /**
     * The user must still exist, be active and not be locked out; otherwise the session
     * is revoked. usp_RefreshToken_Get already reports deactivated/deleted users as
     * SESSION_INVALID, so this mainly covers lockout and changes made in between.
     */
    private LoginUser loadActiveUser(RefreshTokenRecord stored) {
        UserDetail detail;
        try {
            detail = userRepository.findById(stored.getUserId());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw rejectSession(stored, REASON_USER_DELETED);
            }
            throw ex;
        }
        if (!detail.isActive()) {
            throw rejectSession(stored, REASON_USER_INACTIVE);
        }
        if (detail.isLockedOut()) {
            throw rejectSession(stored, REASON_USER_LOCKED);
        }

        LoginUser user = authRepository.findForLogin(detail.getUsername()).orElse(null);
        if (user == null || user.getId() == null || user.getId().longValue() != stored.getUserId()) {
            throw rejectSession(stored, REASON_USER_DELETED);
        }
        if (!user.isActive()) {
            throw rejectSession(stored, REASON_USER_INACTIVE);
        }
        if (user.isLockedOut()) {
            throw rejectSession(stored, REASON_USER_LOCKED);
        }
        return user;
    }

    private BusinessException rejectSession(RefreshTokenRecord stored, String reason) {
        log.info("Refresh rejected, revoking session: {}", reason);
        try {
            sessionRepository.revoke(stored.getSessionId(), reason);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.SESSION_NOT_FOUND) {
                throw ex;
            }
        }
        sessionValidationService.evictSession(stored.getSessionId());
        return sessionInvalid();
    }

    private BusinessException reuseDetected(long userId) {
        log.warn("Refresh token reuse detected for user {}: revoking all sessions", userId);
        try {
            sessionRepository.revokeAllForUser(userId, REASON_REUSE, null);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.USER_NOT_FOUND) {
                throw ex;
            }
        }
        sessionValidationService.evictUser(userId);
        return new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
    }

    private BusinessException sessionInvalid() {
        return new BusinessException(ErrorCode.SESSION_INVALID);
    }

    private Instant requestedExpiry() {
        return clock.instant().plus(authProperties.refreshToken().ttl());
    }
}
