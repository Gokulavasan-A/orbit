/* =============================================================================
   V9__create_sp_role.sql
   Role CRUD. System roles (IsSystem = 1, e.g. Admin/User) cannot be renamed,
   deactivated or deleted; their description and permissions can change.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* Internal helper: JSON snapshot of a role for AuditLog. */
CREATE OR ALTER FUNCTION dbo.fn_Role_AuditJson (@RoleId BIGINT)
RETURNS NVARCHAR(MAX)
AS
BEGIN
    RETURN
    (
        SELECT r.Id, r.Name, r.Description, r.IsSystem, r.IsActive, r.IsDeleted,
               (SELECT p.Code
                FROM dbo.RolePermissions rp
                INNER JOIN dbo.Permissions p ON p.Id = rp.PermissionId
                WHERE rp.RoleId = r.Id
                ORDER BY p.Code
                FOR JSON PATH) AS Permissions
        FROM dbo.Roles r
        WHERE r.Id = @RoleId
        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES
    );
END;
GO

/* @PermissionIds: optional comma-separated permission ids. Returns the new Id. */
CREATE OR ALTER PROCEDURE dbo.usp_Role_Create
    @Name          NVARCHAR(100),
    @Description   NVARCHAR(500) = NULL,
    @IsActive      BIT           = 1,
    @PermissionIds NVARCHAR(MAX) = NULL,
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @Name        = LTRIM(RTRIM(@Name));
    SET @Description = NULLIF(LTRIM(RTRIM(@Description)), N'');

    IF NULLIF(@Name, N'') IS NULL OR LEN(@Name) < 2
        THROW 50003, N'Role name must be at least 2 characters.', 1;
    IF @ActorUserId IS NULL
        THROW 50003, N'ActorUserId is required.', 1;

    DECLARE @Permissions TABLE (PermissionId BIGINT PRIMARY KEY);
    IF NULLIF(LTRIM(RTRIM(@PermissionIds)), N'') IS NOT NULL
    BEGIN
        IF EXISTS (SELECT 1 FROM STRING_SPLIT(@PermissionIds, N',')
                   WHERE LTRIM(RTRIM(value)) <> N'' AND TRY_CAST(LTRIM(RTRIM(value)) AS BIGINT) IS NULL)
            THROW 50003, N'PermissionIds must be a comma-separated list of numbers.', 1;

        INSERT INTO @Permissions (PermissionId)
        SELECT DISTINCT TRY_CAST(LTRIM(RTRIM(value)) AS BIGINT)
        FROM STRING_SPLIT(@PermissionIds, N',')
        WHERE LTRIM(RTRIM(value)) <> N'';
    END

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @NewId BIGINT, @EntityId NVARCHAR(100), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        IF EXISTS (SELECT 1 FROM dbo.Roles WITH (UPDLOCK, HOLDLOCK) WHERE Name = @Name AND IsDeleted = 0)
            THROW 50402, N'Role name already exists.', 1;
        IF EXISTS (SELECT 1 FROM @Permissions x
                   WHERE NOT EXISTS (SELECT 1 FROM dbo.Permissions p WHERE p.Id = x.PermissionId))
            THROW 50501, N'One or more permissions were not found.', 1;

        INSERT INTO dbo.Roles (Name, Description, IsSystem, IsActive, CreatedBy, CreatedOn)
        VALUES (@Name, @Description, 0, ISNULL(@IsActive, 1), @ActorUserId, @Now);

        SET @NewId = CAST(SCOPE_IDENTITY() AS BIGINT);

        INSERT INTO dbo.RolePermissions (RoleId, PermissionId, CreatedBy, CreatedOn)
        SELECT @NewId, PermissionId, @ActorUserId, @Now FROM @Permissions;

        SET @EntityId  = CAST(@NewId AS NVARCHAR(100));
        SET @NewValues = dbo.fn_Role_AuditJson(@NewId);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'CREATE', @EntityName = N'Role', @EntityId = @EntityId,
             @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT @NewId AS Id;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        IF ERROR_NUMBER() IN (2601, 2627) AND ERROR_MESSAGE() LIKE N'%UX_Roles_Name%'
            THROW 50402, N'Role name already exists.', 1;
        THROW;
    END CATCH
END;
GO

/* Permissions are changed via usp_RolePermission_Assign/Revoke.
   Returns Id and the new RowVersion. */
