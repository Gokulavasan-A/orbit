# enterprise-db

SQL Server (2019+) schema for the Enterprise Base application. It covers users, roles, permissions, server-side sessions, refresh tokens, login and audit logging, and all the stored procedures and functions the API uses. The API never runs inline SQL.

```
migrations/   Flyway versioned scripts (V1__…, V2__…), applied in order
scripts/      create-database, create-app-login, grant-app-permissions, verify-app-permissions
docs/         er-model.md, stored-procedures.md, error-codes.md
```

| Script | Contents |
|---|---|
| V1 | Tables, PK/FK/UNIQUE/CHECK/DEFAULT constraints, filtered unique indexes |
| V2 | Performance indexes |
| V3 | `fn_GetUserRoles`, `fn_GetUserPermissions`, `fn_UserHasPermission` (+ internal `fn_IsLastActiveAdmin`) |
| V4 | Auth procedures |
| V5 | Session procedures |
| V6 | Refresh token procedures |
| V7 | Audit procedures |
| V8 | User procedures |
| V9 | Role procedures |
| V10 | User-role and role-permission assignment |
| V11 | Seed data (roles, permissions, admin user) |
| V12 | Paged procedures run `WITH EXECUTE AS OWNER` (dynamic ORDER BY works without table permissions) |
| V13 | Password change (keeps the current session) and admin password reset (clears lockout) |
| V14 | User and role management: `Users.ConcurrencyStamp`, user unlock, `usp_Permission_GetAll`, `usp_Role_GetById`, revoke sessions for a role |

## Setup order

| # | Step | Run by | Script |
|---|---|---|---|
| 1 | Create the database | login with `CREATE ANY DATABASE` (e.g. `enterprise_dev`) | `scripts/create-database.sql` |
| 2 | Apply migrations V1…Vn | database owner | `migrations/` (Flyway or sqlcmd, see 2a/2b) |
| 3 | Create the API login `enterprise_app` | **sysadmin** (or securityadmin), once per server | `scripts/create-app-login.sql` |
| 4 | Create the API user and grant permissions | database owner | `scripts/grant-app-permissions.sql` |
| 5 | Verify the API's access | **enterprise_app** | `scripts/verify-app-permissions.sql` |

**After every new migration**, re-run step 2 for the new file, then step 4, then step 5. Step 5 must pass before the migration is committed.

Step 4 needs both the login from step 3 and the migrated database. Re-run step 4 after future migrations; it's idempotent. The schema-wide `EXECUTE` grant already covers new procedures, so a re-run is only strictly needed if a new inline table-valued function is added (it must then be added to the script).

## 1. Create the database

Run this once, connected to `master`:

```bash
sqlcmd -S localhost -C -I -b -i scripts/create-database.sql
```

This creates `EnterpriseBase` and turns on `READ_COMMITTED_SNAPSHOT`. Add `-U <login>`, or set `SQLCMDUSER` and `SQLCMDPASSWORD`, when using SQL authentication. Use `-E` for Windows authentication.

## 2a. Apply migrations with Flyway (recommended)

```bash
cp flyway.conf.example flyway.conf     # flyway.conf is git-ignored
export FLYWAY_USER=<login>
export FLYWAY_PASSWORD=<password>
flyway -configFiles=flyway.conf info
flyway -configFiles=flyway.conf migrate
```

Flyway records applied versions in `flyway_schema_history`.

- Never edit a migration that has already been applied. Add a new `V<n>__description.sql` instead. Procedures use `CREATE OR ALTER`, so a changed procedure is simply re-declared in a new version.
- `placeholderReplacement=false` is required because the seed contains `$` characters (the BCrypt hash).

## 2b. Apply migrations manually with sqlcmd

Scripts must run in **numeric** order: V10 comes after V9, not after V1. Always pass `-I` (QUOTED_IDENTIFIER ON, which filtered indexes require) and `-b` (stop on the first error).

Bash:

```bash
for f in $(ls migrations/V*.sql | sort -V); do
  echo "Applying $f"
  sqlcmd -S localhost -d EnterpriseBase -C -I -b -i "$f" || break
done
```

PowerShell:

```powershell
Get-ChildItem migrations\V*.sql |
  Sort-Object { [int]($_.Name -replace '^V(\d+)__.*', '$1') } |
  ForEach-Object {
    Write-Host "Applying $($_.Name)"
    sqlcmd -S localhost -d EnterpriseBase -C -I -b -i $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "Failed on $($_.Name)" }
  }
```

Manual runs are not tracked. Don't mix manual runs and Flyway on the same database. To move a manually built database onto Flyway, use `flyway baseline -baselineVersion=<last applied V>`.

## 3. Create the API login (sysadmin, once per server)

The password is read from the sqlcmd scripting variable `AppPassword`. sqlcmd also reads scripting variables from environment variables, so passing it as an env var keeps it out of the command line and shell history:

