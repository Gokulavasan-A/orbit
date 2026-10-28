package com.enterprise.api.repository;

import java.util.List;
import java.util.UUID;

import com.enterprise.api.entity.PasswordChangeResult;
import com.enterprise.api.entity.UserDetail;
import com.enterprise.api.entity.UserListItem;
import com.enterprise.api.repository.support.PageQuery;
import com.enterprise.api.repository.support.PagedResult;

public interface IUserRepository {

    /** {@code usp_User_GetById}: the user without its hash, plus its roles (50303 if not found or deleted). */
    UserDetail findById(long id);

    /** {@code usp_User_GetPaged}; {@code isActive} and {@code roleId} null mean all. */
    PagedResult<UserListItem> findPaged(PageQuery query, Boolean isActive, Long roleId);

    /** {@code usp_User_Create} with MustChangePassword = 1. Returns the new id. */
    long create(String username, String email, String passwordHash, String firstName, String lastName,
                String phoneNumber, List<Long> roleIds, long actorUserId, String ipAddress);

    /**
     * {@code usp_User_Update}: 50002 when {@code concurrencyStamp} is stale. Deactivating
     * revokes the user's sessions. Returns the new concurrency stamp.
     */
    UUID update(long id, String email, String firstName, String lastName, String phoneNumber, boolean isActive,
                UUID concurrencyStamp, long actorUserId, String ipAddress);

    /** {@code usp_User_SoftDelete}: also revokes all sessions. {@code concurrencyStamp} may be null. */
    void softDelete(long id, UUID concurrencyStamp, long actorUserId, String ipAddress);

    /**
     * {@code usp_User_ChangePassword}: stores the new hash, clears MustChangePassword and
     * revokes every other session of the user (the current one is kept).
     */
    PasswordChangeResult changePassword(long userId, String newPasswordHash, UUID currentSessionId, long actorUserId,
                                        String ipAddress);

    /**
     * {@code usp_User_ResetPassword}: stores the new hash with MustChangePassword = 1,
     * clears the lockout and revokes all sessions of the user.
     */
    PasswordChangeResult resetPassword(long userId, String newPasswordHash, long actorUserId, String ipAddress);

    /** {@code usp_User_Unlock}: clears FailedLoginCount and LockoutUntil. */
    void unlock(long userId, long actorUserId, String ipAddress);

    /** {@code usp_UserRole_Assign}: no-op when already assigned. */
    void assignRole(long userId, long roleId, long actorUserId, String ipAddress);

    /** {@code usp_UserRole_Revoke}: no-op when not assigned; 50004 for the last active administrator. */
    void revokeRole(long userId, long roleId, long actorUserId, String ipAddress);
}
