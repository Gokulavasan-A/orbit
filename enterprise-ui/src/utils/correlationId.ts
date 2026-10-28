/** Header the API reads and echoes on every response. */
export const CORRELATION_ID_HEADER = 'X-Correlation-Id';

/** A new correlation id for one request. */
export function newCorrelationId(): string {
  return crypto.randomUUID();
}
