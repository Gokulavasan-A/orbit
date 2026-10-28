/* =============================================================================
   V2__create_indexes.sql
   Performance indexes. Integrity indexes (PK / UNIQUE / filtered unique)
   live with their tables in V1.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- Users: default grid sort (name) for non-deleted rows.
CREATE NONCLUSTERED INDEX IX_Users_LastName_FirstName
    ON dbo.Users (LastName, FirstName)
    WHERE IsDeleted = 0;

-- Link tables: UserId / RoleId leading columns are already covered by the UNIQUE constraints.
CREATE NONCLUSTERED INDEX IX_UserRoles_RoleId             ON dbo.UserRoles (RoleId);
CREATE NONCLUSTERED INDEX IX_RolePermissions_PermissionId ON dbo.RolePermissions (PermissionId);

-- Sessions: per-user lookups (logout-all) and cleanup.
CREATE NONCLUSTERED INDEX IX_UserSession_UserId_IsRevoked
    ON dbo.UserSession (UserId, IsRevoked)
    INCLUDE (ExpiresOn, LastActivityOn);
CREATE NONCLUSTERED INDEX IX_UserSession_ExpiresOn ON dbo.UserSession (ExpiresOn);

-- Refresh tokens: revoke by session / user, cleanup by expiry.
CREATE NONCLUSTERED INDEX IX_RefreshToken_SessionId ON dbo.RefreshToken (SessionId) INCLUDE (RevokedOn);
CREATE NONCLUSTERED INDEX IX_RefreshToken_UserId    ON dbo.RefreshToken (UserId)    INCLUDE (RevokedOn);
CREATE NONCLUSTERED INDEX IX_RefreshToken_ExpiresOn ON dbo.RefreshToken (ExpiresOn);

-- Audit tables: per-user history and time-range queries.
CREATE NONCLUSTERED INDEX IX_LoginAudit_UserId_OccurredOn ON dbo.LoginAudit (UserId, OccurredOn);
CREATE NONCLUSTERED INDEX IX_LoginAudit_OccurredOn        ON dbo.LoginAudit (OccurredOn);
CREATE NONCLUSTERED INDEX IX_AuditLog_Entity              ON dbo.AuditLog (EntityName, EntityId) INCLUDE (OccurredOn);
CREATE NONCLUSTERED INDEX IX_AuditLog_ActorUserId         ON dbo.AuditLog (ActorUserId, OccurredOn);
CREATE NONCLUSTERED INDEX IX_AuditLog_OccurredOn          ON dbo.AuditLog (OccurredOn);
GO
