/* =============================================================================
   V7__create_sp_audit.sql
   Audit inserts. Both return the new Id through an OUTPUT parameter (no result
   set) so they can be called from inside other procedures.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_LoginAudit_Insert
    @UserId            BIGINT        = NULL,
    @UsernameAttempted NVARCHAR(256),
    @IsSuccess         BIT,
    @FailureReason     NVARCHAR(100) = NULL,
    @IpAddress         NVARCHAR(45)  = NULL,
    @UserAgent         NVARCHAR(512) = NULL,
    @LoginAuditId      BIGINT        = NULL OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UsernameAttempted IS NULL OR @IsSuccess IS NULL
        THROW 50003, N'UsernameAttempted and IsSuccess are required.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        -- Unknown/stale user ids are recorded as NULL rather than failing the audit.
        IF @UserId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Id = @UserId)
            SET @UserId = NULL;

        INSERT INTO dbo.LoginAudit (UserId, UsernameAttempted, IsSuccess, FailureReason, IpAddress, UserAgent)
        VALUES (@UserId, @UsernameAttempted, @IsSuccess, @FailureReason, @IpAddress, @UserAgent);

        SET @LoginAuditId = CAST(SCOPE_IDENTITY() AS BIGINT);

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_AuditLog_Insert
    @ActorUserId   BIGINT        = NULL,
    @Action        NVARCHAR(50),
    @EntityName    NVARCHAR(100),
    @EntityId      NVARCHAR(100) = NULL,
    @OldValues     NVARCHAR(MAX) = NULL,
    @NewValues     NVARCHAR(MAX) = NULL,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL,
    @AuditLogId    BIGINT        = NULL OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NULLIF(LTRIM(RTRIM(@Action)), N'') IS NULL OR NULLIF(LTRIM(RTRIM(@EntityName)), N'') IS NULL
        THROW 50003, N'Action and EntityName are required.', 1;
    IF (@OldValues IS NOT NULL AND ISJSON(@OldValues) = 0) OR (@NewValues IS NOT NULL AND ISJSON(@NewValues) = 0)
        THROW 50003, N'OldValues and NewValues must be valid JSON.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        INSERT INTO dbo.AuditLog (ActorUserId, Action, EntityName, EntityId, OldValues, NewValues, IpAddress, CorrelationId)
        VALUES (@ActorUserId, @Action, @EntityName, @EntityId, @OldValues, @NewValues, @IpAddress, @CorrelationId);

        SET @AuditLogId = CAST(SCOPE_IDENTITY() AS BIGINT);

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO
