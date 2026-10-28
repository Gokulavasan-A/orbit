/* =============================================================================
   V5__create_sp_session.sql
   Server-side sessions: create, validate (absolute + idle timeout),
   revoke, logout-all, cleanup. Revoking a session also revokes its refresh tokens.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* Internal helper (no result set, safe to call from other procedures).
   Revokes one session (@SessionId) or all sessions of a user (@UserId,
   optionally keeping @ExceptSessionId), plus their active refresh tokens. */
CREATE OR ALTER PROCEDURE dbo.usp_Internal_RevokeSessions
    @UserId          BIGINT           = NULL,
    @SessionId       UNIQUEIDENTIFIER = NULL,
    @ExceptSessionId UNIQUEIDENTIFIER = NULL,
    @Reason          NVARCHAR(100),
    @SessionsRevoked INT = NULL OUTPUT,
    @TokensRevoked   INT = NULL OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF (@UserId IS NULL AND @SessionId IS NULL) OR (@UserId IS NOT NULL AND @SessionId IS NOT NULL)
        THROW 50003, N'Exactly one of UserId or SessionId is required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        UPDATE dbo.UserSession
        SET IsRevoked = 1, RevokedOn = @Now, RevokedReason = @Reason
        WHERE IsRevoked = 0
          AND ((@SessionId IS NOT NULL AND Id = @SessionId)
            OR (@UserId IS NOT NULL AND UserId = @UserId AND (@ExceptSessionId IS NULL OR Id <> @ExceptSessionId)));
        SET @SessionsRevoked = @@ROWCOUNT;

        UPDATE dbo.RefreshToken
        SET RevokedOn = @Now, RevokedReason = @Reason
        WHERE RevokedOn IS NULL
          AND ((@SessionId IS NOT NULL AND SessionId = @SessionId)
            OR (@UserId IS NOT NULL AND UserId = @UserId AND (@ExceptSessionId IS NULL OR SessionId <> @ExceptSessionId)));
        SET @TokensRevoked = @@ROWCOUNT;

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_Session_Create
    @UserId                 BIGINT,
    @AbsoluteTimeoutMinutes INT,
    @IpAddress              NVARCHAR(45)  = NULL,
    @UserAgent              NVARCHAR(512) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL OR ISNULL(@AbsoluteTimeoutMinutes, 0) <= 0
        THROW 50003, N'UserId and a positive AbsoluteTimeoutMinutes are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @SessionId UNIQUEIDENTIFIER = NEWID();
    DECLARE @ExpiresOn DATETIME2(3) = DATEADD(MINUTE, @AbsoluteTimeoutMinutes, @Now);
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Id = @UserId AND IsActive = 1 AND IsDeleted = 0)
            THROW 50101, N'User not found or inactive.', 1;

        INSERT INTO dbo.UserSession (Id, UserId, CreatedOn, LastActivityOn, ExpiresOn, IpAddress, UserAgent)
        VALUES (@SessionId, @UserId, @Now, @Now, @ExpiresOn, @IpAddress, @UserAgent);

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @SessionId AS SessionId, @Now AS CreatedOn, @ExpiresOn AS ExpiresOn;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

/* Status values: VALID, NOT_FOUND, REVOKED, USER_INACTIVE, EXPIRED, IDLE.
   USER_INACTIVE / EXPIRED / IDLE sessions are revoked on the spot.
   LastActivityOn is refreshed at most once a minute to limit writes, so the
   idle timeout has up to 60 seconds of slack. */
CREATE OR ALTER PROCEDURE dbo.usp_Session_Validate
    @SessionId          UNIQUEIDENTIFIER,
    @IdleTimeoutMinutes INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @SessionId IS NULL OR ISNULL(@IdleTimeoutMinutes, 0) <= 0
        THROW 50003, N'SessionId and a positive IdleTimeoutMinutes are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @UserId BIGINT, @IsRevoked BIT, @ExpiresOn DATETIME2(3), @LastActivityOn DATETIME2(3),
            @UserActive BIT, @Status VARCHAR(20);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @UserId         = s.UserId,
               @IsRevoked      = s.IsRevoked,
               @ExpiresOn      = s.ExpiresOn,
               @LastActivityOn = s.LastActivityOn,
               @UserActive     = CASE WHEN u.IsActive = 1 AND u.IsDeleted = 0 THEN 1 ELSE 0 END
        FROM dbo.UserSession s
        INNER JOIN dbo.Users u ON u.Id = s.UserId
        WHERE s.Id = @SessionId;

        SET @Status = CASE
                          WHEN @UserId IS NULL                                            THEN 'NOT_FOUND'
                          WHEN @IsRevoked = 1                                             THEN 'REVOKED'
                          WHEN @UserActive = 0                                            THEN 'USER_INACTIVE'
                          WHEN @ExpiresOn <= @Now                                         THEN 'EXPIRED'
                          WHEN DATEADD(MINUTE, @IdleTimeoutMinutes, @LastActivityOn) <= @Now THEN 'IDLE'
                          ELSE 'VALID'
                      END;

        IF @Status IN ('USER_INACTIVE', 'EXPIRED', 'IDLE')
        BEGIN
            DECLARE @Reason NVARCHAR(100) = @Status;
            EXEC dbo.usp_Internal_RevokeSessions @SessionId = @SessionId, @Reason = @Reason;
        END
        ELSE IF @Status = 'VALID' AND @LastActivityOn < DATEADD(SECOND, -60, @Now)
        BEGIN
            UPDATE dbo.UserSession SET LastActivityOn = @Now WHERE Id = @SessionId;
            SET @LastActivityOn = @Now;
        END

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @SessionId      AS SessionId,
               @UserId         AS UserId,
               @Status         AS Status,
               @ExpiresOn      AS ExpiresOn,
               @LastActivityOn AS LastActivityOn;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

