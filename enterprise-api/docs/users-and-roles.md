# Users, roles and permissions (for frontend developers)

The endpoint contract for the user and role screens. Authentication, CSRF, the refresh flow and the shared error envelope are described in [security.md](security.md). Everything here needs a valid access token, and every `POST`/`PUT`/`DELETE` needs the `X-XSRF-TOKEN` header.

All responses use the standard envelope: `{success, data, error, correlationId, timestamp}`. Times are ISO-8601 UTC.

## Endpoints at a glance

| Method and path | Permission | Success |
|---|---|---|
| `GET /api/v1/users/paged` | `USER_VIEW` | 200 `PagedResponse<UserListItem>` |
| `GET /api/v1/users/{id}` | `USER_VIEW` | 200 `UserDetail` |
| `POST /api/v1/users` | `USER_CREATE` | **201** `TemporaryPassword` + `Location` header |
| `PUT /api/v1/users/{id}` | `USER_EDIT` | 200 `UserDetail` |
| `PUT /api/v1/users/{id}/roles` | `USER_EDIT` | 200 `UserDetail` |
| `POST /api/v1/users/{id}/reset-password` | `USER_EDIT` | 200 `TemporaryPassword` |
| `POST /api/v1/users/{id}/unlock` | `USER_EDIT` | 200 `UserDetail` |
| `DELETE /api/v1/users/{id}?concurrencyStamp=` | `USER_DELETE` | 200, `data: null` |
| `GET /api/v1/roles` | `ROLE_VIEW` | 200 `Role[]` (existing; `?includeInactive=true`) |
| `GET /api/v1/roles/paged` | `ROLE_VIEW` | 200 `PagedResponse<RoleListItem>` (existing) |
| `GET /api/v1/roles/{id}` | `ROLE_VIEW` | 200 `RoleDetail` |
| `POST /api/v1/roles` | `ROLE_MANAGE` | **201** `RoleDetail` + `Location` header |
| `PUT /api/v1/roles/{id}` | `ROLE_MANAGE` | 200 `RoleDetail` |
| `PUT /api/v1/roles/{id}/permissions` | `ROLE_MANAGE` | 200 `RoleDetail` |
| `DELETE /api/v1/roles/{id}?rowVersion=` | `ROLE_MANAGE` | 200, `data: null` |
| `GET /api/v1/permissions` | `ROLE_VIEW` | 200 `PermissionGroup[]` |

A missing permission gives 403 `ACCESS_DENIED`, and a missing CSRF header gives 403 `CSRF_INVALID`. Show or hide actions with the `permissions` array from `/auth/me`, but treat the server as the authority.

## Users

### `GET /users/paged`

Query parameters, all optional:

| Parameter | Default | Notes |
|---|---|---|
| `page` | 1 | 1-based |
| `pageSize` | 25 | 1–500 |
| `sortColumn` | `createdOn` | `id`, `username`, `email`, `firstName`, `lastName`, `phoneNumber`, `isActive`, `isLockedOut`, `lastLoginOn`, `createdOn`, `modifiedOn`. Unknown columns fall back to the default. |
| `sortDirection` | `DESC` | `ASC` or `DESC` |
| `searchText` | none | Contains search on username, email, first and last name (max 200). Debounce it. |
| `isActive` | all | `true` / `false` |
| `roleId` | all | Only users holding this role |

Row (`UserListItem`):

```json
{
  "id": 42, "username": "jdoe", "email": "jdoe@example.com",
  "firstName": "Jane", "lastName": "Doe", "phoneNumber": null,
  "isActive": true, "isLockedOut": false,
  "lastLoginOn": "2026-10-27T08:15:00Z", "createdOn": "2026-10-01T10:00:00Z", "modifiedOn": null,
  "roles": ["Admin", "User"],
  "concurrencyStamp": "0b9f3c1e-5d8a-4c2b-9e7f-1a2b3c4d5e6f"
}
```

### `GET /users/{id}`

```json
{
  "id": 42, "username": "jdoe", "email": "jdoe@example.com",
  "firstName": "Jane", "lastName": "Doe", "phoneNumber": "+1 555 0100",
  "isActive": true, "isLockedOut": false, "mustChangePassword": true,
  "lastLoginOn": null, "passwordChangedOn": "2026-10-27T08:00:00Z",
  "createdOn": "2026-10-27T08:00:00Z", "modifiedOn": null,
  "roles": [{ "id": 2, "name": "User", "isActive": true }],
  "concurrencyStamp": "0b9f3c1e-5d8a-4c2b-9e7f-1a2b3c4d5e6f"
}
```

An inactive role is still listed but grants no permissions. No response ever contains a password hash.

### `POST /users`: create