```powershell
$s = Read-Host 'enterprise_app password' -AsSecureString
$env:AppPassword = [Net.NetworkCredential]::new('', $s).Password
sqlcmd -S localhost,1433 -C -b -i scripts/create-app-login.sql
Remove-Item Env:AppPassword
```

- The login is created with `CHECK_POLICY = ON`, so the password must meet the Windows password policy. It must not contain a single quote.
- If the login already exists, the script makes no changes. To rotate the password, run `ALTER LOGIN [enterprise_app] WITH PASSWORD = '<new>';`.
- The API reads the same password from its own environment variable. It is never stored in this repo.

## 4. Grant the API's database permissions (database owner)

```bash
sqlcmd -S localhost,1433 -d EnterpriseBase -C -I -b -i scripts/grant-app-permissions.sql
```

This creates the user `enterprise_app` in `EnterpriseBase` with **least privilege**:

| Granted | Why |
|---|---|
| `EXECUTE` on schema `dbo` | All stored procedures and scalar functions, including future ones |
| `SELECT` on `fn_GetUserRoles`, `fn_GetUserPermissions` | Inline table-valued functions need SELECT, not EXECUTE |

The user gets no table permissions and no role membership (`db_owner`, `db_datareader`, `db_datawriter`). The script fails if any role membership is found. Procedures reach the tables through ownership chaining. Procedures that use dynamic SQL must be declared `WITH EXECUTE AS OWNER`, as the paged procedures are since V12; otherwise the dynamic statement runs with the caller's own permissions and is denied.

## 5. Verify the API's access (after every migration)

Run this **as `enterprise_app`**. It refuses to run as the database owner, because the owner would pass every check. The credentials come from the API's env vars for this one process:

```powershell
$env:SQLCMDUSER = $env:DB_USERNAME; $env:SQLCMDPASSWORD = $env:DB_PASSWORD
sqlcmd -S localhost,1433 -d EnterpriseBase -C -I -b -W -i scripts/verify-app-permissions.sql
$env:SQLCMDUSER = [Environment]::GetEnvironmentVariable('SQLCMDUSER','User')          # restore your own login
$env:SQLCMDPASSWORD = [Environment]::GetEnvironmentVariable('SQLCMDPASSWORD','User')
```

It checks two things:
- **Allowed:** the procedure calls (including the paged ones, which use dynamic SQL) and all three functions.
- **Denied with Msg 229:** `SELECT`, `INSERT`, `UPDATE` and `DELETE` on every table. Any other error counts as a failure.

It ends with `verify-app-permissions: N passed, 0 failed`, and with `-b` the exit code is non-zero if any check fails. The script never changes data: every write attempt is rolled back.

`-I` is required. Without it, writes to `Users` and `Roles` (filtered indexes) fail with error 1934 before the permission check runs.

When a migration adds a table, add it to the `@Tables` list in the script with an updatable column. `enterprise_app` can't see `sys.tables`, so the list has to be explicit.

## Seeded data

| What | Value |
|---|---|
| Roles | `Admin` (all permissions), `User` (`USER_VIEW`). Both are system roles. |
| Permissions | `USER_VIEW`, `USER_CREATE`, `USER_EDIT`, `USER_DELETE`, `ROLE_VIEW`, `ROLE_MANAGE` |
| Admin user | username `admin`, email `admin@enterprise.local`, **dev password `ChangeMe@123!`**, `MustChangePassword = 1` |

### Replacing the admin password

The seeded hash is a real BCrypt (cost 12) hash of a **publicly documented** dev password. Replace it on any shared or production database.

1. Generate a BCrypt hash (`$2a$` / `$2b$`, cost 12) using any of these:
   - Spring (same encoder the API uses): `new BCryptPasswordEncoder(12).encode("<new password>")`
   - Apache htpasswd: `htpasswd -bnBC 12 "" '<new password>' | tr -d ':\n'`
   - Node (after `npm i bcryptjs`): `node -e "console.log(require('bcryptjs').hashSync(process.argv[1], 12))" '<new password>'`
2. Update the row:

   ```sql
   UPDATE dbo.Users
   SET PasswordHash = N'<hash>', MustChangePassword = 1, PasswordChangedOn = SYSUTCDATETIME()
   WHERE Username = N'admin';
   ```

Never change `V11__seed_data.sql` after it has been applied, because Flyway checksums would fail. Do the update above, or ship it as a new migration that reads the hash from a deployment variable.

## Conventions

- Times are UTC.
- Business errors are raised as `THROW 5xxxx`; see [docs/error-codes.md](docs/error-codes.md).
- Write procedures take `@ActorUserId` and audit themselves in the same transaction.
- Paged procedures implement AG Grid server-side paging, sorting and filtering, with the `ORDER BY` built from a whitelist.

See [docs/stored-procedures.md](docs/stored-procedures.md) for every parameter and result set, and [docs/er-model.md](docs/er-model.md) for the data model.
