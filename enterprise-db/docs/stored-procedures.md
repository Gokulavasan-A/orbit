# Stored procedures and functions

All objects live in `dbo`. Error codes are listed in [error-codes.md](error-codes.md).

## Conventions

- Every procedure starts with `SET NOCOUNT ON; SET XACT_ABORT ON;`.
- **Transactions:** a write procedure opens and commits its own transaction only when none is open (`@OwnsTran`). If it's called inside a caller's transaction (Spring `@Transactional` or a nested `EXEC`), it just rethrows and leaves the rollback to the caller.
- **Auditing:** user, role and assignment write procedures take `@ActorUserId`, plus optional `@IpAddress` and `@CorrelationId`. They write an `AuditLog` row in the same transaction.
- **Concurrency:** `Update` procedures require `@RowVersion` (`BINARY(8)`) and return the new value. `SoftDelete` accepts it as an optional parameter.
- **Paged procedures:**
  - Return the page rows plus a `TotalCount` column. An empty page means 0.
  - `@SortColumn` takes the AG Grid `colId` (case-insensitive). Unknown values fall back to the default sort.
  - `@PageSize` is capped at 500.
  - `@SearchText` is a contains search with LIKE wildcards escaped.
- Dynamic SQL is used only in the paged procedures, and only for the whitelisted `ORDER BY`. Everything else is passed as `sp_executesql` parameters. Because dynamic SQL breaks ownership chaining, these procedures are declared `WITH EXECUTE AS OWNER` (V12). That lets them work for the API user, which has `EXECUTE` but no table permissions. Any future procedure that uses dynamic SQL needs the same.
- Policy values (lockout, timeouts, retention) are parameters, so they live in the API config.
- `usp_Internal_*` procedures and the `fn_*_AuditJson` and `fn_IsLastActiveAdmin` functions are internal helpers. The API shouldn't call them.

## Functions

| Function | Returns |
|---|---|
| `fn_GetUserRoles(@UserId)` | TVF: `RoleId`, `RoleName` (active roles of an active user) |
| `fn_GetUserPermissions(@UserId)` | TVF: `PermissionId`, `PermissionCode`, `PermissionName`, `Module` (distinct effective permissions) |
| `fn_UserHasPermission(@UserId, @PermissionCode)` | `BIT` |
| `fn_IsLastActiveAdmin(@UserId)` | `BIT` (internal) |
| `fn_User_AuditJson(@UserId)`, `fn_Role_AuditJson(@RoleId)` | JSON snapshot (internal) |

## Auth

| Procedure | Parameters | Result | Errors |
|---|---|---|---|
| `usp_Auth_GetUserForLogin` | `@UsernameOrEmail` | 3 result sets, always returned (empty if unknown): (1) user incl. `PasswordHash`, `IsActive`, `FailedLoginCount`, `LockoutUntil`, `IsLockedOut`, `MustChangePassword`; (2) `RoleId`, `RoleName`; (3) `PermissionCode` | 50003 |
| `usp_Auth_RecordFailedLogin` | `@UserId, @MaxFailedAttempts, @LockoutMinutes` | `FailedLoginCount`, `LockoutUntil`, `IsLockedOut` | 50003, 50303 |
| `usp_Auth_RecordSuccessfulLogin` | `@UserId` | none (resets the counter and lockout, sets `LastLoginOn`) | 50003, 50303 |

`usp_Auth_RecordFailedLogin` details:
- An expired lockout restarts the count at 1.
- An active lockout is never extended.
- The API should reject a login while `IsLockedOut = 1` without checking the password.

## Session

| Procedure | Parameters | Result | Errors |
|---|---|---|---|
| `usp_Session_Create` | `@UserId, @AbsoluteTimeoutMinutes, @IpAddress?, @UserAgent?` | `SessionId`, `CreatedOn`, `ExpiresOn` | 50003, 50101 |
| `usp_Session_Validate` | `@SessionId, @IdleTimeoutMinutes` | `SessionId`, `UserId`, `Status`, `ExpiresOn`, `LastActivityOn` | 50003 |
| `usp_Session_Revoke` | `@SessionId, @Reason = 'LOGOUT'` | `SessionsRevoked`, `TokensRevoked` | 50003, 50201 |
| `usp_Session_RevokeAllForUser` | `@UserId, @Reason = 'LOGOUT_ALL', @ExceptSessionId?` | `SessionsRevoked`, `TokensRevoked` | 50003, 50303 |
| `usp_Session_CleanupExpired` | `@RetentionDays = 30, @BatchSize = 5000` | `SessionsDeleted`, `TokensDeleted` | 50003 |
| `usp_Session_RevokeAllForRole` | `@RoleId, @Reason = 'ROLE_CHANGED'` | (1) `UserId` of every user in the role; (2) `SessionsRevoked`, `TokensRevoked` | 50003, 50403 |

