/* =============================================================================
   V8__create_sp_user.sql
   User CRUD. Write procedures take @ActorUserId (+ optional @IpAddress,
   @CorrelationId) and write an AuditLog row in the same transaction.
   PasswordHash is never returned except by usp_Auth_GetUserForLogin and is
   never written to the audit log.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* Internal helper: JSON snapshot of a user for AuditLog (no PasswordHash). */
CREATE OR ALTER FUNCTION dbo.fn_User_AuditJson (@UserId BIGINT)
RETURNS NVARCHAR(MAX)
AS
BEGIN
    RETURN
    (
        SELECT u.Id, u.Username, u.Email, u.FirstName, u.LastName, u.PhoneNumber,
               u.IsActive, u.IsDeleted, u.MustChangePassword,
               (SELECT ur.RoleId FROM dbo.UserRoles ur WHERE ur.UserId = u.Id ORDER BY ur.RoleId FOR JSON PATH) AS Roles
        FROM dbo.Users u
        WHERE u.Id = @UserId
        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES
    );
END;
GO

/* @RoleIds: optional comma-separated role ids, e.g. N'1,2'. Returns the new Id. */
CREATE OR ALTER PROCEDURE dbo.usp_User_Create
    @Username           NVARCHAR(100),
    @Email              NVARCHAR(256),
    @PasswordHash       NVARCHAR(255),
    @FirstName          NVARCHAR(100),
    @LastName           NVARCHAR(100),
    @PhoneNumber        NVARCHAR(30)  = NULL,
    @IsActive           BIT           = 1,
    @MustChangePassword BIT           = 1,
    @RoleIds            NVARCHAR(MAX) = NULL,
    @ActorUserId        BIGINT,
    @IpAddress          NVARCHAR(45)  = NULL,
    @CorrelationId      NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @Username    = LTRIM(RTRIM(@Username));
    SET @Email       = LTRIM(RTRIM(@Email));
    SET @FirstName   = LTRIM(RTRIM(@FirstName));
    SET @LastName    = LTRIM(RTRIM(@LastName));
    SET @PhoneNumber = NULLIF(LTRIM(RTRIM(@PhoneNumber)), N'');

    IF NULLIF(@Username, N'') IS NULL OR NULLIF(@Email, N'') IS NULL OR NULLIF(@PasswordHash, N'') IS NULL
       OR NULLIF(@FirstName, N'') IS NULL OR NULLIF(@LastName, N'') IS NULL
        THROW 50003, N'Username, Email, PasswordHash, FirstName and LastName are required.', 1;
    IF @ActorUserId IS NULL
        THROW 50003, N'ActorUserId is required.', 1;
    IF LEN(@Username) < 3 OR CHARINDEX(N'@', @Username) > 0
        THROW 50003, N'Username must be at least 3 characters and cannot contain ''@''.', 1;
    IF @Email NOT LIKE N'%_@_%._%'
        THROW 50003, N'Email is not valid.', 1;

    DECLARE @Roles TABLE (RoleId BIGINT PRIMARY KEY);
    IF NULLIF(LTRIM(RTRIM(@RoleIds)), N'') IS NOT NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM STRING_SPLIT(@RoleIds, N',')
                   WHERE LTRIM(RTRIM(value)) <> N'' AND TRY_CAST(LTRIM(RTRIM(value)) AS BIGINT) IS NULL)
            THROW 50003, N'RoleIds must be a comma-separated list of numbers.', 1;

        INSERT INTO @Roles (RoleId)
        SELECT DISTINCT TRY_CAST(LTRIM(RTRIM(value)) AS BIGINT)
        FROM STRING_SPLIT(@RoleIds, N',')
        WHERE LTRIM(RTRIM(value)) <> N'';
    END

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @NewId BIGINT, @EntityId NVARCHAR(100), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF EXISTS (SELECT 1 FROM dbo.Users WITH (UPDLOCK, HOLDLOCK) WHERE Username = @Username AND IsDeleted = 0)
            THROW 50301, N'Username already exists.', 1;
        IF EXISTS (SELECT 1 FROM dbo.Users WITH (UPDLOCK, HOLDLOCK) WHERE Email = @Email AND IsDeleted = 0)
            THROW 50302, N'Email already exists.', 1;
        IF EXISTS (SELECT 1 FROM @Roles x
                   WHERE NOT EXISTS (SELECT 1 FROM dbo.Roles r WHERE r.Id = x.RoleId AND r.IsDeleted = 0))
            THROW 50403, N'One or more roles were not found.', 1;

        INSERT INTO dbo.Users (Username, Email, PasswordHash, FirstName, LastName, PhoneNumber,
                               MustChangePassword, PasswordChangedOn, IsActive, CreatedBy, CreatedOn)
        VALUES (@Username, @Email, @PasswordHash, @FirstName, @LastName, @PhoneNumber,
                ISNULL(@MustChangePassword, 1), @Now, ISNULL(@IsActive, 1), @ActorUserId, @Now);

        SET @NewId = CAST(SCOPE_IDENTITY() AS BIGINT);

        INSERT INTO dbo.UserRoles (UserId, RoleId, CreatedBy, CreatedOn)
        SELECT @NewId, RoleId, @ActorUserId, @Now FROM @Roles;

        SET @EntityId  = CAST(@NewId AS NVARCHAR(100));
        SET @NewValues = dbo.fn_User_AuditJson(@NewId);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'CREATE', @EntityName = N'User', @EntityId = @EntityId,
             @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @NewId AS Id;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        -- Map unique-index races to business errors.
        IF ERROR_NUMBER() IN (2601, 2627) AND ERROR_MESSAGE() LIKE N'%UX_Users_Username%'
            THROW 50301, N'Username already exists.', 1;
        IF ERROR_NUMBER() IN (2601, 2627) AND ERROR_MESSAGE() LIKE N'%UX_Users_Email%'
            THROW 50302, N'Email already exists.', 1;
        THROW;
    END CATCH
