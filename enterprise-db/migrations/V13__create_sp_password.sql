/* =============================================================================
   V13__create_sp_password.sql
   Password change (by the user) and password reset (by an administrator).
   The API hashes the new password and, for a change, verifies the old one
   before calling. Hashes are never returned or written to the audit log.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* Self-service change. Clears MustChangePassword and revokes every OTHER
   session of the user: pass the caller's @CurrentSessionId to keep it
   (NULL revokes all). Returns Id, RowVersion, PasswordChangedOn and the
   revoke counts. */
CREATE OR ALTER PROCEDURE dbo.usp_User_ChangePassword
    @UserId           BIGINT,
    @NewPasswordHash  NVARCHAR(255),
    @CurrentSessionId UNIQUEIDENTIFIER = NULL,
    @ActorUserId      BIGINT,
    @IpAddress        NVARCHAR(45)     = NULL,
    @CorrelationId    NVARCHAR(100)    = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL OR NULLIF(LTRIM(RTRIM(@NewPasswordHash)), N'') IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'UserId, NewPasswordHash and ActorUserId are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @SessionsRevoked INT, @TokensRevoked INT,
            @EntityId NVARCHAR(100) = CAST(@UserId AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM dbo.Users WITH (UPDLOCK, HOLDLOCK)
                       WHERE Id = @UserId AND IsActive = 1 AND IsDeleted = 0)
            THROW 50303, N'User not found.', 1;
        IF @CurrentSessionId IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM dbo.UserSession WHERE Id = @CurrentSessionId AND UserId = @UserId)
            THROW 50205, N'Session does not belong to the user.', 1;

        SET @OldValues = dbo.fn_User_AuditJson(@UserId);

        UPDATE dbo.Users
        SET PasswordHash       = @NewPasswordHash,
            MustChangePassword = 0,
            PasswordChangedOn  = @Now,
            ModifiedBy         = @ActorUserId,
            ModifiedOn         = @Now
        WHERE Id = @UserId;

        EXEC dbo.usp_Internal_RevokeSessions
             @UserId          = @UserId,
             @ExceptSessionId = @CurrentSessionId,
             @Reason          = N'PASSWORD_CHANGED',
             @SessionsRevoked = @SessionsRevoked OUTPUT,
             @TokensRevoked   = @TokensRevoked   OUTPUT;

        SET @NewValues = JSON_MODIFY(JSON_MODIFY(dbo.fn_User_AuditJson(@UserId),
                             N'$.SessionsRevoked', @SessionsRevoked),
                             N'$.TokensRevoked', @TokensRevoked);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'PASSWORD_CHANGE', @EntityName = N'User', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT Id, RowVersion, PasswordChangedOn,
               @SessionsRevoked AS SessionsRevoked, @TokensRevoked AS TokensRevoked
        FROM dbo.Users WHERE Id = @UserId;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

/* Administrator reset. Sets MustChangePassword = 1, clears the lockout and
   revokes ALL sessions of the user. Inactive users can be reset; deleted
   users cannot. Same result shape as usp_User_ChangePassword. */
CREATE OR ALTER PROCEDURE dbo.usp_User_ResetPassword
    @UserId          BIGINT,
    @NewPasswordHash NVARCHAR(255),
    @ActorUserId     BIGINT,
    @IpAddress       NVARCHAR(45)  = NULL,
    @CorrelationId   NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL OR NULLIF(LTRIM(RTRIM(@NewPasswordHash)), N'') IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'UserId, NewPasswordHash and ActorUserId are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @SessionsRevoked INT, @TokensRevoked INT,
            @FailedLoginCount INT, @LockoutUntil DATETIME2(3),
            @EntityId NVARCHAR(100) = CAST(@UserId AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @FailedLoginCount = FailedLoginCount, @LockoutUntil = LockoutUntil
        FROM dbo.Users WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @UserId AND IsDeleted = 0;

        IF @@ROWCOUNT = 0
            THROW 50303, N'User not found.', 1;

        -- fn_User_AuditJson has no lockout state; record what the reset clears
        -- (LockoutUntil is omitted when it was NULL).
        SET @OldValues = JSON_MODIFY(JSON_MODIFY(dbo.fn_User_AuditJson(@UserId),
                             N'$.FailedLoginCount', @FailedLoginCount),
                             N'$.LockoutUntil', CONVERT(NVARCHAR(30), @LockoutUntil, 127));

        UPDATE dbo.Users
        SET PasswordHash       = @NewPasswordHash,
            MustChangePassword = 1,
            PasswordChangedOn  = @Now,
            FailedLoginCount   = 0,
            LockoutUntil       = NULL,
            ModifiedBy         = @ActorUserId,
            ModifiedOn         = @Now
        WHERE Id = @UserId;

        EXEC dbo.usp_Internal_RevokeSessions
             @UserId          = @UserId,
             @Reason          = N'PASSWORD_RESET',
             @SessionsRevoked = @SessionsRevoked OUTPUT,
             @TokensRevoked   = @TokensRevoked   OUTPUT;

        -- JSON_MODIFY drops a key set to NULL, so the cleared lockout is a flag.
        SET @NewValues = JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(dbo.fn_User_AuditJson(@UserId),
                             N'$.FailedLoginCount', 0),
                             N'$.LockoutCleared', CAST(1 AS BIT)),
                             N'$.SessionsRevoked', @SessionsRevoked),
                             N'$.TokensRevoked', @TokensRevoked);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'PASSWORD_RESET', @EntityName = N'User', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT Id, RowVersion, PasswordChangedOn,
               @SessionsRevoked AS SessionsRevoked, @TokensRevoked AS TokensRevoked
        FROM dbo.Users WHERE Id = @UserId;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO
