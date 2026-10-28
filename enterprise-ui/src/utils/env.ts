/**
 * Typed, validated access to the VITE_* build-time settings. Every value is public (compiled
 * into the bundle), so nothing here may be a secret.
 */
function readNumber(raw: string | undefined, fallback: number, name: string): number {
  if (raw === undefined || raw.trim() === '') {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number.`);
  }
  return value;
}

function readApiBaseUrl(): string {
  const raw = import.meta.env.VITE_API_BASE_URL;
  if (!raw) {
    throw new Error('VITE_API_BASE_URL is not set. See .env.example.');
  }
  return raw.replace(/\/+$/, '');
}

export const env = {
  apiBaseUrl: readApiBaseUrl(),
  appName: import.meta.env.VITE_APP_NAME ?? 'Enterprise Base',
  idleTimeoutMs:
    readNumber(import.meta.env.VITE_SESSION_IDLE_TIMEOUT_MINUTES, 30, 'VITE_SESSION_IDLE_TIMEOUT_MINUTES') *
    60_000,
  idleWarningMs:
    readNumber(import.meta.env.VITE_SESSION_IDLE_WARNING_SECONDS, 120, 'VITE_SESSION_IDLE_WARNING_SECONDS') *
    1000,
} as const;