CREATE OR ALTER PROCEDURE dbo.usp_Role_Update
    @Id            BIGINT,
    @Name          NVARCHAR(100),
    @Description   NVARCHAR(500) = NULL,
    @IsActive      BIT,
    @RowVersion    BINARY(8),
    @ActorUserId   BIGINT,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @Name        = LTRIM(RTRIM(@Name));
    SET @Description = NULLIF(LTRIM(RTRIM(@Description)), N'');

    IF @Id IS NULL OR @RowVersion IS NULL OR @IsActive IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'Id, RowVersion, IsActive and ActorUserId are required.', 1;
    IF NULLIF(@Name, N'') IS NULL OR LEN(@Name) < 2
        THROW 50003, N'Role name must be at least 2 characters.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @CurrentRowVersion BINARY(8), @CurrentName NVARCHAR(100), @IsSystem BIT,
            @EntityId NVARCHAR(100) = CAST(@Id AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @CurrentRowVersion = RowVersion, @CurrentName = Name, @IsSystem = IsSystem
        FROM dbo.Roles WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @Id AND IsDeleted = 0;

        IF @CurrentRowVersion IS NULL
            THROW 50403, N'Role not found.', 1;
        IF @CurrentRowVersion <> @RowVersion
            THROW 50002, N'The record was modified by someone else. Reload and try again.', 1;
        IF @IsSystem = 1 AND (@Name <> @CurrentName COLLATE Latin1_General_BIN OR @IsActive = 0)
            THROW 50401, N'System roles cannot be renamed, deactivated or deleted.', 1;
        IF EXISTS (SELECT 1 FROM dbo.Roles WITH (UPDLOCK, HOLDLOCK) WHERE Name = @Name AND IsDeleted = 0 AND Id <> @Id)
            THROW 50402, N'Role name already exists.', 1;

        SET @OldValues = dbo.fn_Role_AuditJson(@Id);

        UPDATE dbo.Roles
        SET Name        = @Name,
            Description = @Description,
            IsActive    = @IsActive,
            ModifiedBy  = @ActorUserId,
            ModifiedOn  = @Now
        WHERE Id = @Id;

        SET @NewValues = dbo.fn_Role_AuditJson(@Id);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'UPDATE', @EntityName = N'Role', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;

        SELECT Id, RowVersion FROM dbo.Roles WHERE Id = @Id;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        IF ERROR_NUMBER() IN (2601, 2627) AND ERROR_MESSAGE() LIKE N'%UX_Roles_Name%'
            THROW 50402, N'Role name already exists.', 1;
        THROW;
    END CATCH
END;
GO

/* Lightweight list for dropdowns. */
CREATE OR ALTER PROCEDURE dbo.usp_Role_GetAll
    @IncludeInactive BIT = 0
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SELECT Id, Name, Description, IsSystem, IsActive, RowVersion
    FROM dbo.Roles
    WHERE IsDeleted = 0
      AND (ISNULL(@IncludeInactive, 0) = 1 OR IsActive = 1)
    ORDER BY Name;
END;
GO

/* Server-side paging; same contract as usp_User_GetPaged. */
CREATE OR ALTER PROCEDURE dbo.usp_Role_GetPaged
    @PageNumber    INT           = 1,
    @PageSize      INT           = 25,
    @SortColumn    NVARCHAR(50)  = N'name',
    @SortDirection NVARCHAR(4)   = N'ASC',
    @SearchText    NVARCHAR(200) = NULL,
    @IsActive      BIT           = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @PageNumber = CASE WHEN ISNULL(@PageNumber, 0) < 1 THEN 1 ELSE @PageNumber END;
    SET @PageSize   = CASE WHEN ISNULL(@PageSize, 0) < 1 THEN 25 WHEN @PageSize > 500 THEN 500 ELSE @PageSize END;

    DECLARE @OrderBy SYSNAME = CASE LOWER(LTRIM(RTRIM(@SortColumn)))
                                   WHEN N'id'              THEN N'Id'
                                   WHEN N'name'            THEN N'Name'
                                   WHEN N'description'     THEN N'Description'
                                   WHEN N'issystem'        THEN N'IsSystem'
                                   WHEN N'isactive'        THEN N'IsActive'
                                   WHEN N'usercount'       THEN N'UserCount'
                                   WHEN N'permissioncount' THEN N'PermissionCount'
                                   WHEN N'createdon'       THEN N'CreatedOn'
                                   WHEN N'modifiedon'      THEN N'ModifiedOn'
                                   ELSE N'Name'
                               END;
    DECLARE @Direction NVARCHAR(4) = CASE WHEN UPPER(LTRIM(RTRIM(@SortDirection))) = N'DESC' THEN N'DESC' ELSE N'ASC' END;

    DECLARE @Pattern NVARCHAR(410) = NULL;
    IF NULLIF(LTRIM(RTRIM(@SearchText)), N'') IS NOT NULL
        SET @Pattern = N'%' + REPLACE(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(@SearchText)),
                           N'!', N'!!'), N'%', N'!%'), N'_', N'!_'), N'[', N'![') + N'%';

    DECLARE @Sql NVARCHAR(MAX) = N'
