/* =============================================================================
   V14__user_role_management.sql
   User and role management (Phase 5a):
   - Users.ConcurrencyStamp: optimistic concurrency for administrative edits.
     RowVersion changes on every login (LastLoginOn, FailedLoginCount), so an
     admin's save would fail just because the user signed in. The stamp is
     changed only by usp_User_Update and usp_User_SoftDelete; login, lockout,
     unlock and password procedures leave it alone.
   - usp_User_Update / usp_User_SoftDelete check @ConcurrencyStamp instead of
     @RowVersion. usp_User_GetById / usp_User_GetPaged return it.
   - New: usp_User_Unlock, usp_Permission_GetAll, usp_Role_GetById,
     usp_Session_RevokeAllForRole.
   Roles keep RowVersion (nothing else updates role rows).
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH(N'dbo.Users', N'ConcurrencyStamp') IS NULL
    ALTER TABLE dbo.Users
        ADD ConcurrencyStamp UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT DF_Users_ConcurrencyStamp DEFAULT (NEWID());
GO

/* Username is immutable. Roles are changed via usp_UserRole_Assign/Revoke.
   @ConcurrencyStamp must match the current value (optimistic concurrency).
   Returns Id and the new ConcurrencyStamp. Deactivating a user revokes their sessions. */
CREATE OR ALTER PROCEDURE dbo.usp_User_Update
    @Id               BIGINT,
    @Email            NVARCHAR(256),
    @FirstName        NVARCHAR(100),
    @LastName         NVARCHAR(100),
    @PhoneNumber      NVARCHAR(30)     = NULL,
    @IsActive         BIT,
    @ConcurrencyStamp UNIQUEIDENTIFIER,
    @ActorUserId      BIGINT,
    @IpAddress        NVARCHAR(45)     = NULL,
    @CorrelationId    NVARCHAR(100)    = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @Email       = LTRIM(RTRIM(@Email));
    SET @FirstName   = LTRIM(RTRIM(@FirstName));
    SET @LastName    = LTRIM(RTRIM(@LastName));
    SET @PhoneNumber = NULLIF(LTRIM(RTRIM(@PhoneNumber)), N'');

    IF @Id IS NULL OR @ConcurrencyStamp IS NULL OR @IsActive IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'Id, ConcurrencyStamp, IsActive and ActorUserId are required.', 1;
    IF NULLIF(@Email, N'') IS NULL OR NULLIF(@FirstName, N'') IS NULL OR NULLIF(@LastName, N'') IS NULL
        THROW 50003, N'Email, FirstName and LastName are required.', 1;
    IF @Email NOT LIKE N'%_@_%._%'
        THROW 50003, N'Email is not valid.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @CurrentStamp UNIQUEIDENTIFIER, @WasActive BIT,
            @NewStamp UNIQUEIDENTIFIER = NEWID(),
            @EntityId NVARCHAR(100) = CAST(@Id AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @CurrentStamp = ConcurrencyStamp, @WasActive = IsActive
        FROM dbo.Users WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @Id AND IsDeleted = 0;

        IF @CurrentStamp IS NULL
            THROW 50303, N'User not found.', 1;
        IF @CurrentStamp <> @ConcurrencyStamp
            THROW 50002, N'The record was modified by someone else. Reload and try again.', 1;
        IF EXISTS (SELECT 1 FROM dbo.Users WITH (UPDLOCK, HOLDLOCK) WHERE Email = @Email AND IsDeleted = 0 AND Id <> @Id)
            THROW 50302, N'Email already exists.', 1;

        IF @WasActive = 1 AND @IsActive = 0
        BEGIN
            IF @Id = @ActorUserId
                THROW 50304, N'You cannot deactivate or delete your own account.', 1;
            IF dbo.fn_IsLastActiveAdmin(@Id) = 1
                THROW 50004, N'This would remove the last active administrator.', 1;
        END

        SET @OldValues = dbo.fn_User_AuditJson(@Id);

        UPDATE dbo.Users
        SET Email            = @Email,
            FirstName        = @FirstName,
            LastName         = @LastName,
            PhoneNumber      = @PhoneNumber,
            IsActive         = @IsActive,
            ConcurrencyStamp = @NewStamp,
            ModifiedBy       = @ActorUserId,
            ModifiedOn       = @Now
        WHERE Id = @Id;

        IF @WasActive = 1 AND @IsActive = 0
            EXEC dbo.usp_Internal_RevokeSessions @UserId = @Id, @Reason = N'USER_DEACTIVATED';

        SET @NewValues = dbo.fn_User_AuditJson(@Id);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'UPDATE', @EntityName = N'User', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @Id AS Id, @NewStamp AS ConcurrencyStamp;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        IF ERROR_NUMBER() IN (2601, 2627) AND ERROR_MESSAGE() LIKE N'%UX_Users_Email%'
            THROW 50302, N'Email already exists.', 1;
        THROW;
    END CATCH
END;
GO

/* Two result sets: 1) user (no PasswordHash) incl. ConcurrencyStamp, 2) assigned roles. */
CREATE OR ALTER PROCEDURE dbo.usp_User_GetById
    @Id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Id = @Id AND IsDeleted = 0)
        THROW 50303, N'User not found.', 1;

    SELECT u.Id, u.Username, u.Email, u.FirstName, u.LastName, u.PhoneNumber,
           u.IsActive, u.FailedLoginCount, u.LockoutUntil,
           CAST(CASE WHEN u.LockoutUntil > SYSUTCDATETIME() THEN 1 ELSE 0 END AS BIT) AS IsLockedOut,
           u.LastLoginOn, u.MustChangePassword, u.PasswordChangedOn,
           u.CreatedBy, u.CreatedOn, u.ModifiedBy, u.ModifiedOn, u.RowVersion, u.ConcurrencyStamp
    FROM dbo.Users u
    WHERE u.Id = @Id;

    SELECT r.Id AS RoleId, r.Name AS RoleName, r.IsActive
    FROM dbo.UserRoles ur
    INNER JOIN dbo.Roles r ON r.Id = ur.RoleId
    WHERE ur.UserId = @Id AND r.IsDeleted = 0
    ORDER BY r.Name;
