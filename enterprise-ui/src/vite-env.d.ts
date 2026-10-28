/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origin of enterprise-api, e.g. http://localhost:8080. */
  readonly VITE_API_BASE_URL: string;
  readonly VITE_APP_NAME?: string;
  /** Must match the API's idle timeout (app.security.auth.session.idle-timeout). */
  readonly VITE_SESSION_IDLE_TIMEOUT_MINUTES?: string;
  readonly VITE_SESSION_IDLE_WARNING_SECONDS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
