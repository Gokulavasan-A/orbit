import type { AuthUser } from '../types/auth';

/** "First Last", or the username when no name is set. */
export function userDisplayName(user: Pick<AuthUser, 'firstName' | 'lastName' | 'username'>): string {
  const full = [user.firstName, user.lastName].filter(Boolean).join(' ');
  return full || user.username;
}
