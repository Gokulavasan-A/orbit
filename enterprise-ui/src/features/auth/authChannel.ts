/**
 * Cross-tab auth messages over BroadcastChannel. Tabs of one browser share the refresh cookie and
 * therefore the server session, so what happens to the session in one tab applies to all.
 *
 * - `logout`: another tab signed out (or timed out). Clear state; do not call the API again.
 * - `session-renewed`: another tab changed the password, which revoked the old session and set a
 *   new refresh cookie. Refresh to get a token for the new session.
 * - `activity`: another tab made an authenticated request, so the server's idle timer was reset.
 */
export type AuthChannelMessage =
  | { type: 'logout'; reason: 'signedOut' | 'idle' }
  | { type: 'session-renewed' }
  | { type: 'activity'; at: number };

const CHANNEL_NAME = 'enterprise-auth';

let channel: BroadcastChannel | null = null;

function getChannel(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') {
    return null;
  }
  channel ??= new BroadcastChannel(CHANNEL_NAME);
  return channel;
}

/** Sends a message to every other tab (a tab does not receive its own messages). */
export function postAuthMessage(message: AuthChannelMessage): void {
  getChannel()?.postMessage(message);
}

/** Closes the channel (tests; a later post or subscribe opens a new one). */
export function closeAuthChannel(): void {
  channel?.close();
  channel = null;
}

/** Listens for messages from other tabs. Returns the unsubscribe function. */
export function subscribeAuthMessages(handler: (message: AuthChannelMessage) => void): () => void {
  const target = getChannel();
  if (!target) {
    return () => undefined;
  }
  const listener = (event: MessageEvent<AuthChannelMessage>) => handler(event.data);
  target.addEventListener('message', listener);
  return () => target.removeEventListener('message', listener);
}
