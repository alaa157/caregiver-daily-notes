import {ApiError, toApiError} from './errors';
import {HttpMethod, RequestOptions, Transport} from './Transport';

export interface FakeResponse {
  status: number;
  body: unknown;
}

export interface FakeCall {
  method: HttpMethod;
  path: string;
}

/** Scripted transport for tests and backend-less dev. Routes keyed `METHOD path`. */
export class FakeTransport implements Transport {
  readonly calls: FakeCall[] = [];

  constructor(private readonly routes: Record<string, FakeResponse | Error>) {}

  async request<T>(method: HttpMethod, path: string, _options?: RequestOptions): Promise<T> {
    this.calls.push({method, path});
    const scripted = this.routes[`${method} ${path}`];
    if (scripted === undefined) {
      throw new ApiError('UNKNOWN', `No scripted route for ${method} ${path}`);
    }
    if (scripted instanceof Error) {
      throw new ApiError('NETWORK', scripted.message);
    }
    if (scripted.status < 200 || scripted.status >= 300) {
      throw toApiError(scripted.status, scripted.body);
    }
    if (
      typeof scripted.body === 'object' &&
      scripted.body !== null &&
      'error' in scripted.body
    ) {
      const marker = (scripted.body as {error: unknown}).error;
      if (typeof marker === 'string' && marker.startsWith('AI_UNAVAILABLE')) {
        throw toApiError(scripted.status, scripted.body);
      }
    }
    return scripted.body as T;
  }
}
