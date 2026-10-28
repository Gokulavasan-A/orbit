import { isAxiosError } from 'axios';
import type { ApiResponse, FieldErrorDetail } from '../types/api';
import { CORRELATION_ID_HEADER } from '../utils/correlationId';

/** `error.code` values the UI branches on (see enterprise-api/docs/security.md). */
export const ErrorCodes = {
  VALIDATION_FAILED: 'VALIDATION_FAILED',
  UNAUTHORIZED: 'UNAUTHORIZED',
  TOKEN_EXPIRED: 'TOKEN_EXPIRED',
  TOKEN_INVALID: 'TOKEN_INVALID',
  SESSION_INVALID: 'SESSION_INVALID',
  REFRESH_TOKEN_REUSED: 'REFRESH_TOKEN_REUSED',
  INVALID_CREDENTIALS: 'INVALID_CREDENTIALS',
  ACCOUNT_LOCKED: 'ACCOUNT_LOCKED',
  ACCESS_DENIED: 'ACCESS_DENIED',
  PASSWORD_CHANGE_REQUIRED: 'PASSWORD_CHANGE_REQUIRED',
  CSRF_INVALID: 'CSRF_INVALID',
  RATE_LIMITED: 'RATE_LIMITED',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
  /** Client-side only: no response (server down, CORS, offline). */
  NETWORK_ERROR: 'NETWORK_ERROR',
} as const;

/** Plain, serializable form of an ApiError (safe for Redux actions). */
export interface ApiErrorData {
  /** HTTP status; 0 when there was no response. */
  status: number;
  code: string;
  message: string;
  fieldErrors: FieldErrorDetail[];
  correlationId: string | null;
  /** From the Retry-After header on 429. */
  retryAfterSeconds: number | null;
}

/** The only error type the axios client rejects with. */
export class ApiError extends Error implements ApiErrorData {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldErrorDetail[];
  readonly correlationId: string | null;
  readonly retryAfterSeconds: number | null;

  constructor(data: ApiErrorData) {
    super(data.message);
    this.name = 'ApiError';
    this.status = data.status;
    this.code = data.code;
    this.fieldErrors = data.fieldErrors;
    this.correlationId = data.correlationId;
    this.retryAfterSeconds = data.retryAfterSeconds;
  }

  toData(): ApiErrorData {
    return {
      status: this.status,
      code: this.code,
      message: this.message,
      fieldErrors: this.fieldErrors,
      correlationId: this.correlationId,
      retryAfterSeconds: this.retryAfterSeconds,
    };
  }

  /** Builds an ApiError from anything axios (or an interceptor) can throw. */
  static from(error: unknown): ApiError {
    if (error instanceof ApiError) {
      return error;
    }
    if (isAxiosError<Partial<ApiResponse<unknown>>>(error)) {
      const response = error.response;
      if (!response) {
        return new ApiError({
          status: 0,
          code: ErrorCodes.NETWORK_ERROR,
          message: 'Cannot reach the server. Check your connection and try again.',
          fieldErrors: [],
          correlationId: null,
          retryAfterSeconds: null,
        });
      }
      // The body may be empty or not an envelope (e.g. a proxy error page).
      const body = response.data as Partial<ApiResponse<unknown>> | '' | undefined;
      const envelope = body && typeof body === 'object' ? body : undefined;
      const headerCorrelationId = response.headers[CORRELATION_ID_HEADER.toLowerCase()] as unknown;
      const retryAfter = Number(response.headers['retry-after']);
      return new ApiError({
        status: response.status,
        code:
          envelope?.error?.code ??
          (response.status >= 500 ? ErrorCodes.INTERNAL_ERROR : `HTTP_${response.status}`),
        message: envelope?.error?.message ?? 'An unexpected error occurred.',
        fieldErrors: envelope?.error?.fieldErrors ?? [],
        correlationId:
          envelope?.correlationId ?? (typeof headerCorrelationId === 'string' ? headerCorrelationId : null),
        retryAfterSeconds: Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null,
      });
    }
    return new ApiError({
      status: 0,
      code: ErrorCodes.INTERNAL_ERROR,
      message: error instanceof Error ? error.message : 'An unexpected error occurred.',
      fieldErrors: [],
      correlationId: null,
      retryAfterSeconds: null,
    });
  }
}

/** True for ApiError instances and their serialized ApiErrorData form. */
export function isApiErrorData(value: unknown): value is ApiErrorData {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    'status' in value &&
    'fieldErrors' in value &&
    Array.isArray((value as ApiErrorData).fieldErrors)
  );
}
