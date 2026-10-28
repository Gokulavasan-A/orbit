/* =============================================================================
   grant-app-permissions.sql
   DATABASE level. Run by the database owner in EnterpriseBase, AFTER
   create-app-login.sql and after all migrations have been applied.

     sqlcmd -S localhost,1433 -d EnterpriseBase -C -I -b -i scripts/grant-app-permissions.sql

   The API user gets least privilege:
     - EXECUTE on schema dbo: all stored procedures and scalar functions,
       including ones added by future migrations.
     - SELECT on the two inline table-valued functions only.
   No table access, no db_owner, no db_datareader / db_datawriter.
   Procedures reach the tables through ownership chaining (everything is
   owned by dbo). Safe to re-run.
   ============================================================================= */
:on error exit
SET NOCOUNT ON;
GO

IF SUSER_ID(N'enterprise_app') IS NULL
    THROW 50003, N'Login enterprise_app does not exist. Run create-app-login.sql first (sysadmin).', 1;
GO

IF DATABASE_PRINCIPAL_ID(N'enterprise_app') IS NULL
    CREATE USER [enterprise_app] FOR LOGIN [enterprise_app] WITH DEFAULT_SCHEMA = dbo;
GO

GRANT EXECUTE ON SCHEMA::dbo TO [enterprise_app];
GRANT SELECT ON OBJECT::dbo.fn_GetUserRoles       TO [enterprise_app];
GRANT SELECT ON OBJECT::dbo.fn_GetUserPermissions TO [enterprise_app];
GO

-- Guard: fail if the user has picked up broader rights through role membership.
IF EXISTS (SELECT 1
           FROM sys.database_role_members rm
           WHERE rm.member_principal_id = DATABASE_PRINCIPAL_ID(N'enterprise_app'))
    THROW 50003, N'enterprise_app must not be a member of any database role. Remove the membership and re-run.', 1;
GO

PRINT N'enterprise_app permissions:';
SELECT pe.state_desc, pe.permission_name, pe.class_desc,
       COALESCE(SCHEMA_NAME(CASE WHEN pe.class = 3 THEN pe.major_id END),
                OBJECT_SCHEMA_NAME(pe.major_id) + N'.' + OBJECT_NAME(pe.major_id)) AS securable
FROM sys.database_permissions pe
WHERE pe.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'enterprise_app')
ORDER BY pe.class_desc, securable, pe.permission_name;
GO