```json
{
  "username": "jdoe",
  "email": "jdoe@example.com",
  "firstName": "Jane",
  "lastName": "Doe",
  "phoneNumber": "+1 555 0100",
  "roleIds": [2]
}
```

| Field | Rules |
|---|---|
| `username` | required, 3–100 characters, no `@` and no spaces; can't be changed later |
| `email` | required, valid address, max 256 |
| `firstName`, `lastName` | required, max 100 |
| `phoneNumber` | optional, max 30, digits, spaces and `+ ( ) - .` |
| `roleIds` | optional, max 200 positive ids |

There is no password field. See [the temporary-password flow](#temporary-password-flow).

### `PUT /users/{id}`: edit profile and active flag

```json
{
  "email": "jdoe@example.com", "firstName": "Jane", "lastName": "Doe", "phoneNumber": null,
  "isActive": false,
  "concurrencyStamp": "0b9f3c1e-5d8a-4c2b-9e7f-1a2b3c4d5e6f"
}
```

- `isActive` and `concurrencyStamp` are required. Field rules are the same as for create.
- Deactivating a user ends all of their sessions immediately.
- The response carries the **new** `concurrencyStamp`. Keep it for the next save.

### `PUT /users/{id}/roles`: set the complete role list

```json
{ "roleIds": [2, 5] }
```

- The list replaces the user's roles: missing ids are removed, new ids are added, all in one transaction. `[]` removes every role.
- If **any** role was removed, all of the user's sessions end, so the user must sign in again. Adding roles keeps the sessions; the new permissions arrive with the next token refresh.
- The last active administrator can't lose the Admin role (409 `LAST_ACTIVE_ADMIN`).

### `POST /users/{id}/reset-password`

No body. Returns a new temporary password (see below). It also clears any lockout, sets `mustChangePassword = true` and ends all of the user's sessions. It works on inactive users.

### `POST /users/{id}/unlock`

No body. Clears the failed sign-in counter and the lockout. Sessions are not touched. Returns the user.

### `DELETE /users/{id}`

Soft delete. Optional query parameter `concurrencyStamp`: when given, a stale value gives 409. All of the user's sessions end. The username and email become free for reuse.

### Self-protection

An administrator can't act on their own account in these ways (400, checked before anything is changed):

| Action | Error code |
|---|---|
| Deactivate yourself (`PUT /users/{own id}` with `isActive: false`) | `SELF_DEACTIVATE_NOT_ALLOWED` |
| Delete yourself | `SELF_DELETE_NOT_ALLOWED` |
| Reset your own password (use `POST /auth/change-password`) | `SELF_PASSWORD_RESET_NOT_ALLOWED` |

The database's own safeguards still apply behind these: 409 `CANNOT_MODIFY_SELF` and 409 `LAST_ACTIVE_ADMIN` (nobody can deactivate, delete or remove the Admin role from the last active administrator).

## Temporary-password flow

1. `POST /users` or `POST /users/{id}/reset-password` returns:

   ```json
   { "userId": 42, "username": "jdoe", "temporaryPassword": "k7#Rq2mX!pB9wZ4e" }
   ```

2. The password is generated by the server with a secure random generator and meets the password policy (16 characters by default, with upper case, lower case, digits and special characters, and no look-alike characters such as `0/O` or `1/l/I`). Only its BCrypt hash is stored, and it is never logged.
3. **It is returned only in this one response.** Show it once, offer a copy button, and don't keep it in Redux, local storage or logs. If it's lost, reset the password again.
4. The administrator gives it to the user through a secure channel.
5. The user signs in with it. The login response has `mustChangePassword: true`, and every endpoint except `/auth/me`, `/auth/change-password`, `/auth/logout` and `/auth/refresh` answers 403 `PASSWORD_CHANGE_REQUIRED` until the user changes it (see [security.md](security.md)).

## Roles

### `GET /roles/{id}`

```json
{
  "id": 5, "name": "Auditor", "description": "Read-only access",
  "isSystem": false, "isActive": true, "userCount": 3,
  "createdOn": "2026-10-27T08:00:00Z", "modifiedOn": null,
  "rowVersion": "AAAAAAAAB9E=",
  "permissionIds": [1, 5],
  "permissionCodes": ["ROLE_VIEW", "USER_VIEW"]
}
```

### `POST /roles`

```json
{ "name": "Auditor", "description": "Read-only access", "isActive": true, "permissionIds": [1, 5] }
```

`name` is required (2–100 characters, unique). `description` is optional (max 500). `isActive` defaults to `true`. `permissionIds` is optional.

### `PUT /roles/{id}`

```json
{ "name": "Auditor", "description": "Read-only access", "isActive": false, "rowVersion": "AAAAAAAAB9E=" }
```

- `isActive` and `rowVersion` are required. A stale `rowVersion` gives 409 `CONCURRENCY_CONFLICT`.
- System roles (Admin, User) can't be renamed or deactivated (409 `SYSTEM_ROLE_PROTECTED`). Their description can change.
- Deactivating a role ends the sessions of all of its users.

### `PUT /roles/{id}/permissions`: set the complete permission list

```json
{ "permissionIds": [1, 5, 6] }
```

- Replaces the role's permissions in one transaction, like the user-roles endpoint.
- If **any** permission was removed, the sessions of all users in the role end.
- `ROLE_MANAGE` can't be removed from the Admin role (409 `ADMIN_ROLE_MANAGE_REQUIRED`).

### `DELETE /roles/{id}`

Soft delete; optional query parameter `rowVersion`. The role is removed from all users and their sessions end. System roles give 409 `SYSTEM_ROLE_PROTECTED` (and nothing is changed).

## Permissions

### `GET /permissions`

Active permissions grouped by module, for the role editor:

```json
[
  { "module": "Roles", "permissions": [
      { "id": 6, "code": "ROLE_MANAGE", "name": "Manage roles", "description": "Create, edit and delete roles and assign permissions" },
      { "id": 5, "code": "ROLE_VIEW", "name": "View roles", "description": "View roles and their permissions" } ] },
  { "module": "Users", "permissions": [ ... ] }
]
```

Permissions are defined by the system and can't be created or edited through the API.

## Concurrency

| Entity | Token | Where it comes from | Sent back on |
|---|---|---|---|
| User | `concurrencyStamp` (UUID) | `GET /users/{id}`, `GET /users/paged`, `PUT /users/{id}` response | `PUT /users/{id}` (required), `DELETE /users/{id}` (optional) |
| Role | `rowVersion` (Base64) | `GET /roles`, `GET /roles/{id}`, `PUT /roles/{id}` response | `PUT /roles/{id}` (required), `DELETE /roles/{id}` (optional) |

- A stale value gives 409 `CONCURRENCY_CONFLICT`. Reload the record, show the current values and let the user save again.
- The user's `concurrencyStamp` changes only when an administrator edits or deletes the user. Sign-ins, failed sign-ins, lockouts, unlocks, password changes and resets, and role assignments **don't** change it, so an edit screen doesn't go stale just because the user signed in.
- **Role and permission assignment is last-write-wins.** `PUT /users/{id}/roles` and `PUT /roles/{id}/permissions` take no version: the list in the request becomes the new list. If two administrators edit the same assignments at the same time, the later save wins. Reload before editing assignments, and show the returned list after saving.

## When sessions end

| Action | Whose sessions end |
|---|---|
| User deactivated or deleted | that user |
| Password reset | that user |
| A role removed from a user | that user |
| Permissions removed from a role, role deactivated or deleted | every user in the role |
| Roles or permissions only **added** | nobody; the change arrives with the next token refresh (at most one access-token lifetime) |

An ended session answers 401 `SESSION_INVALID` on the user's next request, and the UI goes back to the login page as described in [security.md](security.md). If administrators remove a role from themselves, their own session ends too.

## Error codes

| HTTP | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A field rule failed; see `error.fieldErrors` |
| 400 | `INVALID_ARGUMENT` | The database rejected a value (the message says which) |
| 400 | `SELF_DEACTIVATE_NOT_ALLOWED`, `SELF_DELETE_NOT_ALLOWED`, `SELF_PASSWORD_RESET_NOT_ALLOWED` | Action on your own account |
| 403 | `ACCESS_DENIED` | Missing permission |
| 403 | `CSRF_INVALID` | Missing or wrong CSRF header |
| 404 | `USER_NOT_FOUND`, `ROLE_NOT_FOUND` | Unknown or deleted id (also for an unknown id in `roleIds`) |
| 404 | `PERMISSION_NOT_FOUND` | Unknown id in `permissionIds` |
| 409 | `CONCURRENCY_CONFLICT` | Stale `concurrencyStamp` / `rowVersion` |
| 409 | `USERNAME_EXISTS`, `EMAIL_EXISTS`, `ROLE_NAME_EXISTS` | Duplicate |
| 409 | `CANNOT_MODIFY_SELF` | Database safeguard: own account |
| 409 | `LAST_ACTIVE_ADMIN` | Would remove the last active administrator |
| 409 | `SYSTEM_ROLE_PROTECTED` | Rename, deactivate or delete a system role |
| 409 | `ADMIN_ROLE_MANAGE_REQUIRED` | Remove `ROLE_MANAGE` from Admin |

The database codes behind these are listed in `enterprise-db/docs/error-codes.md`.
