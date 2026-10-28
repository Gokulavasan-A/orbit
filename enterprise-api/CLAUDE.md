# CLAUDE.md (enterprise-api)

Guidance for working in `enterprise-api`. The root `CLAUDE.md` still applies (workspace rules, backend architecture, security targets).

## Stack

- Java 25 (LTS), Spring Boot 4.1.2 (Spring Framework 7, Spring Security 7, Jackson 3 `tools.jackson.*`)
- Maven via the wrapper (Maven 3.10.0)
- `spring-boot-starter-jdbc` + `mssql-jdbc` (overridden to the latest stable), **no JPA/Hibernate, no Lombok**
- MapStruct, springdoc-openapi, actuator
- Security: Spring Security OAuth2 resource server (Nimbus JWT, HS256), BCrypt, Bucket4j (`bucket4j_jdk17-core` + `-caffeine`), Caffeine (overridden to the latest stable)
- Tests: JUnit 6 (Jupiter API), Mockito, AssertJ

## Commands

Run from `enterprise-api/`. On Windows use `.\mvnw.cmd` instead of `./mvnw`.

| What | Command |
|---|---|
| Build + all tests | `./mvnw clean verify` |
| Run (dev profile) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| One test class | `./mvnw test -Dtest=RoleServiceImplTest` |
| One test method | `./mvnw test -Dtest=RoleServiceImplTest#getAllMapsEntitiesToResponses` |
| Skip the DB integration tests | unset `DB_URL` for that shell (they are skipped when any `DB_*` var is missing) |

Always run with an explicit profile: `dev` (Swagger UI + API docs open, CORS defaults to `http://localhost:5173`) or `prod` (HSTS on, `APP_CORS_ORIGINS` required). Every profile uses the same JWT security chain; nothing under `/api/**` is anonymous except `GET /api/v1/auth/csrf`, `POST /login` and `POST /refresh`.

Env vars set at User scope are not visible to an already-running shell. Load them into the process first (PowerShell, values never printed): `foreach ($n in 'DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SIGNING_KEY') { Set-Item "env:$n" ([Environment]::GetEnvironmentVariable($n,'User')) }`.

## Configuration

- DB settings come only from env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. The API connects as `enterprise_app` (EXECUTE on `dbo` + SELECT on the inline TVFs, **no table access**).
- `DB_URL` format: `jdbc:sqlserver://localhost:1433;databaseName=EnterpriseBase;encrypt=true;trustServerCertificate=true;sendTimeAsDatetime=false`. Use `trustServerCertificate=true` only for local dev.
- `application.yml` holds the shared settings; `application-dev.yml` / `application-prod.yml` override them; `src/test/resources/application-test.yml` is for tests.
- `JWT_SIGNING_KEY`: Base64 HMAC key, at least 256 bits (64 random bytes on dev machines). Startup fails when it is missing, not Base64 or too short; the value is never put in a message. Generate it without printing it (Windows PowerShell 5.1 has no `RandomNumberGenerator.Fill`):
  `$r=[Security.Cryptography.RandomNumberGenerator]::Create(); $b=[byte[]]::new(64); $r.GetBytes($b); [Environment]::SetEnvironmentVariable('JWT_SIGNING_KEY',[Convert]::ToBase64String($b),'User')`
- `APP_CORS_ORIGINS`: comma-separated exact origins (no `*`). Required outside `dev`.
- App settings live under `app.*` and bind to validated records in `config/properties`: `DbProperties`, `SessionCleanupProperties`, `JwtProperties` (`app.security.jwt`), `AuthProperties` (`app.security.auth`: refresh cookie, session timeouts and cache, lockout, CSRF cookie), `PasswordPolicyProperties`, `RateLimitProperties`, `CorsProperties`, `ClientIpProperties` (trusted proxies), `SecurityHeadersProperties`.
- Never hardcode secrets, and never give `${DB_*}` or `${JWT_SIGNING_KEY}` placeholders a default value. `application-test.yml` carries a public, test-only signing key.

## Package rules (`com.enterprise.api`)

`controller` → `service` (interface `IXxxService`) → `service/impl` → `repository` (interface `IXxxRepository`) → `repository/impl` → `repository/support/StoredProcedureExecutor`.

