package com.enterprise.api.service.impl;

import java.util.UUID;

import com.enterprise.api.config.properties.AuthProperties;
import com.enterprise.api.entity.SessionValidation;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.service.ISessionValidationService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caches only VALID results, keyed by session id, with the owning user id as the
 * value so that all sessions of a user can be evicted. The cache is local to this
 * instance (see {@code docs/security.md} for running several instances).
 */
@Service
public class SessionValidationServiceImpl implements ISessionValidationService {

    private static final Logger log = LoggerFactory.getLogger(SessionValidationServiceImpl.class);

    private final ISessionRepository sessionRepository;
    private final int idleTimeoutMinutes;
    private final Cache<UUID, Long> validSessions;

    public SessionValidationServiceImpl(ISessionRepository sessionRepository, AuthProperties authProperties) {
        AuthProperties.Session session = authProperties.session();
        this.sessionRepository = sessionRepository;
        this.idleTimeoutMinutes = (int) Math.max(1, session.idleTimeout().toMinutes());
        this.validSessions = Caffeine.newBuilder()
                .expireAfterWrite(session.validationCacheTtl())
                .maximumSize(session.validationCacheSize())
                .build();
    }

    @Override
    public boolean isValid(UUID sessionId, long userId) {
        Long cachedUserId = validSessions.getIfPresent(sessionId);
        if (cachedUserId != null) {
            return cachedUserId == userId;
        }

        SessionValidation validation = sessionRepository.validate(sessionId, idleTimeoutMinutes);
        if (!validation.isValid()) {
            log.debug("Session rejected: {}", validation.getStatus());
            return false;
        }
        if (validation.getUserId() == null || validation.getUserId() != userId) {
            log.warn("Session does not belong to the token subject");
            return false;
        }
        validSessions.put(sessionId, userId);
        return true;
    }

    @Override
    public void evictSession(UUID sessionId) {
        if (sessionId != null) {
            validSessions.invalidate(sessionId);
        }
    }

    @Override
    public void evictUser(long userId) {
        validSessions.asMap().values().removeIf(cachedUserId -> cachedUserId == userId);
    }
}
