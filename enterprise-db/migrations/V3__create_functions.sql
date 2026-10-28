/* =============================================================================
   V3__create_functions.sql
   Effective-permission functions. Only active, non-deleted users, roles and
   active permissions count.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER FUNCTION dbo.fn_GetUserRoles (@UserId BIGINT)
RETURNS TABLE
AS
RETURN
(
    SELECT r.Id   AS RoleId,
           r.Name AS RoleName
    FROM dbo.UserRoles ur
    INNER JOIN dbo.Roles r ON r.Id = ur.RoleId
    INNER JOIN dbo.Users u ON u.Id = ur.UserId
    WHERE ur.UserId  = @UserId
      AND u.IsActive = 1 AND u.IsDeleted = 0
      AND r.IsActive = 1 AND r.IsDeleted = 0
);
GO

CREATE OR ALTER FUNCTION dbo.fn_GetUserPermissions (@UserId BIGINT)
RETURNS TABLE
AS
RETURN
(
    SELECT DISTINCT
           p.Id     AS PermissionId,
           p.Code   AS PermissionCode,
           p.Name   AS PermissionName,
           p.Module AS Module
    FROM dbo.fn_GetUserRoles(@UserId) ur
    INNER JOIN dbo.RolePermissions rp ON rp.RoleId = ur.RoleId
    INNER JOIN dbo.Permissions p      ON p.Id = rp.PermissionId
    WHERE p.IsActive = 1
);
GO

CREATE OR ALTER FUNCTION dbo.fn_UserHasPermission (@UserId BIGINT, @PermissionCode VARCHAR(100))
RETURNS BIT
AS
BEGIN
    RETURN CASE
               WHEN EXISTS (SELECT 1 FROM dbo.fn_GetUserPermissions(@UserId) WHERE PermissionCode = @PermissionCode)
               THEN CAST(1 AS BIT)
               ELSE CAST(0 AS BIT)
           END;
END;
GO

/* Internal helper: 1 when @UserId is an active holder of the system Admin role
   and no other active user holds it. Used to block removing the last admin. */
CREATE OR ALTER FUNCTION dbo.fn_IsLastActiveAdmin (@UserId BIGINT)
RETURNS BIT
AS
BEGIN
    DECLARE @AdminRoleId BIGINT =
        (SELECT Id FROM dbo.Roles WHERE Name = N'Admin' AND IsSystem = 1 AND IsDeleted = 0);

    IF @AdminRoleId IS NULL
        RETURN 0;

    IF NOT EXISTS (SELECT 1
                   FROM dbo.UserRoles ur
                   INNER JOIN dbo.Users u ON u.Id = ur.UserId
                   WHERE ur.UserId = @UserId AND ur.RoleId = @AdminRoleId
                     AND u.IsActive = 1 AND u.IsDeleted = 0)
        RETURN 0;

    IF EXISTS (SELECT 1
               FROM dbo.UserRoles ur
               INNER JOIN dbo.Users u ON u.Id = ur.UserId
               WHERE ur.RoleId = @AdminRoleId AND ur.UserId <> @UserId
                 AND u.IsActive = 1 AND u.IsDeleted = 0)
        RETURN 0;

    RETURN 1;
END;
GO
