# CLAUDE.md (enterprise-ui)

Guidance for working in `enterprise-ui`. The root `CLAUDE.md` still applies (workspace rules, frontend architecture). The API's auth contract is in [../enterprise-api/docs/security.md](../enterprise-api/docs/security.md); the UI implements it exactly. Read it before changing anything in `src/api/` or `src/features/auth/`.

## Stack

Node 22.12+ (dev machines run 24), npm. Versions are pinned exactly in `package.json` (no `^`); `package-lock.json` is committed.

- React 19, Vite 8 (Rolldown), TypeScript 6 (`strict`, `noUncheckedIndexedAccess`)
- MUI 9 (+ `@mui/icons-material`, path imports only), Emotion
- Redux Toolkit 2 + react-redux 9, React Router 8 (data router, `react-router` package)
- Formik 2 + Yup 1, AG Grid Community 36 (`ag-grid-react`), axios 1
- Inter via `@fontsource-variable/inter` (self-hosted; no Google Fonts CDN)
- ESLint 10 (flat config, `typescript-eslint` strict type-checked, `react-hooks`, `react-refresh`), Prettier 3
- Vitest 5 + jsdom, React Testing Library, MSW 3

**TypeScript is pinned to 6.0.x on purpose.** The latest `typescript-eslint` (8.72.1) only supports `typescript >=4.8.4 <6.1.0`, and no release supports TS 7 yet. Upgrade to TS 7 only once `typescript-eslint` supports it. TS 6 has no `baseUrl`, and `types` is listed explicitly in the tsconfigs.

## Commands

Run from `enterprise-ui/`.

