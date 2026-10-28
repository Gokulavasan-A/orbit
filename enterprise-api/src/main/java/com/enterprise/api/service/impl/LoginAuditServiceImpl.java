package com.enterprise.api.service.impl;

import com.enterprise.api.repository.ILoginAuditRepository;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.service.ILoginAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LoginAuditServiceImpl implements ILoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditServiceImpl.class);
    private static final int MAX_USERNAME = 256;

    private final ILoginAuditRepository loginAuditRepository;

    public LoginAuditServiceImpl(ILoginAuditRepository loginAuditRepository) {
        this.loginAuditRepository = loginAuditRepository;
    }

    @Async
    @Override
    public void record(Long userId, String usernameAttempted, boolean success, String failureReason, ClientInfo client) {
        String username = usernameAttempted == null ? "" : usernameAttempted.strip();
        if (username.length() > MAX_USERNAME) {
            username = username.substring(0, MAX_USERNAME);
        }
        try {
            loginAuditRepository.insert(userId, username, success, failureReason,
                    client == null ? null : client.ipAddress(), client == null ? null : client.userAgent());
        } catch (RuntimeException ex) {
            // The exception text never contains parameter values (see StoredProcedureExecutor).
            log.error("Login audit could not be written (success={}, reason={})", success, failureReason, ex);
        }
    }
}
