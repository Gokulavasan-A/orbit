package com.enterprise.api.repository;

import java.util.Optional;

import com.enterprise.api.entity.LoginAttemptResult;
import com.enterprise.api.entity.LoginUser;

public interface IAuthRepository {

    /** {@code usp_Auth_GetUserForLogin}: user (incl. hash), role names and permission codes; empty if unknown. */
    Optional<LoginUser> findForLogin(String usernameOrEmail);

    /** {@code usp_Auth_RecordFailedLogin}: increments the counter and locks at the threshold. */
    LoginAttemptResult recordFailedLogin(long userId, int maxFailedAttempts, int lockoutMinutes);

    /** {@code usp_Auth_RecordSuccessfulLogin}: resets the counter and lockout, sets LastLoginOn. */
    void recordSuccessfulLogin(long userId);
}
