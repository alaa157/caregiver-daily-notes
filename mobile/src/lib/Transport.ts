export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export interface RequestOptions {
  body?: unknown;
}

/** Transport seam: production fetch vs scripted fakes. Never throws ApiError itself. */
export interface Transport {
  request<T>(method: HttpMethod, path: string, options?: RequestOptions): Promise<T>;
}