/* Idempotent: revoking an already revoked session returns 0 counts. */
CREATE OR ALTER PROCEDURE dbo.usp_Session_Revoke
    @SessionId UNIQUEIDENTIFIER,
    @Reason    NVARCHAR(100) = N'LOGOUT'
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @SessionId IS NULL
        THROW 50003, N'SessionId is required.', 1;

    DECLARE @SessionsRevoked INT, @TokensRevoked INT;

    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.UserSession WHERE Id = @SessionId)
            THROW 50201, N'Session not found.', 1;

        EXEC dbo.usp_Internal_RevokeSessions
             @SessionId       = @SessionId,
             @Reason          = @Reason,
             @SessionsRevoked = @SessionsRevoked OUTPUT,
             @TokensRevoked   = @TokensRevoked   OUTPUT;

        SELECT @SessionsRevoked AS SessionsRevoked, @TokensRevoked AS TokensRevoked;
    END TRY
    BEGIN CATCH
        THROW;
    END CATCH
END;
GO

/* Logout-all. Pass @ExceptSessionId to keep the caller's current session
   ("sign out other devices"). */
CREATE OR ALTER PROCEDURE dbo.usp_Session_RevokeAllForUser
    @UserId          BIGINT,
    @Reason          NVARCHAR(100)    = N'LOGOUT_ALL',
    @ExceptSessionId UNIQUEIDENTIFIER = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL
        THROW 50003, N'UserId is required.', 1;

    DECLARE @SessionsRevoked INT, @TokensRevoked INT;

    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Id = @UserId)
            THROW 50303, N'User not found.', 1;

        EXEC dbo.usp_Internal_RevokeSessions
             @UserId          = @UserId,
             @ExceptSessionId = @ExceptSessionId,
             @Reason          = @Reason,
             @SessionsRevoked = @SessionsRevoked OUTPUT,
             @TokensRevoked   = @TokensRevoked   OUTPUT;

        SELECT @SessionsRevoked AS SessionsRevoked, @TokensRevoked AS TokensRevoked;
    END TRY
    BEGIN CATCH
        THROW;
    END CATCH
END;
GO

/* Purges sessions and refresh tokens that expired or were revoked more than
   @RetentionDays ago. Intended for a scheduled job. Deletes run in small
   batches, each batch its own atomic statement, so the job never holds a
   long transaction or large lock footprint. */
CREATE OR ALTER PROCEDURE dbo.usp_Session_CleanupExpired
    @RetentionDays INT = 30,
    @BatchSize     INT = 5000
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF ISNULL(@RetentionDays, -1) < 0 OR ISNULL(@BatchSize, 0) <= 0
        THROW 50003, N'RetentionDays must be >= 0 and BatchSize must be positive.', 1;

    DECLARE @Cutoff DATETIME2(3) = DATEADD(DAY, -@RetentionDays, SYSUTCDATETIME());
    DECLARE @TokensDeleted INT = 0, @SessionsDeleted INT = 0, @Rows INT = 1;

    BEGIN TRY
        -- Tokens that are old on their own, or belong to a purgeable session.
        WHILE @Rows > 0
        BEGIN
            DELETE TOP (@BatchSize) rt
            FROM dbo.RefreshToken rt
            WHERE rt.ExpiresOn < @Cutoff
               OR rt.RevokedOn < @Cutoff
               OR EXISTS (SELECT 1 FROM dbo.UserSession s
                          WHERE s.Id = rt.SessionId
                            AND (s.ExpiresOn < @Cutoff OR s.RevokedOn < @Cutoff));
            SET @Rows = @@ROWCOUNT;
            SET @TokensDeleted += @Rows;
        END

        SET @Rows = 1;
        WHILE @Rows > 0
        BEGIN
            DELETE TOP (@BatchSize) s
            FROM dbo.UserSession s
            WHERE (s.ExpiresOn < @Cutoff OR s.RevokedOn < @Cutoff)
              AND NOT EXISTS (SELECT 1 FROM dbo.RefreshToken rt WHERE rt.SessionId = s.Id);
            SET @Rows = @@ROWCOUNT;
            SET @SessionsDeleted += @Rows;
        END

        SELECT @SessionsDeleted AS SessionsDeleted, @TokensDeleted AS TokensDeleted;
    END TRY
    BEGIN CATCH
        THROW;
    END CATCH
END;
GO