| Package | Contains |
|---|---|
| `controller` | REST endpoints. Returns `ApiResponse<T>`. Never returns entities. `@Valid` on every request model. |
| `service`, `service/impl` | Business logic. Works with entities and request/response models, maps them with MapStruct. No SQL. |
| `repository`, `repository/impl` | One SP/FN call per method via `StoredProcedureExecutor`. Static `RowMapper`s. Returns entities. |
| `repository/support` | `StoredProcedureExecutor`, `StoredProcedureCall`, `StoredProcedureResult`, `PagedResult`, `PageQuery` |
| `entity` | Plain classes (getters/setters) that map SP result rows |
| `model/request`, `model/response` | Java records. Bean Validation on request records. |
| `mapper` | MapStruct interfaces (Spring component model; unmapped target properties fail the build) |
| `exception` | `ErrorCode`, `SqlErrorTranslator`, `BusinessException`, `TechnicalException`, `FieldValidationException`, `RateLimitExceededException`, `GlobalExceptionHandler` |
| `security` | `SecurityConfig` (chains, CORS, CSRF, headers), `JwtConfig` (encoder/decoder/authorities), the three filters, JSON 401/403 handlers, `ClientIpResolver`, `RefreshTokenCookies`, `RefreshTokenCodec`, `AuthPaths`, token value records (`TokenClaims`, `IssuedToken`, `CurrentUser`, ...) |
| `config` | Spring configuration and `config/properties` records |
| `async` | `@Scheduled` jobs, `MdcTaskDecorator` |
| `util` | Correlation id filter/helper, `SqlRowUtils`, `TransactionHooks` (run work after commit) |

## Calling stored procedures

```java
executor.call("usp_Role_GetPaged")                 // name only; "dbo." is added
        .in("PageNumber", Types.INTEGER, page)     // declare IN/OUT params in the SP's exact order
        .in("PageSize", Types.INTEGER, size)       // trailing params left out use the SP defaults
        .pagedResultSet("rows", ROW_MAPPER)        // result sets in the order the SP returns them
        .execute()
        .paged("rows");
```

- **Parameters are positional.** Declare them in signature order. Skipping a middle parameter shifts all the following ones.
- No metadata lookup (`enterprise_app` can't read it). Undeclared result sets are skipped.
- Write procedures: add `.withCorrelationId()` at the `@CorrelationId` position. It is read from the MDC.
- Timeouts: `app.db.default-query-timeout-seconds`, or per call with `.timeout(Duration)`.
- Calls join the current `@Transactional` transaction. The procedures only open their own transaction when none is open.
- Failures become `BusinessException` (documented `THROW 5xxxx`, with `ErrorCode` and HTTP status) or `TechnicalException` (→ 500). Catch `BusinessException` and check `getErrorCode()` when a service must react to a specific code (e.g. `REFRESH_TOKEN_REUSED`).
- When `enterprise-db/docs/error-codes.md` gets a new code, add it to `ErrorCode` and `SqlErrorTranslatorTest`.
- A new SP/FN belongs in `enterprise-db` as a migration first. No inline SQL anywhere in the API.

## Responses

- Success: `ApiResponse.ok(data)` → `{success: true, data, error: null, correlationId, timestamp}`.
- Failure: produced only by `GlobalExceptionHandler` and the security handlers → `{success: false, data: null, error: {code, message, fieldErrors}, ...}`.
- `error.code` is an `ErrorCode` name, and `error.message` is always a safe, fixed text. The one exception is 50003, which carries the procedure's authored validation message.
- Paged endpoints return `PagedResponse<T>` `{rows, totalCount, page, pageSize}`. Response field names match the SP's grid sort column ids (e.g. `isActive`, `userCount`).
- Times are `Instant`, serialized as ISO-8601 UTC. DATETIME2 columns are read with `SqlRowUtils.getInstant`.

## Security (Phase 3)

The frontend contract (call order, cookie and header names, error codes, deployment rules) is in [docs/security.md](docs/security.md). Server-side rules:

- **Filter order:**
  1. `CorrelationIdFilter` → CORS → CSRF.
  2. `BearerTokenAuthenticationFilter`, which validates the JWT: HS256 signature, `exp` (30 s skew), `iss`, `aud`, `iat`, `jti`, numeric `sub`, UUID `sid`.
  3. `SessionValidationFilter`: `usp_Session_Validate`, with VALID results cached by Caffeine for 15 s.
  4. `PasswordChangeRequiredFilter`, then `RateLimitFilter`.
  5. Authorization rules, then `@PreAuthorize`.
  - The three custom filters are created in `SecurityConfig` with `new`, not as beans, so Boot does not register them a second time as servlet filters.
- **Authorities:** `ROLE_<name>` and `PERM_<code>` come from the token claims (Spring Security 7 also adds `FACTOR_BEARER`). Protect endpoints with `@PreAuthorize("hasAuthority('PERM_<CODE>')")`, e.g. `RoleController` needs `PERM_ROLE_VIEW`.
- **CSRF:**
  - Uses `csrf.spa()` with a `CookieCsrfTokenRepository` (`XSRF-TOKEN` / `X-XSRF-TOKEN`, `SameSite=Strict`, `Path=/`).
  - The resource-server configurer exempts bearer requests from CSRF. `SecurityConfig` resets the `CsrfFilter` matcher so **every** state-changing request needs the header.
  - `GET /csrf` returns the **raw** token from the `DeferredCsrfToken`, not the masked `_csrf` attribute (with `spa()` a header value is compared raw).
- **Cache eviction:** any code that revokes sessions must also evict them from `ISessionValidationService`:
  - one session: `evictSession`
  - all of a user's sessions (logout-all, password change, token reuse): `evictUser`
  - inside a `@Transactional` write, evict immediately **and** again through `TransactionHooks.afterCommit`. A concurrent request can still read the pre-commit state (READ_COMMITTED_SNAPSHOT) and re-cache the session as VALID.
- **Refresh:**
  - Follows the flow in `enterprise-db/docs/stored-procedures.md`.
  - Any non-ACTIVE status, or a deleted, inactive or locked user, gives 401 `SESSION_INVALID` (and revokes the session).
  - Reuse (`REUSED` / 50204) revokes all sessions of the user and gives 401 `REFRESH_TOKEN_REUSED`.
  - The controller clears the refresh cookie on every failed refresh.
- **Login:**
  - Identical `INVALID_CREDENTIALS` for unknown, wrong-password and inactive users, with a dummy BCrypt compare for unknown users.
  - Locked users are rejected before the password check.
  - The login audit is written with `@Async` and never fails the login.
- **Single instance:** rate-limit buckets and the session cache are in memory, per instance. See "Running several API instances" in `docs/security.md`.

## User and role management (Phase 5a)

The UI contract (endpoints, bodies, error codes, the temporary-password flow) is in [docs/users-and-roles.md](docs/users-and-roles.md). Server-side rules:

- **Controllers:** `UserController` (`USER_VIEW` / `USER_CREATE` / `USER_EDIT` / `USER_DELETE`), `RoleController` (class-level `ROLE_VIEW`; writes have a method-level `ROLE_MANAGE`, which replaces the class rule), `PermissionController` (`ROLE_VIEW`). Write methods pass `CurrentUser.from(jwt)` (actor) and a `ClientInfo` built with `ClientIpResolver`; the repositories add `.withCorrelationId()`. Creates return 201 with a `Location` header.
- **Transactions:** every write method in `UserServiceImpl` / `RoleServiceImpl` is `@Transactional`. The stored procedures join it, so a multi-call change (role diff + session revocation) commits or rolls back as a whole.
- **Full-list updates** (`PUT /users/{id}/roles`, `PUT /roles/{id}/permissions`): read the current ids with GetById, then Assign/Revoke only the difference. No version check (last-write-wins, documented).
- **Session revocation** (always followed by eviction, see Security above):
  - user deactivated, deleted or password reset: the procedure revokes; the service evicts the user
  - a role removed from a user: `revokeAllForUser(id, "ROLES_CHANGED", null)`
  - permissions removed from a role, role deactivated (was active, now not) or deleted: `revokeAllForRole`, which returns the user ids to evict. For a delete it runs **before** `usp_Role_SoftDelete` (which removes the user links); a refusal rolls it back.
  - adding roles or permissions revokes nothing.
- **Concurrency:** users use `concurrencyStamp` (UUID, `Users.ConcurrencyStamp`, V14), not RowVersion, because every login changes RowVersion. Roles keep Base64 `rowVersion` (request pattern `UpdateRoleRequest.ROW_VERSION_PATTERN`).
- **Self-action guards** (400, before any DB call): `SELF_DEACTIVATE_NOT_ALLOWED`, `SELF_DELETE_NOT_ALLOWED`, `SELF_PASSWORD_RESET_NOT_ALLOWED`. The DB's 50304 and 50004 stay authoritative.
- **Temporary passwords:** `TemporaryPasswordGeneratorImpl` (SecureRandom; length = max(16, policy min), capped at the policy max; every character class; no look-alikes `0O1lI`; re-generated until `IPasswordPolicyService` accepts it). Only the BCrypt hash goes to the DB. `TemporaryPasswordResponse` is the only place the plain value appears, and its `toString()` is redacted; never log it.

## Logging rules

- Never log passwords, password hashes, tokens, refresh-token hashes, cookies, the JWT signing key, connection strings or SP parameter values. Records that hold secrets (`LoginRequest`, `ChangePasswordRequest`, `AuthResponse`, `IssuedToken`, `JwtProperties`, `LoginUser`, `TemporaryPasswordResponse`) override `toString()` to redact them; do the same for new ones.
- Every log line carries `correlationId` (MDC). `CorrelationIdFilter` sets it per request, `@Async` and `@Scheduled` work inherit it through `MdcTaskDecorator`, and jobs set their own.
- The `prod` profile logs structured JSON (ECS); `dev` logs a readable pattern.
- Keep `com.zaxxer.hikari`, `com.microsoft.sqlserver` and `org.springframework.jdbc` at INFO or above.

## Tests

- Unit tests for every service impl and for shared infrastructure (Mockito, real MapStruct `XxxMapperImpl`).
- `StoredProcedureExecutorIntegrationTest` runs against the database from the `DB_*` env vars as `enterprise_app`. Keep it read-only; any write must run in a `@Transactional` (rolled-back) test.
- `SecurityMockMvcTest` (`@WebMvcTest`, services mocked, no DB) covers the real security chain:
  - 401 without a token, and for expired or tampered tokens
  - 403 without the permission, and without CSRF
  - CORS preflight allowed and denied
  - 429 with `Retry-After`
  - `PASSWORD_CHANGE_REQUIRED`
  - the Phase 5a permission matrix: every user/role/permission endpoint is 403 `ACCESS_DENIED` with every permission except its own, 2xx with it, and every write is 403 `CSRF_INVALID` without the CSRF header. Add new management endpoints to `managementEndpoints()` with a **valid** body (validation runs before `@PreAuthorize`).
  - Every test uses its own user id and remote IP so the shared rate-limit buckets don't interfere.
- `UserManagementIntegrationTest` (DB, skipped without the `DB_*` env vars) runs create role and user → assign role → login with the temporary password → `PASSWORD_CHANGE_REQUIRED` → change password → edit with a pre-login `concurrencyStamp` (succeeds) → remove the role (the user's session ends) → reset password → lockout → unlock → delete. **It writes to the dev database:**
  - The admin acts with a test-key token for a real admin session created through `usp_Session_Create`; the admin password is not touched, and the session is revoked at the end.
  - Usernames, emails and role names get a random suffix, so repeated runs never collide.
  - The user and role are soft-deleted at the end (also on failure). The soft-deleted rows, `AuditLog` and `LoginAudit` rows stay; `enterprise_app` can't hard-delete.
- `AuthFlowIntegrationTest` runs the full flow against the real DB as the seeded `admin`: login → me → refresh → reuse of the old refresh token (401, all sessions revoked) → login → change password → logout. **It writes to the dev database:**
  - Before and after the test, it resets the admin password to the V11 seed value **`ChangeMe@123!`** through `usp_User_ResetPassword`. That sets `MustChangePassword = 1`, clears any lockout and revokes the admin's sessions.
  - Whatever admin password you had set is replaced, and `PasswordChangedOn` changes.
  - It **leaves `LoginAudit` rows** (plus `AuditLog` rows for the password change and reset) behind.
  - It is skipped without the `DB_*` env vars. It uses the test-only signing key, not `JWT_SIGNING_KEY`.
