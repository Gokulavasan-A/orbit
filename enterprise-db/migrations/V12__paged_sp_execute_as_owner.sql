/* =============================================================================
   V12__paged_sp_execute_as_owner.sql
   The paged procedures build their whitelisted ORDER BY with sp_executesql.
   Dynamic SQL breaks ownership chaining, so with least-privilege API access
   (EXECUTE on schema dbo, no table permissions; see
   scripts/grant-app-permissions.sql) the inner SELECT was denied.

   Fix: run both procedures WITH EXECUTE AS OWNER (dbo). The bodies are
   unchanged from V8/V9: the sort column comes from a fixed whitelist and is
   QUOTENAME'd, and every value is passed as a parameter, so impersonating the
   owner does not widen what a caller can do.
   ============================================================================= */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_User_GetPaged
    @PageNumber    INT           = 1,
    @PageSize      INT           = 25,
    @SortColumn    NVARCHAR(50)  = N'createdOn',
    @SortDirection NVARCHAR(4)   = N'DESC',
    @SearchText    NVARCHAR(200) = NULL,
    @IsActive      BIT           = NULL,
    @RoleId        BIGINT        = NULL
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @PageNumber = CASE WHEN ISNULL(@PageNumber, 0) < 1 THEN 1 ELSE @PageNumber END;
    SET @PageSize   = CASE WHEN ISNULL(@PageSize, 0) < 1 THEN 25 WHEN @PageSize > 500 THEN 500 ELSE @PageSize END;

    DECLARE @OrderBy SYSNAME = CASE LOWER(LTRIM(RTRIM(@SortColumn)))
                                   WHEN N'id'          THEN N'Id'
                                   WHEN N'username'    THEN N'Username'
                                   WHEN N'email'       THEN N'Email'
                                   WHEN N'firstname'   THEN N'FirstName'
                                   WHEN N'lastname'    THEN N'LastName'
                                   WHEN N'phonenumber' THEN N'PhoneNumber'
                                   WHEN N'isactive'    THEN N'IsActive'
                                   WHEN N'islockedout' THEN N'IsLockedOut'
                                   WHEN N'lastloginon' THEN N'LastLoginOn'
                                   WHEN N'createdon'   THEN N'CreatedOn'
                                   WHEN N'modifiedon'  THEN N'ModifiedOn'
                                   ELSE N'CreatedOn'
                               END;
    DECLARE @Direction NVARCHAR(4) = CASE WHEN UPPER(LTRIM(RTRIM(@SortDirection))) = N'ASC' THEN N'ASC' ELSE N'DESC' END;

    -- Contains-search with LIKE wildcards escaped ('!' is the escape character).
    DECLARE @Pattern NVARCHAR(410) = NULL;
    IF NULLIF(LTRIM(RTRIM(@SearchText)), N'') IS NOT NULL
        SET @Pattern = N'%' + REPLACE(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(@SearchText)),
                           N'!', N'!!'), N'%', N'!%'), N'_', N'!_'), N'[', N'![') + N'%';

    DECLARE @Sql NVARCHAR(MAX) = N'
SELECT u.Id,
       u.Username,
       u.Email,
       u.FirstName,
       u.LastName,
       u.PhoneNumber,
       u.IsActive,
       CAST(CASE WHEN u.LockoutUntil > SYSUTCDATETIME() THEN 1 ELSE 0 END AS BIT) AS IsLockedOut,
       u.LastLoginOn,
       u.CreatedOn,
       u.ModifiedOn,
       (SELECT STRING_AGG(r.Name, N'', '') WITHIN GROUP (ORDER BY r.Name)
        FROM dbo.UserRoles ur
        INNER JOIN dbo.Roles r ON r.Id = ur.RoleId
        WHERE ur.UserId = u.Id AND r.IsDeleted = 0) AS Roles,
       COUNT(*) OVER () AS TotalCount
FROM dbo.Users u
WHERE u.IsDeleted = 0
  AND (@IsActive IS NULL OR u.IsActive = @IsActive)
  AND (@RoleId IS NULL OR EXISTS (SELECT 1 FROM dbo.UserRoles x WHERE x.UserId = u.Id AND x.RoleId = @RoleId))
  AND (@Pattern IS NULL
       OR u.Username  LIKE @Pattern ESCAPE N''!''
       OR u.Email     LIKE @Pattern ESCAPE N''!''
       OR u.FirstName LIKE @Pattern ESCAPE N''!''
       OR u.LastName  LIKE @Pattern ESCAPE N''!'')
ORDER BY ' + QUOTENAME(@OrderBy) + N' ' + @Direction + N', Id ' + @Direction + N'
OFFSET (@PageNumber - 1) * @PageSize ROWS FETCH NEXT @PageSize ROWS ONLY
OPTION (RECOMPILE);';

    EXEC sys.sp_executesql
         @Sql,
         N'@PageNumber INT, @PageSize INT, @IsActive BIT, @RoleId BIGINT, @Pattern NVARCHAR(410)',
         @PageNumber = @PageNumber, @PageSize = @PageSize, @IsActive = @IsActive, @RoleId = @RoleId, @Pattern = @Pattern;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_Role_GetPaged
    @PageNumber    INT           = 1,
    @PageSize      INT           = 25,
    @SortColumn    NVARCHAR(50)  = N'name',
    @SortDirection NVARCHAR(4)   = N'ASC',
    @SearchText    NVARCHAR(200) = NULL,
    @IsActive      BIT           = NULL
WITH EXECUTE AS OWNER
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