| What                               | Command                                                          |
| ---------------------------------- | ---------------------------------------------------------------- |
| Install                            | `npm ci`                                                         |
| Dev server (http://localhost:5173) | `npm run dev` (the API must run on :8080 with the `dev` profile) |
| Production build                   | `npm run build` (`tsc -b` + `vite build` → `dist/`)              |
| Preview the build                  | `npm run preview`                                                |
| Lint / typecheck                   | `npm run lint` / `npm run typecheck`                             |
| All tests                          | `npm test`                                                       |
| One test file                      | `npx vitest run src/api/axiosClient.test.ts`                     |
| One test by name                   | `npx vitest run src/api/axiosClient.test.ts -t "single"`         |
| Format / check                     | `npm run format` / `npm run format:check`                        |

## Configuration

- `.env.development` (committed) and `.env.example` hold the `VITE_*` settings. **Every `VITE_*` value is compiled into the public bundle: never put a secret in one.** Local overrides go in `.env.local`, which git ignores.
- `VITE_API_BASE_URL` is the API origin, without a trailing slash. `VITE_SESSION_IDLE_TIMEOUT_MINUTES` must match the API's `app.security.auth.session.idle-timeout` (30). `VITE_SESSION_IDLE_WARNING_SECONDS` (120) sets when the warning appears.
- Read settings only through `utils/env.ts`, which validates them.

## Folder rules (`src/`)

| Folder                                     | Contains                                                                                                                                                                                                                    |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `api/`                                     | `endpoints.ts` (every URL), `axiosClient.ts` (the only axios instance and every interceptor), `apiError.ts`, `csrfToken.ts`, `refreshCoordinator.ts`, `encryption.ts` (empty slot), and one `<module>Service.ts` per module |
| `app/`                                     | `store.ts` (`setupStore`), `hooks.ts` (`useAppDispatch` / `useAppSelector`), `apiBridge.ts` (connects the client and the cross-tab channel to the store)                                                                    |
| `features/<module>/`                       | the slice, `pages/` (default-exported, lazy-loaded), `components/`, `hooks/`, types                                                                                                                                         |
| `components/common/`                       | the shared wrappers. Screens use these, not raw MUI inputs, buttons, dialogs or AG Grid                                                                                                                                     |
| `layouts/`                                 | `AuthLayout`, `MainLayout` (+ `components/`: TopBar, UserMenu, SideNav)                                                                                                                                                     |
| `routes/`                                  | `AppRoutes.tsx` (route tree), `ProtectedRoute`, `PermissionRoute`, `menuConfig.ts`, the 403/404/error pages                                                                                                                 |
| `theme/`                                   | `palette.ts`, `typography.ts`, `theme.ts`, `agGridTheme.ts`, `augmentation.d.ts`                                                                                                                                            |
| `constants/`, `types/`, `hooks/`, `utils/` | permission codes and route paths; API/auth types; shared hooks; helpers                                                                                                                                                     |
| `test/`                                    | MSW server, envelope helpers, `renderWithProviders`, setup                                                                                                                                                                  |

- Services return the unwrapped payload (`response.data`). They never catch errors, build headers or touch tokens.
- Import from `react-router`, not `react-router-dom`.
- A file that exports a component exports only components (Fast Refresh). Put helpers in `utils/`.

## Theme and typography

- **Font sizes and weights are defined only in `theme/typography.ts`:** h1–h6, subtitle1/2, body1/2, caption, and the custom variants `pageTitle`, `sectionTitle`, `label` and `helperText`, which are typed in `augmentation.d.ts`. Screens choose a `variant`. They override only locally, via `sx` or a local `styled` component, and never set a `fontSize` that duplicates a variant.
- Colours come only from `theme/palette.ts`: white background, light grey surfaces, one soft blue primary, muted status colours. No neon or saturated colours, and no hex values in screens.
- `theme.ts` sets component defaults: buttons without uppercase, radius 8, small outlined inputs, dense tables, subtle shadows. Change behaviour there, not per screen.
- MUI 9 has no system props on `Stack` and `Box`: write `sx={{ justifyContent: … }}`, not `justifyContent=…`.
- AG Grid uses `agGridTheme` (Theming API, `themeQuartz.withParams`), built from the MUI tokens. Only `AppGrid` passes it.
- Icons only for navigation, the user menu and grid actions. Use path imports (`@mui/icons-material/HomeOutlined`).

## Auth rules

- **The access token lives only in `authSlice` (memory).** Never use `localStorage` or `sessionStorage`; ESLint blocks both. Redux has no persistence. The CSRF token is a module variable in `api/csrfToken.ts`, taken from the `GET /csrf` body.
- The interceptors in `axiosClient.ts` handle every cross-cutting concern. Components and services never add headers, refresh, or catch 401/403 themselves.
  - Request: `X-Correlation-Id` → Bearer token → `X-XSRF-TOKEN` on POST/PUT/PATCH/DELETE (fetched first if missing) → encryption slot.
  - Response: encryption slot → unwrap `ApiResponse.data`. Errors always reject with `ApiError {status, code, message, fieldErrors, correlationId, retryAfterSeconds}`. Branch on `code`, never on `message`.
  - 401 `TOKEN_EXPIRED`/`TOKEN_INVALID`: one shared refresh, then retry once. Any other 401, or a failed refresh: the session ends (`sessionEnded`) and `ProtectedRoute` shows `/login` with a message.
  - 403 `CSRF_INVALID`: refetch the CSRF token and retry once. 403 `PASSWORD_CHANGE_REQUIRED`: sets `mustChangePassword`, and routing switches to `/change-password`.
  - 429: a toast with the `Retry-After` wait. 5xx and network errors: a toast with the correlation id. Opt out per request with `skipErrorToast`.
  - Request flags: `skipAuth` (no Bearer header; `/csrf`, `/login`, `/refresh`), `skipRefresh` (never refresh on 401; `/login`, `/refresh`, `/change-password`), `skipErrorToast`, `background` (no global progress bar).
- **Refresh tokens are single-use.** Sending one twice signs out every session of the user (`REFRESH_TOKEN_REUSED`). So every refresh goes through `sessionCoordinator` (`api/refreshCoordinator.ts`):
  - single-flight within a tab;
  - a Web Lock across tabs;
  - `bootstrap()` caches its promise, so StrictMode's double effect never sends the cookie twice.
  - Never call `POST /refresh` any other way.
- **Startup:** `AuthBootstrap` shows a full-page loader while it runs `GET /csrf` → `POST /refresh`. If that succeeds, it calls `GET /me`. A 401 at startup just means signed out.
- **Routing follows state, not imperative redirects:**
  - `ProtectedRoute` sends signed-out users to `/login`, keeping `from`.
  - While `mustChangePassword` is set, every route goes to `/change-password`.
  - `PermissionRoute` sends users without the permission to `/403`.
- **Idle timeout:** the server counts idle time from the last authenticated request. So the UI clock (`lastActivityAt`) restarts on every successful authenticated response, in any tab, and not on mouse moves.
  - The deadline is `idle − 60 s`, because the server stores activity at most once a minute.
  - The warning dialog appears `VITE_SESSION_IDLE_WARNING_SECONDS` before the deadline.
  - "Stay signed in" calls refresh, then `GET /me`. When the countdown runs out, the user is logged out with reason `idle`.
- **Multi-tab** (`BroadcastChannel('enterprise-auth')`):
  - `logout`: the other tabs clear their state without calling the API.
  - `session-renewed`, after a password change: the other tabs refresh onto the new session.
  - `activity`: keeps the idle clocks in sync.
- Permissions are the bare codes from the API (`ROLE_VIEW`; constants in `constants/permissions.ts`). `PermissionGate` and `PermissionRoute` only hide UI; the API enforces everything.
- Password fields use `AppPasswordField`, which requires `autoComplete`: `current-password` for an existing password, `new-password` for new and confirm fields. Login usernames use `autoComplete="username"`.
- `features/auth/passwordPolicy.ts` mirrors the API's default policy. The API's `fieldErrors` remain the authority; `AppForm` shows them on the fields.

## Common components

- `AppForm`: a Formik wrapper with Yup, submit/cancel and an error alert. Its `onSubmit` throws (for example `dispatch(thunk).unwrap()`); an `ApiError`'s `fieldErrors` land on the matching fields automatically.
- Formik-bound fields: `AppTextField`, `AppPasswordField`, `AppSelect`, `AppCheckbox`, all keyed by `name`.
- `AppGrid` handles server-paged lists. It uses the client-side row model (the Server-Side Row Model is Enterprise-only).
  - Paging, header sorting and a 300 ms debounced search map to `page`/`pageSize`/`sortColumn`/`sortDirection`/`searchText`.
  - A column's `field` (or `colId`) is sent as `sortColumn`, so it must be a sort id the SP accepts.
  - Pass a stable `fetchPage` (module-level or `useCallback`).
  - Only the AG Grid modules AppGrid needs are registered. If a new grid option needs another module, the dev console (`ValidationModule`) names it: add it to the list in `AppGrid.tsx`.
- `AppButton` (`primary`/`secondary`/`text`/`danger`, `loading`), `AppDialog`, `ConfirmDialog`, `AppLoader` (`page`/`inline`), `PageHeader`, `PermissionGate`, `ErrorBoundary`.
- Toasts: `dispatch(showToast(message, severity))`. `AppToast` is mounted once in App.

## Adding a module and menu item

1. Add the URLs to `api/endpoints.ts`, then create `api/<module>Service.ts` (unwrapped return types, no error handling).
2. Put the types in `features/<module>/<module>Types.ts` and the slice (if needed) in `features/<module>/<module>Slice.ts`, registered in `app/store.ts`.
3. Write the page in `features/<module>/pages/<Name>Page.tsx` as a **default export**, built from `components/common` (PageHeader + AppGrid / AppForm).
4. Add the path to `constants/routes.ts`. Add the route to `routes/AppRoutes.tsx` inside the `MainLayout` children, wrapped in `<PermissionRoute permission={Permissions.X} />` when it needs a permission, and use `lazy: page(() => import(...))`.
5. Add the permission code to `constants/permissions.ts` if it is new, and the entry to `routes/menuConfig.ts` (`label`, `path`, icon, the same `permission`, `description`). The side nav and the home page pick it up.
6. Write tests: the service via MSW, plus any new common component.

## Tests

- Vitest + jsdom. `src/test/setup.ts` starts MSW (`onUnhandledFrame: 'error'`: every request needs a handler) and resets the client's module state after each test (CSRF token, coordinator, channel).
- jsdom runs on `http://api.test`, the same origin as the test API base URL, so jsdom sends no CORS preflights. Real CORS is checked against the running API.
- Use `renderWithProviders` (store connected to the axios client, theme, optional `route`) and `signedInState()`. Build API responses with `okEnvelope` / `errorEnvelope` / `authResponse`.
- Covered: AppButton, AppForm (server field-error mapping), AppGrid (paging, page size, debounced search), authSlice (reducers and thunks), the axios client (single-flight refresh, queued retries, retry-once, refresh failure, no refresh on `/login`, CSRF retry, CSRF header only on state-changing methods, toasts), and the bootstrap (one `/refresh` under concurrent calls and under StrictMode).

## Deployment

- **Serve over HTTPS, on the same site as the API**, i.e. the same registrable domain, such as `app.company.com` and `api.company.com`. The refresh and CSRF cookies are `Secure; SameSite=Strict`, so a cross-site UI never gets them back (see "Deployment requirements" in security.md). Set the API's `APP_CORS_ORIGINS` to the exact UI origin.
- Build with the production `VITE_API_BASE_URL` (`npm run build`) and serve `dist/` as static files. Route every unknown path to `index.html` (SPA fallback), but not missing `/assets/*` files.
- **Response headers** (set on the web server or CDN; Vite does not set them):
  - `Content-Security-Policy: default-src 'self'; connect-src 'self' https://api.company.com; frame-ancestors 'none'`
    - Use the real API origin in `connect-src`. `'self'` is only needed if the UI fetches its own origin.
    - The build uses no inline scripts. MUI/Emotion inject `<style>` tags, so if `style-src` is tightened later, configure an Emotion nonce rather than adding `'unsafe-inline'`.
    - Fonts are self-hosted, so `default-src 'self'` covers them.
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: no-referrer`
  - Also recommended: `Strict-Transport-Security` (HSTS) and `X-Frame-Options: DENY`.
- Caching: `index.html` gets `Cache-Control: no-cache`. Hashed files under `/assets/` get `Cache-Control: public, max-age=31536000, immutable`.
