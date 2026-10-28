package com.enterprise.api.service;

import java.util.UUID;

import com.enterprise.api.model.request.CreateUserRequest;
import com.enterprise.api.model.request.UpdateUserRequest;
import com.enterprise.api.model.request.UpdateUserRolesRequest;
import com.enterprise.api.model.request.UserPageRequest;
import com.enterprise.api.model.response.PagedResponse;
import com.enterprise.api.model.response.TemporaryPasswordResponse;
import com.enterprise.api.model.response.UserDetailResponse;
import com.enterprise.api.model.response.UserListItemResponse;
import com.enterprise.api.security.ClientInfo;
import com.enterprise.api.security.CurrentUser;

/** User administration. Every write is audited with the actor, client IP and correlation id. */
public interface IUserService {

    PagedResponse<UserListItemResponse> getPaged(UserPageRequest request);

    UserDetailResponse getById(long id);

    /** Creates the user with a generated temporary password (MustChangePassword = 1), returned once. */
    TemporaryPasswordResponse create(CreateUserRequest request, CurrentUser actor, ClientInfo client);

    /** Profile and active flag; deactivation ends the user's sessions immediately. */
    UserDetailResponse update(long id, UpdateUserRequest request, CurrentUser actor, ClientInfo client);

    /** Sets the full role list; removing any role ends the user's sessions. Last write wins. */
    UserDetailResponse updateRoles(long id, UpdateUserRolesRequest request, CurrentUser actor, ClientInfo client);

    /** New temporary password (returned once); clears the lockout and ends all sessions. */
    TemporaryPasswordResponse resetPassword(long id, CurrentUser actor, ClientInfo client);

    UserDetailResponse unlock(long id, CurrentUser actor, ClientInfo client);

    /** Soft delete; ends all sessions. {@code concurrencyStamp} is optional. */
    void delete(long id, UUID concurrencyStamp, CurrentUser actor, ClientInfo client);
}
