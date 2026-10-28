/* =============================================================================
   V1__create_tables.sql
   Core security schema: users, roles, permissions, sessions, refresh tokens,
   login audit and audit log.

   Conventions
   - All dates are UTC, DATETIME2(3), default SYSUTCDATETIME().
   - CreatedBy / ModifiedBy hold a Users.Id but have no FK (avoids self-reference
     cycles and allows NULL for system actions such as seeding).
   - Uniqueness on soft-deletable tables uses filtered unique indexes
     (WHERE IsDeleted = 0) so names can be reused after a soft delete.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ----------------------------------------------------------------------------
   Users
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.Users
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    Username            NVARCHAR(100)   NOT NULL,
    Email               NVARCHAR(256)   NOT NULL,
    PasswordHash        NVARCHAR(255)   NOT NULL,
    FirstName           NVARCHAR(100)   NOT NULL,
    LastName            NVARCHAR(100)   NOT NULL,
    PhoneNumber         NVARCHAR(30)    NULL,
    FailedLoginCount    INT             NOT NULL CONSTRAINT DF_Users_FailedLoginCount   DEFAULT (0),
    LockoutUntil        DATETIME2(3)    NULL,
    LastLoginOn         DATETIME2(3)    NULL,
    MustChangePassword  BIT             NOT NULL CONSTRAINT DF_Users_MustChangePassword DEFAULT (0),
    PasswordChangedOn   DATETIME2(3)    NULL,
    CreatedBy           BIGINT          NULL,
    CreatedOn           DATETIME2(3)    NOT NULL CONSTRAINT DF_Users_CreatedOn          DEFAULT (SYSUTCDATETIME()),
    ModifiedBy          BIGINT          NULL,
    ModifiedOn          DATETIME2(3)    NULL,
    IsActive            BIT             NOT NULL CONSTRAINT DF_Users_IsActive           DEFAULT (1),
    IsDeleted           BIT             NOT NULL CONSTRAINT DF_Users_IsDeleted          DEFAULT (0),
    RowVersion          ROWVERSION      NOT NULL,

    CONSTRAINT PK_Users PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT CK_Users_FailedLoginCount CHECK (FailedLoginCount >= 0),
    -- Username may not contain '@' so a login identifier is unambiguously a username or an email.
    CONSTRAINT CK_Users_Username        CHECK (LEN(Username) >= 3 AND Username NOT LIKE N'%@%'),
    CONSTRAINT CK_Users_Email           CHECK (Email LIKE N'%_@_%._%')
);
GO

CREATE UNIQUE NONCLUSTERED INDEX UX_Users_Username ON dbo.Users (Username) WHERE IsDeleted = 0;
CREATE UNIQUE NONCLUSTERED INDEX UX_Users_Email    ON dbo.Users (Email)    WHERE IsDeleted = 0;
GO

/* ----------------------------------------------------------------------------
   Roles
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.Roles
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    Name                NVARCHAR(100)   NOT NULL,
    Description         NVARCHAR(500)   NULL,
    IsSystem            BIT             NOT NULL CONSTRAINT DF_Roles_IsSystem   DEFAULT (0),
    CreatedBy           BIGINT          NULL,
    CreatedOn           DATETIME2(3)    NOT NULL CONSTRAINT DF_Roles_CreatedOn  DEFAULT (SYSUTCDATETIME()),
    ModifiedBy          BIGINT          NULL,
    ModifiedOn          DATETIME2(3)    NULL,
    IsActive            BIT             NOT NULL CONSTRAINT DF_Roles_IsActive   DEFAULT (1),
    IsDeleted           BIT             NOT NULL CONSTRAINT DF_Roles_IsDeleted  DEFAULT (0),
    RowVersion          ROWVERSION      NOT NULL,

    CONSTRAINT PK_Roles PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT CK_Roles_Name CHECK (LEN(Name) >= 2)
);
GO

CREATE UNIQUE NONCLUSTERED INDEX UX_Roles_Name ON dbo.Roles (Name) WHERE IsDeleted = 0;
GO

/* ----------------------------------------------------------------------------
   Permissions (code-defined, seeded per module; no soft delete)
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.Permissions
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    Code                VARCHAR(100)    NOT NULL,
    Name                NVARCHAR(150)   NOT NULL,
    Module              NVARCHAR(50)    NOT NULL,
    Description         NVARCHAR(500)   NULL,
    CreatedBy           BIGINT          NULL,
    CreatedOn           DATETIME2(3)    NOT NULL CONSTRAINT DF_Permissions_CreatedOn DEFAULT (SYSUTCDATETIME()),
    ModifiedBy          BIGINT          NULL,
    ModifiedOn          DATETIME2(3)    NULL,
    IsActive            BIT             NOT NULL CONSTRAINT DF_Permissions_IsActive  DEFAULT (1),

    CONSTRAINT PK_Permissions PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Permissions_Code UNIQUE (Code),
    -- UPPER_SNAKE_CASE only, e.g. USER_VIEW.
    CONSTRAINT CK_Permissions_Code CHECK (Code NOT LIKE '%[^A-Z0-9_]%' COLLATE Latin1_General_BIN AND LEN(Code) >= 3)
);
GO

/* ----------------------------------------------------------------------------
   UserRoles / RolePermissions (link tables; revoke = delete, history in AuditLog)
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.UserRoles
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    UserId              BIGINT          NOT NULL,
    RoleId              BIGINT          NOT NULL,
    CreatedBy           BIGINT          NULL,
    CreatedOn           DATETIME2(3)    NOT NULL CONSTRAINT DF_UserRoles_CreatedOn DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_UserRoles PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_UserRoles_UserId_RoleId UNIQUE (UserId, RoleId),
    CONSTRAINT FK_UserRoles_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id),
    CONSTRAINT FK_UserRoles_Roles FOREIGN KEY (RoleId) REFERENCES dbo.Roles (Id)
);
GO

CREATE TABLE dbo.RolePermissions
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    RoleId              BIGINT          NOT NULL,
    PermissionId        BIGINT          NOT NULL,
    CreatedBy           BIGINT          NULL,
    CreatedOn           DATETIME2(3)    NOT NULL CONSTRAINT DF_RolePermissions_CreatedOn DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_RolePermissions PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_RolePermissions_RoleId_PermissionId UNIQUE (RoleId, PermissionId),
    CONSTRAINT FK_RolePermissions_Roles       FOREIGN KEY (RoleId)       REFERENCES dbo.Roles (Id),
    CONSTRAINT FK_RolePermissions_Permissions FOREIGN KEY (PermissionId) REFERENCES dbo.Permissions (Id)
);
GO

/* ----------------------------------------------------------------------------
   UserSession (server-side session: revocation, logout-all, idle timeout)
   Id is a random GUID (goes into the JWT "sid" claim), so the PK is
   nonclustered and the table is clustered on CreatedOn to avoid page splits.
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.UserSession
(
    Id                  UNIQUEIDENTIFIER NOT NULL,
    UserId              BIGINT          NOT NULL,
    CreatedOn           DATETIME2(3)    NOT NULL CONSTRAINT DF_UserSession_CreatedOn DEFAULT (SYSUTCDATETIME()),
    LastActivityOn      DATETIME2(3)    NOT NULL CONSTRAINT DF_UserSession_LastActivityOn DEFAULT (SYSUTCDATETIME()),
    ExpiresOn           DATETIME2(3)    NOT NULL,
    IpAddress           NVARCHAR(45)    NULL,
    UserAgent           NVARCHAR(512)   NULL,
    IsRevoked           BIT             NOT NULL CONSTRAINT DF_UserSession_IsRevoked DEFAULT (0),
    RevokedOn           DATETIME2(3)    NULL,
    RevokedReason       NVARCHAR(100)   NULL,

    CONSTRAINT PK_UserSession PRIMARY KEY NONCLUSTERED (Id),
    CONSTRAINT FK_UserSession_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id),
    CONSTRAINT CK_UserSession_ExpiresOn CHECK (ExpiresOn > CreatedOn),
    CONSTRAINT CK_UserSession_Revoked   CHECK ((IsRevoked = 0 AND RevokedOn IS NULL) OR (IsRevoked = 1 AND RevokedOn IS NOT NULL))
);
GO

CREATE CLUSTERED INDEX CX_UserSession_CreatedOn ON dbo.UserSession (CreatedOn);
GO

/* ----------------------------------------------------------------------------
   RefreshToken (only the SHA-256 hex hash of the token is stored)
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.RefreshToken
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    SessionId           UNIQUEIDENTIFIER NOT NULL,
    UserId              BIGINT          NOT NULL,
    TokenHash           CHAR(64)        NOT NULL,
    ExpiresOn           DATETIME2(3)    NOT NULL,
    CreatedOn           DATETIME2(3)    NOT NULL CONSTRAINT DF_RefreshToken_CreatedOn DEFAULT (SYSUTCDATETIME()),
    CreatedByIp         NVARCHAR(45)    NULL,
    RevokedOn           DATETIME2(3)    NULL,
    RevokedReason       NVARCHAR(100)   NULL,
    ReplacedByTokenHash CHAR(64)        NULL,

    CONSTRAINT PK_RefreshToken PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_RefreshToken_TokenHash UNIQUE (TokenHash),
    CONSTRAINT FK_RefreshToken_UserSession FOREIGN KEY (SessionId) REFERENCES dbo.UserSession (Id),
    CONSTRAINT FK_RefreshToken_Users       FOREIGN KEY (UserId)    REFERENCES dbo.Users (Id),
    -- Lowercase hex SHA-256 (64 chars).
    CONSTRAINT CK_RefreshToken_TokenHash CHECK (TokenHash NOT LIKE '%[^0-9a-f]%' COLLATE Latin1_General_BIN),
    CONSTRAINT CK_RefreshToken_ReplacedByTokenHash CHECK (ReplacedByTokenHash IS NULL OR ReplacedByTokenHash NOT LIKE '%[^0-9a-f]%' COLLATE Latin1_General_BIN),
    CONSTRAINT CK_RefreshToken_ExpiresOn CHECK (ExpiresOn > CreatedOn)
);
GO

/* ----------------------------------------------------------------------------
   LoginAudit (every login attempt; UserId NULL when the identifier is unknown)
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.LoginAudit
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    UserId              BIGINT          NULL,
    UsernameAttempted   NVARCHAR(256)   NOT NULL,
    IsSuccess           BIT             NOT NULL,
    FailureReason       NVARCHAR(100)   NULL,
    IpAddress           NVARCHAR(45)    NULL,
    UserAgent           NVARCHAR(512)   NULL,
    OccurredOn          DATETIME2(3)    NOT NULL CONSTRAINT DF_LoginAudit_OccurredOn DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_LoginAudit PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT FK_LoginAudit_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id)
);
GO

/* ----------------------------------------------------------------------------
   AuditLog (data changes; ActorUserId has no FK so history survives anything)
   ---------------------------------------------------------------------------- */
CREATE TABLE dbo.AuditLog
(
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    ActorUserId         BIGINT          NULL,
    Action              NVARCHAR(50)    NOT NULL,
    EntityName          NVARCHAR(100)   NOT NULL,
    EntityId            NVARCHAR(100)   NULL,
    OldValues           NVARCHAR(MAX)   NULL,
    NewValues           NVARCHAR(MAX)   NULL,
    IpAddress           NVARCHAR(45)    NULL,
    CorrelationId       NVARCHAR(100)   NULL,
    OccurredOn          DATETIME2(3)    NOT NULL CONSTRAINT DF_AuditLog_OccurredOn DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_AuditLog PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT CK_AuditLog_OldValues CHECK (OldValues IS NULL OR ISJSON(OldValues) = 1),
    CONSTRAINT CK_AuditLog_NewValues CHECK (NewValues IS NULL OR ISJSON(NewValues) = 1)
);
GO
