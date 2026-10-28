# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workspace layout

`enterprise-base` is a **single git repository** (`https://github.com/Gokulavasan-A/enterprise-base`, branch `master`). It contains the three project folders plus the workspace files (this CLAUDE.md, `README.md`, the shared `.claude/` config). The projects stay **independent** even though they share a repo:
- Each has its own build: Maven in `enterprise-api`, npm/Vite in `enterprise-ui`, and SQL scripts in `enterprise-db`.
- No project imports files from another, and there is nothing shared at build time.
- They communicate only via REST/JSON over HTTPS.

| Project | Stack |
|---|---|
| `enterprise-ui/` | React (latest stable), Vite, TypeScript, MUI, Redux Toolkit, Formik + Yup, AG Grid, Axios |
| `enterprise-api/` | Java (LTS), Spring Boot (latest stable), Maven, Spring Security, SQL Server |
| `enterprise-db/` | T-SQL scripts: tables, stored procedures (SP), functions (FN), seed data, versioned migrations |

Run git commands from the repository root (`enterprise-base`). There are no nested repos or submodules; never run `git init` inside a project folder. Build and run commands are run from inside the relevant project folder. Prefer one commit per project per change, so each tier's history stays readable.

**Dependencies:** always verify the latest stable version before adding any dependency. Never use beta or RC versions.

> Status (2026-10-27): `enterprise-db` is migrated through V14. `enterprise-api` Phase 3 (authentication and security) is done:
> - JWT access tokens and rotating refresh cookies, server-side session checks, lockout and login audit
> - CSRF, CORS, security headers and Bucket4j rate limiting
> - `@PreAuthorize` permissions (Roles needs `PERM_ROLE_VIEW`)
>
> The frontend auth contract is in `enterprise-api/docs/security.md`.
>
> Phase 5a (user and role management, DB + API) is done:
> - V14: `Users.ConcurrencyStamp` (user edits no longer conflict with logins), user unlock, permission list, role detail, revoke sessions for a role
> - `/api/v1/users` (paged, detail, create with a one-time temporary password, edit, set roles, reset password, unlock, delete), role detail/create/edit/permissions/delete, `/api/v1/permissions`
> - removing roles or permissions ends the affected sessions; assignment lists are last-write-wins
>
> The UI contract is in `enterprise-api/docs/users-and-roles.md`.
>
> `enterprise-ui` Phase 4 (frontend foundation) is done:
> - theme and typography; an axios client that implements the auth contract (single-flight cross-tab refresh, CSRF, error mapping)
> - session bootstrap, idle timeout and multi-tab logout
> - common components (AppForm, AppGrid, …), layouts and lazy routes
> - Login, Change password, Home and a read-only Roles grid
>
> Not built yet: optional AES-GCM payload encryption (empty slot in `enterprise-ui/src/api/encryption.ts`), and the Users and role-editing screens (the API for them is ready). Full UI rules are in `enterprise-ui/CLAUDE.md`.

## API commands (`enterprise-api`)

Java 25, Spring Boot 4.1.2, Maven wrapper. Full rules are in `enterprise-api/CLAUDE.md`.
- Build + tests: `./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`). The DB integration tests run only when `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` are set. `AuthFlowIntegrationTest` resets the dev admin password to the seed value `ChangeMe@123!` and leaves `LoginAudit` rows.
- Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. Needs `JWT_SIGNING_KEY` (Base64, at least 256-bit, User env var) as well as the `DB_*` vars; outside `dev` also `APP_CORS_ORIGINS`.
- Single test: `./mvnw test -Dtest=RoleServiceImplTest#getAllMapsEntitiesToResponses`

## UI commands (`enterprise-ui`)

