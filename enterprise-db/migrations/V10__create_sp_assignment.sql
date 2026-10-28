/* =============================================================================
   V10__create_sp_assignment.sql
   User-role and role-permission links. Assign is idempotent (existing link =
   no-op, no audit row). Revoke deletes the link; AuditLog keeps the history.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_UserRole_Assign
    @UserId        BIGINT,
    @RoleId        BIGINT,
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL OR @RoleId IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'UserId, RoleId and ActorUserId are required.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @EntityId NVARCHAR(100) = CAST(@UserId AS NVARCHAR(100)), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Id = @UserId AND IsDeleted = 0)
            THROW 50303, N'User not found.', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.Roles WHERE Id = @RoleId AND IsDeleted = 0)
            THROW 50403, N'Role not found.', 1;

        IF NOT EXISTS (SELECT 1 FROM dbo.UserRoles WITH (UPDLOCK, HOLDLOCK) WHERE UserId = @UserId AND RoleId = @RoleId)
        BEGIN
            INSERT INTO dbo.UserRoles (UserId, RoleId, CreatedBy) VALUES (@UserId, @RoleId, @ActorUserId);

            SET @NewValues = (SELECT r.Id AS RoleId, r.Name AS RoleName FROM dbo.Roles r WHERE r.Id = @RoleId
                              FOR JSON PATH, WITHOUT_ARRAY_WRAPPER);

            EXEC dbo.usp_AuditLog_Insert
                 @ActorUserId = @ActorUserId, @Action = N'ASSIGN_ROLE', @EntityName = N'User', @EntityId = @EntityId,
                 @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;
        END

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_UserRole_Revoke
    @UserId        BIGINT,
    @RoleId        BIGINT,
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @UserId IS NULL OR @RoleId IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'UserId, RoleId and ActorUserId are required.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @EntityId NVARCHAR(100) = CAST(@UserId AS NVARCHAR(100)), @OldValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF EXISTS (SELECT 1 FROM dbo.UserRoles WITH (UPDLOCK, HOLDLOCK) WHERE UserId = @UserId AND RoleId = @RoleId)
        BEGIN
            IF EXISTS (SELECT 1 FROM dbo.Roles WHERE Id = @RoleId AND Name = N'Admin' AND IsSystem = 1)
               AND dbo.fn_IsLastActiveAdmin(@UserId) = 1
                THROW 50004, N'This would remove the last active administrator.', 1;

            SET @OldValues = (SELECT r.Id AS RoleId, r.Name AS RoleName FROM dbo.Roles r WHERE r.Id = @RoleId
                              FOR JSON PATH, WITHOUT_ARRAY_WRAPPER);

            DELETE FROM dbo.UserRoles WHERE UserId = @UserId AND RoleId = @RoleId;

            EXEC dbo.usp_AuditLog_Insert
                 @ActorUserId = @ActorUserId, @Action = N'REVOKE_ROLE', @EntityName = N'User', @EntityId = @EntityId,
                 @OldValues = @OldValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;
        END

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_RolePermission_Assign
    @RoleId        BIGINT,
    @PermissionId  BIGINT,
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @RoleId IS NULL OR @PermissionId IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'RoleId, PermissionId and ActorUserId are required.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @EntityId NVARCHAR(100) = CAST(@RoleId AS NVARCHAR(100)), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM dbo.Roles WHERE Id = @RoleId AND IsDeleted = 0)
            THROW 50403, N'Role not found.', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.Permissions WHERE Id = @PermissionId)
            THROW 50501, N'Permission not found.', 1;

        IF NOT EXISTS (SELECT 1 FROM dbo.RolePermissions WITH (UPDLOCK, HOLDLOCK) WHERE RoleId = @RoleId AND PermissionId = @PermissionId)
        BEGIN
            INSERT INTO dbo.RolePermissions (RoleId, PermissionId, CreatedBy) VALUES (@RoleId, @PermissionId, @ActorUserId);

            SET @NewValues = (SELECT p.Id AS PermissionId, p.Code AS PermissionCode FROM dbo.Permissions p WHERE p.Id = @PermissionId
                              FOR JSON PATH, WITHOUT_ARRAY_WRAPPER);

            EXEC dbo.usp_AuditLog_Insert
                 @ActorUserId = @ActorUserId, @Action = N'ASSIGN_PERMISSION', @EntityName = N'Role', @EntityId = @EntityId,
                 @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;
        END

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO

/* ROLE_MANAGE can never be removed from the system Admin role, so nobody can
   lock themselves out of role management. */
CREATE OR ALTER PROCEDURE dbo.usp_RolePermission_Revoke
    @RoleId        BIGINT,
    @PermissionId  BIGINT,
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @RoleId IS NULL OR @PermissionId IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'RoleId, PermissionId and ActorUserId are required.', 1;

    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @EntityId NVARCHAR(100) = CAST(@RoleId AS NVARCHAR(100)), @OldValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF EXISTS (SELECT 1 FROM dbo.RolePermissions WITH (UPDLOCK, HOLDLOCK) WHERE RoleId = @RoleId AND PermissionId = @PermissionId)
        BEGIN
            IF EXISTS (SELECT 1 FROM dbo.Roles WHERE Id = @RoleId AND Name = N'Admin' AND IsSystem = 1)
               AND EXISTS (SELECT 1 FROM dbo.Permissions WHERE Id = @PermissionId AND Code = 'ROLE_MANAGE')
                THROW 50502, N'ROLE_MANAGE cannot be removed from the Admin role.', 1;

            SET @OldValues = (SELECT p.Id AS PermissionId, p.Code AS PermissionCode FROM dbo.Permissions p WHERE p.Id = @PermissionId
                              FOR JSON PATH, WITHOUT_ARRAY_WRAPPER);

            DELETE FROM dbo.RolePermissions WHERE RoleId = @RoleId AND PermissionId = @PermissionId;

            EXEC dbo.usp_AuditLog_Insert
                 @ActorUserId = @ActorUserId, @Action = N'REVOKE_PERMISSION', @EntityName = N'Role', @EntityId = @EntityId,
                 @OldValues = @OldValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;
        END

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO
