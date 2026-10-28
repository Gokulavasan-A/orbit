/* =============================================================================
   create-database.sql
   Run ONCE against the master database before applying migrations.
   Not a Flyway migration (Flyway connects to the target database itself).

   sqlcmd -S <server> -C -I -b -i scripts/create-database.sql
   To use another database name, edit the :setvar line below.
   ============================================================================= */
:setvar DatabaseName "EnterpriseBase"

IF DB_ID(N'$(DatabaseName)') IS NULL
BEGIN
    CREATE DATABASE [$(DatabaseName)];
END
GO

-- Snapshot-based reads so grid queries don't block writers.
ALTER DATABASE [$(DatabaseName)] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO
