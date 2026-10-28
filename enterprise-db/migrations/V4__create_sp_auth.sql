/* =============================================================================
   V4__create_sp_auth.sql
   Login support. Lockout policy values come from API configuration.

   Transaction pattern used by all write procedures in this project:
   a procedure only opens/commits/rolls back a transaction when none is open
   (@OwnsTran = 1). Inside a caller's transaction (e.g. Spring @Transactional or
   a nested EXEC) it just rethrows and lets the caller roll back. XACT_ABORT ON
   guarantees the transaction is doomed on any error.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* Returns 3 result sets (always, empty when the user is not found):
   1) user row incl. PasswordHash and lockout state
   2) active roles
   3) effective permission codes */
CREATE OR ALTER PROCEDURE dbo.usp_Auth_GetUserForLogin
    @UsernameOrEmail NVARCHAR(256)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @Identifier NVARCHAR(256) = LTRIM(RTRIM(@UsernameOrEmail));
    DECLARE @UserId BIGINT;

    IF NULLIF(@Identifier, N'') IS NULL
        THROW 50003, N'UsernameOrEmail is required.', 1;

    -- Usernames cannot contain '@' (CK_Users_Username), so this is unambiguous.
    IF CHARINDEX(N'@', @Identifier) > 0
        SELECT @UserId = Id FROM dbo.Users WHERE Email = @Identifier AND IsDeleted = 0;
    ELSE
        SELECT @UserId = Id FROM dbo.Users WHERE Username = @Identifier AND IsDeleted = 0;

    SELECT u.Id,
           u.Username,
           u.Email,
           u.PasswordHash,
           u.FirstName,
           u.LastName,
           u.IsActive,
           u.FailedLoginCount,
           u.LockoutUntil,
           CAST(CASE WHEN u.LockoutUntil > SYSUTCDATETIME() THEN 1 ELSE 0 END AS BIT) AS IsLockedOut,
           u.MustChangePassword,
           u.LastLoginOn
    FROM dbo.Users u
    WHERE u.Id = @UserId;

    SELECT RoleId, RoleName
    FROM dbo.fn_GetUserRoles(@UserId)
    ORDER BY RoleName;

    SELECT PermissionCode
    FROM dbo.fn_GetUserPermissions(@UserId)
    ORDER BY PermissionCode;
END;
GO

/* Increments the failed counter; locks the account when it reaches
   @MaxFailedAttempts. An expired lockout restarts the count at 1. An active
   lockout is never extended (so attackers cannot keep an account locked). */
CREATE OR ALTER PROCEDURE dbo.usp_Auth_RecordFailedLogin
    @UserId            BIGINT,
    @MaxFailedAttempts INT,
    @LockoutMinutes    INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL OR ISNULL(@MaxFailedAttempts, 0) <= 0 OR ISNULL(@LockoutMinutes, 0) <= 0
        THROW 50003, N'UserId, a positive MaxFailedAttempts and a positive LockoutMinutes are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @Result TABLE (FailedLoginCount INT, LockoutUntil DATETIME2(3));

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        UPDATE u
        SET FailedLoginCount = c.NewCount,
            LockoutUntil = CASE
                               WHEN u.LockoutUntil > @Now               THEN u.LockoutUntil
                               WHEN c.NewCount >= @MaxFailedAttempts   THEN DATEADD(MINUTE, @LockoutMinutes, @Now)
                               ELSE NULL
                           END
        OUTPUT inserted.FailedLoginCount, inserted.LockoutUntil INTO @Result
        FROM dbo.Users u
        CROSS APPLY (SELECT CASE WHEN u.LockoutUntil <= @Now THEN 1 ELSE u.FailedLoginCount + 1 END AS NewCount) c
        WHERE u.Id = @UserId AND u.IsDeleted = 0;

        IF NOT EXISTS (SELECT 1 FROM @Result)
            THROW 50303, N'User not found.', 1;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT FailedLoginCount,
               LockoutUntil,
               CAST(CASE WHEN LockoutUntil > @Now THEN 1 ELSE 0 END AS BIT) AS IsLockedOut
        FROM @Result;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_Auth_RecordSuccessfulLogin
    @UserId BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL
        THROW 50003, N'UserId is required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        UPDATE dbo.Users
        SET FailedLoginCount = 0,
            LockoutUntil     = NULL,
            LastLoginOn      = @Now
        WHERE Id = @UserId AND IsDeleted = 0;

        IF @@ROWCOUNT = 0
            THROW 50303, N'User not found.', 1;

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO
