export type ApiErrorCode =
  | 'NETWORK'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'VALIDATION'
  | 'SERVER'
  | 'AI_UNAVAILABLE'
  | 'UNKNOWN';

export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status?: number;

  constructor(code: ApiErrorCode, message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

export function toApiError(status: number, body: unknown): ApiError {
  const message = readMessage(body) ?? `Request failed with status ${status}`;
  if (typeof body === 'object' && body !== null && 'error' in body) {
    const marker = (body as {error: unknown}).error;
    if (typeof marker === 'string' && marker.startsWith('AI_UNAVAILABLE')) {
      return new ApiError('AI_UNAVAILABLE', marker, status);
    }
  }
  switch (true) {
    case status === 401:
      return new ApiError('UNAUTHORIZED', message, status);
    case status === 403:
      return new ApiError('FORBIDDEN', message, status);
    case status === 400 || status === 422:
      return new ApiError('VALIDATION', message, status);
    case status >= 500:
      return new ApiError('SERVER', message, status);
    default:
      return new ApiError('UNKNOWN', message, status);
  }
}

function readMessage(body: unknown): string | undefined {
  if (typeof body === 'object' && body !== null && 'message' in body) {
    const message = (body as {message: unknown}).message;
    return typeof message === 'string' ? message : undefined;
  }
  return undefined;
}
