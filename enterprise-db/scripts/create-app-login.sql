/* =============================================================================
   create-app-login.sql
   SERVER level. Run ONCE by a sysadmin (or a securityadmin) against master.
   Creates the SQL login the API connects with. No password is stored here.

   The password comes from the sqlcmd scripting variable AppPassword. sqlcmd
   also resolves scripting variables from environment variables, so set it as
   an env var to keep it off the command line and out of shell history:

     PowerShell (5.1 and 7):
       $s = Read-Host 'enterprise_app password' -AsSecureString
       $env:AppPassword = [Net.NetworkCredential]::new('', $s).Password
       sqlcmd -S localhost,1433 -C -b -i scripts/create-app-login.sql
       Remove-Item Env:AppPassword

   The password must meet the Windows password policy (CHECK_POLICY = ON) and
   must not contain a single quote ('), because sqlcmd substitutes it as text.
   If the login already exists, the script makes no changes. To rotate the password:
     ALTER LOGIN [enterprise_app] WITH PASSWORD = '<new>';
   ============================================================================= */
:on error exit
SET NOCOUNT ON;
GO

DECLARE @Password NVARCHAR(128) = N'$(AppPassword)';

IF LEN(@Password) = 0
    THROW 50003, N'AppPassword is empty. Set the AppPassword environment variable or pass -v AppPassword=...', 1;

IF SUSER_ID(N'enterprise_app') IS NOT NULL
BEGIN
    PRINT N'Login enterprise_app already exists - no changes made.';
END
ELSE
BEGIN
    -- Built with QUOTENAME so the value is always a single, properly quoted literal.
    DECLARE @Sql NVARCHAR(MAX) =
        N'CREATE LOGIN [enterprise_app] WITH PASSWORD = ' + QUOTENAME(@Password, N'''') +
        N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;';
    EXEC sys.sp_executesql @Sql;
    PRINT N'Login enterprise_app created.';
END
GO