END;
GO

/* Same contract as V12, plus ConcurrencyStamp. Still WITH EXECUTE AS OWNER
   (dynamic ORDER BY; see V12). */
CREATE OR ALTER PROCEDURE dbo.usp_User_GetPaged
    @PageNumber    INT           = 1,
    @PageSize      INT           = 25,
    @SortColumn    NVARCHAR(50)  = N'createdOn',
    @SortDirection NVARCHAR(4)   = N'DESC',
    @SearchText    NVARCHAR(200) = NULL,
    @IsActive      BIT           = NULL,
    @RoleId        BIGINT        = NULL
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @PageNumber = CASE WHEN ISNULL(@PageNumber, 0) < 1 THEN 1 ELSE @PageNumber END;
    SET @PageSize   = CASE WHEN ISNULL(@PageSize, 0) < 1 THEN 25 WHEN @PageSize > 500 THEN 500 ELSE @PageSize END;

    DECLARE @OrderBy SYSNAME = CASE LOWER(LTRIM(RTRIM(@SortColumn)))
                                   WHEN N'id'          THEN N'Id'
                                   WHEN N'username'    THEN N'Username'
                                   WHEN N'email'       THEN N'Email'
                                   WHEN N'firstname'   THEN N'FirstName'
                                   WHEN N'lastname'    THEN N'LastName'
                                   WHEN N'phonenumber' THEN N'PhoneNumber'
                                   WHEN N'isactive'    THEN N'IsActive'
                                   WHEN N'islockedout' THEN N'IsLockedOut'
                                   WHEN N'lastloginon' THEN N'LastLoginOn'
                                   WHEN N'createdon'   THEN N'CreatedOn'
                                   WHEN N'modifiedon'  THEN N'ModifiedOn'
                                   ELSE N'CreatedOn'
                               END;
    DECLARE @Direction NVARCHAR(4) = CASE WHEN UPPER(LTRIM(RTRIM(@SortDirection))) = N'ASC' THEN N'ASC' ELSE N'DESC' END;

    -- Contains-search with LIKE wildcards escaped ('!' is the escape character).
    DECLARE @Pattern NVARCHAR(410) = NULL;
    IF NULLIF(LTRIM(RTRIM(@SearchText)), N'') IS NOT NULL
        SET @Pattern = N'%' + REPLACE(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(@SearchText)),
                           N'!', N'!!'), N'%', N'!%'), N'_', N'!_'), N'[', N'![') + N'%';

    DECLARE @Sql NVARCHAR(MAX) = N'