Node 22.12+ / npm, React 19 + Vite 8 + TypeScript 6. TypeScript is pinned to 6.0.x because `typescript-eslint` doesn't support TS 7 yet. Full rules are in `enterprise-ui/CLAUDE.md`.
- Install: `npm ci`. Dev server: `npm run dev` (http://localhost:5173). It calls the API on `VITE_API_BASE_URL` (`.env.development`: http://localhost:8080), so run the API with the `dev` profile.
- Checks: `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`.
- Single test: `npx vitest run src/api/axiosClient.test.ts -t "single"`

## Database commands (`enterprise-db`)

- Migrations live in `migrations/V<n>__*.sql` and are applied in **numeric** order (V10 after V9). Never edit an applied migration; add a new V-file. Procedures use `CREATE OR ALTER`.
- Manual apply: `sqlcmd -S <server> -d <db> -C -I -b -i <file>`. `-I` is required because filtered indexes need QUOTED_IDENTIFIER ON. Full loops are in `enterprise-db/README.md`.
- Local dev server: `localhost,1433` (SQL Server 2025 Developer, mixed-mode auth) with the SQL login `enterprise_dev`. sqlcmd picks up the credentials from the `SQLCMDUSER` and `SQLCMDPASSWORD` env vars, so don't pass `-U`/`-P`. `-C` is required because the server certificate isn't trusted. Use a scratch database (e.g. `EnterpriseBase_Verify`) for tests and drop it afterwards.
- Databases on localhost: `EnterpriseBase` (dev, migrated through V14). The API connects as the SQL login `enterprise_app` (credentials in the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` user env vars). That login only has `EXECUTE` on schema `dbo` plus `SELECT` on the inline TVFs, with no table access (see `enterprise-db/scripts/grant-app-permissions.sql`), so every procedure reaches tables through dbo ownership chaining.
- Rules for every new migration:
  - Any new stored procedure that uses dynamic SQL (`sp_executesql`) **must** be declared `WITH EXECUTE AS OWNER`.
  - Any new inline table-valued function **must** get a `SELECT` grant in `enterprise-db/scripts/grant-app-permissions.sql`.
  - Any new table must be added to the `@Tables` list in `enterprise-db/scripts/verify-app-permissions.sql`.
  - After applying it, re-run `grant-app-permissions.sql` (as `enterprise_dev`), then run `verify-app-permissions.sql` **as `enterprise_app`** with `-I -b`. It must report 0 failed. Set `SQLCMDUSER`/`SQLCMDPASSWORD` from `DB_USERNAME`/`DB_PASSWORD` for that one process, then restore them from the User-scope values. Don't just delete them.
- SP contracts (parameters, result sets, error codes) are in `enterprise-db/docs/stored-procedures.md` and `enterprise-db/docs/error-codes.md`. The API maps `THROW 5xxxx` codes to HTTP statuses.

## Backend architecture (`enterprise-api`, strict)

Layering: `Controller -> Service interface -> Service impl -> Repository interface -> Repository impl (SP/FN calls)`

- Packages: `config`, `controller`, `service`, `service/impl`, `repository`, `repository/impl`, `entity`, `model/request`, `model/response`, `mapper`, `security`, `exception`, `util`, `async`.
- Entities are **never** exposed from controllers. Use request/response models with MapStruct mappers.
- DB access goes mostly through stored procedures and functions (`SimpleJdbcCall` / `NamedParameterJdbcTemplate`). No inline SQL in services. A new SP/FN belongs in `enterprise-db` as a versioned migration.
- Bean Validation (`@Valid`, `jakarta.validation`) on every request model. A global exception handler returns the standard `ApiResponse<T>` envelope.
- Security:
  - JWT access token: short-lived, held in memory on the client.
  - Refresh token: in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie.
  - CSRF: `CookieCsrfTokenRepository`.
  - Passwords: BCrypt or Argon2.
  - Access control: role + permission based, with `@PreAuthorize`.
  - Rate limiting: Bucket4j, per IP and per user, with a strict limit on `/auth/login`.
  - CORS: whitelist from config, never `*`.
  - Optional AES-GCM payload encryption for sensitive endpoints (key exchanged per session), on top of mandatory TLS.
  - Security headers: CSP, HSTS, X-Frame-Options.
  - Account lockout after failed logins, plus an audit log table.
  - Server-side `UserSession` table for token revocation, logout-all and idle timeout.
- Response compression enabled. `@Async` with a configured `ThreadPoolTaskExecutor` (or virtual threads), `CompletableFuture` for parallel work, `@Scheduled` for background jobs.
- Secrets come only from environment variables and profiles (`application-dev.yml`, `application-prod.yml`). Never hardcode them.

## Frontend architecture (`enterprise-ui`, strict)

```
src/
  api/               endpoints.ts (all URLs), axiosClient.ts (interceptors: token, CSRF, refresh, encryption, errors), one service per module
  app/               store.ts, hooks.ts
  features/          one folder per module (auth, home, users, roles), each with slice, pages, components
  components/common/ AppButton, AppGrid (AG Grid wrapper), AppForm, AppTextField, AppSelect, AppDialog, AppLoader, AppToast, PageHeader
  layouts/           MainLayout (header, sidebar, content), AuthLayout
  routes/            AppRoutes, ProtectedRoute, RoleGuard
  theme/             theme.ts, typography.ts, palette.ts
  utils/ constants/ types/ hooks/
```

- All URLs live in `api/endpoints.ts`. All cross-cutting HTTP concerns (auth header, CSRF, refresh-on-401, encryption, error mapping) live in `axiosClient.ts` interceptors, not in components or services.
- Typography (h1..h6, subtitle, label, body sizes) is defined **once** in `theme/typography.ts`. Screens may override locally only via `sx` or a local styled component.
- UI style: white background, light neutral palette with soft blue/grey accents, no neon colors, and icons only where they help navigation.
- Never store tokens in `localStorage`. Lazy-load routes. Debounce search inputs.
- Build screens from `components/common` wrappers rather than raw MUI or AG Grid.

## Working rules

- Plan first and show the file list, then implement. Keep changes scoped to the current phase.
- Line endings are pinned with `.gitattributes`, regardless of each developer's `core.autocrlf`. The root file sets `* text=auto eol=lf`. When scaffolding a project, add its own `.gitattributes` as the **first** file in that folder:
  - `enterprise-db/.gitattributes` (done): `* text=auto eol=lf`, `*.sql text eol=lf`
  - `enterprise-api/.gitattributes` (done): `* text=auto eol=lf`, `mvnw text eol=lf`, `*.cmd text eol=crlf`, `*.java text eol=lf`, `*.yml text eol=lf`, `*.xml text eol=lf`
  - `enterprise-ui/.gitattributes` (done): `* text=auto eol=lf`
- Write unit tests for services and common components.
- After each phase, summarize what was built and what is pending.
