/* =============================================================================
   V6__create_sp_refresh_token.sql
   Refresh tokens are stored as lowercase hex SHA-256 hashes; the raw token
   only ever exists in the client's HttpOnly cookie.

   Rotation: call usp_RefreshToken_Save with @ReplacesTokenHash = old hash.
   That revokes the old token and inserts the new one atomically, and throws
   50204 if the old token was already used (reuse => API should call
   usp_Session_RevokeAllForUser).
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_RefreshToken_Save
    @SessionId         UNIQUEIDENTIFIER,
    @UserId            BIGINT,
    @TokenHash         CHAR(64),
    @ExpiresOn         DATETIME2(3),
    @CreatedByIp       NVARCHAR(45) = NULL,
    @ReplacesTokenHash CHAR(64)     = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @SessionId IS NULL OR @UserId IS NULL OR @TokenHash IS NULL OR @ExpiresOn IS NULL
        THROW 50003, N'SessionId, UserId, TokenHash and ExpiresOn are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @SessionUserId BIGINT, @SessionExpiresOn DATETIME2(3), @SessionRevoked BIT, @NewId BIGINT;

    IF @ExpiresOn <= @Now
        THROW 50003, N'ExpiresOn must be in the future.', 1;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @SessionUserId    = UserId,
               @SessionExpiresOn = ExpiresOn,
               @SessionRevoked   = IsRevoked
        FROM dbo.UserSession WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @SessionId;

        IF @SessionUserId IS NULL
            THROW 50201, N'Session not found.', 1;
        IF @SessionUserId <> @UserId
            THROW 50205, N'Session does not belong to the user.', 1;
        IF @SessionRevoked = 1 OR @SessionExpiresOn <= @Now
            THROW 50202, N'Session is revoked or expired.', 1;

        IF @ReplacesTokenHash IS NOT NULL
        BEGIN
            UPDATE dbo.RefreshToken
            SET RevokedOn = @Now, RevokedReason = N'ROTATED', ReplacedByTokenHash = @TokenHash
            WHERE TokenHash = @ReplacesTokenHash
              AND SessionId = @SessionId
              AND RevokedOn IS NULL;

            IF @@ROWCOUNT = 0
            BEGIN
                IF EXISTS (SELECT 1 FROM dbo.RefreshToken WHERE TokenHash = @ReplacesTokenHash AND SessionId = @SessionId)
                    THROW 50204, N'Refresh token has already been used or revoked.', 1;
                THROW 50203, N'Refresh token not found.', 1;
            END
        END

        -- A refresh token never outlives its session.
        IF @ExpiresOn > @SessionExpiresOn
            SET @ExpiresOn = @SessionExpiresOn;

        INSERT INTO dbo.RefreshToken (SessionId, UserId, TokenHash, ExpiresOn, CreatedOn, CreatedByIp)
        VALUES (@SessionId, @UserId, @TokenHash, @ExpiresOn, @Now, @CreatedByIp);

        SET @NewId = CAST(SCOPE_IDENTITY() AS BIGINT);

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @NewId AS Id, @ExpiresOn AS ExpiresOn;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

/* Status values: ACTIVE, EXPIRED, REVOKED, REUSED (rotated token presented
   again), SESSION_INVALID (session revoked/expired or user inactive).
   Returns an empty result set when the hash is unknown. */
CREATE OR ALTER PROCEDURE dbo.usp_RefreshToken_Get
    @TokenHash CHAR(64)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @TokenHash IS NULL
        THROW 50003, N'TokenHash is required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();

    SELECT rt.Id,
           rt.SessionId,
           rt.UserId,
           rt.ExpiresOn,
           rt.CreatedOn,
           rt.RevokedOn,
           rt.RevokedReason,
           rt.ReplacedByTokenHash,
           s.ExpiresOn AS SessionExpiresOn,
           s.IsRevoked AS SessionIsRevoked,
           CASE
               WHEN rt.RevokedOn IS NOT NULL AND rt.ReplacedByTokenHash IS NOT NULL THEN 'REUSED'
               WHEN rt.RevokedOn IS NOT NULL                                       THEN 'REVOKED'
               WHEN s.IsRevoked = 1 OR s.ExpiresOn <= @Now
                    OR u.IsActive = 0 OR u.IsDeleted = 1                           THEN 'SESSION_INVALID'
               WHEN rt.ExpiresOn <= @Now                                           THEN 'EXPIRED'
               ELSE 'ACTIVE'
           END AS Status
    FROM dbo.RefreshToken rt
    INNER JOIN dbo.UserSession s ON s.Id = rt.SessionId
    INNER JOIN dbo.Users u       ON u.Id = rt.UserId
    WHERE rt.TokenHash = @TokenHash;
END;
GO

/* Idempotent: returns TokensRevoked = 0 when already revoked. */
CREATE OR ALTER PROCEDURE dbo.usp_RefreshToken_Revoke
    @TokenHash           CHAR(64),
    @ReplacedByTokenHash CHAR(64)      = NULL,
    @Reason              NVARCHAR(100) = N'REVOKED'
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @TokenHash IS NULL
        THROW 50003, N'TokenHash is required.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @Revoked INT;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM dbo.RefreshToken WHERE TokenHash = @TokenHash)
            THROW 50203, N'Refresh token not found.', 1;

        UPDATE dbo.RefreshToken
        SET RevokedOn           = SYSUTCDATETIME(),
            RevokedReason       = @Reason,
            ReplacedByTokenHash = @ReplacedByTokenHash
        WHERE TokenHash = @TokenHash AND RevokedOn IS NULL;

        SET @Revoked = @@ROWCOUNT;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @Revoked AS TokensRevoked;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO
