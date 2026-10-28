# Authentication and security (for frontend developers)

The API is stateless. Every request is authenticated with a short-lived **access token** (JWT) in the `Authorization` header. A long-lived **refresh token** lives in an `HttpOnly` cookie that JavaScript cannot read. Every state-changing request also needs a **CSRF token** header.

All responses, including errors, use the `ApiResponse` envelope:

```json
{ "success": false, "data": null,
  "error": { "code": "TOKEN_EXPIRED", "message": "Your access token has expired.", "fieldErrors": [] },
  "correlationId": "…", "timestamp": "2026-10-27T10:00:00Z" }
```

Branch on `error.code`, never on the message text.

## Names at a glance

| What | Name | Notes |
|---|---|---|
| Access token | `Authorization: Bearer <accessToken>` | Keep it **in memory only** (never `localStorage` or `sessionStorage`). It expires after 15 min (`expiresIn` / `expiresAt` in the login response). |
| Refresh token | cookie `refresh_token` | `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, max-age 8 h. Set and rotated by the API; the browser sends it automatically. Requests must use `withCredentials: true`. |
| CSRF cookie | cookie `XSRF-TOKEN` | Readable by JS, `Secure; SameSite=Strict; Path=/`. |
| CSRF header | `X-XSRF-TOKEN: <token>` | Required on every `POST`/`PUT`/`PATCH`/`DELETE`, **including** `/login`, `/refresh` and `/logout`, and even when a bearer token is sent. |
| Correlation id | `X-Correlation-Id` | Optional on requests, always on responses. Quote it in bug reports. |
| Rate limit | `Retry-After: <seconds>` | On every 429. |

## Call order

```
1. GET  /api/v1/auth/csrf            -> data.token (+ XSRF-TOKEN cookie)
2. POST /api/v1/auth/login           X-XSRF-TOKEN, body {username, password}
                                     -> data.accessToken (+ refresh_token cookie)
3. Any API call                      Authorization: Bearer <accessToken>
                                     (+ X-XSRF-TOKEN on state-changing calls)
4. On 401 TOKEN_EXPIRED              POST /api/v1/auth/refresh (X-XSRF-TOKEN, cookie sent automatically)
                                     -> new accessToken, then retry the original call once
5. POST /api/v1/auth/logout          Authorization + X-XSRF-TOKEN -> session ends, refresh cookie cleared
```

- **App start / page reload:** the access token is gone (it was only in memory). Call `GET /csrf`, then `POST /refresh`. If that works, the user is still signed in. If it returns 401, show the login page.
- **CSRF token:** take it from the `GET /csrf` response body (`data.token`) and keep it in memory. Reading the `XSRF-TOKEN` cookie only works when the UI and API share a host. With `app.company.com` and `api.company.com` the cookie belongs to the API host, unless the API sets `app.security.auth.csrf.cookie-domain=company.com`. The token stays the same until the cookie expires or is removed; calling `GET /csrf` again returns the current one. On a 403 `CSRF_INVALID`, call `GET /csrf` once and retry.
- **Refresh concurrency:** run at most **one** refresh at a time and queue other requests behind it. Refresh tokens are single-use. If an old token is sent again (two tabs refreshing in parallel, or a stolen cookie), the API treats it as theft and **signs out every session of the user** (`REFRESH_TOKEN_REUSED`).
- **Do not** send the `Authorization` header to `/csrf`, `/login` or `/refresh`. The API ignores it there, but stale tokens only add noise.
- **Axios interceptor:** do not run refresh-on-401 for `/auth/login`, `/auth/refresh` or `/auth/change-password` responses. A 401 there is final: show the error or the login page.

## Endpoints (`/api/v1/auth`)

| Method and path | Auth | Body | Response `data` |
|---|---|---|---|
| `GET /csrf` | none | | `{headerName, token}` |
| `POST /login` | CSRF | `{username, password}` (username or email) | `AuthResponse` + refresh cookie |
| `POST /refresh` | CSRF + refresh cookie | | `AuthResponse` + rotated refresh cookie |
| `GET /me` | Bearer | | `{user, roles, permissions, mustChangePassword}` |
| `POST /change-password` | Bearer + CSRF | `{currentPassword, newPassword, confirmPassword}` | `AuthResponse` (`mustChangePassword: false`) + new refresh cookie |
| `POST /logout` | Bearer + CSRF | | `null`; refresh cookie cleared |
| `POST /logout-all` | Bearer + CSRF | | `{sessionsRevoked}`. Signs out all **other** sessions; the current one stays. |

`AuthResponse` contains:

| Field | Meaning |
|---|---|
| `accessToken` | The JWT |
| `tokenType` | `"Bearer"` |
| `expiresAt` | Access token expiry (UTC) |
| `expiresIn` | Seconds until `expiresAt` |
| `user` | `{id, username, email, firstName, lastName}` |
| `roles` | Role names |
| `permissions` | Permission codes |
| `mustChangePassword` | When true, only `/me`, `/change-password` and `/logout` are allowed |

Use `roles` and `permissions` to show or hide UI. The API enforces them anyway: role endpoints need `ROLE_VIEW`.

### Must change password

When `mustChangePassword` is `true`, only `GET /me`, `POST /change-password` and `POST /logout` work. Every other call returns **403 `PASSWORD_CHANGE_REQUIRED`**, so route the user to the change-password screen. A successful change returns fresh tokens with `mustChangePassword: false` and signs out the user's other sessions.

### Password policy (defaults, configurable)

- 12 to 64 characters (and at most 72 UTF-8 bytes)
- At least one uppercase letter, one lowercase letter, one digit and one special character
- No spaces
- Not equal to the username
- Not equal to the current password

Violations come back as 400 `VALIDATION_FAILED` with `fieldErrors` on `newPassword`. A wrong current password comes back as 400 with a field error on `currentPassword`, not as 401. It also counts as a failed login for the lockout.

## Error codes

| HTTP | `error.code` | When | What the UI should do |
|---|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation or password-policy errors; see `fieldErrors` | Show the field errors |
| 401 | `UNAUTHORIZED` | No access token sent | Login page |
| 401 | `TOKEN_EXPIRED` | Access token expired | Refresh, then retry once |
| 401 | `TOKEN_INVALID` | Malformed, tampered or foreign token | Refresh; if that fails, login page |
| 401 | `SESSION_INVALID` | Session revoked (logout, logout-all, password change), idle or expired, or user deactivated, deleted or locked. Also returned for a missing, unknown or expired refresh token | Login page |
| 401 | `REFRESH_TOKEN_REUSED` | An already-used refresh token was presented; **all** sessions of the user were revoked | Login page |
| 401 | `INVALID_CREDENTIALS` | Login: unknown user, wrong password, or inactive user (one generic message on purpose) | Show the message |
| 423 | `ACCOUNT_LOCKED` | Too many failed logins (default 5; locked for 15 min) | Show the message (no remaining time is given) |
| 403 | `ACCESS_DENIED` | Missing permission | Show "not allowed" |
| 403 | `PASSWORD_CHANGE_REQUIRED` | See above | Change-password screen |
| 403 | `CSRF_INVALID` | Missing or wrong `X-XSRF-TOKEN` | `GET /csrf`, then retry once |
| 429 | `RATE_LIMITED` | Too many requests | Wait `Retry-After` seconds |

### Rate limits (defaults, configurable under `app.security.rate-limit`)

| Scope | Limit |
|---|---|
| `POST /auth/login` per IP | 10 / min |
| `POST /auth/login` per username | 5 / 15 min (every attempt counts) |
| `POST /auth/refresh` per IP | 30 / min |
| Authenticated requests per user | 300 / min |
| Anonymous requests per IP | 60 / min |
| `GET /actuator/health` | not limited |

The client IP is the TCP remote address. `X-Forwarded-For` is only trusted when the request comes from an address in `app.security.client-ip.trusted-proxies`.

## Deployment requirements

- **Same site:** the UI and API must be deployed on the **same site**, i.e. the same registrable domain, such as `app.company.com` and `api.company.com`. The refresh and CSRF cookies are `SameSite=Strict`, so browsers do not send them on cross-site requests. A UI on `company-app.com` calling `api.company.net` would never get its refresh cookie back. Local dev works because `localhost:5173` and `localhost:8080` are the same site.
- **HTTPS everywhere:** the cookies are `Secure`. Browsers accept `Secure` cookies on `http://localhost` only.
- **CORS:** set `APP_CORS_ORIGINS` to the exact UI origin(s), comma-separated, for example `https://app.company.com`. Wildcards are rejected at startup. The dev default is `http://localhost:5173`.
- **Signing key:** `JWT_SIGNING_KEY` must be Base64 and at least 256 bits. The API refuses to start without it. Rotating it invalidates every issued access token (`TOKEN_INVALID`). Refresh cookies are not affected, so clients recover with one refresh.
- **Response headers:**
  - `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`
  - `X-Frame-Options: DENY`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: no-referrer`
  - `Permissions-Policy`
  - `Cache-Control: no-store`
  - HSTS in the `prod` profile only, sent over HTTPS.

## Running several API instances

Two pieces of state are **in memory, per instance**:

1. **Rate-limit buckets** (Bucket4j `ProxyManager` on Caffeine, in `RateLimitServiceImpl`). With N instances behind a load balancer, each limit is effectively N times higher. To share limits, replace the Caffeine proxy manager with a distributed Bucket4j backend, for example `bucket4j_jdk17-redis` (Lettuce or Redisson), `bucket4j_jdk17-postgresql`/`mysql`, Hazelcast or Infinispan. Only the `ProxyManager` construction changes; the bucket configurations and keys stay the same.
2. **Session-validation cache** (Caffeine in `SessionValidationServiceImpl`, 15 s TTL). Logout evicts the entry only on the instance that handled it. Other instances may still accept that session's access token for up to `app.security.auth.session.validation-cache-ttl`. To close that window, lower the TTL (0 disables caching and checks every request in the DB), or broadcast evictions, for example through Redis pub/sub.

Everything else (sessions, refresh tokens, lockout counters) is in the database and already shared.

## Server-side behaviour

**Sessions**
- Every request with a bearer token is checked against the `UserSession` row named in the token's `sid` claim, using `usp_Session_Validate` with a 30 min idle timeout.
- A session ends when it is logged out, idle, past its 8 h absolute lifetime, or when its user is deactivated or deleted.

**Tokens and authorization**
- Roles and permissions are copied into the access token at login and on refresh. A permission change takes effect at the next refresh (at most 15 min).

**Passwords and login**
- Passwords are hashed with BCrypt (cost 12).
- Unknown usernames still cost one BCrypt comparison, so the response time does not reveal which usernames exist.
- Every login attempt is written to `LoginAudit` in the background.

**Logging**
- The API never logs passwords, tokens, cookies, hashes or the signing key.