SELECT r.Id,
       r.Name,
       r.Description,
       r.IsSystem,
       r.IsActive,
       r.CreatedOn,
       r.ModifiedOn,
       (SELECT COUNT(*) FROM dbo.UserRoles ur
        INNER JOIN dbo.Users u ON u.Id = ur.UserId
        WHERE ur.RoleId = r.Id AND u.IsDeleted = 0) AS UserCount,
       (SELECT COUNT(*) FROM dbo.RolePermissions rp WHERE rp.RoleId = r.Id) AS PermissionCount,
       (SELECT STRING_AGG(p.Code, '','') WITHIN GROUP (ORDER BY p.Code)
        FROM dbo.RolePermissions rp
        INNER JOIN dbo.Permissions p ON p.Id = rp.PermissionId
        WHERE rp.RoleId = r.Id) AS Permissions,
       COUNT(*) OVER () AS TotalCount
FROM dbo.Roles r
WHERE r.IsDeleted = 0
  AND (@IsActive IS NULL OR r.IsActive = @IsActive)
  AND (@Pattern IS NULL
       OR r.Name        LIKE @Pattern ESCAPE N''!''
       OR r.Description LIKE @Pattern ESCAPE N''!'')
ORDER BY ' + QUOTENAME(@OrderBy) + N' ' + @Direction + N', Id ' + @Direction + N'
OFFSET (@PageNumber - 1) * @PageSize ROWS FETCH NEXT @PageSize ROWS ONLY
OPTION (RECOMPILE);';

    EXEC sys.sp_executesql
         @Sql,
         N'@PageNumber INT, @PageSize INT, @IsActive BIT, @Pattern NVARCHAR(410)',
         @PageNumber = @PageNumber, @PageSize = @PageSize, @IsActive = @IsActive, @Pattern = @Pattern;
END;
GO

/* Sets IsDeleted = 1 / IsActive = 0 and removes the role from all users
   (the removed user ids are recorded in the audit OldValues). */
CREATE OR ALTER PROCEDURE dbo.usp_Role_SoftDelete
    @Id            BIGINT,
    @ActorUserId   BIGINT,
    @RowVersion    BINARY(8)     = NULL,
    @IpAddress     NVARCHAR(45)  = NULL,
    @CorrelationId NVARCHAR(100) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @Id IS NULL OR @ActorUserId IS NULL
        THROW 50003, N'Id and ActorUserId are required.', 1;

    DECLARE @Now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @OwnsTran BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    DECLARE @CurrentRowVersion BINARY(8), @IsSystem BIT,
            @EntityId NVARCHAR(100) = CAST(@Id AS NVARCHAR(100)),
            @OldValues NVARCHAR(MAX), @NewValues NVARCHAR(MAX);

    BEGIN TRY
        IF @OwnsTran = 1 BEGIN TRANSACTION;

        SELECT @CurrentRowVersion = RowVersion, @IsSystem = IsSystem
        FROM dbo.Roles WITH (UPDLOCK, HOLDLOCK)
        WHERE Id = @Id AND IsDeleted = 0;

        IF @CurrentRowVersion IS NULL
            THROW 50403, N'Role not found.', 1;
        IF @RowVersion IS NOT NULL AND @CurrentRowVersion <> @RowVersion
            THROW 50002, N'The record was modified by someone else. Reload and try again.', 1;
        IF @IsSystem = 1
            THROW 50401, N'System roles cannot be renamed, deactivated or deleted.', 1;

        SET @OldValues = JSON_MODIFY(dbo.fn_Role_AuditJson(@Id), N'$.UserIds',
                             JSON_QUERY(ISNULL((SELECT N'[' + STRING_AGG(CAST(UserId AS NVARCHAR(20)), N',') + N']'
                                                FROM dbo.UserRoles WHERE RoleId = @Id), N'[]')));

        DELETE FROM dbo.UserRoles WHERE RoleId = @Id;

        UPDATE dbo.Roles
        SET IsDeleted  = 1,
            IsActive   = 0,
            ModifiedBy = @ActorUserId,
            ModifiedOn = @Now
        WHERE Id = @Id;

        SET @NewValues = dbo.fn_Role_AuditJson(@Id);

        EXEC dbo.usp_AuditLog_Insert
             @ActorUserId = @ActorUserId, @Action = N'DELETE', @EntityName = N'Role', @EntityId = @EntityId,
             @OldValues = @OldValues, @NewValues = @NewValues, @IpAddress = @IpAddress, @CorrelationId = @CorrelationId;

        IF @OwnsTran = 1 COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @OwnsTran = 1 AND @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END;
GO