SELECT u.Id,
       u.Username,
       u.Email,
       u.FirstName,
       u.LastName,
       u.PhoneNumber,
       u.IsActive,
       CAST(CASE WHEN u.LockoutUntil > SYSUTCDATETIME() THEN 1 ELSE 0 END AS BIT) AS IsLockedOut,
       u.LastLoginOn,
       u.CreatedOn,
       u.ModifiedOn,
       (SELECT STRING_AGG(r.Name, N'', '') WITHIN GROUP (ORDER BY r.Name)
        FROM dbo.UserRoles ur
        INNER JOIN dbo.Roles r ON r.Id = ur.RoleId
        WHERE ur.UserId = u.Id AND r.IsDeleted = 0) AS Roles,
       u.ConcurrencyStamp,
       COUNT(*) OVER () AS TotalCount
FROM dbo.Users u
WHERE u.IsDeleted = 0
  AND (@IsActive IS NULL OR u.IsActive = @IsActive)
  AND (@RoleId IS NULL OR EXISTS (SELECT 1 FROM dbo.UserRoles x WHERE x.UserId = u.Id AND x.RoleId = @RoleId))
  AND (@Pattern IS NULL
       OR u.Username  LIKE @Pattern ESCAPE N''!''
       OR u.Email     LIKE @Pattern ESCAPE N''!''
       OR u.FirstName LIKE @Pattern ESCAPE N''!''
       OR u.LastName  LIKE @Pattern ESCAPE N''!'')
ORDER BY ' + QUOTENAME(@OrderBy) + N' ' + @Direction + N', Id ' + @Direction + N'
OFFSET (@PageNumber - 1) * @PageSize ROWS FETCH NEXT @PageSize ROWS ONLY
OPTION (RECOMPILE);';

    EXEC sys.sp_executesql
         @Sql,
         N'@PageNumber INT, @PageSize INT, @IsActive BIT, @RoleId BIGINT, @Pattern NVARCHAR(410)',
         @PageNumber = @PageNumber, @PageSize = @PageSize, @IsActive = @IsActive, @RoleId = @RoleId, @Pattern = @Pattern;
END;
GO

/* Sets IsDeleted = 1 / IsActive = 0 and revokes all sessions. Role links are
   kept for history; the username/email become reusable. @ConcurrencyStamp is optional. */
CREATE OR ALTER PROCEDURE dbo.usp_User_SoftDelete
    @Id               BIGINT,
    @ActorUserId      BIGINT,
    @ConcurrencyStamp UNIQUEIDENTIFIER = NULL,
    @IpAddress        NVARCHAR(45)     = NULL,
    @CorrelationId    NVARCHAR(100)    = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @Id IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'Id and ActorUserId are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @CurrentStamp UNIQUEIDENTIFIER,
            @EntityId NVARCHAR(100) = CAST(@Id AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @CurrentStamp = ConcurrencyStamp
        FROM dbo.Users WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @Id AND IsDeleted = 0;

        IF @CurrentStamp IS NULL
            THROW 50303, N'User not found.', 1;
        IF @ConcurrencyStamp IS NOT NULL AND @CurrentStamp <> @ConcurrencyStamp
            THROW 50002, N'The record was modified by someone else. Reload and try again.', 1;
        IF @Id = @ActorUserId
            THROW 50304, N'You cannot deactivate or delete your own account.', 1;
        IF dbo.fn_IsLastActiveAdmin(@Id) = 1
            THROW 50004, N'This would remove the last active administrator.', 1;

        SET @OldValues = dbo.fn_User_AuditJson(@Id);

        UPDATE dbo.Users
        SET IsDeleted        = 1,
            IsActive         = 0,
            ConcurrencyStamp = NEWID(),
            ModifiedBy       = @ActorUserId,
            ModifiedOn       = @Now
        WHERE Id = @Id;

        EXEC dbo.usp_Internal_RevokeSessions @UserId = @Id, @Reason = N'USER_DELETED';

        SET @NewValues = dbo.fn_User_AuditJson(@Id);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'DELETE', @EntityName = N'User', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

/* Administrator unlock: clears FailedLoginCount and LockoutUntil. Works on
   inactive users; deleted users raise 50303. Sessions and ConcurrencyStamp
   are not touched. Returns Id. */
CREATE OR ALTER PROCEDURE dbo.usp_User_Unlock
    @UserId        BIGINT,
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'UserId and ActorUserId are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @EntityId NVARCHAR(100) = CAST(@UserId AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SET @OldValues = (SELECT u.FailedLoginCount, u.LockoutUntil
                          FROM dbo.Users u WITH (UPDLOCK, HOLDLOCK)
                          WHERE u.Id = @UserId AND u.IsDeleted = 0
                          FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES);

        IF @OldValues IS NULL
            THROW 50303, N'User not found.', 1;

        UPDATE dbo.Users
        SET FailedLoginCount = 0,
            LockoutUntil     = NULL,
            ModifiedBy       = @ActorUserId,
            ModifiedOn       = @Now
        WHERE Id = @UserId;

        SET @NewValues = N'{"FailedLoginCount":0,"LockoutUntil":null}';

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'UNLOCK', @EntityName = N'User', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @UserId AS Id;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

