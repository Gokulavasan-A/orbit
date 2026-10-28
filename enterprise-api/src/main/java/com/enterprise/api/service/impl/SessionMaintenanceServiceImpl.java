package com.enterprise.api.service.impl;

import com.enterprise.api.config.properties.SessionCleanupProperties;
import com.enterprise.api.entity.SessionCleanupResult;
import com.enterprise.api.repository.ISessionRepository;
import com.enterprise.api.service.ISessionMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SessionMaintenanceServiceImpl implements ISessionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(SessionMaintenanceServiceImpl.class);

    private final ISessionRepository sessionRepository;
    private final SessionCleanupProperties properties;

    public SessionMaintenanceServiceImpl(ISessionRepository sessionRepository, SessionCleanupProperties properties) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
    }

    @Override
    public SessionCleanupResult cleanupExpiredSessions() {
        SessionCleanupResult result = sessionRepository.cleanupExpired(properties.retentionDays(), properties.batchSize());
        log.info("Session cleanup: {} sessions and {} refresh tokens deleted (retention {} days)",
                result.getSessionsDeleted(), result.getTokensDeleted(), properties.retentionDays());
        return result;
    }
}