`usp_Session_Validate` details:
- `Status` is one of `VALID`, `NOT_FOUND`, `REVOKED`, `USER_INACTIVE`, `EXPIRED` or `IDLE`. Only `VALID` should be accepted.
- `USER_INACTIVE`, `EXPIRED` and `IDLE` revoke the session on the spot.
- `USER_INACTIVE` covers both deactivated (`IsActive = 0`) and soft-deleted (`IsDeleted = 1`) users. A locked-out user is **not** rejected here: lockout only blocks new logins, and the API rejects locked users at their next refresh.
- `LastActivityOn` is refreshed at most once a minute.

Other session notes:
- Revoking a session also revokes its refresh tokens.
- `@ExceptSessionId` keeps the current session ("sign out other devices").
- `usp_Session_CleanupExpired` is for a `@Scheduled` job. It deletes in batches and never holds a long transaction.
- `usp_Session_RevokeAllForRole` (V14) revokes the sessions and refresh tokens of every user holding the role, including users of an inactive role. The API calls it when permissions are removed from a role, when a role is deactivated, and **before** `usp_Role_SoftDelete` (which removes the user links, so afterwards the users can't be found). It returns the user ids so the API can evict them from its session cache.

## Refresh token

| Procedure | Parameters | Result | Errors |
|---|---|---|---|
| `usp_RefreshToken_Save` | `@SessionId, @UserId, @TokenHash, @ExpiresOn, @CreatedByIp?, @ReplacesTokenHash?` | `Id`, `ExpiresOn` (capped at the session's expiry) | 50003, 50201, 50202, 50203, 50204, 50205 |
| `usp_RefreshToken_Get` | `@TokenHash` | token row + `SessionExpiresOn`, `SessionIsRevoked`, `Status` (empty if unknown) | 50003 |
| `usp_RefreshToken_Revoke` | `@TokenHash, @ReplacedByTokenHash?, @Reason = 'REVOKED'` | `TokensRevoked` (0 = already revoked) | 50003, 50203 |

`usp_RefreshToken_Get` returns `Status` as one of `ACTIVE`, `EXPIRED`, `REVOKED`, `REUSED` or `SESSION_INVALID` (session revoked or expired, or user deactivated or soft-deleted).

Refresh flow in the API:
1. Hash the cookie value and call `usp_RefreshToken_Get`. Anything other than `ACTIVE` is rejected.
2. If the status is `REUSED`, also call `usp_Session_RevokeAllForUser`.
3. Otherwise call `usp_RefreshToken_Save` with `@ReplacesTokenHash` set to the old hash. This rotates atomically.
4. If that throws 50204 (a concurrent reuse), treat it as reuse too.

## Users

| Procedure | Parameters | Result | Errors |
|---|---|---|---|
| `usp_User_Create` | `@Username, @Email, @PasswordHash, @FirstName, @LastName, @PhoneNumber?, @IsActive = 1, @MustChangePassword = 1, @RoleIds? ('1,2'), @ActorUserId, @IpAddress?, @CorrelationId?` | `Id` | 50003, 50301, 50302, 50403 |
| `usp_User_Update` | `@Id, @Email, @FirstName, @LastName, @PhoneNumber?, @IsActive, @ConcurrencyStamp, @ActorUserId, @IpAddress?, @CorrelationId?` | `Id`, `ConcurrencyStamp` (new value) | 50002, 50003, 50004, 50302, 50303, 50304 |
| `usp_User_GetById` | `@Id` | (1) user (no hash) incl. `RowVersion` and `ConcurrencyStamp`; (2) `RoleId`, `RoleName`, `IsActive` | 50303 |
| `usp_User_GetPaged` | `@PageNumber = 1, @PageSize = 25, @SortColumn = 'createdOn', @SortDirection = 'DESC', @SearchText?, @IsActive?, @RoleId?` | `Id, Username, Email, FirstName, LastName, PhoneNumber, IsActive, IsLockedOut, LastLoginOn, CreatedOn, ModifiedOn, Roles (comma list), ConcurrencyStamp, TotalCount` | none |
| `usp_User_SoftDelete` | `@Id, @ActorUserId, @ConcurrencyStamp?, @IpAddress?, @CorrelationId?` | none | 50002, 50003, 50004, 50303, 50304 |
| `usp_User_Unlock` | `@UserId, @ActorUserId, @IpAddress?, @CorrelationId?` | `Id` | 50003, 50303 |

- The username can't be changed after creation.
- Deactivating or deleting a user revokes all of their sessions.
- `usp_User_Unlock` (V14, audit action `UNLOCK`) clears `FailedLoginCount` and `LockoutUntil`. It works on inactive users and doesn't touch sessions.

**User concurrency (V14):** users use `ConcurrencyStamp` (`UNIQUEIDENTIFIER`), not `RowVersion`, for optimistic concurrency. `RowVersion` changes on every login and failed login, so an administrator's save would fail just because the user signed in. Only `usp_User_Update` and `usp_User_SoftDelete` set a new stamp. The login, lockout, unlock and password procedures and the role assignments leave it unchanged. Roles keep `RowVersion`, because nothing else writes role rows.

User grid sort columns: `id`, `username`, `email`, `firstName`, `lastName`, `phoneNumber`, `isActive`, `isLockedOut`, `lastLoginOn`, `createdOn`, `modifiedOn`.

## Passwords

Both procedures return `Id`, `RowVersion`, `PasswordChangedOn`, `SessionsRevoked` and `TokensRevoked`, and write an `AuditLog` row without any hash.

| Procedure | Parameters | Errors |
|---|---|---|
| `usp_User_ChangePassword` | `@UserId, @NewPasswordHash, @CurrentSessionId?, @ActorUserId, @IpAddress?, @CorrelationId?` | 50003, 50205, 50303 |
| `usp_User_ResetPassword` | `@UserId, @NewPasswordHash, @ActorUserId, @IpAddress?, @CorrelationId?` | 50003, 50303 |

`usp_User_ChangePassword` (self-service, audit action `PASSWORD_CHANGE`):
- The API verifies the **old** password against `usp_Auth_GetUserForLogin` before calling it. The procedure never sees a plaintext password.
- The user must be active and not deleted (50303).
- Clears `MustChangePassword` and sets `PasswordChangedOn`.
- Revokes every **other** session of the user, plus their refresh tokens. Pass the caller's `@CurrentSessionId` to keep it; it must belong to the user (50205). NULL revokes all sessions.

`usp_User_ResetPassword` (administrator, audit action `PASSWORD_RESET`):
- Sets `MustChangePassword = 1` and `PasswordChangedOn`, and clears the lockout (`FailedLoginCount = 0`, `LockoutUntil = NULL`).
- Revokes **all** sessions of the user.
- Works on inactive users. Deleted users raise 50303.

## Roles

| Procedure | Parameters | Result | Errors |
|---|---|---|---|
| `usp_Role_Create` | `@Name, @Description?, @IsActive = 1, @PermissionIds?, @ActorUserId, @IpAddress?, @CorrelationId?` | `Id` | 50003, 50402, 50501 |
| `usp_Role_Update` | `@Id, @Name, @Description?, @IsActive, @RowVersion, @ActorUserId, @IpAddress?, @CorrelationId?` | `Id`, `RowVersion` | 50002, 50003, 50401, 50402, 50403 |
| `usp_Role_GetAll` | `@IncludeInactive = 0` | `Id, Name, Description, IsSystem, IsActive, RowVersion` | none |
| `usp_Role_GetById` | `@Id` | (1) `Id, Name, Description, IsSystem, IsActive, UserCount, CreatedOn, ModifiedOn, RowVersion`; (2) `PermissionId`, `PermissionCode` | 50403 |
| `usp_Role_GetPaged` | `@PageNumber = 1, @PageSize = 25, @SortColumn = 'name', @SortDirection = 'ASC', @SearchText?, @IsActive?` | `Id, Name, Description, IsSystem, IsActive, CreatedOn, ModifiedOn, UserCount, PermissionCount, Permissions (comma list), TotalCount` | none |
| `usp_Role_SoftDelete` | `@Id, @ActorUserId, @RowVersion?, @IpAddress?, @CorrelationId?` | none (also removes the role from all users) | 50002, 50003, 50401, 50403 |

Role grid sort columns: `id`, `name`, `description`, `isSystem`, `isActive`, `userCount`, `permissionCount`, `createdOn`, `modifiedOn`.

## Permissions

| Procedure | Parameters | Result | Errors |
|---|---|---|---|
| `usp_Permission_GetAll` | none | `Id, Code, Name, Module, Description` (active permissions, ordered by `Module`, `Code`) | none |

Permissions are seeded per module (V11) and aren't created through the API.

## Assignments

All four take `@ActorUserId, @IpAddress?, @CorrelationId?` and return no result. Assigning an existing link does nothing and writes no audit row; revoking a missing link also does nothing.

| Procedure | Parameters | Errors |
|---|---|---|
| `usp_UserRole_Assign` | `@UserId, @RoleId` | 50003, 50303, 50403 |
| `usp_UserRole_Revoke` | `@UserId, @RoleId` | 50003, 50004 |
| `usp_RolePermission_Assign` | `@RoleId, @PermissionId` | 50003, 50403, 50501 |
| `usp_RolePermission_Revoke` | `@RoleId, @PermissionId` | 50003, 50502 |

## Audit

Both procedures return the new Id through an `OUTPUT` parameter, not a result set, so they can be called from other procedures.

| Procedure | Parameters | Output |
|---|---|---|
| `usp_LoginAudit_Insert` | `@UserId?, @UsernameAttempted, @IsSuccess, @FailureReason?, @IpAddress?, @UserAgent?` | `@LoginAuditId OUTPUT` |
| `usp_AuditLog_Insert` | `@ActorUserId?, @Action, @EntityName, @EntityId?, @OldValues? (JSON), @NewValues? (JSON), @IpAddress?, @CorrelationId?` | `@AuditLogId OUTPUT` |

An unknown `@UserId` in `usp_LoginAudit_Insert` is stored as NULL rather than failing the audit.
