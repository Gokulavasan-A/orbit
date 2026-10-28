/* =============================================================================
   verify-app-permissions.sql
   Checks that the API login enterprise_app has exactly the intended access in
   EnterpriseBase: it can call procedures and functions, and it cannot touch
   tables directly.

   Run AS enterprise_app (not as the DB owner), after every new migration and
   after grant-app-permissions.sql. -I is required (QUOTED_IDENTIFIER ON),
   otherwise writes to tables with filtered indexes fail with error 1934 before
   the permission check:

     PowerShell (credentials from the API's env vars, for this process only):
       $env:SQLCMDUSER = $env:DB_USERNAME; $env:SQLCMDPASSWORD = $env:DB_PASSWORD
       sqlcmd -S localhost,1433 -d EnterpriseBase -C -I -b -W -i scripts/verify-app-permissions.sql
       # then restore your own SQLCMDUSER / SQLCMDPASSWORD

   Self-contained and read-only:
   - Table and column names are listed explicitly, because enterprise_app
     cannot see tables in sys.tables or sys.columns.
   - Every write attempt runs in a transaction that is always rolled back, so
     nothing changes even if a permission were wrongly granted.
   - Results are kept in a table variable, which survives those rollbacks.
   Exit code is non-zero (with -b) when any check fails.

   When adding a migration, extend this script:
   - a new table goes into the @Tables list, with an updatable column
   - a new procedure or function the API calls can get an ALLOW check
   ============================================================================= */
SET NOCOUNT ON;
SET XACT_ABORT OFF;   -- keep the batch alive after expected permission errors
GO

IF USER_NAME() = N'dbo' OR IS_MEMBER(N'db_owner') = 1
    THROW 50003, N'Run this script as enterprise_app, not as the database owner (owner would pass every check).', 1;
GO

DECLARE @Results TABLE (Seq INT IDENTITY(1,1), Test NVARCHAR(150), Expected VARCHAR(10), Actual VARCHAR(10), Detail NVARCHAR(300));
DECLARE @Rows INT;

PRINT CONCAT(N'Connected as ', SUSER_SNAME(), N' / database user ', USER_NAME(), N' / ', DB_NAME());

/* ---------------------------------------------------------------------------
   1. Must be ALLOWED: procedures (ownership chaining), paged procedures
      (dynamic SQL, WITH EXECUTE AS OWNER), scalar and table-valued functions.
   --------------------------------------------------------------------------- */
DECLARE @Roles TABLE (Id BIGINT, Name NVARCHAR(100), Description NVARCHAR(500), IsSystem BIT, IsActive BIT, RowVersion BINARY(8));
BEGIN TRY
    INSERT @Roles EXEC dbo.usp_Role_GetAll;
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Role_GetAll', 'ALLOW', 'ALLOW', CONCAT(@@ROWCOUNT, N' rows'));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Role_GetAll', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

DECLARE @UserPage TABLE (Id BIGINT, Username NVARCHAR(100), Email NVARCHAR(256), FirstName NVARCHAR(100), LastName NVARCHAR(100),
                         PhoneNumber NVARCHAR(30), IsActive BIT, IsLockedOut BIT, LastLoginOn DATETIME2(3), CreatedOn DATETIME2(3),
                         ModifiedOn DATETIME2(3), Roles NVARCHAR(MAX), ConcurrencyStamp UNIQUEIDENTIFIER, TotalCount INT);
BEGIN TRY
    INSERT @UserPage EXEC dbo.usp_User_GetPaged @PageNumber = 1, @PageSize = 10, @SortColumn = N'username', @SortDirection = N'ASC';
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_User_GetPaged (sort: username)', 'ALLOW', 'ALLOW', CONCAT(@@ROWCOUNT, N' rows'));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_User_GetPaged (sort: username)', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

DECLARE @RolePage TABLE (Id BIGINT, Name NVARCHAR(100), Description NVARCHAR(500), IsSystem BIT, IsActive BIT, CreatedOn DATETIME2(3),
                         ModifiedOn DATETIME2(3), UserCount INT, PermissionCount INT, Permissions NVARCHAR(MAX), TotalCount INT);
BEGIN TRY
    INSERT @RolePage EXEC dbo.usp_Role_GetPaged @PageNumber = 1, @PageSize = 10, @SortColumn = N'name', @SortDirection = N'ASC';
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Role_GetPaged (sort: name)', 'ALLOW', 'ALLOW', CONCAT(@@ROWCOUNT, N' rows'));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Role_GetPaged (sort: name)', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

DECLARE @Permissions TABLE (Id BIGINT, Code VARCHAR(100), Name NVARCHAR(150), Module NVARCHAR(50), Description NVARCHAR(500));
BEGIN TRY
    INSERT @Permissions EXEC dbo.usp_Permission_GetAll;
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Permission_GetAll', 'ALLOW', 'ALLOW', CONCAT(@@ROWCOUNT, N' rows'));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Permission_GetAll', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

-- usp_Role_GetById returns two differently shaped result sets, which INSERT ... EXEC cannot
-- capture, so its rows go to the output; only the permission check matters here.
DECLARE @RoleId BIGINT = (SELECT MIN(Id) FROM @Roles);
BEGIN TRY
    EXEC dbo.usp_Role_GetById @Id = @RoleId;
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Role_GetById', 'ALLOW', 'ALLOW', CONCAT(N'role ', @RoleId));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'EXEC dbo.usp_Role_GetById', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

