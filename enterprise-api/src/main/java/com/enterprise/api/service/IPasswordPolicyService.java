package com.enterprise.api.service;

import java.util.List;

public interface IPasswordPolicyService {

    /**
     * Checks a new password against {@code app.security.password-policy}, the username
     * and the current password.
     *
     * @return safe, fixed violation messages; empty when the password is acceptable
     */
    List<String> validate(String newPassword, String username, String currentPassword);
}
