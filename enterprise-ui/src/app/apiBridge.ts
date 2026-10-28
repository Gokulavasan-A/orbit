import { configureApiClient, sessionCoordinator } from '../api/axiosClient';
import { postAuthMessage, subscribeAuthMessages } from '../features/auth/authChannel';
import {
  activityRecorded,
  passwordChangeRequired,
  sessionEnded,
  sessionEstablished,
} from '../features/auth/authSlice';
import { requestFinished, requestStarted, showToast } from '../features/ui/uiSlice';
import type { AppStore } from './store';

/** Broadcast our activity to other tabs at most this often. */
const ACTIVITY_BROADCAST_INTERVAL_MS = 15_000;

/**
 * Connects the axios client and the cross-tab channel to the store. Called once in main.tsx
 * (and by tests). Kept out of axiosClient.ts so the api layer does not import the slices.
 * Returns a function that disconnects the channel listener.
 */
export function connectApiClient(store: AppStore): () => void {
  let lastBroadcastAt = 0;

  configureApiClient({
    getAccessToken: () => store.getState().auth.accessToken,
    onRefreshed: (auth) => store.dispatch(sessionEstablished(auth)),
    onSessionEnded: (reason) => {
      if (store.getState().auth.status !== 'unauthenticated') {
        store.dispatch(sessionEnded(reason));
      }
    },
    onPasswordChangeRequired: () => store.dispatch(passwordChangeRequired()),
    onAuthenticatedActivity: () => {
      const now = Date.now();
      store.dispatch(activityRecorded(now));
      if (now - lastBroadcastAt >= ACTIVITY_BROADCAST_INTERVAL_MS) {
        lastBroadcastAt = now;
        postAuthMessage({ type: 'activity', at: now });
      }
    },
    showErrorToast: (message) => store.dispatch(showToast(message, 'error')),
    onRequestStart: () => store.dispatch(requestStarted()),
    onRequestEnd: () => store.dispatch(requestFinished()),
  });

  return subscribeAuthMessages((message) => {
    const signedIn = store.getState().auth.status === 'authenticated';
    switch (message.type) {
      case 'logout':
        if (signedIn) {
          store.dispatch(sessionEnded(message.reason));
        }
        break;
      case 'session-renewed':
        if (signedIn) {
          // Our token belongs to the revoked session; get one for the new session.
          sessionCoordinator.refresh().catch(() => store.dispatch(sessionEnded('revoked')));
        }
        break;
      case 'activity':
        store.dispatch(activityRecorded(message.at));
        break;
    }
  });
}