BEGIN TRY
    SELECT @Rows = COUNT(*) FROM dbo.fn_GetUserPermissions(1);
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'SELECT dbo.fn_GetUserPermissions(1)', 'ALLOW', 'ALLOW', CONCAT(@Rows, N' rows'));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'SELECT dbo.fn_GetUserPermissions(1)', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

BEGIN TRY
    SELECT @Rows = COUNT(*) FROM dbo.fn_GetUserRoles(1);
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'SELECT dbo.fn_GetUserRoles(1)', 'ALLOW', 'ALLOW', CONCAT(@Rows, N' rows'));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'SELECT dbo.fn_GetUserRoles(1)', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

BEGIN TRY
    DECLARE @Has BIT = dbo.fn_UserHasPermission(1, 'USER_VIEW');
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'dbo.fn_UserHasPermission(1, USER_VIEW)', 'ALLOW', 'ALLOW', CONCAT(N'returned ', @Has));
END TRY
BEGIN CATCH
    INSERT @Results (Test, Expected, Actual, Detail) VALUES (N'dbo.fn_UserHasPermission(1, USER_VIEW)', 'ALLOW', 'DENY', CONCAT(N'Msg ', ERROR_NUMBER(), N': ', ERROR_MESSAGE()));
END CATCH

/* ---------------------------------------------------------------------------
   2. Must be DENIED (Msg 229): SELECT / INSERT / UPDATE / DELETE on every table.
      Any other error counts as a failure, because it means the permission check
      was never reached.
   --------------------------------------------------------------------------- */
DECLARE @Tables TABLE (TableName SYSNAME PRIMARY KEY, UpdatableColumn SYSNAME NOT NULL);
INSERT @Tables (TableName, UpdatableColumn) VALUES
    (N'Users',           N'FirstName'),
    (N'Roles',           N'Name'),
    (N'Permissions',     N'Name'),
    (N'UserRoles',       N'RoleId'),
    (N'RolePermissions', N'RoleId'),
    (N'UserSession',     N'UserAgent'),
    (N'RefreshToken',    N'RevokedReason'),
    (N'LoginAudit',      N'FailureReason'),
    (N'AuditLog',        N'Action');

DECLARE @Table SYSNAME, @Column SYSNAME, @Op VARCHAR(10), @Sql NVARCHAR(MAX), @Test NVARCHAR(150);
DECLARE probe CURSOR LOCAL FAST_FORWARD FOR
    SELECT t.TableName, t.UpdatableColumn, o.Op
    FROM @Tables t
    CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE')) o (Op)
    ORDER BY t.TableName, o.Op;

OPEN probe;
FETCH NEXT FROM probe INTO @Table, @Column, @Op;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @Test = CONCAT(@Op, N' dbo.', @Table);
    SET @Sql = CASE @Op
                   WHEN 'SELECT' THEN N'SELECT TOP (0) 1 AS x FROM dbo.' + QUOTENAME(@Table) + N';'
                   WHEN 'INSERT' THEN N'INSERT INTO dbo.' + QUOTENAME(@Table) + N' DEFAULT VALUES;'
                   WHEN 'UPDATE' THEN N'UPDATE dbo.' + QUOTENAME(@Table) + N' SET ' + QUOTENAME(@Column) + N' = ' + QUOTENAME(@Column) + N' WHERE 1 = 0;'
                   WHEN 'DELETE' THEN N'DELETE FROM dbo.' + QUOTENAME(@Table) + N' WHERE 1 = 0;'
               END;

    BEGIN TRANSACTION;
    BEGIN TRY
        EXEC sys.sp_executesql @Sql;
        INSERT @Results (Test, Expected, Actual, Detail) VALUES (@Test, 'DENY', 'ALLOW', N'statement succeeded (rolled back)');
    END TRY
    BEGIN CATCH
        INSERT @Results (Test, Expected, Actual, Detail)
        VALUES (@Test, 'DENY', CASE WHEN ERROR_NUMBER() = 229 THEN 'DENY' ELSE 'ERROR' END,
                CONCAT(N'Msg ', ERROR_NUMBER(), N': ', LEFT(ERROR_MESSAGE(), 200)));
    END CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;   -- always: nothing may ever be changed

    FETCH NEXT FROM probe INTO @Table, @Column, @Op;
END
CLOSE probe;
DEALLOCATE probe;

/* ---------------------------------------------------------------------------
   3. Report. Throws (non-zero exit with -b) if any check failed.
   --------------------------------------------------------------------------- */
SELECT CASE WHEN Expected = Actual THEN 'OK  ' ELSE 'FAIL' END AS Result, Test, Expected, Actual, Detail
FROM @Results
ORDER BY CASE WHEN Expected = Actual THEN 1 ELSE 0 END, Seq;

DECLARE @Ok INT = (SELECT COUNT(*) FROM @Results WHERE Expected = Actual);
DECLARE @Failed INT = (SELECT COUNT(*) FROM @Results WHERE Expected <> Actual);
DECLARE @Summary NVARCHAR(200) = CONCAT(N'verify-app-permissions: ', @Ok, N' passed, ', @Failed, N' failed, of ', @Ok + @Failed, N' checks.');
PRINT @Summary;

IF @Failed > 0
    THROW 50003, @Summary, 1;
GO
