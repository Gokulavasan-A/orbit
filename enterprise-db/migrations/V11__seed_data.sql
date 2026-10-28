/* =============================================================================
   V11__seed_data.sql
   Base roles, permissions, role-permission links and the initial admin user.
   Idempotent (safe to re-run manually).

   ADMIN PASSWORD
   The admin hash below is BCrypt (cost 12) of the DEV-ONLY password
       ChangeMe@123!
   and MustChangePassword = 1. Replace it before any shared or production
   deployment (see README.md "Replacing the admin password"):
       UPDATE dbo.Users
       SET PasswordHash = N'<new $2a$/$2b$ bcrypt hash>', MustChangePassword = 1,
           PasswordChangedOn = SYSUTCDATETIME()
       WHERE Username = N'admin';
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRANSACTION;

/* Roles ----------------------------------------------------------------- */
INSERT INTO dbo.Roles (Name, Description, IsSystem)
SELECT v.Name, v.Description, 1
FROM (VALUES (N'Admin', N'Full system administration'),
             (N'User',  N'Standard application user')) v (Name, Description)
WHERE NOT EXISTS (SELECT 1 FROM dbo.Roles r WHERE r.Name = v.Name AND r.IsDeleted = 0);

/* Permissions ----------------------------------------------------------- */
INSERT INTO dbo.Permissions (Code, Name, Module, Description)
SELECT v.Code, v.Name, v.Module, v.Description
FROM (VALUES ('USER_VIEW',   N'View users',   N'Users', N'View the user list and user details'),
             ('USER_CREATE', N'Create users', N'Users', N'Create new users'),
             ('USER_EDIT',   N'Edit users',   N'Users', N'Edit users and their role assignments'),
             ('USER_DELETE', N'Delete users', N'Users', N'Soft-delete users'),
             ('ROLE_VIEW',   N'View roles',   N'Roles', N'View roles and their permissions'),
             ('ROLE_MANAGE', N'Manage roles', N'Roles', N'Create, edit and delete roles and assign permissions')
     ) v (Code, Name, Module, Description)
WHERE NOT EXISTS (SELECT 1 FROM dbo.Permissions p WHERE p.Code = v.Code);

/* Role permissions: Admin = all, User = USER_VIEW ------------------------ */
INSERT INTO dbo.RolePermissions (RoleId, PermissionId)
SELECT r.Id, p.Id
FROM dbo.Roles r
INNER JOIN dbo.Permissions p
        ON (r.Name = N'Admin')
        OR (r.Name = N'User' AND p.Code IN ('USER_VIEW'))
WHERE r.IsSystem = 1 AND r.IsDeleted = 0
  AND NOT EXISTS (SELECT 1 FROM dbo.RolePermissions rp WHERE rp.RoleId = r.Id AND rp.PermissionId = p.Id);

/* Admin user (dev password: ChangeMe@123!) ------------------------------ */
INSERT INTO dbo.Users (Username, Email, PasswordHash, FirstName, LastName, MustChangePassword, PasswordChangedOn)
SELECT N'admin', N'admin@enterprise.local',
       N'$2a$12$NC.nL3Xt/p0tIx1/g.W8bOJVHDXm6hyTorOvv3atbkMGGILvgHLWK',
       N'System', N'Administrator', 1, SYSUTCDATETIME()
WHERE NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Username = N'admin' AND IsDeleted = 0);

INSERT INTO dbo.UserRoles (UserId, RoleId)
SELECT u.Id, r.Id
FROM dbo.Users u
CROSS JOIN dbo.Roles r
WHERE u.Username = N'admin' AND u.IsDeleted = 0
  AND r.Name = N'Admin' AND r.IsSystem = 1 AND r.IsDeleted = 0
  AND NOT EXISTS (SELECT 1 FROM dbo.UserRoles ur WHERE ur.UserId = u.Id AND ur.RoleId = r.Id);

COMMIT TRANSACTION;
GO
