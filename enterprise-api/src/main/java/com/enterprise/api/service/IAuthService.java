package com.enterprise.api.service;

import com.enterprise.api.model.request.ChangePasswordRequest;
import com.enterprise.api.model.request.LoginRequest;
import com.enterprise.api.model.response.LogoutAllResponse;
import com.enterprise.api.model.response.MeResponse;
import com.enterprise.api.security.AuthResult;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.CurrentUser;

public interface IAuthService {

    /**
     * Password login. Unknown user, wrong password and inactive user all fail with
     * {@code INVALID_CREDENTIALS}; a locked account fails with {@code ACCOUNT_LOCKED}.
     */
    AuthResult login(LoginRequest request, ClientInfo client);

    /** Rotates the refresh token and issues a new access token with current roles and permissions. */
    AuthResult refresh(String rawRefreshToken, ClientInfo client);

    /** Revokes the current session (and with it its refresh tokens). */
    void logout(CurrentUser user);

    /** Revokes every other session of the user; the current one stays signed in. */
    LogoutAllResponse logoutAll(CurrentUser user);

    MeResponse me(CurrentUser user);

    /** Verifies the current password, applies the policy and issues fresh tokens with pwd_change = false. */
    AuthResult changePassword(CurrentUser user, ChangePasswordRequest request, String rawRefreshToken,
                              ClientInfo client);
}