END;
GO

/* Username is immutable. Roles are changed via usp_UserRole_Assign/Revoke.
   @RowVersion must match the current value (optimistic concurrency).
   Returns Id and the new RowVersion. Deactivating a user revokes their sessions. */
CREATE OR ALTER PROCEDURE dbo.usp_User_Update
    @Id            BIGINT,
    @Email         NVARCHAR(256),
    @FirstName     NVARCHAR(100),
    @LastName      NVARCHAR(100),
    @PhoneNumber   NVARCHAR(30)  = NULL,
    @IsActive      BIT,
    @RowVersion    BINARY(8),
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @Email       = LTRIM(RTRIM(@Email));
    SET @FirstName   = LTRIM(RTRIM(@FirstName));
    SET @LastName    = LTRIM(RTRIM(@LastName));
    SET @PhoneNumber = NULLIF(LTRIM(RTRIM(@PhoneNumber)), N'');

    IF @Id IS NULL OR @RowVersion IS NULL OR @IsActive IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'Id, RowVersion, IsActive and ActorUserId are required.', 1;
    IF NULLIF(@Email, N'') IS NULL OR NULLIF(@FirstName, N'') IS NULL OR NULLIF(@LastName, N'') IS NULL
        THROW 50003, N'Email, FirstName and LastName are required.', 1;
    IF @Email NOT LIKE N'%_@_%._%'
        THROW 50003, N'Email is not valid.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @CurrentRowVersion BINARY(8), @WasActive BIT,
            @EntityId NVARCHAR(100) = CAST(@Id AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @CurrentRowVersion = RowVersion, @WasActive = IsActive
        FROM dbo.Users WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @Id AND IsDeleted = 0;

        IF @CurrentRowVersion IS NULL
            THROW 50303, N'User not found.', 1;
        IF @CurrentRowVersion <> @RowVersion
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
        SET Email       = @Email,
            FirstName   = @FirstName,
            LastName    = @LastName,
            PhoneNumber = @PhoneNumber,
            IsActive    = @IsActive,
            ModifiedBy  = @ActorUserId,
            ModifiedOn  = @Now
        WHERE Id = @Id;

        IF @WasActive = 1 AND @IsActive = 0
            EXEC dbo.usp_Internal_RevokeSessions @UserId = @Id, @Reason = N'USER_DEACTIVATED';

        SET @NewValues = dbo.fn_User_AuditJson(@Id);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'UPDATE', @EntityName = N'User', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT Id, RowVersion FROM dbo.Users WHERE Id = @Id;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        IF ERROR_NUMBER() IN (2601, 2627) AND ERROR_MESSAGE() LIKE N'%UX_Users_Email%'
            THROW 50302, N'Email already exists.', 1;
        THROW;
    END CATCH
END;
GO

/* Two result sets: 1) user (no PasswordHash), 2) assigned roles. */
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
           u.CreatedBy, u.CreatedOn, u.ModifiedBy, u.ModifiedOn, u.RowVersion
    FROM dbo.Users u
    WHERE u.Id = @Id;

    SELECT r.Id AS RoleId, r.Name AS RoleName, r.IsActive
    FROM dbo.UserRoles ur
    INNER JOIN dbo.Roles r ON r.Id = ur.RoleId
    WHERE ur.UserId = @Id AND r.IsDeleted = 0
    ORDER BY r.Name;
END;
GO

/* Server-side paging for the AG Grid server-side row model.
   @SortColumn accepts the grid colId (camelCase or PascalCase); unknown values
   fall back to createdOn. Only the ORDER BY is dynamic and it is built from a
   fixed whitelist; every value is passed as a parameter to sp_executesql.
   TotalCount is repeated on every row (absent when the page is empty => 0). */
CREATE OR ALTER PROCEDURE dbo.usp_User_GetPaged
    @PageNumber    INT           = 1,
    @PageSize      INT           = 25,
    @SortColumn    NVARCHAR(50)  = N'createdOn',
    @SortDirection NVARCHAR(4)   = N'DESC',
    @SearchText    NVARCHAR(200) = NULL,
    @IsActive      BIT           = NULL,
    @RoleId        BIGINT        = NULL
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
   kept for history; the username/email become reusable. @RowVersion is optional. */
CREATE OR ALTER PROCEDURE dbo.usp_User_SoftDelete
    @Id            BIGINT,
    @ActorUserId   BIGINT,
    @RowVersion    BINARY(8)     = NULL,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @Id IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'Id and ActorUserId are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @CurrentRowVersion BINARY(8),
            @EntityId NVARCHAR(100) = CAST(@Id AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @CurrentRowVersion = RowVersion
        FROM dbo.Users WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @Id AND IsDeleted = 0;

        IF @CurrentRowVersion IS NULL
            THROW 50303, N'User not found.', 1;
        IF @RowVersion IS NOT NULL AND @CurrentRowVersion <> @RowVersion
            THROW 50002, N'The record was modified by someone else. Reload and try again.', 1;
        IF @Id = @ActorUserId
            THROW 50304, N'You cannot deactivate or delete your own account.', 1;
        IF dbo.fn_IsLastActiveAdmin(@Id) = 1
            THROW 50004, N'This would remove the last active administrator.', 1;

        SET @OldValues = dbo.fn_User_AuditJson(@Id);

        UPDATE dbo.Users
        SET IsDeleted  = 1,
            IsActive   = 0,
            ModifiedBy = @ActorUserId,
            ModifiedOn = @Now
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
