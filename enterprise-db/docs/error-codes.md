# Error codes

Every business error is raised with `THROW <code>, '<message>', 1`. The API maps `SQLException.getErrorCode()` to an HTTP status and a stable error key. Any number below 50000 is an unexpected SQL Server error, and the API should treat it as a 500.

| Code | Meaning | Raised by | Suggested HTTP |
|---|---|---|---|
| 50002 | Concurrency conflict (`ConcurrencyStamp` mismatch for users, `RowVersion` for roles) | User/Role Update, SoftDelete | 409 |
| 50003 | Invalid or missing argument | all | 400 |
| 50004 | Would remove the last active administrator | User Update/SoftDelete, UserRole_Revoke | 409 |
| 50101 | User not found or inactive (session creation) | Session_Create | 401 |
| 50201 | Session not found | Session_Revoke, RefreshToken_Save | 404 / 401 |
| 50202 | Session revoked or expired | RefreshToken_Save | 401 |
| 50203 | Refresh token not found | RefreshToken_Save (rotation), RefreshToken_Revoke | 401 |
| 50204 | Refresh token already used or revoked (**reuse**) | RefreshToken_Save (rotation) | 401, and call `usp_Session_RevokeAllForUser` |
| 50205 | Session does not belong to the user | RefreshToken_Save, User_ChangePassword | 401 |
| 50301 | Username already exists | User_Create | 409 |
| 50302 | Email already exists | User_Create, User_Update | 409 |
| 50303 | User not found (User_ChangePassword: also inactive) | Auth_Record*, Session_RevokeAllForUser, User_* (incl. ChangePassword/ResetPassword/Unlock), UserRole_* | 404 |
| 50304 | Cannot deactivate or delete your own account | User_Update, User_SoftDelete | 409 |
| 50401 | System role cannot be renamed, deactivated or deleted | Role_Update, Role_SoftDelete | 409 |
| 50402 | Role name already exists | Role_Create, Role_Update | 409 |
| 50403 | Role not found | Role_* (incl. Role_GetById), Session_RevokeAllForRole, User_Create, UserRole_Assign, RolePermission_Assign | 404 |
| 50501 | Permission not found | Role_Create, RolePermission_Assign | 404 |
| 50502 | ROLE_MANAGE cannot be removed from the Admin role | RolePermission_Revoke | 409 |

Ranges reserved for future codes:

| Range | Area |
|---|---|
| 50000–50099 | general |
| 50100–50199 | auth |
| 50200–50299 | session/token |
| 50300–50399 | user |
| 50400–50499 | role |
| 50500–50599 | assignment/permission |

Unique-index races are mapped back to 50301, 50302 and 50402 inside the CATCH blocks, so the API never sees raw error 2601 or 2627 for those cases.
