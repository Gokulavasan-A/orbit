import { useEffect, useRef, useState } from 'react';
import { authService } from '../../../api/authService';
import { useAppDispatch, useAppSelector } from '../../../app/hooks';
import { env } from '../../../utils/env';
import { loadProfile, logout, selectIsAuthenticated, selectLastActivityAt } from '../authSlice';

/**
 * The server stores `LastActivityOn` at most once a minute, so its idle clock can be up to a
 * minute ahead of ours. The UI deadline is moved forward by that much to never outlive the server.
 */
export const SERVER_ACTIVITY_GRANULARITY_MS = 60_000;

export interface IdleTimeoutState {
  /** True during the warning period before the timeout. */
  warningOpen: boolean;
  /** Seconds until sign-out (meaningful while warningOpen). */
  secondsLeft: number;
  /** True while "Stay signed in" runs. */
  extending: boolean;
  /** POST /refresh, then GET /me (a bearer call resets the server's idle timer). */
  staySignedIn: () => Promise<void>;
  signOut: () => void;
}

/**
 * Idle timeout that mirrors the server's. The server measures idle time from the last
 * authenticated request (usp_Session_Validate), so the clock restarts on every successful
 * authenticated response in any tab, not on mouse or keyboard events.
 */
export function useIdleTimeout(): IdleTimeoutState {
  const dispatch = useAppDispatch();
  const authenticated = useAppSelector(selectIsAuthenticated);
  const lastActivityAt = useAppSelector(selectLastActivityAt);
  const [now, setNow] = useState(Date.now);
  const [extending, setExtending] = useState(false);
  const firedFor = useRef<number | null>(null);

  const deadline =
    authenticated && lastActivityAt !== null
      ? lastActivityAt + env.idleTimeoutMs - SERVER_ACTIVITY_GRANULARITY_MS
      : null;

  useEffect(() => {
    if (deadline === null) {
      return;
    }
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [deadline]);

  const remainingMs = deadline === null ? Number.POSITIVE_INFINITY : deadline - now;

  useEffect(() => {
    if (deadline !== null && remainingMs <= 0 && firedFor.current !== deadline) {
      firedFor.current = deadline;
      void dispatch(logout({ reason: 'idle' }));
    }
  }, [deadline, remainingMs, dispatch]);

  const staySignedIn = async () => {
    setExtending(true);
    try {
      await authService.refresh();
      await dispatch(loadProfile());
    } catch {
      // A failed refresh already ended the session (login page).
    } finally {
      setExtending(false);
    }
  };

  return {
    warningOpen: deadline !== null && remainingMs <= env.idleWarningMs && remainingMs > 0,
    secondsLeft: Math.max(0, Math.ceil(remainingMs / 1000)),
    extending,
    staySignedIn,
    signOut: () => void dispatch(logout()),
  };
}
