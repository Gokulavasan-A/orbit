package com.enterprise.api.repository.impl;

import java.sql.Types;

import com.enterprise.api.repository.ILoginAuditRepository;
import com.enterprise.api.repository.support.StoredProcedureExecutor;
import org.springframework.stereotype.Repository;

@Repository
public class LoginAuditRepositoryImpl implements ILoginAuditRepository {

    private final StoredProcedureExecutor executor;

    public LoginAuditRepositoryImpl(StoredProcedureExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Long insert(Long userId, String usernameAttempted, boolean success, String failureReason, String ipAddress,
                       String userAgent) {
        return executor.call("usp_LoginAudit_Insert")
                .in("UserId", Types.BIGINT, userId)
                .in("UsernameAttempted", Types.NVARCHAR, usernameAttempted)
                .in("IsSuccess", Types.BIT, success)
                .in("FailureReason", Types.NVARCHAR, failureReason)
                .in("IpAddress", Types.NVARCHAR, ipAddress)
                .in("UserAgent", Types.NVARCHAR, userAgent)
                .out("LoginAuditId", Types.BIGINT)
                .execute()
                .out("LoginAuditId", Long.class);
    }
}