/* Active permissions for the role editor, grouped by module in the API. */
CREATE OR ALTER PROCEDURE dbo.usp_Permission_GetAll
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SELECT Id, Code, Name, Module, Description
    FROM dbo.Permissions
    WHERE IsActive = 1
    ORDER BY Module, Code;
END;
GO

/* Two result sets: 1) role incl. UserCount and RowVersion, 2) assigned permissions. */
CREATE OR ALTER PROCEDURE dbo.usp_Role_GetById
    @Id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.Roles WHERE Id = @Id AND IsDeleted = 0)
        THROW 50403, N'Role not found.', 1;

    SELECT r.Id, r.Name, r.Description, r.IsSystem, r.IsActive,
           (SELECT COUNT(*) FROM dbo.UserRoles ur
            INNER JOIN dbo.Users u ON u.Id = ur.UserId
            WHERE ur.RoleId = r.Id AND u.IsDeleted = 0) AS UserCount,
           r.CreatedOn, r.ModifiedOn, r.RowVersion
    FROM dbo.Roles r
    WHERE r.Id = @Id;

    SELECT p.Id AS PermissionId, p.Code AS PermissionCode
    FROM dbo.RolePermissions rp
    INNER JOIN dbo.Permissions p ON p.Id = rp.PermissionId
    WHERE rp.RoleId = @Id
    ORDER BY p.Code;
END;
GO

/* Revokes every session (and refresh token) of every user that holds the role,
   e.g. after permissions were removed or the role was deactivated or is about
   to be deleted. Works for inactive roles. Two result sets: 1) UserId of each
   user in the role (the API evicts them from its session cache), 2) counts. */
CREATE OR ALTER PROCEDURE dbo.usp_Session_RevokeAllForRole
    @RoleId BIGINT,
    @Reason NVARCHAR(100) = N'ROLE_CHANGED'
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @RoleId IS NULL OR NULLIF(LTRIM(RTRIM(@Reason)), N'') IS NULL
        THROW 50003, N'RoleId and Reason are required.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @Users TABLE (UserId BIGINT PRIMARY KEY);
    DECLARE @UserId BIGINT, @Sessions INT, @Tokens INT,
            @SessionsRevoked INT = 0, @TokensRevoked INT = 0;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM dbo.Roles WHERE Id = @RoleId AND IsDeleted = 0)
            THROW 50403, N'Role not found.', 1;

        INSERT INTO @Users (UserId)
        SELECT ur.UserId FROM dbo.UserRoles ur WITH (UPDLOCK, HOLDLOCK) WHERE ur.RoleId = @RoleId;

        DECLARE role_users CURSOR LOCAL FAST_FORWARD FOR SELECT UserId FROM @Users;
        OPEN role_users;
        FETCH NEXT FROM role_users INTO @UserId;
        WHILE @@FETCH_STATUS = 0
        BEGIN
            EXEC dbo.usp_Internal_RevokeSessions
                 @UserId          = @UserId,
                 @Reason          = @Reason,
                 @SessionsRevoked = @Sessions OUTPUT,
                 @TokensRevoked   = @Tokens   OUTPUT;
            SET @SessionsRevoked += @Sessions;
            SET @TokensRevoked   += @Tokens;
            FETCH NEXT FROM role_users INTO @UserId;
        END
        CLOSE role_users;
        DEALLOCATE role_users;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT UserId FROM @Users ORDER BY UserId;
        SELECT @SessionsRevoked AS SessionsRevoked, @TokensRevoked AS TokensRevoked;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO
